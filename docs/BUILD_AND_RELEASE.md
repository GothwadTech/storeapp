# Browser-style Store build and release workflows

Adapted from `GothwadTech/browser/.github/workflows/{build_apk,quality,release}.yml`
and `GothwadTech/launcher/.github/workflows/{build_apk,release}.yml`.
The same Android flow is retained: JDK 17 → automatic/manual version → Release
APK + Debug APK + AAB → signing and verification → artifacts → GitHub Release.
Browser-only Linux/Windows/macOS package scripts are intentionally not copied.

## Workflows

- **Build Signed Release APK & Play Store AAB** (`build_apk.yml`): runs on
  **every push to `main`** (and `master`), on a `v*` tag through `release.yml`,
  or manually. Includes a read-only production Worker API smoke check,
  JVM/UI tests, lint, APK/AAB build, APK signature/alignment verification and
  AAB signature verification. Downloads are not triggered by CI.
- **Kiwi Store Quality Gate & Tests** (`quality.yml`): PRs, main pushes and
  manual runs; tests/debug compile/lint. Lint failures are not ignored.
- **Release Kiwi Store to GitHub Releases** (`release.yml`): `v*` tags or
  manual run. Calls the same build workflow instead of duplicating signing code.

GitHub automatically creates the normal `GITHUB_TOKEN`; do not add a PAT.

**Every commit on `main` produces a published GitHub Release** (tag
`v1.0.<code>`, name `v1.0.<code>`, not a draft, not a prerelease) with the
signed APK, the Debug APK, the Play Store AAB, `SIGNING.txt` and
`SHA256SUMS.txt` attached - exactly like the Browser and Launcher repos.
Tag and manual runs publish the same way.

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
creates a temporary signing key **for testing only** and deletes it after
signing. Such a build installs and works, but a later permanent-key build
cannot update it: the user has to uninstall first (app data is lost).
`SIGNING.txt` and the release notes state which key was used, so a
temporary-key build is never passed off as update-stable. A configured but
invalid/incomplete permanent key fails the job; it does not silently fall back
to another identity.

## Output files

Artifact: `KiwiStore-v<version>-Build<code>`

- `KiwiStore-v<version>-Release.apk` - the signed, installable build.
- `KiwiStore-v<version>-Debug.apk` (development/testing only).
- `KiwiStore-v<version>-PlayStore.aab` - a signed upload bundle. An AAB is
  **not directly installable** and does not imply Play Store approval.
- `SHA256SUMS.txt` and `SIGNING.txt`.

Reports (including `gradle.log`) are a separate artifact. Keys/passwords never
belong in uploaded outputs. Temporary keys are kept under runner temp and removed
by a shell EXIT trap, including signing failures.

## Versions

`scripts/ci/version.sh` resolves the version before the build:

- the version code is `github.run_number` by default, but it is always raised
  above the highest published release tag (`v<version>`), because a
  tag-triggered run re-uses the run number of the calling workflow and would
  otherwise publish a *lower* version code that Android rejects as a
  downgrade;
- the version name is `1.0.<code>` unless one is supplied;
- both are validated before they reach Gradle (`-PversionCode`, `-PversionName`).

**For a manual release, supply a code higher than all previously distributed
builds.** No workflow can infer which APKs were distributed privately.

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
handling. A signed APK is not automatically web-feature-parity or
production-ready.

## Verified runs

- 2026-10-16: [run 38869108860](https://github.com/GothwadTech/storeapp/actions/runs/38869108860)
  - production API smoke check, unit/Compose tests, lint, Release/Debug APK and
    AAB build, APK signing/alignment verification, AAB signing verification and
    artifact upload all passed. This is build/contract verification, not a
    device test.
- 2026-10-18: the workflows were rewritten to the Browser/Launcher shape - every
  push to `main` publishes a release - and the launcher icon set was replaced
  with the Icon Kitchen assets. The first release published that way is
  [v1.0.15](https://github.com/GothwadTech/storeapp/releases/tag/v1.0.15)
  (run `39101551940`): tests, lint, Release/Debug APK and AAB build, signing,
  signature verification, checksum file and the GitHub Release with
  `KiwiStore-v1.0.15-Release.apk`, `KiwiStore-v1.0.15-Debug.apk`,
  `KiwiStore-v1.0.15-PlayStore.aab`, `SIGNING.txt` and `SHA256SUMS.txt` - all
  signed with the permanent release key.
