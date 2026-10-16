# Browser-style Store build and release workflows

Adapted from `GothwadTech/browser/.github/workflows/{build_apk,quality,release}.yml`.
The same Android flow is retained: JDK 17 → automatic/manual version → Release
APK + Debug APK + AAB → signing and verification → artifacts → GitHub Release.
Names are `GothwadStore-*` / `Gothwad-Store-*`, not `GothwadTVBrowser-*`.
Browser-only Linux/Windows/macOS package scripts are intentionally not copied.

## Workflows

- **Build Gothwad Store Signed APK & AAB** (`build_apk.yml`): runs on `main` and
  the Arena working branch, or manually. Includes a read-only production Worker
  API smoke check, JVM/UI tests, lint, APK/AAB build, APK signature/alignment
  verification and AAB signature verification. Downloads are not triggered by CI.
- **Gothwad Store Quality Gate & Tests** (`quality.yml`): PRs, main pushes and
  manual runs; tests/debug compile/lint. Lint failures are not ignored.
- **Release Gothwad Store to GitHub Releases** (`release.yml`): `v*` tags or
  manual run. Calls the same build workflow instead of duplicating signing code.

GitHub automatically creates the normal `GITHUB_TOKEN`; do not add a PAT.
Feature-branch pushes upload artifacts but do **not** create a GitHub release.
Main pushes publish; manual builds publish only when `publish_release` is true.
Branch builds and temporary-key builds are always marked prerelease.

## Permanent signing (GitHub Actions repository secrets)

Use the same names as Browser, but configure them in **storeapp**:

| Name | Value |
| --- | --- |
| `RELEASE_KEYSTORE_BASE64` | Base64-encoded existing Store signing keystore |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_PASSWORD` | Private-key password |
| `KEY_ALIAS` | Private-key alias; optional only for a single-private-key keystore |

Manage these only through GitHub's repository Settings → Secrets and variables
→ Actions. Never paste them into chat or commit the keystore/base64 file.
Browser's repository-level secrets are not automatically inherited by Store.
Do not assume Browser's signing identity is Store's existing signing identity.
If Store has previously been distributed, reuse its existing key for updates.

If no permanent key is configured, matching Browser's fallback behavior, CI
creates a temporary signing key **for testing only**, deletes it after signing,
and labels output `TestRelease`. It never labels this artifact a production
Release. Installing another run's TestRelease or Debug APK may require uninstall
(and lose app data), because CI signing keys change. Do not distribute these as
an update-stable release. A configured but invalid/incomplete permanent key
fails the job; it does not silently fall back to another identity.

## Output files

Artifact: `Gothwad-Store-v<version>-Build<code>`

- `GothwadStore-v<version>-Release.apk` with permanent key; otherwise
  `GothwadStore-v<version>-TestRelease.apk`.
- `GothwadStore-v<version>-Debug.apk` (development/testing only).
- Matching signed `Release.aab` / `TestRelease.aab`. AAB is an upload bundle,
  **not directly installable** and does not imply Play Store approval.
- `SHA256SUMS.txt` and `SIGNING.txt`.

Reports (including `gradle.log`) are a separate artifact. Keys/passwords never
belong in uploaded outputs. Temporary keys are kept under runner temp and removed
by a shell EXIT trap, including signing failures.

## Versions and local build

Gradle now actually consumes `-PversionCode` and `-PversionName`, unlike the
original Store skeleton that hardcoded both. Automatic values follow Browser:
`github.run_number` and `1.0.<code>`. Inputs are passed as environment variables
and validated before use; unsafe shell snippets are rejected.

**For releases, explicitly supply a code higher than all previously distributed
builds.** GitHub run counters are per workflow, not a global version authority;
tag/manual release runs can have a lower counter than main's build workflow.
Rerunning the same run also reuses its code. No workflow can infer which APKs
have already been distributed privately.

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug \
  -PversionCode=12 -PversionName=1.0.12
```

Without `KEYSTORE_PATH`, local release outputs are unsigned, for the CI signing
step. Optional local release signing supports `KEYSTORE_PATH`,
`KEYSTORE_PASSWORD` (or legacy `STORE_PASSWORD`), `KEY_ALIAS` (default `upload`)
and `KEY_PASSWORD`.

## What success means

A green run verifies compilation/tests/lint, public API shape, signing and
artifact packaging. It does **not** verify all device/OEM installer behaviors,
account login, wishlist/library, review posting, checksum enforcement or update
handling. Those native features are still outstanding; see
`ANDROID_INTEGRATION.md`. A signed APK is not automatically web-feature-parity
or production-ready.

## Verified run — 2026-10-16

[Run 38869108860](https://github.com/GothwadTech/storeapp/actions/runs/38869108860)
completed successfully for code commit `7074039` on the Arena branch.
Production API smoke check, unit/Compose tests, lint, Release/Debug APK and AAB
build, APK signing/alignment verification, AAB signing verification and artifact
upload all passed (6m23s). This is build/contract verification, not a device test.

Download: [Gothwad-Store-v1.0.2-Build2](https://github.com/GothwadTech/storeapp/actions/runs/38869108860/artifacts/12032164722).
It contains the signed **TestRelease** APK/AAB, Debug APK, SIGNING.txt and
SHA256SUMS.txt. The permanent keystore was not configured in this run. Do not
ship it as an update-stable production release. The sandbox cannot download
Azure-hosted Actions artifact blobs; use the GitHub artifact link above.
