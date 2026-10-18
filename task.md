# 🏗️ GOTHWAD STORE — Updated Build Plan (14 Phases)

> **App Name:** Kiwi Store
> **Look:** EXACTLY like Google Play Store (2024 new UI)
> **Brand colour:** sky blue `#0494F4` (the `#01875F` green values below are historical — take colours from `ui/theme/Color.kt`).
> **Tabs (5):** Home | Apps | Search | Games | Profile
> **Top Bar Rule:**
>   - Home, Apps, Games, Profile → "Kiwi Store" title (bold, 20sp, #202124) + Profile avatar (32dp green circle, white "G") on right. NO search bar.
>   - Search tab → ONLY clean search bar (pill shape, grey bg, back arrow, text input, mic icon). No title, no avatar.
> **Data Source:** ONLY Aptoide API (https://ws75.aptoide.com/api/7/). No F-Droid, no backend, no Supabase, no Firebase.
> **Status:** Phase 1 & 2 already DONE. Continue from Phase 3.

---

## PHASE 1: ✅ DONE — Project Setup + Scaffold + Bottom Nav + Top Bars
## PHASE 2: ✅ DONE — Home Tab Filter Chips + Suggested Apps Carousel

---

## PHASE 3: Home Tab — Complete (Banner + Top Free + Games + Trending + Categories)
Continue building "Kiwi Store" (Play Store 2024 clone). Phase 1 & 2 are DONE. HomeScreen already has filter chips row and "Suggested for you" carousel with dummy data. Now complete the Home tab by adding ALL remaining sections below the existing content.

"FEATURED BANNER" (full width, 180dp height, 12dp radius, 16dp margin):

Gradient background: #01875F → #00BCD4.
Left: "New & Updated" (bold, white, 22sp), "Discover the latest apps" (white, 14sp), white filled "Explore" pill button.
Right: 3-4 overlapping decorative app icons (semi-transparent).
"TOP FREE APPS" SECTION:

Heading: "Top free apps" (bold, 18sp, #202124) + right arrow ">" (grey).
Horizontal scroll of small cards (120dp wide each):
a) App icon centered (56dp, squircle, 12dp radius).
b) App name below (bold, 13sp, max 2 lines, centered).
c) Star rating (grey, 11sp, centered).
d) "Install" text button (green #01875F, 12sp).
12dp gap between cards, 16dp horizontal margin.
8 dummy apps.
"RECOMMENDED GAMES" SECTION:

Heading: "Recommended games" (bold, 18sp) + ">".
Horizontal scroll of game cards (200x140dp, 12dp radius):
a) Background: colored placeholder rectangle (game screenshot).
b) Bottom overlay: dark gradient (transparent → black 60%).
c) Game name on overlay (white, bold, 14sp).
d) Rating + small green "Install" button on overlay.
12dp gap, 6 dummy games.
"TRENDING NOW" NUMBERED LIST:

Heading: "Trending now" (bold, 18sp) + ">".
Vertical numbered list (show 5 visible, scrollable within section).
Each row (72dp height, 16dp horizontal padding):
a) Rank number (bold, 18sp, grey #5F6368, 24dp width).
b) App icon (48dp, squircle, 8dp margin).
c) App name (bold, 15sp, #202124) + developer name below (grey, 12sp).
d) Category tag right side (grey pill, 11sp).
e) Star rating (grey, 11sp).
f) Green outlined "Install" pill button on far right.
Divider between rows (#E8EAED, 1dp, 16dp start indent).
10 dummy apps.
"CATEGORIES" GRID:

Heading: "Categories" (bold, 18sp) + ">".
2-column grid of cards (80dp height, 12dp radius, tinted background):
• "Social" (blue #4285F4), "Entertainment" (red #EA4335), "Tools" (yellow #FBBC04),
• "Photography" (green #34A853), "Education" (purple #A142F4), "Finance" (teal #24C1E0),
• "Health" (pink #F538A0), "Shopping" (orange #FA903E).
Each card: colored icon (32dp) + label (bold, 14sp).
8dp gap between items.
80dp bottom padding for bottom nav clearance.

Pull-to-refresh with green spinner at top.

All sections inside a single LazyColumn with 20dp vertical gap between sections.

Give me the COMPLETE updated HomeScreen composable with ALL sections (Phase 2 content + all new sections above) in one scrollable LazyColumn.

---

## PHASE 4: Apps Tab — Complete (Charts + Toggle + Categories + Category Detail)
Continue building "Kiwi Store" (Play Store clone). Now build the COMPLETE Apps tab.

CONTEXT: Top bar = "Kiwi Store" + avatar. No search bar. AppsScreen is currently empty placeholder.

FILTER CHIPS ROW (top of content):

"For you" (default active), "Top charts", "Categories", "Early access".
Active: light green bg (#E6F4EA), green text (#01875F), green border.
Inactive: white bg, grey border (#DADCE0), grey text.
Height 32dp, radius 16dp, 8dp gap, horizontal scroll.
TOP CHARTS SECTION:

Toggle row: "Top free" | "Top grossing" | "Trending" — segmented buttons.
• Active: green bg (#01875F), white text. Inactive: white bg, grey text.
Below toggle: numbered vertical list of 15 dummy apps.
Each row: rank number, app icon (48dp), app name (bold), developer (grey), download count (e.g., "100M+", grey 11sp), green "Install" button.
Dividers between rows.
CATEGORIES GRID (show at bottom or when "Categories" chip active):

Heading: "Browse by category" (bold, 18sp).
2-column grid, 16 categories: "Social", "Communication", "Tools", "Photography", "Education", "Finance", "Health & Fitness", "Shopping", "Travel", "Food & Drink", "Music & Audio", "Video Players", "Books & Reference", "News", "Maps", "Productivity".
Each: colored icon + label, tinted background, 80dp height, 12dp radius.
CATEGORY DETAIL SCREEN (new composable "CategoryDetailScreen"):

Top: back arrow + category name as title.
Filter chips: "Top free", "Top grossing", "New".
Numbered app list (20 dummy apps, same row style).
LazyColumn, 80dp bottom padding.
Navigation: tapping category card → CategoryDetailScreen.
AppsScreen = LazyColumn, 80dp bottom padding.

Give me the complete AppsScreen + CategoryDetailScreen composables.

---

## PHASE 5: Games Tab — Complete (Featured + Cards + Categories)
Continue building "Kiwi Store" (Play Store clone). Build the COMPLETE Games tab.

CONTEXT: Top bar = "Kiwi Store" + avatar. No search bar. GamesScreen is empty placeholder.

FILTER CHIPS: "For you", "Top charts", "Premium", "Categories". Same style.

FEATURED GAME BANNER (full width, 200dp, 12dp radius, 16dp margin):

Dark gradient background.
Game icon (72dp, rounded) left side.
Game name (white, bold, 22sp) + developer (white, 13sp).
Rating (white, 13sp).
Green filled "Install" pill button bottom-right.
"In-app purchases" small text (white, 10sp).
"TOP FREE GAMES" horizontal scroll (cards 160x200dp, 12dp radius):

Top 60%: screenshot placeholder (colored rectangle).
Bottom 40%: white bg, app icon (40dp overlapping screenshot), game name (bold 13sp), rating (11sp), size (e.g., "150 MB", 10sp), green "Install" button.
12dp gap, 8 dummy games.
"NEW & UPDATED GAMES" horizontal scroll (same card style, 6 dummy games).

"TOP GAMES" numbered list (10 dummy games, same row style as Apps tab).

GAME CATEGORIES GRID:

Heading: "Game categories" (bold, 18sp).
2-column grid: "Action" (red), "Puzzle" (blue), "Racing" (orange), "RPG" (purple), "Strategy" (green), "Casual" (yellow), "Sports" (teal), "Simulation" (pink), "Adventure" (brown), "Arcade" (cyan).
Game-themed icons, tinted backgrounds.
LazyColumn, 80dp bottom padding, 20dp gap between sections.

Give me the complete GamesScreen composable.

---

## PHASE 6: Universal App Detail Screen — Complete
Continue building "Kiwi Store" (Play Store clone). Build the UNIVERSAL App Detail Screen used when ANY app is tapped from ANY tab.

FEATURE GRAPHIC BANNER (full width, 200dp):

Placeholder gradient background.
Back arrow (white, 24dp) top-left, share icon (white) top-right, overlaid on banner.
APP HEADER (overlapping banner bottom by 40dp):

App icon (80dp, squircle, 16dp radius, 2dp white border).
App name (bold, 24sp, #202124) right of icon.
Developer name (green #01875F, 14sp, tappable) below name.
Tags: "Contains ads" + "In-app purchases" (grey, 11sp).
STATS ROW (horizontal, centered, vertical dividers between):

Rating "4.5 ★" + "1M reviews" | Downloads "100M+" | Age "PEGI 3" | Size "45 MB".
Each: bold value + grey label below.
INSTALL BUTTON ROW:

Green "Install" button (flex 2, filled, pill, 48dp height, white bold 16sp).
Share icon button (outlined circle) + Bookmark icon button (outlined circle).
SCREENSHOTS horizontal scroll (140x280dp, 12dp radius, 8dp gap, 5 placeholders).

"ABOUT THIS APP" section:

Heading + right arrow.
Description (14sp, grey, max 4 lines, "Read more" green link to expand).
Tags row (horizontal scroll, grey pills).
"Updated on" date.
"RATINGS & REVIEWS" section:

Large "4.5" (48sp) + 5 yellow stars + bar chart (5★ to 1★).
2-3 sample reviews: avatar (32dp) + name + date + stars + text + "Helpful?" icon.
"DATA SAFETY" section:

Shield icon + info text + checklist with green checkmarks.
"See details" link.
"WHAT'S NEW" section: changelog text + version + date.

"SIMILAR APPS" horizontal scroll (6 small app cards).

"DEVELOPER CONTACT" section: name, website, email, privacy policy rows.

LazyColumn, 80dp bottom padding. Use dummy data.

Give me the complete AppDetailScreen composable.

---

## PHASE 7: Search Tab — Clean Dedicated Search Experience
Continue building "Kiwi Store" (Play Store clone). Build the Search tab.

CONTEXT: Search tab top bar has ONLY a search bar (back arrow + text input + mic icon). No title, no avatar. Entire tab is dedicated to search. SearchScreen is currently empty.

DEFAULT STATE (nothing typed yet):

"Trending searches" heading (bold, 16sp).
Vertical list of 8 trending queries with magnifying glass icon: "WhatsApp", "Instagram", "BGMI", "Spotify", "YouTube", "Telegram", "Netflix", "PhonePe".
Each tappable → fills search bar + triggers search.
"Recent searches" heading + "Clear all" green button.
Clock icon + query text + "X" remove button per item.
5 dummy recent searches.
TYPING STATE (user typing, debounce 300ms):

Live suggestion list.
Each: magnifying glass + text + arrow (↗) to auto-fill.
Max 8 suggestions.
RESULTS STATE (after submit):

Filter chips below search bar: "All", "Apps", "Games".
Vertical list of app result rows (icon 48dp, name bold, developer grey, rating, "Install" button).
15 dummy results.
"No results found" empty state with illustration.
Tapping result → AppDetailScreen. Back arrow → Home tab.

LazyColumn, 80dp bottom padding.

Give me the complete SearchScreen with all 3 states.

---

## PHASE 8: Profile Tab — User Header + Settings Menu
Continue building "Kiwi Store" (Play Store clone). Build Profile tab.

CONTEXT: Top bar = "Kiwi Store" + avatar. ProfileScreen is empty placeholder.

USER HEADER (16dp padding):

Avatar (72dp, green circle, white "G").
Name "Gothwad User" (bold, 20sp).
Email "user@gothwad.com" (grey, 14sp).
"Edit Profile" outlined button (green border, green text, pill).
SETTINGS LIST (clickable rows, 56dp each, 16dp padding):

Icon (24dp grey) + Label (15sp) + optional subtext (12sp grey) + chevron ">" (grey).
Divider at bottom of each row.
Items:
🔄 "Manage apps & device" — "Updates available"
🔔 "Notifications"
💳 "Payments & subscriptions"
🔒 "Privacy & security"
👨💻 "Developer Login" — green "Dev" badge, highlighted bg (#F0FFF0)
🌐 "Language & region"
📱 "Parental controls"
⚙️ "Settings"
📄 "Terms of service"
❓ "Help & feedback"
🚪 "Sign out" — RED text (#D93025), no chevron
Bottom: "Kiwi Store v1.0.0" (grey, 12sp, centered).

LazyColumn, 80dp bottom padding.

Give me the complete ProfileScreen composable.

---

## PHASE 9: Data Models + Aptoide API Network Layer
Continue building "Kiwi Store". Now set up the data layer for Aptoide API integration. This is the ONLY data source — no F-Droid, no backend.

APTOIDE API DETAILS:

Base URL: https://ws75.aptoide.com/api/7/
Key endpoints:
a) Top Apps: GET /apps/top/group=TOP&limit=50
b) Search: GET /apps/search?query={query}&limit=20
c) App Details: GET /app?package={package_name}
d) List by Category: GET /listApps/group={category}&limit=20
JSON response per app:
• "name" (string)
• "package" (string, e.g., "com.whatsapp")
• "icon" (string, high-res icon URL)
• "graphic" (string, feature graphic URL)
• "stats" object: "rating" → "avg" (float), "downloads" (int), "pdownloads" (string like "1B")
• "file" object: "vername" (string), "vercode" (int), "filesize" (long, bytes), "path" (string, direct APK download URL), "md5sum" (string)
• "description" (string, HTML)
• "screenshots" (array of image URL strings)
• "store" object: store info
TASK:

DATA MODEL (Kotlin data class "AppItem"):

packageName, name, iconUrl, graphicUrl, description
category, rating (Float), downloadCount (String)
versionName, versionCode (Int), fileSize (Long)
screenshots (List<String>), developerName
apkUrl (direct download link), md5Hash
RETROFIT SETUP:

AptoideApiService interface with all 4 endpoints.
OkHttpClient with: logging interceptor, 15s timeout, User-Agent header.
Gson converter.
Singleton Retrofit instance.
REPOSITORY:

AppRepository class with methods:
• getTopApps(limit): fetches top apps
• searchApps(query, limit): searches
• getAppDetail(packageName): full details
• getAppsByCategory(category, limit): category list
Each method returns Result<List<AppItem>> or Result<AppItem>.
Maps raw Aptoide JSON → AppItem.
VIEWMODEL BASE:

BaseViewModel with common loading/error states.
StateFlow: isLoading (Boolean), error (String?), data (T).
Give me all data classes, Retrofit setup, API interface, repository, and base ViewModel.

---

## PHASE 10: Aptoide API Integration — Fetch Real Apps
Continue building "Kiwi Store". Now connect the Aptoide API to fetch REAL apps.

CONTEXT: Phase 9 data layer is ready. Now use it to fetch real data.

TASK:

HOME VIEWMODEL (HomeViewModel):

On init, fetch from Aptoide:
a) getTopApps(50) → use for "Suggested", "Top Free", "Trending" sections.
b) searchApps("games", 20) → use for "Recommended Games" section.
Expose: homeFeed (StateFlow with all sections data), isLoading, error.
Cache results in SharedPreferences (valid 6 hours).
Show cached data instantly on next launch, refresh in background.
APPS VIEWMODEL (AppsViewModel):

getTopApps(50) for Top Charts.
getAppsByCategory(category) when category tapped.
Toggle support: "Top free" / "Top grossing" / "Trending" (use different Aptoide group params).
GAMES VIEWMODEL (GamesViewModel):

searchApps("games", 30) for game lists.
getAppsByCategory for game categories.
SEARCH VIEWMODEL (SearchViewModel):

searchApps(query, 20) on user input (debounce 300ms).
Expose: searchResults, isSearching, error.
Save search history locally (last 10 queries).
DETAIL VIEWMODEL (DetailViewModel):

getAppDetail(packageName) when app tapped.
Cache detail for 24 hours.
All ViewModels show shimmer loading while fetching.

Error handling: retry button on failure, graceful fallback to cached data.

Give me all 5 ViewModels with Aptoide API calls, caching logic, and state management.

---

## PHASE 11: Connect ALL Tabs to Real Aptoide Data
Continue building "Kiwi Store". Now replace ALL dummy data in ALL screens with real Aptoide data from ViewModels.

TASK:

UPDATE HomeScreen:

Observe HomeViewModel.
Replace dummy apps with real AppItem data in ALL sections.
App icons load from real iconUrl using Coil library.
"Install" button stores real apkUrl from AppItem.
Tapping any app → navigate to AppDetailScreen with packageName.
Shimmer skeleton loading while data loads.
UPDATE AppsScreen + CategoryDetailScreen:

Observe AppsViewModel.
Real top charts data, real category data.
Real icons, ratings, download counts.
UPDATE GamesScreen:

Observe GamesViewModel.
Real game data with real screenshots from graphicUrl.
UPDATE SearchScreen:

Observe SearchViewModel.
Real search results from Aptoide.
Trending searches = top Aptoide queries.
Filter chips filter results client-side.
UPDATE AppDetailScreen:

Observe DetailViewModel.
Real feature graphic, real screenshots, real description, real rating, real APK URL.
Share button shares Aptoide app link.
IMAGE LOADING (Coil):

Add Coil dependency.
AsyncImage composable for all icons and screenshots.
Placeholder: grey shimmer. Error: default app icon.
Crossfade animation 200ms.
Give me all updated screen composables with real data binding and Coil image loading.

---

## PHASE 12: Download & Install System
Continue building "Kiwi Store". Build the APK download and install functionality.

CONTEXT: All "Install" buttons currently do nothing. Now make them download real APKs from Aptoide CDN.

DOWNLOAD MANAGER CLASS:

Uses Android DownloadManager system service.
Takes apkUrl from AppItem.
Downloads to: /storage/emulated/0/Download/KiwiStore/{packageName}.apk
System notification with progress bar.
Progress tracking (0-100%) as StateFlow.
Handles download completion broadcast.
INSTALL FLOW:

Download complete → check REQUEST_INSTALL_PACKAGES permission.
If granted → Intent.ACTION_VIEW with FileProvider URI, type "application/vnd.android.package-archive".
If not granted → show dialog "Allow Kiwi Store to install apps" → opens Settings → Install Unknown Apps.
After permission granted → auto-retry install.
INSTALL BUTTON STATES (on ALL screens):

"Install" (green outlined) → "Downloading 45%" (circular progress) → "Installing..." → "Open" (green filled).
Already installed → "Open" button.
Update available → "Update" (green filled).
Check installed status using PackageManager (compare package name + version code).
SECURITY:

Verify downloaded file size matches API response fileSize.
Verify MD5 hash if available from Aptoide.
Show "✅ Verified" or "⚠️ Unverified" on install button.
PERMISSION HANDLING:

First install: show friendly explanation dialog before requesting.
Handle denial gracefully.
Give me DownloadManager class, InstallHelper, permission handling, and updated install button logic for all screens.

---

## PHASE 13: App Updates + Installed Apps Manager
Continue building "Kiwi Store". Build app update checking and installed apps management.

INSTALLED APPS DETECTION:

InstalledAppsHelper class using PackageManager.
Gets all installed apps: package name, version code, version name, icon.
Compares with Aptoide cached data to find available updates.
"MANAGE APPS" SCREEN (from Profile → "Manage apps & device"):

Tab 1: "Updates available" — installed apps with newer versions on Aptoide.
• Each row: icon, name, current → new version, "Update" button.
• "Update All" button at top.
Tab 2: "Installed" — all installed apps.
• Each row: icon, name, version, size, "Open" + "Uninstall" (red outlined).
Tab 3: "Storage" — total storage used, clear cache option.
BACKGROUND UPDATE CHECK:

WorkManager job every 12 hours.
Checks installed apps vs Aptoide latest versions.
Notification: "{N} app updates available" → opens Manage Apps screen.
Notification channel: "App Updates".
AUTO-UPDATE TOGGLE (in Profile → Settings):

"Auto-update apps" toggle (default off).
If on + WiFi → auto download and install updates.
Give me InstalledAppsHelper, ManageAppsScreen, WorkManager job, and notification code.

---

## PHASE 14: Final Polish — Animations + Caching + Offline + Branding
Continue building "Kiwi Store". FINAL phase — production quality polish.

ANIMATIONS:

Tab switch: crossfade between screens.
Pull-to-refresh: green circular spinner.
Install button: smooth state transitions (color + text morph).
Search tab: slide-up open, slide-down close.
Shimmer skeleton loading for all list items while data loads.
IMAGE CACHING (Coil config):

Memory cache: 50MB, Disk cache: 200MB.
Crossfade 200ms, placeholder shimmer, error default icon.
Preload next 5 items in scrollable lists.
API CACHING:

Aptoide top apps: 6 hours.
Aptoide search: 1 hour.
Aptoide detail: 24 hours.
"Last updated: X ago" label on cached data.
ERROR HANDLING:

No internet: full-screen "No connection" illustration + retry. Load cached data.
Timeout (15s): "Taking too long" + retry.
API error: "Something went wrong" + retry.
Download failed: snackbar + retry.
OFFLINE MODE:

Browse cached apps when offline.
"Showing offline results" banner.
Install: "Available offline" if APK already downloaded.
PERFORMANCE:

LazyColumn keys, remember scroll position per tab.
Debounce search 300ms.
Max 3 concurrent API calls.
Dispatchers.IO for all network/disk.
BRANDING:

Launcher icon: Play Store triangle style with "G" center (green/blue/red/yellow).
Splash screen: "Kiwi Store" logo + "Your Apps, Your Way" tagline (2s fade).
About: "Kiwi Store v1.0.0 — Built with ❤️".
Give me all polish code: animations, Coil config, caching layer, error states, offline mode, splash screen, and launcher icon XML.

---

## 📋 PHASE SUMMARY

| Phase | Status | What |
|-------|--------|------|
| 1 | ✅ DONE | Setup + Scaffold + Nav + Top Bars |
| 2 | ✅ DONE | Home: Chips + Suggested |
| 3 | ✅ DONE | Home: Complete (Banner + Free + Games + Trending + Categories) |
| 4 | ✅ DONE | Apps: Complete (Charts + Categories + Detail) |
| 5 | ✅ DONE | Games: Complete (Featured + Cards + Categories) |
| 6 | ⏳ NEXT | App Detail: Complete (All sections) |
| 7 | | Search: Clean Dedicated Tab |
| 8 | | Profile: User + Settings |
| 9 | | Data Models + Aptoide Network Layer |
| 10 | | Aptoide API Integration (Real Apps) |
| 11 | | Connect All Tabs to Real Data |
| 12 | | Download + Install System |
| 13 | | Updates + Installed Apps Manager |
| 14 | | Final Polish + Offline + Branding |

> **Usage:** Give each phase prompt to AI one by one starting from Phase 3.

---

## Backend-connected Android phase — 2026-10-16

This section supersedes earlier demo-only completion claims for the runtime app.

- [x] Inspect sibling `GothwadTech/store` Worker/API and integration conventions.
- [x] Replace MainActivity's reachable demo flow with Worker-backed catalog,
  app/game filters, categories, debounced search and app details/reviews.
- [x] Explicit loading/error/retry/empty states; no silent fake-data fallback.
- [x] Approved/live/minimum-SDK release selection and system APK downloads.
- [x] Configurable HTTPS API base without client-side database secrets.
- [x] Restore Gradle wrapper, use generated debug key, add contract/UI tests and CI.
- [x] Execute compile, tests, lint and APK build remotely in Actions run 38869108860.
- [ ] Verify live API and downloads on devices, including API 24–28 permissions.
- [ ] Native Accounts PKCE callback, secure session and authenticated actions.
- [ ] Verified download/installer, installed/update state, account library and wishlist.
- [ ] Production polish, accessibility, privacy/support links and release signing.

Details and acceptance checklist: `docs/ANDROID_INTEGRATION.md`.

### Browser-style workflow adaptation — 2026-10-16

- [x] Inspect Browser's build_apk.yml, quality.yml, release.yml and Gradle signing.
- [x] Add Store-named APK/AAB build, quality and reusable tag/manual release flow.
- [x] Wire real version properties, post-build signing/verification, checksums,
  permanent-key secrets and clearly labeled temporary-key test artifacts.
- [x] Add read-only production API contract smoke check to APK workflow.
- [x] Push working branch to trigger CI (no main merge or production release).
- [x] Confirm remote tests/lint/build/signing: run 38869108860 passed; downloadable GitHub test-APK artifact linked in docs.
- [ ] Test on device and finish native authenticated feature parity.


Verified run: https://github.com/GothwadTech/storeapp/actions/runs/38869108860
Artifact: https://github.com/GothwadTech/storeapp/actions/runs/38869108860/artifacts/12032164722
Permanent signing key absent: outputs are TestRelease/Debug, not production-ready.
No device install/end-to-end test has been performed.
