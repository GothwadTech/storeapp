package com.gothwad.store.core

import android.content.Context
import com.gothwad.store.data.auth.AuthManager
import com.gothwad.store.data.download.ApkDownloader
import com.gothwad.store.data.download.DownloadStore
import com.gothwad.store.data.install.InstallEngine
import com.gothwad.store.data.install.ShizukuBridge
import com.gothwad.store.data.library.LibraryRepository
import com.gothwad.store.data.remote.StoreNetwork
import com.gothwad.store.data.remote.StoreRepository
import com.gothwad.store.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

/**
 * Hand-rolled dependency graph. Small enough that a DI framework would only add
 * build time: one instance of each repository lives for the whole process.
 */
object AppGraph {

    @Volatile
    private var ready = false

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var settings: SettingsRepository
        private set
    lateinit var store: StoreRepository
        private set
    lateinit var auth: AuthManager
        private set
    lateinit var downloads: DownloadStore
        private set
    lateinit var downloader: ApkDownloader
        private set
    lateinit var installer: InstallEngine
        private set
    lateinit var library: LibraryRepository
        private set

    private lateinit var appContext: Context

    private val _navigationRequest = MutableStateFlow<NavigationRequest?>(null)
    val navigationRequest: StateFlow<NavigationRequest?> = _navigationRequest.asStateFlow()

    val http: OkHttpClient get() = StoreNetwork.downloadClient

    @Synchronized
    fun ensure(context: Context): Boolean {
        val application = context.applicationContext
        if (ready) return true
        appContext = application
        settings = SettingsRepository(application)
        store = StoreRepository()
        auth = AuthManager(application)
        downloads = DownloadStore(application)
        installer = InstallEngine(application) { downloads.apkDir }
        downloader = ApkDownloader(application, downloads)
        library = LibraryRepository(application, store, auth)
        auth.attachTokenProvider()
        ready = true

        appScope.launch {
            downloads.load()
            library.load()
            installer.refreshCapabilities()
            ShizukuBridge.start(appContext)
            auth.restoreSession()
        }
        return true
    }

    /** Safe "is the graph built yet?" check for activities and receivers. */
    val hasAuth: Boolean get() = ::auth.isInitialized

    fun requestNavigation(route: String, taskId: String? = null) {
        _navigationRequest.value = NavigationRequest(route, taskId, System.currentTimeMillis())
    }

    fun consumeNavigation() {
        _navigationRequest.value = null
    }

    fun context(): Context = appContext
}

data class NavigationRequest(val route: String, val taskId: String? = null, val at: Long = 0L)
