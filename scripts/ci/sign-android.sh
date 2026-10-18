#!/usr/bin/env bash
# Browser's APK/AAB signing flow adapted for Store, with strict failure handling.
#
# The output names match GothwadTech/browser and GothwadTech/launcher:
#   KiwiStore-v<version>-Release.apk
#   KiwiStore-v<version>-Debug.apk
#   KiwiStore-v<version>-PlayStore.aab
# SIGNING.txt always states which key signed the build, so a temporary-key
# build is never passed off as an update-stable production release.
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
  echo 'permanent=true' >> "$GITHUB_OUTPUT"
  printf '%s\n' \
    "Kiwi Store v${V_NAME} - signed with the configured permanent release key." \
    'This build can be installed over an earlier release signed with the same key.' \
    'Do not commit the keystore or its secrets.' > output_release/SIGNING.txt
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
    -dname 'CN=Kiwi Store CI Test, O=Gothwad Tech, C=IN' >/dev/null 2>&1
  echo 'permanent=false' >> "$GITHUB_OUTPUT"
  echo '::warning::No permanent release key configured. This build is signed with a temporary CI key: a later build with the permanent key cannot update it, so uninstall before installing that one.'
  printf '%s\n' \
    "Kiwi Store v${V_NAME} - TEST BUILD signed with a temporary CI key." \
    'No permanent release key is configured in the repository secrets, so CI generated a throwaway key for this build.' \
    'It installs and works, but a later permanent-key release cannot update it: uninstall this one first (app data is lost).' \
    'Configure RELEASE_KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_PASSWORD and KEY_ALIAS to publish update-stable builds.' > output_release/SIGNING.txt
fi

APK=app/build/outputs/apk/release/app-release-unsigned.apk
DEBUG_APK=app/build/outputs/apk/debug/app-debug.apk
AAB=app/build/outputs/bundle/release/app-release.aab
for file in "$APK" "$DEBUG_APK" "$AAB"; do
  [[ -s "$file" ]] || { echo "::error::Missing build output: $file"; exit 1; }
done
RELEASE_APK="output_release/KiwiStore-v${V_NAME}-Release.apk"
PLAY_AAB="output_release/KiwiStore-v${V_NAME}-PlayStore.aab"
"$BUILD_TOOLS/zipalign" -f -P 16 4 "$APK" "$ALIGNED"
"$BUILD_TOOLS/apksigner" sign --ks "$KS_PATH" --ks-key-alias "$KEY_ALIAS" \
  --ks-pass env:KEYSTORE_PASS --key-pass env:KEY_PASS \
  --out "$RELEASE_APK" "$ALIGNED"
"$BUILD_TOOLS/apksigner" verify --verbose --print-certs "$RELEASE_APK"
"$BUILD_TOOLS/zipalign" -c -P 16 4 "$RELEASE_APK"
cp "$DEBUG_APK" "output_release/KiwiStore-v${V_NAME}-Debug.apk"
"$BUILD_TOOLS/apksigner" verify "output_release/KiwiStore-v${V_NAME}-Debug.apk"
cp "$AAB" "$PLAY_AAB"
jarsigner -keystore "$KS_PATH" -storepass:env KEYSTORE_PASS -keypass:env KEY_PASS \
  -sigalg SHA256withRSA -digestalg SHA-256 \
  "$PLAY_AAB" "$KEY_ALIAS"
# A newly created key is self-signed, so trust-chain warnings are expected.
# Validate signature/digest and assert the manifest is actually signed.
jarsigner -verify "$PLAY_AAB" > "$RUNNER_TEMP/store-aab-verification.txt"
grep -q 'jar verified' "$RUNNER_TEMP/store-aab-verification.txt"
(cd output_release && sha256sum ./*.apk ./*.aab > SHA256SUMS.txt)
