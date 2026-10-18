package com.gothwad.store.data.library

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import com.gothwad.store.data.auth.AuthManager
import com.gothwad.store.data.remote.StoreNetwork
import com.gothwad.store.data.remote.StoreRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

data class InstalledApp(
    val packageName: String,
    val label: String,
    val versionCode: Long,
    val versionName: String,
    val icon: Drawable? = null,
    val system: Boolean = false,
    val installedAt: Long = 0,
)

data class LibraryState(
    /** package name → installed version code, as reported by Android. */
    val installed: Map<String, Long> = emptyMap(),
    /** store app ids that were installed through Kiwi Store. */
    val installedAppIds: List<String> = emptyList(),
    val wishlist: List<String> = emptyList(),
    /** app ids the Worker knows about for this account. */
    val remoteLibrary: List<String> = emptyList(),
    val remoteWishlist: List<String> = emptyList(),
    val lastSync: Long = 0,
)

/**
 * "My apps & games": what is installed on the device, what came from Gothwad
 * Store, and the wishlist. Works offline; syncs with the Worker when the user is
 * signed in with Gothwad Accounts.
 */
class LibraryRepository(
    private val context: Context,
    private val repository: StoreRepository,
    private val auth: AuthManager,
) {

    private val file = File(context.filesDir, "library.json")
    private val mutex = Mutex()
    private val _state = MutableStateFlow(LibraryState())
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) {
        mutex.withLock {
            val parsed = runCatching {
                if (!file.exists()) LibraryState()
                else StoreNetwork.moshi.adapter(LibraryState::class.java).fromJson(file.readText())
                    ?: LibraryState()
            }.getOrDefault(LibraryState())
            _state.value = parsed.copy(installed = scanInstalledVersions())
        }
    }

    fun installedVersion(packageName: String): Long? =
        _state.value.installed[packageName] ?: scanVersion(packageName)

    fun isInstalled(packageName: String): Boolean = installedVersion(packageName) != null

    /** Full list for the "On this device" screen (labels + icons come from Android). */
    suspend fun installedApps(includeSystem: Boolean = false): List<InstalledApp> =
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val packages = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getInstalledPackages(0)
                }
            }.getOrDefault(emptyList())

            packages.mapNotNull { info ->
                val appInfo = info.applicationInfo ?: return@mapNotNull null
                val system = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                if (system && !includeSystem) return@mapNotNull null
                val label = runCatching { appInfo.loadLabel(pm).toString() }.getOrDefault(info.packageName)
                InstalledApp(
                    packageName = info.packageName,
                    label = label,
                    versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                        info.longVersionCode else info.versionCode.toLong(),
                    versionName = info.versionName.orEmpty(),
                    icon = runCatching { appInfo.loadIcon(pm) }.getOrNull(),
                    system = system,
                    installedAt = runCatching { info.firstInstallTime }.getOrDefault(0L),
                )
            }.sortedBy { it.label.lowercase() }
        }

    private fun scanInstalledVersions(): Map<String, Long> = runCatching {
        val pm = context.packageManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledPackages(0)
        }.associate { info ->
            info.packageName to (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                info.longVersionCode else info.versionCode.toLong())
        }
    }.getOrDefault(emptyMap())

    private fun scanVersion(packageName: String): Long? = runCatching {
        val pm = context.packageManager
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(packageName, 0)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()
    }.getOrNull()

    suspend fun refreshInstalled() {
        mutex.withLock {
            _state.update { it.copy(installed = scanInstalledVersions()) }
        }
    }

    /** Called after a successful install so updates + "My apps" stay accurate. */
    suspend fun recordInstalled(packageName: String, versionCode: Long, appId: String?) {
        mutex.withLock {
            _state.update { current ->
                current.copy(
                    installed = current.installed + (packageName to versionCode),
                    installedAppIds = if (appId != null && appId !in current.installedAppIds)
                        current.installedAppIds + appId else current.installedAppIds,
                )
            }
            persist()
        }
        if (appId != null && auth.isSignedIn) {
            runCatching { repository.addToLibrary(appId, versionCode) }
        }
    }

    suspend fun recordUninstalled(packageName: String) {
        mutex.withLock {
            _state.update { it.copy(installed = it.installed - packageName) }
            persist()
        }
    }

    suspend fun toggleWishlist(appId: String): Boolean {
        val current = _state.value.wishlist
        val adding = appId !in current
        if (auth.isSignedIn) {
            if (adding) repository.addWishlist(appId) else repository.removeWishlist(appId)
        }
        mutex.withLock {
            _state.update {
                it.copy(wishlist = if (adding) it.wishlist + appId else it.wishlist - appId)
            }
            persist()
        }
        return adding
    }

    fun isWishlisted(appId: String): Boolean = appId in _state.value.wishlist

    suspend fun syncRemote() {
        if (!auth.isSignedIn) return
        val library = runCatching { repository.libraryIds() }.getOrDefault(emptyList())
        val wishlist = runCatching { repository.wishlistIds() }.getOrDefault(emptyList())
        mutex.withLock {
            _state.update {
                it.copy(
                    remoteLibrary = library,
                    remoteWishlist = wishlist,
                    wishlist = (it.wishlist + wishlist).distinct(),
                    lastSync = System.currentTimeMillis(),
                )
            }
            persist()
        }
    }

    private suspend fun persist() = withContext(Dispatchers.IO) {
        runCatching { file.writeText(StoreNetwork.moshi.adapter(LibraryState::class.java).toJson(_state.value)) }
        Unit
    }
}
