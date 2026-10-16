#!/usr/bin/env bash
# Browser's APK/AAB signing flow adapted for Store, with strict failure handling.
set -euo pipefail
umask 077
: "${V_NAME:?}" "${ANDROID_HOME:?}" "${RUNNER_TEMP:?}" "${GITHUB_OUTPUT:?}"
BUILD_TOOLS="$ANDROID_HOME/build-tools/36.0.0"
KS_PATH="$(mktemp "$RUNNER_TEMP/store-signing-XXXXXX.jks")"
rm -f "$KS_PATH" # keytool creates a new keystore, not an empty file.
ALIGNED="$(mktemp "$RUNNER_TEMP/store-aligned-XXXXXX.apk")"
trap 'rm -f "$KS_PATH" "$ALIGNED"' EXIT
mkdir -p output_release

if [[ -n "${KEYSTORE_BASE64:-}" ]]; then
  : "${KEYSTORE_PASS:?KEYSTORE_PASSWORD secret is required with a permanent key}"
  : "${KEY_PASS:?KEY_PASSWORD secret is required with a permanent key}"
  printf '%s' "$KEYSTORE_BASE64" | tr -d '\r\n ' | base64 --decode > "$KS_PATH"
  export KEYSTORE_PASS KEY_PASS
  keytool -list -keystore "$KS_PATH" -storepass:env KEYSTORE_PASS >/dev/null
  if [[ -z "${KEY_ALIAS:-}" ]]; then
    mapfile -t aliases < <(keytool -J-Duser.language=en -J-Duser.country=US -list -v \
      -keystore "$KS_PATH" -storepass:env KEYSTORE_PASS |
      awk '/^Alias name: / {alias=substr($0,13)} /^Entry type: PrivateKeyEntry/ {print alias}')
    if [[ ${#aliases[@]} -ne 1 ]]; then
      echo '::error::Set KEY_ALIAS explicitly for a keystore with multiple/no private key entries.'
      exit 1
    fi
    KEY_ALIAS="${aliases[0]}"
  fi
  BUILD_KIND=Release
  echo 'permanent=true' >> "$GITHUB_OUTPUT"
  printf '%s\n' 'Signed with the configured permanent release key.' > output_release/SIGNING.txt
else
  # Installable testing artifact only. Never pretend this key is update-stable.
  KEYSTORE_PASS="$(openssl rand -hex 24)"
  KEY_PASS="$KEYSTORE_PASS"
  KEY_ALIAS=gothwad_store_test
  echo "::add-mask::$KEYSTORE_PASS"
  export KEYSTORE_PASS KEY_PASS
  keytool -genkeypair -noprompt -keystore "$KS_PATH" -storetype JKS \
    -storepass:env KEYSTORE_PASS -keypass:env KEY_PASS -alias "$KEY_ALIAS" \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -dname 'CN=Gothwad Store CI Test, O=Gothwad Tech, C=IN' >/dev/null 2>&1
  BUILD_KIND=TestRelease
  echo 'permanent=false' >> "$GITHUB_OUTPUT"
  echo '::warning::No permanent release key configured. TestRelease only; future builds cannot update this install without uninstalling.'
  printf '%s\n' 'TEST ONLY: signed with a temporary CI key.' \
    'Do not distribute as a production release. Future builds require uninstall/reinstall.' \
    'App data can be lost on uninstall. Configure permanent signing secrets before publishing.' > output_release/SIGNING.txt
fi

APK=app/build/outputs/apk/release/app-release-unsigned.apk
DEBUG_APK=app/build/outputs/apk/debug/app-debug.apk
AAB=app/build/outputs/bundle/release/app-release.aab
for file in "$APK" "$DEBUG_APK" "$AAB"; do
  [[ -s "$file" ]] || { echo "::error::Missing build output: $file"; exit 1; }
done
"$BUILD_TOOLS/zipalign" -f -P 16 4 "$APK" "$ALIGNED"
"$BUILD_TOOLS/apksigner" sign --ks "$KS_PATH" --ks-key-alias "$KEY_ALIAS" \
  --ks-pass env:KEYSTORE_PASS --key-pass env:KEY_PASS \
  --out "output_release/GothwadStore-v${V_NAME}-${BUILD_KIND}.apk" "$ALIGNED"
"$BUILD_TOOLS/apksigner" verify --verbose --print-certs "output_release/GothwadStore-v${V_NAME}-${BUILD_KIND}.apk"
"$BUILD_TOOLS/zipalign" -c -P 16 4 "output_release/GothwadStore-v${V_NAME}-${BUILD_KIND}.apk"
cp "$DEBUG_APK" "output_release/GothwadStore-v${V_NAME}-Debug.apk"
"$BUILD_TOOLS/apksigner" verify "output_release/GothwadStore-v${V_NAME}-Debug.apk"
cp "$AAB" "output_release/GothwadStore-v${V_NAME}-${BUILD_KIND}.aab"
jarsigner -keystore "$KS_PATH" -storepass:env KEYSTORE_PASS -keypass:env KEY_PASS \
  -sigalg SHA256withRSA -digestalg SHA-256 \
  "output_release/GothwadStore-v${V_NAME}-${BUILD_KIND}.aab" "$KEY_ALIAS"
# A newly created key is self-signed, so trust-chain warnings are expected.
# Validate signature/digest and assert the manifest is actually signed.
jarsigner -verify "output_release/GothwadStore-v${V_NAME}-${BUILD_KIND}.aab" > "$RUNNER_TEMP/store-aab-verification.txt"
grep -q 'jar verified' "$RUNNER_TEMP/store-aab-verification.txt"
(cd output_release && sha256sum ./*.apk ./*.aab > SHA256SUMS.txt)
