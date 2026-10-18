# Kiwi Store — Android

Managed by Gothwad Tech & Dark Root. The Android application ID is `com.gothwad.store` (namespace/source package: `com.gothwad.store`). This replaces the earlier `com.aistudio.gothwadstore.kzvqnp` ID: Android treats it as a separate app, so install the new Kiwi Store build alongside the old one or uninstall the old one first. App data does not migrate automatically.

Native Kotlin / Jetpack Compose client for the existing
[GothwadTech/store](https://github.com/GothwadTech/store) Cloudflare Worker.
The app never connects directly to Supabase and includes no backend credentials.

![Kiwi Store launcher icon](docs/play_store_512.png)

## Current implementation

- Live published apps and games from the Worker, with category filters.
- F-Droid's public repository index is merged into Home, Apps, and Search; Gothwad listings win when the same package is present in both sources. F-Droid metadata is cached in memory for six hours.
- F-Droid APKs download directly from F-Droid through the existing foreground download/install flow; they are not proxied, hosted, or counted by Gothwad. A compact “From F-Droid” attribution is shown on list and detail screens.
- Debounced server-side search, canceling superseded requests.
- App details, screenshots, real ratings/reviews, developer replies, release
  notes, permissions and minimum-Android compatibility checks.
- APK downloads run in a foreground service with a notification, resume
  support, publisher checksum verification and four installer back-ends
  (PackageInstaller session, APK intent, Shizuku/Sui and root `pm install`).
  Installation is user-driven, not silent.
- Explicit loading, empty, unavailable and retry states. **No demo fallback**.
- Gothwad Accounts sign-in (OAuth 2.0 Authorization Code + PKCE) is wired to
  the real Accounts service, so the wishlist, the account library and reviews
  are stored in the Worker.

Legacy demo screen files remain for design reference but are not reachable from
`MainActivity → MainStoreScreen`.

## Launcher icon

The launcher icon is the Icon Kitchen set: an adaptive icon
(`ic_launcher.xml` + `ic_launcher_background/foreground/monochrome`) for
Android 8+, plus the legacy PNGs for every density. The Play Store listing
icon lives in [`docs/play_store_512.png`](docs/play_store_512.png).

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

## Releases

Exactly like [GothwadTech/browser](https://github.com/GothwadTech/browser) and
[GothwadTech/launcher](https://github.com/GothwadTech/launcher):

| Trigger | What happens |
| --- | --- |
| **Every push to `main`** | Tests + lint run, the Release APK, Debug APK and Play Store AAB are built, signed, checksummed and published as a GitHub Release (`v1.0.<n>`). |
| `v*` tag push | The same build, published under the tag. |
| Manual run | Same pipeline with an optional version name/code. |

The release assets are `KiwiStore-v<version>-Release.apk`,
`KiwiStore-v<version>-Debug.apk` and `KiwiStore-v<version>-PlayStore.aab`,
plus `SIGNING.txt` and `SHA256SUMS.txt`. The version code is derived from the
run number and is always raised above the last published release, so a new
build can update the previous one instead of failing with a version downgrade.

Signing uses the repository secrets `RELEASE_KEYSTORE_BASE64`,
`KEYSTORE_PASSWORD`, `KEY_PASSWORD` and `KEY_ALIAS`. When no permanent key is
configured, CI signs with a throwaway key and says so in `SIGNING.txt` and in
the release notes - a temporary-key build cannot be updated by a later
permanent-key build, so it has to be uninstalled first.

See [docs/BUILD_AND_RELEASE.md](docs/BUILD_AND_RELEASE.md) for the full
signing, versioning and troubleshooting details.
See [docs/ANDROID_INTEGRATION.md](docs/ANDROID_INTEGRATION.md) for the audited
backend contract, device checks and next steps.

## Latest verified build

The GitHub Actions runs are the verification for this repository - the sandbox
that authors the code has no Java/Android SDK, so compile, test, lint, signing
and signature checks happen in CI. Open the
[Actions tab](https://github.com/GothwadTech/storeapp/actions) for the newest
run and download `KiwiStore-v<version>-Release.apk` from the release or from
the run artifacts.

Device installation and the full native account feature parity are still not
verified end to end.
