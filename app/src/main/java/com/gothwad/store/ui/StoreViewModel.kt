package com.gothwad.store.ui

import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gothwad.store.core.AppGraph
import com.gothwad.store.data.download.DownloadTask
import com.gothwad.store.data.install.InstallMethod
import com.gothwad.store.data.install.InstallPhase
import com.gothwad.store.data.install.InstallRequest
import com.gothwad.store.data.model.AppDetailResponse
import com.gothwad.store.data.model.CatalogResponse
import com.gothwad.store.data.model.StoreApp
import com.gothwad.store.data.model.StoreCategory
import com.gothwad.store.data.model.StoreRelease
import com.gothwad.store.data.remote.StoreException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Ready<T>(val value: T) : UiState<T>
    data class Failed(val message: String, val retryable: Boolean = true) : UiState<Nothing>
}

data class CatalogBundle(
    val apps: List<StoreApp> = emptyList(),
    val releases: Map<String, StoreRelease> = emptyMap(),
) {
    fun releaseFor(appId: String): StoreRelease? = releases[appId]
}

sealed interface StoreEvent {
    data class Message(val text: String) : StoreEvent
    data class ShowInstallSheet(val app: StoreApp) : StoreEvent
    data object NeedsInstallPermission : StoreEvent
    data class NeedsSignIn(val reason: String) : StoreEvent
}

/**
 * Catalogue + install orchestration. The data itself lives in [AppGraph], so a
 * download or install started here keeps running while the user navigates.
 */
class StoreViewModel : ViewModel() {

    private val repository = AppGraph.store
    private val settings = AppGraph.settings
    private val downloader = AppGraph.downloader
    private val library = AppGraph.library
    private val installer = AppGraph.installer

    private val _home = MutableStateFlow<UiState<CatalogBundle>>(UiState.Loading)
    val home: StateFlow<UiState<CatalogBundle>> = _home.asStateFlow()

    private val _apps = MutableStateFlow<UiState<CatalogBundle>>(UiState.Loading)
    val apps: StateFlow<UiState<CatalogBundle>> = _apps.asStateFlow()

    private val _games = MutableStateFlow<UiState<CatalogBundle>>(UiState.Loading)
    val games: StateFlow<UiState<CatalogBundle>> = _games.asStateFlow()

    private val _charts = MutableStateFlow<UiState<CatalogBundle>>(UiState.Loading)
    val charts: StateFlow<UiState<CatalogBundle>> = _charts.asStateFlow()

    private val _categories = MutableStateFlow<UiState<List<StoreCategory>>>(UiState.Loading)
    val categories: StateFlow<UiState<List<StoreCategory>>> = _categories.asStateFlow()

    private val _detail = MutableStateFlow<UiState<AppDetailResponse>>(UiState.Loading)
    val detail: StateFlow<UiState<AppDetailResponse>> = _detail.asStateFlow()

    private val _searchResults = MutableStateFlow<UiState<CatalogBundle>>(UiState.Ready(CatalogBundle()))
    val searchResults: StateFlow<UiState<CatalogBundle>> = _searchResults.asStateFlow()

    private val _categoryApps = MutableStateFlow<UiState<CatalogBundle>>(UiState.Loading)
    val categoryApps: StateFlow<UiState<CatalogBundle>> = _categoryApps.asStateFlow()

    private val _events = MutableStateFlow<StoreEvent?>(null)
    val events: StateFlow<StoreEvent?> = _events.asStateFlow()

    /** appId → newest release the Worker published for this device. */
    private val _releases = MutableStateFlow<Map<String, StoreRelease>>(emptyMap())
    val releases: StateFlow<Map<String, StoreRelease>> = _releases.asStateFlow()

    /** Every listing the user has already seen, keyed by id (offline-friendly). */
    private val _knownApps = MutableStateFlow<Map<String, StoreApp>>(emptyMap())
    val knownApps: StateFlow<Map<String, StoreApp>> = _knownApps.asStateFlow()

    val downloads: StateFlow<List<DownloadTask>> = downloader.tasks
    val installPhases: StateFlow<Map<String, InstallPhase>> = installer.phases

    private var homeJob: Job? = null
    private var appsJob: Job? = null
    private var gamesJob: Job? = null
    private var chartsJob: Job? = null
    private var detailJob: Job? = null
    private var searchJob: Job? = null
    private var categoryJob: Job? = null

    init {
        refreshHome()
        refreshCategories()
    }

    // ── Catalogue ─────────────────────────────────────────────────────────
    fun refreshHome(showLoading: Boolean = true) {
        homeJob?.cancel()
        homeJob = viewModelScope.launch {
            if (showLoading) _home.value = UiState.Loading
            _home.value = load { repository.home(null) }
        }
    }

    fun refreshApps(kind: String? = null, category: String? = null) {
        appsJob?.cancel()
        appsJob = viewModelScope.launch {
            _apps.value = UiState.Loading
            _apps.value = load { repository.catalog(kind, category, "") }
        }
    }

    fun refreshGames() {
        gamesJob?.cancel()
        gamesJob = viewModelScope.launch {
            _games.value = UiState.Loading
            _games.value = load { repository.catalog("game", null, "") }
        }
    }

    fun refreshCharts(kind: String?) {
        chartsJob?.cancel()
        chartsJob = viewModelScope.launch {
            _charts.value = UiState.Loading
            _charts.value = load { repository.charts(kind) }
        }
    }

    fun refreshCategories() {
        viewModelScope.launch {
            _categories.value = UiState.Loading
            _categories.value = runCatching { repository.categories() }.fold(
                onSuccess = {
                    if (it.isEmpty()) UiState.Failed("No categories are published yet.", false)
                    else UiState.Ready(it)
                },
                onFailure = { it.toUiState() },
            )
        }
    }

    fun loadCategory(slug: String, kind: String?) {
        categoryJob?.cancel()
        categoryJob = viewModelScope.launch {
            _categoryApps.value = UiState.Loading
            _categoryApps.value = load { repository.catalog(kind?.takeIf { it != "both" }, slug, "") }
        }
    }

    fun search(query: String, debounceMs: Long = 300) {
        searchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            _searchResults.value = UiState.Ready(CatalogBundle())
            return
        }
        searchJob = viewModelScope.launch {
            delay(debounceMs)
            _searchResults.value = UiState.Loading
            _searchResults.value = load { repository.catalog(null, null, trimmed) }
        }
    }

    fun openApp(id: String) {
        detailJob?.cancel()
        _detail.value = UiState.Loading
        detailJob = viewModelScope.launch {
            _detail.value = runCatching { repository.detail(id) }.fold(
                onSuccess = { detail ->
                    _knownApps.value = _knownApps.value + (detail.app.id to detail.app)
                    val index = detail.installableReleases(android.os.Build.VERSION.SDK_INT)
                        .maxByOrNull { it.versionCode }
                    if (index != null) {
                        _releases.value = _releases.value + (detail.app.id to index)
                    }
                    UiState.Ready(detail)
                },
                onFailure = { it.toUiState() },
            )
        }
    }

    /** Retry hook for the detail screen's error state. */
    fun retryDetail(id: String) {
        _detail.value = UiState.Loading
        openApp(id)
    }

    fun releaseFor(app: StoreApp): StoreRelease? =
        (detail.value as? UiState.Ready)?.value?.takeIf { it.app.id == app.id }
            ?.downloadableRelease(android.os.Build.VERSION.SDK_INT)
            ?: _releases.value[app.id]

    fun releaseFor(appId: String): StoreRelease? = _releases.value[appId]

    // ── Install orchestration ─────────────────────────────────────────────
    fun onAppAction(app: StoreApp) {
        val release = releaseFor(app)
        val task = downloader.taskFor(app.id)
        val installed = library.installedVersion(app.packageName)
        val phase = installer.phaseFor(app.id)
        when (resolveInstallAction(installed, release, task, phase)) {
            InstallAction.Open -> openInstalledApp(app.packageName)
            is InstallAction.Downloading -> downloader.pause(app.id)
            is InstallAction.Paused -> downloader.resume(app.id)
            InstallAction.Retry -> downloader.retry(app.id)
            InstallAction.Unavailable -> emit(
                StoreEvent.Message("${app.name} has no release that supports this device yet."),
            )
            InstallAction.Busy -> emit(StoreEvent.Message("Already working on ${app.name}…"))
            is InstallAction.Installing -> emit(StoreEvent.Message("${app.name} is already installing."))
            InstallAction.Install, InstallAction.Update -> startInstall(app, release)
        }
    }

    private fun startInstall(app: StoreApp, release: StoreRelease?) {
        if (release == null) {
            emit(StoreEvent.Message("No approved release is available for ${app.name} on this device."))
            return
        }
        val current = settings.current
        if (current.installMethod == InstallMethod.Ask) {
            emit(StoreEvent.ShowInstallSheet(app))
            return
        }
        if (installerNeedsPermission(current.installMethod)) {
            emit(StoreEvent.NeedsInstallPermission)
            return
        }
        if (current.downloadOnWifiOnly && !NetworkStatus.isWifi()) {
            emit(StoreEvent.Message("Wi-Fi only downloads are on. Connect to Wi-Fi to start this download."))
            return
        }
        viewModelScope.launch {
            val task = downloader.enqueue(
                app = app,
                release = release,
                method = current.installMethod,
                autoInstall = current.autoInstallAfterDownload,
            )
            val alreadyDownloaded = task.filePath != null && task.totalBytes > 0 &&
                task.bytesDownloaded >= task.totalBytes
            if (alreadyDownloaded) {
                installDownloaded(task, current.installMethod)
            } else {
                emit(StoreEvent.Message("Downloading ${app.name}…"))
            }
        }
    }

    /**
     * Installer sheet answered "use this method" for an app with no download yet:
     * queue the APK with that method and let it auto-install when it lands.
     */
    fun startInstallWith(app: StoreApp, method: InstallMethod) {
        val release = releaseFor(app)
        if (release == null) {
            emit(StoreEvent.Message("No approved release is available for ${app.name} on this device."))
            return
        }
        if (method != InstallMethod.Ask && installerNeedsPermission(method)) {
            emit(StoreEvent.NeedsInstallPermission)
            return
        }
        if (settings.current.downloadOnWifiOnly && !NetworkStatus.isWifi()) {
            emit(StoreEvent.Message("Wi-Fi only downloads are on. Connect to Wi-Fi to start this download."))
            return
        }
        viewModelScope.launch {
            val task = downloader.enqueue(
                app = app,
                release = release,
                method = method,
                autoInstall = true,
            )
            val alreadyDownloaded = task.filePath != null && task.totalBytes > 0 &&
                task.bytesDownloaded >= task.totalBytes
            if (alreadyDownloaded) {
                installDownloaded(task, method)
            } else {
                emit(StoreEvent.Message("Downloading ${app.name} — ${method.displayName()} install is queued."))
            }
        }
    }

    fun installerNeedsPermission(method: InstallMethod): Boolean = when (method) {
        InstallMethod.Session, InstallMethod.Intent -> !installer.canInstallPackages()
        InstallMethod.Shizuku, InstallMethod.Root -> !installer.canBypassDialogs(method)
        InstallMethod.Ask -> false
    }

    /** Install a finished download with a method the user just picked. */
    fun installDownloaded(task: DownloadTask, method: InstallMethod) {
        val path = task.filePath
        if (path.isNullOrBlank()) {
            emit(StoreEvent.Message("Download the APK again — the file is missing."))
            return
        }
        viewModelScope.launch {
            val request = InstallRequest(
                appId = task.appId,
                appName = task.appName,
                packageName = task.packageName,
                versionName = task.versionName,
                versionCode = task.versionCode,
                apkPath = path,
                expectedSha256 = task.sha256,
                method = method,
                allowDowngrade = settings.current.allowDowngrade,
                verifyChecksum = false,
            )
            val phase = installer.install(AppGraph.context(), request, allowUi = true) { update ->
                if (update is InstallPhase.Failed) emit(StoreEvent.Message(update.message))
            }
            when (phase) {
                is InstallPhase.Completed -> {
                    downloader.markInstalled(task, phase)
                    library.recordInstalled(request.packageName, request.versionCode, request.appId)
                    if (settings.current.deleteApkAfterInstall) downloader.deleteTaskFile(task)
                    emit(StoreEvent.Message(phase.message))
                }
                is InstallPhase.AwaitingUser -> emit(StoreEvent.Message(phase.label))
                is InstallPhase.Failed -> Unit
                else -> Unit
            }
        }
    }

    fun openInstalledApp(packageName: String) {
        if (packageName.isBlank()) {
            emit(StoreEvent.Message("This listing has no package name yet."))
            return
        }
        val context = AppGraph.context()
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (intent == null) {
            emit(StoreEvent.Message("This app has no launcher activity on your device."))
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure { emit(StoreEvent.Message("Android could not open that app.")) }
    }

    fun requestUninstall(packageName: String) {
        viewModelScope.launch {
            when (val phase = installer.uninstall(packageName)) {
                is InstallPhase.Failed -> emit(StoreEvent.Message(phase.message))
                is InstallPhase.Completed -> library.recordUninstalled(packageName)
                else -> Unit
            }
        }
    }

    fun emit(event: StoreEvent) {
        _events.value = event
    }

    fun consumeEvent() {
        _events.value = null
    }

    // ── Internals ─────────────────────────────────────────────────────────
    private suspend fun load(block: suspend () -> CatalogResponse): UiState<CatalogBundle> =
        runCatching { block() }.fold(
            onSuccess = { response ->
                val index = repository.releaseIndex(response)
                if (index.isNotEmpty()) _releases.value = _releases.value + index
                if (response.apps.isNotEmpty()) {
                    _knownApps.value = _knownApps.value + response.apps.associateBy { it.id }
                }
                if (response.apps.isEmpty()) UiState.Ready(CatalogBundle()) else UiState.Ready(
                    CatalogBundle(response.apps, index),
                )
            },
            onFailure = { it.toUiState() },
        )

    private fun Throwable.toUiState(): UiState.Failed {
        val message = message?.takeIf { it.isNotBlank() } ?: "Could not load Kiwi Store."
        return UiState.Failed(message, this !is StoreException || isRetryable())
    }

    private fun Throwable.isRetryable(): Boolean =
        this is StoreException && isRetryable
}

/** Network helpers, kept out of the ViewModel so they can be unit tested. */
object NetworkStatus {
    fun isWifi(): Boolean {
        val context = AppGraph.context()
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return true
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    /** "Wi-Fi", "Mobile data" or "Offline" for the network screen. */
    fun networkLabel(): String {
        val context = AppGraph.context()
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return "Unknown"
        val network = manager.activeNetwork ?: return "Offline"
        val capabilities = manager.getNetworkCapabilities(network) ?: return "Offline"
        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile data"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            else -> "Connected"
        }
    }

    fun isOnline(): Boolean {
        val context = AppGraph.context()
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return true
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
