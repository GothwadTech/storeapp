package com.gothwad.store.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.gothwad.store.data.install.InstallMethod
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

enum class ThemeMode(val id: String, val label: String) {
    System("system", "Follow system"),
    Light("light", "Light"),
    Dark("dark", "Dark");

    companion object {
        fun fromId(id: String?): ThemeMode = entries.firstOrNull { it.id == id } ?: System
    }
}

enum class ListingLayout(val id: String, val label: String) {
    List("list", "List"),
    Grid("grid", "Grid");

    companion object {
        fun fromId(id: String?): ListingLayout = entries.firstOrNull { it.id == id } ?: List
    }
}

enum class ChartTab(val id: String, val label: String) {
    Free("free", "Top free"),
    Trending("trending", "Trending"),
    New("new", "New & updated");

    companion object {
        fun fromId(id: String?): ChartTab = entries.firstOrNull { it.id == id } ?: Free
    }
}

/** Everything the "Download", "Install" and "Appearance" screens persist. */
data class Settings(
    val themeMode: ThemeMode = ThemeMode.System,
    val listingLayout: ListingLayout = ListingLayout.List,
    val chartTab: ChartTab = ChartTab.Free,
    val downloadOnWifiOnly: Boolean = false,
    val downloadParallel: Int = 1,
    val autoInstallAfterDownload: Boolean = false,
    val deleteApkAfterInstall: Boolean = false,
    val keepApkAfterInstall: Boolean = true,
    val confirmBeforeInstall: Boolean = true,
    val verifyChecksum: Boolean = true,
    val allowDowngrade: Boolean = false,
    val installMethod: InstallMethod = InstallMethod.Session,
    val autoUpdateInstalledApps: Boolean = false,
    val wifiOnlyUpdates: Boolean = true,
    val notifyDownloads: Boolean = true,
    val notifyUpdates: Boolean = true,
    val dataSaver: Boolean = false,
    val showOnlyCompatible: Boolean = true,
    val showInstalledBadges: Boolean = true,
    val autoSyncLibrary: Boolean = true,
    val localDisplayName: String = "",
    val localEmail: String = "",
    val historyEnabled: Boolean = true,
    val lastUpdateCheck: Long = 0,
    val onboardingDone: Boolean = false,
    val searchHistory: List<String> = emptyList(),
    /** App ids the user opened, newest first. Shown at the bottom of Search. */
    val recentlyViewed: List<String> = emptyList(),
)

private val Context.settingStore: DataStore<Preferences> by preferencesDataStore("gothwad_store_settings")

/**
 * DataStore-backed settings. Exposed as a single immutable [Settings] snapshot so
 * Compose screens can read one value instead of a dozen flows.
 */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val theme = stringPreferencesKey("theme_mode")
        val layout = stringPreferencesKey("listing_layout")
        val chartTab = stringPreferencesKey("chart_tab")
        val wifiOnly = booleanPreferencesKey("download_wifi_only")
        val parallel = intPreferencesKey("download_parallel")
        val autoInstall = booleanPreferencesKey("auto_install")
        val deleteApk = booleanPreferencesKey("delete_apk_after_install")
        val keepApk = booleanPreferencesKey("keep_apk_after_install")
        val confirmInstall = booleanPreferencesKey("confirm_install")
        val verifyChecksum = booleanPreferencesKey("verify_checksum")
        val allowDowngrade = booleanPreferencesKey("allow_downgrade")
        val installMethod = stringPreferencesKey("install_method")
        val autoUpdate = booleanPreferencesKey("auto_update")
        val wifiOnlyUpdates = booleanPreferencesKey("wifi_only_updates")
        val notifyDownloads = booleanPreferencesKey("notify_downloads")
        val notifyUpdates = booleanPreferencesKey("notify_updates")
        val dataSaver = booleanPreferencesKey("data_saver")
        val compatibleOnly = booleanPreferencesKey("compatible_only")
        val installedBadges = booleanPreferencesKey("installed_badges")
        val autoSync = booleanPreferencesKey("auto_sync")
        val localName = stringPreferencesKey("local_display_name")
        val localEmail = stringPreferencesKey("local_email")
        val historyEnabled = booleanPreferencesKey("history_enabled")
        val lastUpdateCheck = longPreferencesKey("last_update_check")
        val onboarding = booleanPreferencesKey("onboarding_done")
        // The search history keeps the user's order (newest first). A string
        // set cannot do that, so the terms live in one newline separated value.
        val searchHistory = stringPreferencesKey("search_history_ordered")
        val searchHistoryLegacy = stringSetPreferencesKey("search_history")
        val recentlyViewed = stringPreferencesKey("recently_viewed_apps")
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Nullable on purpose: `null` means DataStore has not answered yet, so the UI
     * can tell "still loading" apart from "user chose the default".
     */
    val settings: StateFlow<Settings?> = context.settingStore.data.map { prefs ->
        Settings(
            themeMode = ThemeMode.fromId(prefs[Keys.theme]),
            listingLayout = ListingLayout.fromId(prefs[Keys.layout]),
            chartTab = ChartTab.fromId(prefs[Keys.chartTab]),
            downloadOnWifiOnly = prefs[Keys.wifiOnly] ?: false,
            downloadParallel = (prefs[Keys.parallel] ?: 1).coerceIn(1, 3),
            autoInstallAfterDownload = prefs[Keys.autoInstall] ?: false,
            deleteApkAfterInstall = prefs[Keys.deleteApk] ?: false,
            keepApkAfterInstall = prefs[Keys.keepApk] ?: true,
            confirmBeforeInstall = prefs[Keys.confirmInstall] ?: true,
            verifyChecksum = prefs[Keys.verifyChecksum] ?: true,
            allowDowngrade = prefs[Keys.allowDowngrade] ?: false,
            installMethod = InstallMethod.fromId(prefs[Keys.installMethod]),
            autoUpdateInstalledApps = prefs[Keys.autoUpdate] ?: false,
            wifiOnlyUpdates = prefs[Keys.wifiOnlyUpdates] ?: true,
            notifyDownloads = prefs[Keys.notifyDownloads] ?: true,
            notifyUpdates = prefs[Keys.notifyUpdates] ?: true,
            dataSaver = prefs[Keys.dataSaver] ?: false,
            showOnlyCompatible = prefs[Keys.compatibleOnly] ?: true,
            showInstalledBadges = prefs[Keys.installedBadges] ?: true,
            autoSyncLibrary = prefs[Keys.autoSync] ?: true,
            localDisplayName = prefs[Keys.localName] ?: "",
            localEmail = prefs[Keys.localEmail] ?: "",
            historyEnabled = prefs[Keys.historyEnabled] ?: true,
            lastUpdateCheck = prefs[Keys.lastUpdateCheck] ?: 0L,
            onboardingDone = prefs[Keys.onboarding] ?: false,
            searchHistory = readSearchHistory(prefs),
            recentlyViewed = readRecentlyViewed(prefs),
        )
    }.stateIn(scope, SharingStarted.Eagerly, null)

    /** Snapshot for non-Compose callers (services, installers). */
    val current: Settings get() = settings.value ?: Settings()

    suspend fun setTheme(mode: ThemeMode) = edit { it[Keys.theme] = mode.id }
    suspend fun setLayout(layout: ListingLayout) = edit { it[Keys.layout] = layout.id }
    suspend fun setChartTab(tab: ChartTab) = edit { it[Keys.chartTab] = tab.id }
    suspend fun setWifiOnly(value: Boolean) = edit { it[Keys.wifiOnly] = value }
    suspend fun setParallel(value: Int) = edit { it[Keys.parallel] = value.coerceIn(1, 3) }
    suspend fun setAutoInstall(value: Boolean) = edit { it[Keys.autoInstall] = value }
    suspend fun setDeleteApkAfterInstall(value: Boolean) = edit { it[Keys.deleteApk] = value }
    suspend fun setKeepApk(value: Boolean) = edit { it[Keys.keepApk] = value }
    suspend fun setConfirmBeforeInstall(value: Boolean) = edit { it[Keys.confirmInstall] = value }
    suspend fun setVerifyChecksum(value: Boolean) = edit { it[Keys.verifyChecksum] = value }
    suspend fun setAllowDowngrade(value: Boolean) = edit { it[Keys.allowDowngrade] = value }
    suspend fun setInstallMethod(method: InstallMethod) = edit { it[Keys.installMethod] = method.id }
    suspend fun setAutoUpdate(value: Boolean) = edit { it[Keys.autoUpdate] = value }
    suspend fun setWifiOnlyUpdates(value: Boolean) = edit { it[Keys.wifiOnlyUpdates] = value }
    suspend fun setNotifyDownloads(value: Boolean) = edit { it[Keys.notifyDownloads] = value }
    suspend fun setNotifyUpdates(value: Boolean) = edit { it[Keys.notifyUpdates] = value }
    suspend fun setDataSaver(value: Boolean) = edit { it[Keys.dataSaver] = value }
    suspend fun setCompatibleOnly(value: Boolean) = edit { it[Keys.compatibleOnly] = value }
    suspend fun setInstalledBadges(value: Boolean) = edit { it[Keys.installedBadges] = value }
    suspend fun setAutoSync(value: Boolean) = edit { it[Keys.autoSync] = value }
    suspend fun setHistoryEnabled(value: Boolean) = edit { it[Keys.historyEnabled] = value }
    suspend fun setLastUpdateCheck(value: Long) = edit { it[Keys.lastUpdateCheck] = value }
    suspend fun setOnboardingDone(value: Boolean) = edit { it[Keys.onboarding] = value }

    suspend fun setLocalProfile(name: String, email: String) = edit {
        it[Keys.localName] = name.trim()
        it[Keys.localEmail] = email.trim()
    }

    suspend fun addSearchTerm(term: String) = edit { prefs ->
        val clean = term.trim()
        if (clean.length < 2) return@edit
        val current = readSearchHistory(prefs).toMutableList()
        current.remove(clean)
        current.add(0, clean)
        writeSearchHistory(prefs, current.take(12))
    }

    suspend fun removeSearchTerm(term: String) = edit { prefs ->
        writeSearchHistory(prefs, readSearchHistory(prefs).filterNot { it == term })
    }

    suspend fun clearSearchHistory() = edit { prefs ->
        prefs.remove(Keys.searchHistory)
        prefs.remove(Keys.searchHistoryLegacy)
    }

    /** Records an opened app id so Search can offer "Recently viewed". */
    suspend fun addRecentlyViewed(appId: String) = edit { prefs ->
        val clean = appId.trim()
        if (clean.isEmpty()) return@edit
        val current = readRecentlyViewed(prefs).toMutableList()
        current.remove(clean)
        current.add(0, clean)
        prefs[Keys.recentlyViewed] = current.take(10).joinToString("\n")
    }

    suspend fun clearRecentlyViewed() = edit { prefs ->
        prefs.remove(Keys.recentlyViewed)
    }

    /** Newest first, capped to the ten ids the UI may ever need. */
    private fun readRecentlyViewed(prefs: androidx.datastore.preferences.core.Preferences): List<String> =
        prefs[Keys.recentlyViewed]
            ?.split('\n')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.distinct()
            ?.take(10)
            .orEmpty()

    /** Newest first; falls back to the old unordered set for existing installs. */
    private fun readSearchHistory(prefs: androidx.datastore.preferences.core.Preferences): List<String> {
        val ordered = prefs[Keys.searchHistory]
            ?.split('\n')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()
        if (ordered.isNotEmpty()) return ordered
        return prefs[Keys.searchHistoryLegacy].orEmpty().sorted()
    }

    private fun writeSearchHistory(
        prefs: androidx.datastore.preferences.core.MutablePreferences,
        terms: List<String>,
    ) {
        prefs[Keys.searchHistory] = terms.joinToString("\n")
        prefs.remove(Keys.searchHistoryLegacy)
    }

    suspend fun resetDownloadPreferences() = edit {
        it[Keys.wifiOnly] = false
        it[Keys.parallel] = 1
        it[Keys.autoInstall] = false
        it[Keys.deleteApk] = false
        it[Keys.keepApk] = true
        it[Keys.confirmInstall] = true
        it[Keys.verifyChecksum] = true
        it[Keys.allowDowngrade] = false
        it[Keys.dataSaver] = false
        it[Keys.notifyDownloads] = true
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.settingStore.edit(block)
    }
}
