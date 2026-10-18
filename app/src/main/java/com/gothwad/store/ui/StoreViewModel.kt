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
import kotlinx.coroutines.CancellationException
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

    /**
     * The Kiwi tab: the listings that are live on the Kiwi Store web. The
     * optional upstream catalogue (F-Droid) is deliberately *not* merged in
     * here — the Kiwi tab is the web store, nothing else.
     */
    private val _kiwi = MutableStateFlow<UiState<CatalogBundle>>(UiState.Loading)
    val kiwi: StateFlow<UiState<CatalogBundle>> = _kiwi.asStateFlow()

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

    /** appId → newest release the Worker or F-Droid published for this device. */
    private val _releases = MutableStateFlow<Map<String, StoreRelease>>(emptyMap())
    val releases: StateFlow<Map<String, StoreRelease>> = _releases.asStateFlow()

    /** Every listing the user has already seen, keyed by id (offline-friendly). */
    private val _knownApps = MutableStateFlow<Map<String, StoreApp>>(emptyMap())
    val knownApps: StateFlow<Map<String, StoreApp>> = _knownApps.asStateFlow()

    /**
     * The F-Droid catalogue, kept apart from the first-party listings so Home
     * can show it as its own dedicated shelf while also merging it into Apps,
     * Games, Categories, Top Charts, and Search.
     */
    private val _fdroid = MutableStateFlow(CatalogBundle())
    val fdroid: StateFlow<CatalogBundle> = _fdroid.asStateFlow()

    /** Why the F-Droid catalogue is missing, if it is. */
    val fdroidError: StateFlow<String?> = repository.fdroidError

    val downloads: StateFlow<List<DownloadTask>> = downloader.tasks
    val installPhases: StateFlow<Map<String, InstallPhase>> = installer.phases

    private var firstPartyHomeCache: CatalogResponse = CatalogResponse()
    private var firstPartyAppsCache: CatalogResponse = CatalogResponse()
    private var currentAppsKind: String? = "app"
    private var currentAppsCategory: String? = null
    private var firstPartyGamesCache: CatalogResponse = CatalogResponse()
    private var firstPartyChartsCache: CatalogResponse = CatalogResponse()
    private var currentChartsKind: String? = null
    private var firstPartyCategoryCache: CatalogResponse = CatalogResponse()
    private var currentCategorySlug: String? = null
    private var currentCategoryKind: String? = null
    private var firstPartySearchCache: CatalogResponse = CatalogResponse()
    private var currentSearchQuery: String = ""

    private var homeJob: Job? = null
    private var appsJob: Job? = null
    private var kiwiJob: Job? = null
    private var gamesJob: Job? = null
    private var chartsJob: Job? = null
    private var detailJob: Job? = null
    private var searchJob: Job? = null
    private var categoryJob: Job? = null
    private var fdroidJob: Job? = null

    init {
        observeFdroidUpdates()
        refreshHome()
        refreshCategories()
        loadFdroid()
    }

    /**
     * Reactively merges F-Droid + IzzyOnDroid catalogue emissions into every
     * screen state so that whether F-Droid finishes before or after the
     * first-party Worker (or emits progressively as each repo completes),
     * every tab automatically shows the combined apps.
     */
    private fun observeFdroidUpdates() {
        viewModelScope.launch {
            repository.fdroidUpdates.collect { external ->
                if (external == null || external.apps.isEmpty()) return@collect
                val index = repository.releaseIndex(external)
                if (index.isNotEmpty()) _releases.value = _releases.value + index
                _knownApps.value = _knownApps.value + external.apps.associateBy { it.id }
                _fdroid.value = CatalogBundle(external.apps, index)

                val mergedHome = repository.mergeWithExternal(
                    gothwad = firstPartyHomeCache,
                    external = external,
                    kind = null,
                    category = null,
                )
                _home.value = registerAndBuildState(mergedHome)

                val mergedApps = repository.mergeWithExternal(
                    gothwad = firstPartyAppsCache,
                    external = external,
                    kind = currentAppsKind,
                    category = currentAppsCategory,
                )
                _apps.value = registerAndBuildState(mergedApps)

                val mergedGames = repository.mergeWithExternal(
                    gothwad = firstPartyGamesCache,
                    external = external,
                    kind = "game",
                    category = null,
                )
                _games.value = registerAndBuildState(mergedGames)

                if (_charts.value is UiState.Ready || chartsJob != null) {
                    val mergedCharts = repository.mergeWithExternal(
                        gothwad = firstPartyChartsCache,
                        external = external,
                        kind = currentChartsKind,
                        category = null,
                    )
                    _charts.value = registerAndBuildState(mergedCharts)
                }

                val slug = currentCategorySlug
                if (!slug.isNullOrBlank()) {
                    val mergedCat = repository.mergeWithExternal(
                        gothwad = firstPartyCategoryCache,
                        external = external,
                        kind = currentCategoryKind,
                        category = slug,
                    )
                    _categoryApps.value = registerAndBuildState(mergedCat)
                }

                val q = currentSearchQuery
                if (q.isNotEmpty()) {
                    val mergedSearch = repository.mergeWithExternal(
                        gothwad = firstPartySearchCache,
                        external = external,
                        kind = null,
                        category = null,
                        query = q,
                    )
                    _searchResults.value = registerAndBuildState(mergedSearch)
                }
            }
        }
    }

    // ── Catalogue ─────────────────────────────────────────────────────────
    fun refreshHome(showLoading: Boolean = true) {
        homeJob?.cancel()
        homeJob = viewModelScope.launch {
            if (showLoading && _home.value !is UiState.Ready) _home.value = UiState.Loading
            _home.value = loadWithUpstream(
                firstParty = { repository.firstPartyHome(null).also { firstPartyHomeCache = it } },
                merged = { repository.withFdroid(it, null, null) },
                publish = { _home.value = it },
            )
        }
    }

    fun refreshApps(kind: String? = null, category: String? = null) {
        currentAppsKind = kind
        currentAppsCategory = category
        appsJob?.cancel()
        appsJob = viewModelScope.launch {
            _apps.value = UiState.Loading
            _apps.value = loadWithUpstream(
                firstParty = { repository.firstPartyCatalog(kind, category, "").also { firstPartyAppsCache = it } },
                merged = { repository.withFdroid(it, kind, category) },
                publish = { _apps.value = it },
            )
        }
    }

    /**
     * Loads the listings published on the Kiwi Store web. Only the Worker
     * catalogue is used here — no F-Droid, no upstream mirrors — so the Kiwi
     * tab shows exactly what the website shows.
     */
    fun refreshKiwi() {
        kiwiJob?.cancel()
        kiwiJob = viewModelScope.launch {
            if (_kiwi.value !is UiState.Ready) _kiwi.value = UiState.Loading
            _kiwi.value = load {
                val response = repository.firstPartyCatalog(null, null, "")
                response.copy(apps = response.apps.filter { app -> app.isKiwiStoreListing })
            }
        }
    }

    fun refreshGames() {
        gamesJob?.cancel()
        gamesJob = viewModelScope.launch {
            if (_games.value !is UiState.Ready) _games.value = UiState.Loading
            _games.value = loadWithUpstream(
                firstParty = { repository.firstPartyCatalog("game", null, "").also { firstPartyGamesCache = it } },
                merged = { repository.withFdroid(it, "game", null) },
                publish = { _games.value = it },
            )
        }
    }

    /**
     * Loads the F-Droid catalogue. It is shown as its own shelf on Home and
     * merged into Apps, Games, Search, Top Charts, and Category screens.
     */
    fun loadFdroid() = loadFdroid(forceRefresh = false)

    fun loadFdroid(forceRefresh: Boolean) {
        if (!forceRefresh && fdroidJob?.isActive == true) return
        fdroidJob?.cancel()
        fdroidJob = viewModelScope.launch {
            val response = try {
                repository.fdroidCatalog(forceRefresh)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                return@launch
            }
            val index = repository.releaseIndex(response)
            if (index.isNotEmpty()) _releases.value = _releases.value + index
            if (response.apps.isNotEmpty()) {
                _knownApps.value = _knownApps.value + response.apps.associateBy { it.id }
            }
            _fdroid.value = CatalogBundle(response.apps, index)
        }
    }

    fun retryFdroid() {
        loadFdroid(forceRefresh = true)
        refreshHome(showLoading = false)
        refreshApps(kind = currentAppsKind, category = currentAppsCategory)
        refreshGames()
    }

    fun refreshCharts(kind: String?) {
        currentChartsKind = kind
        chartsJob?.cancel()
        chartsJob = viewModelScope.launch {
            _charts.value = UiState.Loading
            _charts.value = loadWithUpstream(
                firstParty = { repository.charts(kind).also { firstPartyChartsCache = it } },
                merged = { repository.withFdroid(it, kind, null) },
                publish = { _charts.value = it },
            )
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
        val filter = kind?.takeIf { it != "both" }
        currentCategorySlug = slug
        currentCategoryKind = filter
        categoryJob?.cancel()
        categoryJob = viewModelScope.launch {
            _categoryApps.value = UiState.Loading
            _categoryApps.value = loadWithUpstream(
                firstParty = { repository.firstPartyCatalog(filter, slug, "").also { firstPartyCategoryCache = it } },
                merged = { repository.withFdroid(it, filter, slug) },
                publish = { _categoryApps.value = it },
            )
        }
    }

    fun search(query: String, debounceMs: Long = 300) {
        searchJob?.cancel()
        val trimmed = query.trim()
        currentSearchQuery = trimmed
        if (trimmed.isEmpty()) {
            firstPartySearchCache = CatalogResponse()
            _searchResults.value = UiState.Ready(CatalogBundle())
            return
        }
        searchJob = viewModelScope.launch {
            delay(debounceMs)
            _searchResults.value = UiState.Loading
            _searchResults.value = loadWithUpstream(
                firstParty = { repository.firstPartyCatalog(null, null, trimmed).also { firstPartySearchCache = it } },
                merged = { repository.withFdroid(it, null, null, trimmed) },
                publish = { _searchResults.value = it },
            )
        }
    }

    fun openApp(id: String) {
        detailJob?.cancel()
        _detail.value = UiState.Loading
        // Powers the "Recently viewed" shelf at the bottom of Search.
        viewModelScope.launch { settings.addRecentlyViewed(id) }
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
    private fun registerAndBuildState(response: CatalogResponse): UiState.Ready<CatalogBundle> {
        val index = repository.releaseIndex(response)
        if (index.isNotEmpty()) _releases.value = _releases.value + index
        if (response.apps.isNotEmpty()) {
            _knownApps.value = _knownApps.value + response.apps.associateBy { it.id }
        }
        return if (response.apps.isEmpty()) {
            UiState.Ready(CatalogBundle())
        } else {
            UiState.Ready(CatalogBundle(response.apps, index))
        }
    }

    private suspend fun load(block: suspend () -> CatalogResponse): UiState<CatalogBundle> = try {
        val response = block()
        registerAndBuildState(response)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        error.toUiState()
    }

    /**
     * Publishes the first-party catalogue right away when non-empty and then
     * adds the F-Droid catalogue once its index is available.
     *
     * Avoids flashing a premature empty state on Games, Category, or Search
     * screens when first-party returns 0 items while F-Droid is still loading,
     * and falls back to F-Droid if the first-party Worker is unreachable.
     */
    private suspend fun loadWithUpstream(
        firstParty: suspend () -> CatalogResponse,
        merged: suspend (CatalogResponse) -> CatalogResponse,
        publish: (UiState<CatalogBundle>) -> Unit,
    ): UiState<CatalogBundle> {
        val base = try {
            firstParty()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            val fallback = runCatching { merged(CatalogResponse()) }.getOrNull()
            if (fallback != null && fallback.apps.isNotEmpty()) {
                return registerAndBuildState(fallback).also(publish)
            }
            return error.toUiState()
        }
        if (base.apps.isNotEmpty()) {
            publish(registerAndBuildState(base))
        }
        val second = load { merged(base) }
        return if (second is UiState.Ready) {
            second.also(publish)
        } else {
            registerAndBuildState(base).also(publish)
        }
    }

    private fun Throwable.toUiState(): UiState.Failed {
        val message = message?.takeIf { it.isNotBlank() } ?: "Could not load Kiwi Store."
        val retryable = this !is StoreException || (this as StoreException).isRetryable
        return UiState.Failed(message, retryable)
    }
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
