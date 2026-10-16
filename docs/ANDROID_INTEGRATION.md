# Android integration handover — 2026-10-16

## Backend audit

Reviewed `GothwadTech/store` main: HANDOVER, PLAN/TASK, setup/developer docs,
web API/auth/config/types, Worker routes and APK handling, SQL metadata schema,
storefront, console and admin flow. The Worker implementation, not mock Android
models, is the wire-contract authority. No changes made to that repository.

Architecture remains unchanged:

- Android → Store Worker `/api/*` → Store's Supabase metadata database.
- Icons/screenshots remain remote ImgBB images.
- Worker redirects downloads to GitHub storefiles (≤25 MB) or Backblaze B2.
- Publishing/release approval stays in the existing console/admin. Android is
  a consumer, not an alternative publishing or approval backend.

### Implemented routes

| Relative to configured `/api/` | Use |
| --- | --- |
| `GET store/apps?kind=&category=&q=` | Catalog / search; Worker currently caps results at 100 |
| `GET store/categories` | Category chips and kind filtering |
| `GET store/apps/{id}` | Listing, live release, releases and reviews |
| `GET download/{releaseId}` | Counted Worker redirect → APK; system DownloadManager handles redirects |

Wire DTOs use the Worker's camelCase mapping. Unknown fields are ignored;
required identity/status fields are not fabricated. Optional release checksum,
minimum SDK and developer reply support absent/null values. Public catalog
`source: "unconfigured"` is an error, never a cue to show fake content.
Catalog and category errors are independent so a category failure does not
hide an otherwise available catalog.

Only matching app releases with `isLive`, `reviewStatus: approved`, a nonempty
APK reference, and a compatible minimum SDK are offered. The highest version
code wins. Downloads are constructed from the trusted configured API base and
release ID rather than arbitrary listing URLs. The Worker must still enforce
publication/approval checks server-side; client filtering is not access control.
The audited Worker download handler looks up release IDs directly, so that
server-side hardening should be reviewed in the backend repo before launch.

## Download behavior and limits

- System Downloads owns persistent progress, retries and completion notifications.
- API 24–28 requests legacy storage permission before saving to public Downloads;
  API 29+ needs no legacy storage permission.
- Download enqueue is **not** install success, verified bytes, or an account
  library entry. No install/download metrics are fabricated client-side.
- UI links to Downloads; users open the APK there and approve Android's installer.
  The install-source permission belongs to whichever system app opens the APK.
- Failed downloads can be retried from the app. Requests use distinct filenames
  to avoid a prior partial/complete file blocking retry.
- SHA-256 is shown as publisher metadata only. This phase does not verify bytes,
  package identity, signing certificate, installed status or downgrade safety.
- No automatic updates, silent installs or background package enumeration.
- Native verified installer/download state handling is a subsequent phase.

## Authentication deliberately not faked

The existing web client uses Gothwad Accounts Authorization Code + PKCE (S256)
and exchanges codes through `POST /api/auth/exchange`. Its web redirect is
`https://<site>/auth/callback`; a native redirect must be registered and verified
with Accounts before implementing native login. Do not assume browser cookies
become Android HTTP client cookies and do not ship an embedded password form.

Next auth work: review `GothwadTech/accounts/docs/OAUTH.md`, register the native
redirect/client as required by Accounts, use a system browser with PKCE and state
validation, implement secure token storage excluded from backup, session
validation/expiry and logout. Then wire `/me`, wishlist/library and review/report
mutations. No tokens, fake user or sign-in success are persisted by this phase.

## Tests and verification status

Added MockWebServer tests for query encoding, published-only results,
unconfigured errors, live empty catalog, category contract, detail/review
parsing, HTTP 404 propagation and safe compatible-release selection.
Restored missing wrapper scripts/JAR from the official Gradle v9.3.1 tag and
removed the missing custom debug key requirement. Added Android CI.

`git diff --check` passes. `./gradlew --version` cannot start: no Java installed.
No Kotlin compilation, JVM tests, lint, device test or live production API check
has completed in this sandbox. Network access is restricted to source/package
hosts; do not interpret source inspection as verification of the deployed API.

### Required device / CI acceptance checks

1. Run `./gradlew testDebugUnitTest lintDebug assembleDebug` in a supported SDK
   environment; inspect CI reports and resolve any build/lint failures.
2. Point to a configured HTTPS Worker; confirm empty live catalog, populated
   catalog, missing app, unconfigured backend, offline retry and category failure.
3. Switch Apps/Games, search rapidly, choose a category, open a result, rotate,
   background/resume and back out. Ensure old requests do not replace new results.
4. Verify listing/review/screenshots against the web store and publish a new
   approved release via the existing console/admin; refresh to see changes.
5. Test APK redirects for GitHub and private B2, large files, low storage,
   failed/retried downloads and API 24–28 permission denied/granted.
6. Open completed APKs from Downloads on API 24, 28, 29 and 36; check installer
   permission prompts. Do not claim an install until verified on device.
7. Before production, complete real auth, verified installer/integrity handling,
   update/library support, privacy/support links, accessibility/UI tests, final
   branding/application ID review and release signing. Existing application ID
   is preserved to avoid silently breaking any already-distributed APK identity.
