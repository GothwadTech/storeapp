# Gothwad Store — Android

Native Kotlin / Jetpack Compose client for the existing
[GothwadTech/store](https://github.com/GothwadTech/store) Cloudflare Worker.
The app never connects directly to Supabase and includes no backend credentials.

## Current implementation

- Live published apps and games from the Worker, with category filters.
- Debounced server-side search, canceling superseded requests.
- App details, screenshots, real ratings/reviews, developer replies, release
  notes, permissions and minimum-Android compatibility checks.
- APK downloads through Android DownloadManager and the Worker's counted
  download redirect. The system Downloads screen shows progress/failures and
  allows opening completed APKs. Installation is user-driven, not silent.
- Explicit loading, empty, unavailable and retry states. **No demo fallback**.
- Profile clearly discloses that native authentication is not wired yet.
  Wishlist, posting reviews, account library and update management are not
  implemented. This is the first backend-connected phase, not a release-ready app.

Legacy demo screen files remain for design reference but are not reachable from
`MainActivity → MainStoreScreen`.

## Build

Use Android Studio with **JDK 17**, Android SDK **36.1**, and the included
Gradle 9.3.1 wrapper. Android 7.0 / API 24 is the minimum device version.

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Default API: `https://store.gothwadtech.com/api/`. For an HTTPS staging Worker:

```sh
./gradlew assembleDebug -PSTORE_API_BASE_URL=https://your-staging-host/api/
```

The URL must include the trailing slash. Do not put tokens/keys in this URL.
Cleartext HTTP is disabled. Android's default debug key is generated locally;
no missing repository-local `debug.keystore` is required anymore. Release
signing retains the existing `KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD`
configuration and `upload` key alias. Do not commit signing keys.

GitHub Actions uses the sibling Browser repo's build/sign/release pattern,
adapted to Store: unit tests, lint, production API smoke checks, Release/Debug
APKs and AAB, signature checks, named artifacts and optional GitHub Releases.
See [docs/BUILD_AND_RELEASE.md](docs/BUILD_AND_RELEASE.md) for signing secrets,
version inputs and test-key limitations. The sandbox lacks Java/Android SDK;
actual compile/test/signing verification happens in GitHub Actions.

See [docs/ANDROID_INTEGRATION.md](docs/ANDROID_INTEGRATION.md) for the audited
backend contract, device checks and next steps.

## Latest verified test APK

[Download Gothwad Store v1.0.2 test build](https://github.com/GothwadTech/storeapp/actions/runs/38869108860/artifacts/12032164722)
from the successful [CI run](https://github.com/GothwadTech/storeapp/actions/runs/38869108860).
Unzip and install `GothwadStore-v1.0.2-TestRelease.apk` for testing. This build is
signed with a temporary key, not an update-stable production key. Tests/lint/build
and API/signature checks passed; device installation and full native account
feature parity are not verified/complete yet.
