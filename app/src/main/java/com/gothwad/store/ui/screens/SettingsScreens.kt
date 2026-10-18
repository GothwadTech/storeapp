package com.gothwad.store.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import com.gothwad.store.BuildConfig
import com.gothwad.store.core.AppGraph
import com.gothwad.store.core.Format
import com.gothwad.store.data.remote.FdroidCatalog
import com.gothwad.store.data.settings.ChartTab
import com.gothwad.store.data.settings.ListingLayout
import com.gothwad.store.data.settings.ThemeMode
import com.gothwad.store.ui.NetworkStatus
import com.gothwad.store.ui.Routes
import com.gothwad.store.ui.StoreEvent
import com.gothwad.store.ui.StoreViewModel
import com.gothwad.store.ui.components.BannerTone
import com.gothwad.store.ui.components.CheckRow
import com.gothwad.store.ui.components.GothwadScaffold
import com.gothwad.store.ui.components.IconTextRow
import com.gothwad.store.ui.components.InfoBanner
import com.gothwad.store.ui.components.KeyValueRow
import com.gothwad.store.ui.components.OutlineButton
import com.gothwad.store.ui.components.RadioSettingRow
import com.gothwad.store.ui.components.SectionHeader
import com.gothwad.store.ui.components.SettingRow
import com.gothwad.store.ui.components.SettingsGroup
import com.gothwad.store.ui.components.StatTile
import com.gothwad.store.ui.components.SubScreenHeader
import com.gothwad.store.ui.components.SwitchSettingRow
import com.gothwad.store.ui.theme.GothwadTheme
import com.gothwad.store.ui.theme.Spacing

@Composable
fun SettingsScreen(vm: StoreViewModel, nav: NavController) {
    val settings by AppGraph.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val capabilities by AppGraph.installer.capabilities.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val current = settings

    GothwadScaffold(
        topBar = { SubScreenHeader(title = "Settings", subtitle = "Kiwi Store", onBack = { nav.popBackStack() }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = Spacing.bottomContentPadding),
        ) {
            item { SectionHeader(title = "Personalisation") }
            item {
                SettingsGroup {
                    SettingRow(
                        title = "Appearance",
                        subtitle = "Theme: ${current?.themeMode?.label() ?: "System"}",
                        icon = Icons.Filled.DarkMode,
                        onClick = { nav.navigate(Routes.APPEARANCE) },
                    )
                    SettingRow(
                        title = "Listing layout",
                        subtitle = current?.listingLayout?.label() ?: "List",
                        onClick = { nav.navigate(Routes.APPEARANCE) },
                    )
                    SettingRow(
                        title = "Default charts tab",
                        subtitle = current?.chartTab?.label ?: "Top free",
                        trailingText = current?.chartTab?.label,
                        onClick = { nav.navigate(Routes.CHARTS.replace("{kind}", "apps")) },
                    )
                }
            }

            item { SectionHeader(title = "Downloads & installs") }
            item {
                SettingsGroup {
                    SettingRow(
                        title = "Download preferences",
                        subtitle = "Wi-Fi only, parallel jobs, checksum, cleanup",
                        icon = Icons.Filled.Tune,
                        onClick = { nav.navigate(Routes.DOWNLOAD_SETTINGS) },
                    )
                    SettingRow(
                        title = "Installer",
                        subtitle = capabilities.healthSummary(),
                        trailingText = current?.installMethod?.displayName(),
                        onClick = { nav.navigate(Routes.INSTALL_SETTINGS) },
                    )
                    SettingRow(
                        title = "Install method",
                        subtitle = "Session, intent, Shizuku, root or ask",
                        onClick = { nav.navigate(Routes.INSTALL_METHOD) },
                    )
                    SettingRow(
                        title = "Storage",
                        subtitle = Format.bytes(AppGraph.downloader.usedBytes()) + " of APKs kept",
                        icon = Icons.Filled.Storage,
                        onClick = { nav.navigate(Routes.STORAGE) },
                    )
                    SettingRow(
                        title = "Download history",
                        subtitle = "Everything the app downloaded",
                        onClick = { nav.navigate(Routes.DOWNLOAD_HISTORY) },
                    )
                }
            }

            item { SectionHeader(title = "Notifications & network") }
            item {
                SettingsGroup {
                    SettingRow(
                        title = "Notifications",
                        subtitle = if (current?.notifyDownloads != false) "Download alerts on" else "All alerts off",
                        onClick = { nav.navigate(Routes.NOTIFICATIONS) },
                    )
                    SettingRow(
                        title = "Network & data",
                        subtitle = if (current?.dataSaver == true) "Data saver on" else "Normal data use",
                        icon = Icons.Filled.Wifi,
                        onClick = { nav.navigate(Routes.NETWORK) },
                    )
                }
            }

            item { SectionHeader(title = "Library") }
            item {
                SettingsGroup {
                    SwitchSettingRow(
                        title = "Sync library with Gothwad",
                        subtitle = "Mirror installed apps to your account so other devices see them",
                        checked = current?.autoSyncLibrary ?: true,
                        onCheckedChange = { scope.launch { AppGraph.settings.setAutoSync(it) } },
                    )
                    SwitchSettingRow(
                        title = "Show installed badges",
                        subtitle = "Mark listings you already have",
                        checked = current?.showInstalledBadges ?: true,
                        onCheckedChange = { scope.launch { AppGraph.settings.setInstalledBadges(it) } },
                    )
                    SwitchSettingRow(
                        title = "Only compatible apps",
                        subtitle = "Hide listings whose release needs a newer Android",
                        checked = current?.showOnlyCompatible ?: true,
                        onCheckedChange = { scope.launch { AppGraph.settings.setCompatibleOnly(it) } },
                    )
                    SettingRow(
                        title = "My apps & device",
                        subtitle = "Updates, library, on-device packages",
                        onClick = { nav.navigate(Routes.MY_APPS) },
                    )
                }
            }

            item { SectionHeader(title = "Privacy & advanced") }
            item {
                SettingsGroup {
                    SettingRow(
                        title = "Privacy",
                        subtitle = "What is stored on this device",
                        onClick = { nav.navigate(Routes.PRIVACY) },
                    )
                    SettingRow(
                        title = "Advanced",
                        subtitle = "API host, cache and diagnostics",
                        icon = Icons.Filled.Tune,
                        onClick = { nav.navigate(Routes.ADVANCED) },
                    )
                    SettingRow(
                        title = "About this device",
                        subtitle = "Android, ABI and app id",
                        icon = Icons.Filled.PhoneAndroid,
                        onClick = { nav.navigate(Routes.DEVICE_INFO) },
                    )
                }
            }

            item { SectionHeader(title = "About") }
            item {
                SettingsGroup {
                    SettingRow(
                        title = "About Kiwi Store",
                        subtitle = "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        onClick = { nav.navigate(Routes.ABOUT) },
                    )
                    SettingRow(
                        title = "Help",
                        subtitle = "Install, download and account problems",
                        onClick = { nav.navigate(Routes.HELP) },
                    )
                    SettingRow(
                        title = "Licences",
                        subtitle = "Open-source notices",
                        onClick = { nav.navigate(Routes.LICENSES) },
                    )
                }
            }
        }
    }
}

private fun ThemeMode.label() = when (this) {
    ThemeMode.System -> "System default"
    ThemeMode.Light -> "Light"
    ThemeMode.Dark -> "Dark"
}

private fun ListingLayout.label() = when (this) {
    ListingLayout.List -> "List"
    ListingLayout.Grid -> "Grid"
}

@Composable
fun AppearanceScreen(vm: StoreViewModel, nav: NavController) {
    val settings by AppGraph.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    val current = settings

    GothwadScaffold(
        topBar = { SubScreenHeader(title = "Appearance", onBack = { nav.popBackStack() }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = Spacing.bottomContentPadding),
        ) {
            item { SectionHeader(title = "Theme") }
            item {
                SettingsGroup {
                    ThemeMode.entries.forEach { mode ->
                        RadioSettingRow(
                            title = mode.label(),
                            subtitle = when (mode) {
                                ThemeMode.System -> "Follow the device dark-mode setting"
                                ThemeMode.Light -> "Always the light Play-style palette"
                                ThemeMode.Dark -> "Always the dark palette"
                            },
                            selected = current?.themeMode == mode,
                            onClick = { scope.launch { AppGraph.settings.setTheme(mode) } },
                        )
                    }
                }
            }
            item { SectionHeader(title = "Listings") }
            item {
                SettingsGroup {
                    ListingLayout.entries.forEach { layout ->
                        RadioSettingRow(
                            title = layout.label(),
                            subtitle = when (layout) {
                                ListingLayout.List -> "One app per row, Play Store style"
                                ListingLayout.Grid -> "Three apps per row on wide screens"
                            },
                            selected = current?.listingLayout == layout,
                            onClick = { scope.launch { AppGraph.settings.setLayout(layout) } },
                        )
                    }
                }
            }
            item { SectionHeader(title = "Charts") }
            item {
                SettingsGroup {
                    ChartTab.entries.forEach { tab ->
                        RadioSettingRow(
                            title = tab.label,
                            subtitle = when (tab) {
                                ChartTab.Free -> "Most downloaded apps and games"
                                ChartTab.Trending -> "Biggest movers right now"
                                ChartTab.New -> "Newest approved releases"
                            },
                            selected = current?.chartTab == tab,
                            onClick = { scope.launch { AppGraph.settings.setChartTab(tab) } },
                        )
                    }
                }
            }
            item {
                Column(Modifier.padding(Spacing.screenH)) {
                    InfoBanner(
                        title = "Colours come from the store, not the theme",
                        message = "App icon tints load from the Worker's icon_url, so listings look the same in " +
                            "light and dark mode just like Google Play.",
                    )
                }
            }
        }
    }
}

@Composable
fun NotificationsScreen(vm: StoreViewModel, nav: NavController) {
    val settings by AppGraph.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    val current = settings

    GothwadScaffold(
        topBar = { SubScreenHeader(title = "Notifications", onBack = { nav.popBackStack() }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = Spacing.bottomContentPadding),
        ) {
            item {
                InfoBanner(
                    title = "Android channel",
                    message = "Kiwi Store uses one notification channel: “Kiwi Store downloads”. " +
                        "Android 13+ asks for the notification permission when you start the first download.",
                )
            }
            item { SectionHeader(title = "Alerts") }
            item {
                SettingsGroup {
                    SwitchSettingRow(
                        title = "Download progress & results",
                        subtitle = "Shows a progress notification and a summary when an APK finishes",
                        checked = current?.notifyDownloads ?: true,
                        onCheckedChange = { scope.launch { AppGraph.settings.setNotifyDownloads(it) } },
                    )
                    SwitchSettingRow(
                        title = "Install results",
                        subtitle = "Success, failure and permission errors",
                        checked = current?.notifyDownloads ?: true,
                        onCheckedChange = { scope.launch { AppGraph.settings.setNotifyDownloads(it) } },
                    )
                    SwitchSettingRow(
                        title = "Update alerts",
                        subtitle = "Tell me when an installed app has a new release",
                        checked = current?.notifyUpdates ?: true,
                        onCheckedChange = { scope.launch { AppGraph.settings.setNotifyUpdates(it) } },
                    )
                }
            }
            item { SectionHeader(title = "Updates") }
            item {
                SettingsGroup {
                    SwitchSettingRow(
                        title = "Automatic update checks",
                        subtitle = "Compare installed version codes with the Worker on app start",
                        checked = current?.autoUpdateInstalledApps ?: false,
                        onCheckedChange = { scope.launch { AppGraph.settings.setAutoUpdate(it) } },
                    )
                    SwitchSettingRow(
                        title = "Wi-Fi only when updating",
                        subtitle = "Never auto-update on mobile data",
                        checked = current?.wifiOnlyUpdates ?: true,
                        onCheckedChange = { scope.launch { AppGraph.settings.setWifiOnlyUpdates(it) } },
                    )
                    SettingRow(
                        title = "Open the update list",
                        subtitle = "Everything with a newer release",
                        onClick = { nav.navigate(Routes.MY_APPS) },
                    )
                }
            }
            item {
                Column(Modifier.padding(Spacing.screenH)) {
                    Spacer(Modifier.height(8.dp))
                    OutlineButton(
                        label = "Open Android notification settings",
                        onClick = {
                            val context = AppGraph.context()
                            runCatching {
                                context.startActivity(
                                    android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                        .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, BuildConfig.APPLICATION_ID)
                                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
fun NetworkScreen(vm: StoreViewModel, nav: NavController) {
    val settings by AppGraph.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    var reachable by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var checking by rememberSaveable { mutableStateOf(false) }
    val current = settings
    val status = NetworkStatus.networkLabel()

    GothwadScaffold(
        topBar = { SubScreenHeader(title = "Network & data", onBack = { nav.popBackStack() }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = Spacing.bottomContentPadding),
        ) {
            item { SectionHeader(title = "Right now") }
            item {
                Column {
                    KeyValueRow("Connection", status)
                    KeyValueRow("Store API", BuildConfig.STORE_API_BASE_URL)
                    KeyValueRow("Worker reachable", reachable?.let { if (it) "Yes" else "No" } ?: "Not checked")
                }
            }
            item { SectionHeader(title = "Data use") }
            item {
                SettingsGroup {
                    SwitchSettingRow(
                        title = "Download over Wi-Fi only",
                        subtitle = "APKs are never fetched on mobile data",
                        checked = current?.downloadOnWifiOnly ?: false,
                        onCheckedChange = { scope.launch { AppGraph.settings.setWifiOnly(it) } },
                    )
                    SwitchSettingRow(
                        title = "Data saver",
                        subtitle = "Pause jobs when the network is metered",
                        checked = current?.dataSaver ?: false,
                        onCheckedChange = { scope.launch { AppGraph.settings.setDataSaver(it) } },
                    )
                    SwitchSettingRow(
                        title = "Wi-Fi only for updates",
                        subtitle = "Keep auto-updates off mobile data",
                        checked = current?.wifiOnlyUpdates ?: true,
                        onCheckedChange = { scope.launch { AppGraph.settings.setWifiOnlyUpdates(it) } },
                    )
                }
            }
            item { SectionHeader(title = "Diagnostics") }
            item {
                SettingsGroup {
                    SettingRow(
                        title = "Test the Store API",
                        subtitle = "Calls /api/health on the Worker",
                        trailingText = if (checking) "…" else "Run",
                        onClick = {
                            checking = true
                            scope.launch {
                                reachable = AppGraph.store.pingHealth()
                                checking = false
                            }
                        },
                    )
                    SettingRow(
                        title = "Resume all downloads",
                        subtitle = "Restart anything paused by the data saver",
                        onClick = { AppGraph.downloader.resumeAll() },
                    )
                }
            }
            item {
                Column(Modifier.padding(Spacing.screenH)) {
                    InfoBanner(
                        title = "Offline behaviour",
                        message = "Kiwi Store never invents a catalogue. Without a reachable Worker the " +
                            "screens show an error with a retry button instead of demo data.",
                    )
                }
            }
        }
    }
}

@Composable
fun PrivacyScreen(vm: StoreViewModel, nav: NavController) {
    val settings by AppGraph.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()

    GothwadScaffold(
        topBar = { SubScreenHeader(title = "Privacy", onBack = { nav.popBackStack() }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = Spacing.bottomContentPadding),
        ) {
            item {
                InfoBanner(
                    title = "No trackers, no analytics SDKs",
                    message = "Kiwi Store talks to two hosts: kiwistore.gothwadtech.com (catalogue, downloads, " +
                        "reviews) and accounts.gothwadtech.com (sign-in). Everything else is on-device.",
                )
            }
            item { SectionHeader(title = "Stored on this device") }
            item {
                Column {
                    KeyValueRow("Settings", "DataStore · gothwad_store_settings")
                    KeyValueRow("Download jobs", "JSON · files/gothwad_downloads.json")
                    KeyValueRow("Library & wishlist", "JSON · files/gothwad_library.json")
                    KeyValueRow("Sign-in tokens", if (settings?.localEmail?.isNotBlank() == true) "SharedPreferences · gothwad_accounts" else "SharedPreferences · gothwad_accounts")
                    KeyValueRow("APK files", AppGraph.downloads.apkDir.absolutePath)
                    KeyValueRow("Search history", "${settings?.searchHistory?.size ?: 0} entr(ies)")
                }
            }
            item { SectionHeader(title = "Your controls") }
            item {
                SettingsGroup {
                    SettingRow(
                        title = "Clear search history",
                        subtitle = "Removes the recent-search chips",
                        onClick = {
                            scope.launch {
                                AppGraph.settings.clearSearchHistory()
                                vm.emit(StoreEvent.Message("Search history cleared."))
                            }
                        },
                    )
                    SettingRow(
                        title = "Forget downloaded APKs",
                        subtitle = "Delete the files but keep the history rows",
                        onClick = { nav.navigate(Routes.STORAGE) },
                    )
                    SettingRow(
                        title = "Sign out",
                        subtitle = "Deletes the tokens on this device",
                        onClick = { nav.navigate(Routes.ACCOUNT_SECURITY) },
                    )
                }
            }
            item { SectionHeader(title = "What leaves the device") }
            item {
                SettingsGroup {
                    IconTextRow(
                        Icons.Filled.Language,
                        "Anonymous-ish download pings: the Worker hashes the client IP with the app id and the " +
                            "UTC day, then throws the hash away. Raw IPs are never stored.",
                    )
                    IconTextRow(
                        Icons.Filled.Storage,
                        "Signed in, the app posts your library entries and reviews so other devices can read them.",
                    )
                    IconTextRow(
                        Icons.Filled.PhoneAndroid,
                        "The Worker is told your app id and version code so it can hand back a compatible release.",
                    )
                }
            }
            item {
                Column(Modifier.padding(Spacing.screenH)) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Full policy: kiwistore.gothwadtech.com/privacy",
                        fontSize = 12.sp,
                        color = GothwadTheme.extra.textTertiary,
                    )
                }
            }
        }
    }
}

@Composable
fun AdvancedScreen(vm: StoreViewModel, nav: NavController) {
    val settings by AppGraph.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val known by vm.knownApps.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var cleared by rememberSaveable { mutableStateOf<String?>(null) }

    GothwadScaffold(
        topBar = { SubScreenHeader(title = "Advanced", onBack = { nav.popBackStack() }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = Spacing.bottomContentPadding),
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.screenH),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    StatTile("Cached listings", "${known.size}", modifier = Modifier.weight(1f))
                    StatTile("Jobs", "${AppGraph.downloads.snapshot().size}", modifier = Modifier.weight(1f))
                    StatTile("APK bytes", Format.bytes(AppGraph.downloader.usedBytes()), modifier = Modifier.weight(1f))
                }
            }
            item { SectionHeader(title = "Endpoints") }
            item {
                Column {
                    KeyValueRow("Store API", BuildConfig.STORE_API_BASE_URL)
                    KeyValueRow("Accounts API", BuildConfig.ACCOUNTS_API_BASE_URL)
                    KeyValueRow("Console", BuildConfig.CONSOLE_URL)
                    KeyValueRow("OAuth client", BuildConfig.OAUTH_CLIENT_ID)
                }
            }
            item { SectionHeader(title = "Catalogue sources") }
            item {
                Column {
                    val fdroid by vm.fdroid.collectAsStateWithLifecycle()
                    val fdroidError by vm.fdroidError.collectAsStateWithLifecycle()
                    KeyValueRow("F-Droid index", FdroidCatalog.INDEX_URL)
                    KeyValueRow("F-Droid apps", "${fdroid.apps.size}")
                    fdroidError?.let { KeyValueRow("F-Droid problem", it) }
                }
            }
            item { SectionHeader(title = "Maintenance") }
            item {
                SettingsGroup {
                    SettingRow(
                        title = "Re-check installer capabilities",
                        subtitle = "Permission, root and Shizuku",
                        onClick = {
                            AppGraph.installer.refreshCapabilities()
                            scope.launch { AppGraph.installer.checkRoot() }
                        },
                    )
                    SettingRow(
                        title = "Prune orphan APK files",
                        subtitle = "Delete files no job points at",
                        onClick = { cleared = "Freed ${Format.bytes(AppGraph.downloader.pruneOrphans())}" },
                    )
                    SettingRow(
                        title = "Re-index installed packages",
                        subtitle = "Rescan PackageManager and rewrite the library file",
                        onClick = {
                            scope.launch {
                                AppGraph.library.refreshInstalled()
                                cleared = "Re-indexed ${AppGraph.library.state.value.installed.size} packages"
                            }
                        },
                    )
                    SettingRow(
                        title = "Reset onboarding",
                        subtitle = "Show the welcome screens again",
                        onClick = { scope.launch { AppGraph.settings.setOnboardingDone(false) } },
                    )
                    SettingRow(
                        title = "Reset search history",
                        subtitle = "Delete the stored terms",
                        onClick = { scope.launch { AppGraph.settings.clearSearchHistory() } },
                    )
                }
            }
            if (cleared != null) {
                item {
                    Column(Modifier.padding(Spacing.screenH)) {
                        InfoBanner(title = "Done", message = cleared.orEmpty())
                    }
                }
            }
            item { SectionHeader(title = "Diagnostics") }
            item {
                Column {
                    KeyValueRow("Last update check", Format.relative(settings?.lastUpdateCheck ?: 0L))
                    KeyValueRow("Cache mode", "Memory only — nothing is persisted from the Worker")
                    KeyValueRow("Min SDK", "24 (Android 7.0)")
                }
            }
            item {
                Column(Modifier.padding(Spacing.screenH)) {
                    Spacer(Modifier.height(10.dp))
                    OutlineButton(
                        label = "Open download preferences",
                        onClick = { nav.navigate(Routes.DOWNLOAD_SETTINGS) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
fun DeviceInfoScreen(vm: StoreViewModel, nav: NavController) {
    val capabilities by AppGraph.installer.capabilities.collectAsStateWithLifecycle()

    GothwadScaffold(
        topBar = { SubScreenHeader(title = "About this device", onBack = { nav.popBackStack() }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = Spacing.bottomContentPadding),
        ) {
            item { SectionHeader(title = "Android") }
            item {
                Column {
                    KeyValueRow("Version", "${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
                    KeyValueRow("Security patch", android.os.Build.VERSION.SECURITY_PATCH.ifBlank { "—" })
                    KeyValueRow("Device", "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
                    KeyValueRow("Hardware", android.os.Build.HARDWARE)
                    KeyValueRow("Supported ABIs", android.os.Build.SUPPORTED_ABIS.joinToString(", "))
                }
            }
            item { SectionHeader(title = "Kiwi Store") }
            item {
                Column {
                    KeyValueRow("Version", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                    KeyValueRow("Application id", BuildConfig.APPLICATION_ID)
                    KeyValueRow("Min / target SDK", "24 / 36")
                    KeyValueRow("Install permission", if (capabilities.installPermissionGranted) "Granted" else "Not granted")
                    KeyValueRow("Shizuku", capabilities.healthSummary())
                    KeyValueRow("Root", if (capabilities.rootGranted) "Granted" else "Not granted")
                }
            }
            item { SectionHeader(title = "Storage") }
            item {
                Column {
                    KeyValueRow("APK directory", AppGraph.downloads.apkDir.absolutePath)
                    KeyValueRow("APK bytes", Format.bytes(AppGraph.downloader.usedBytes()))
                    KeyValueRow("Free space", Format.bytes(AppGraph.context().cacheDir.usableSpace.coerceAtLeast(0L)))
                }
            }
            item {
                Column(Modifier.padding(Spacing.screenH)) {
                    InfoBanner(
                        title = "Sharing diagnostics",
                        message = "Everything on this screen is read from the device — nothing is uploaded. " +
                            "Screenshot it when reporting an install problem.",
                    )
                }
            }
        }
    }
}
