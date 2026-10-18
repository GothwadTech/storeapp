package com.gothwad.store.data.install

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import com.gothwad.store.data.settings.Settings as AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Single entry point for every install/uninstall the UI or the download service
 * requests. Keeps a per-app [InstallPhase] so any screen can show live progress,
 * and resolves the user's chosen [InstallMethod] into a real installer.
 */
class InstallEngine(private val appContext: Context, private val apkDirProvider: () -> File) {

    private val _phases = MutableStateFlow<Map<String, InstallPhase>>(emptyMap())
    val phases: StateFlow<Map<String, InstallPhase>> = _phases.asStateFlow()

    private val _capabilities = MutableStateFlow(InstallCapabilities())
    val capabilities: StateFlow<InstallCapabilities> = _capabilities.asStateFlow()

    private var rootCheckedAt = 0L
    private var rootResult = false

    fun phaseFor(appId: String): InstallPhase = _phases.value[appId] ?: InstallPhase.Idle

    fun publish(appId: String, phase: InstallPhase) {
        _phases.update { current ->
            if (phase is InstallPhase.Idle) current - appId else current + (appId to phase)
        }
    }

    fun clear(appId: String) = publish(appId, InstallPhase.Idle)

    /** Called again whenever the process starts or the user opens install settings. */
    fun refreshCapabilities(sync: Boolean = true) {
        val installed = ShizukuBridge.isInstalled(appContext)
        val shizuku = ShizukuBridge.status.value
        _capabilities.update {
            it.copy(
                sessionAvailable = true,
                installPermissionGranted = canInstallPackages(),
                unknownSourcesGranted = canInstallPackages(),
                rootGranted = rootResult,
                rootChecked = rootCheckedAt > 0,
                shizukuInstalled = installed || shizuku.installed || shizuku.running,
                shizukuRunning = shizuku.running,
                shizukuPermissionGranted = shizuku.permissionGranted,
                shizukuVersion = shizuku.version,
                shizukuUid = shizuku.uid,
                storageWritable = apkDirProvider().let { it.exists() || it.mkdirs() },
            )
        }
    }

    /**
     * `su` probing starts a root prompt, so it only runs when the user asks for
     * it (install settings / choosing the root installer).
     */
    suspend fun checkRoot(force: Boolean = false): Boolean {
        val now = System.currentTimeMillis()
        if (!force && rootCheckedAt > 0 && now - rootCheckedAt < 60_000) return rootResult
        rootResult = withContext(Dispatchers.IO) {
            runCatching {
                val process = ProcessBuilder("su", "-c", "id").redirectErrorStream(true).start()
                if (!process.awaitExit(6)) {
                    process.destroy()
                    return@runCatching false
                }
                val output = process.inputStream.bufferedReader().readText()
                process.exitValue() == 0 && output.contains("uid=0")
            }.getOrDefault(false)
        }
        rootCheckedAt = now
        refreshCapabilities()
        return rootResult
    }

    /** Fires the Shizuku permission request; the result lands in [capabilities]. */
    fun requestShizukuPermission() {
        ShizukuBridge.requestPermission()
        refreshCapabilities(sync = false)
    }

    /**
     * Runs `su -c id` and returns a human-readable log for the root screen.
     * Never throws: a missing su binary is a normal answer.
     */
    suspend fun probeRoot(): String = withContext(Dispatchers.IO) {
        val builder = StringBuilder()
        val granted = runCatching {
            val process = ProcessBuilder("su", "-c", "id").redirectErrorStream(true).start()
            val finished = process.awaitExit(20)
            val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
            builder.append("su -c id\n").append(output.ifBlank { "(no output)" })
            if (!finished) {
                process.terminate()
                builder.append("\n\nexpected the command to finish within 20s")
                false
            } else {
                process.exitValue() == 0 && output.contains("uid=0")
            }
        }.getOrElse { failure ->
            builder.append("su -c id\n").append(failure.message ?: failure::class.java.simpleName)
            false
        }
        checkRoot(force = true)
        builder.append("\n\nresult: ").append(if (granted) "root granted" else "no root access")
        builder.toString()
    }

    fun canInstallPackages(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        runCatching { appContext.packageManager.canRequestPackageInstalls() }.getOrDefault(false)
    } else {
        runCatching {
            Settings.Secure.getInt(appContext.contentResolver, "install_non_market_apps", 1) == 1
        }.getOrDefault(true)
    }

    fun isInstalled(packageName: String): Boolean = installedVersion(packageName) != null

    fun installedVersion(packageName: String): Long? = runCatching {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            appContext.packageManager.getPackageInfo(packageName, 0)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()
    }.getOrNull()

    /**
     * Executes [request] with the requested method.
     *
     * @param allowUi false when the caller is a service in the background: the
     *        Intent hand-off is then upgraded to a session install, which needs
     *        no activity launch.
     */
    suspend fun install(
        context: Context,
        request: InstallRequest,
        allowUi: Boolean = true,
        settings: AppSettings? = null,
        onPhase: (InstallPhase) -> Unit = {},
    ): InstallPhase {
        publish(request.appId, InstallPhase.Preparing)
        onPhase(InstallPhase.Preparing)

        val method = when {
            request.method == InstallMethod.Intent && !allowUi -> InstallMethod.Session
            request.method == InstallMethod.Ask -> InstallMethod.Session
            else -> request.method
        }
        if (method != InstallMethod.Session && method != InstallMethod.Intent && !canBypassDialogs(method)) {
            val failed = when (method) {
                InstallMethod.Shizuku -> InstallPhase.Failed(
                    "Shizuku is not ready.",
                    "Open Shizuku, start the service and grant Kiwi Store access.",
                )
                InstallMethod.Root -> InstallPhase.Failed(
                    "Root access is not available.",
                    "Grant superuser access to Kiwi Store, or pick another installer.",
                )
                else -> InstallPhase.Failed("That installer is unavailable.")
            }
            publish(request.appId, failed)
            onPhase(failed)
            return failed
        }

        val progress = { value: Float ->
            val phase = InstallPhase.Installing(value, "Installing ${request.appName}…")
            publish(request.appId, phase)
            onPhase(phase)
        }

        val result = when (method) {
            InstallMethod.Session -> Installers.installViaSession(context, request, progress)
            InstallMethod.Intent -> {
                val phase = Installers.installViaIntent(context, request)
                if (phase is InstallPhase.Failed) phase else {
                    publish(request.appId, phase)
                    onPhase(phase)
                    return phase
                }
            }
            InstallMethod.Shizuku -> Installers.installViaShizuku(
                request,
                settings?.allowDowngrade ?: request.allowDowngrade,
                progress,
            )
            InstallMethod.Root -> Installers.installViaRoot(
                request,
                settings?.allowDowngrade ?: request.allowDowngrade,
                progress,
            )
            InstallMethod.Ask -> Installers.installViaSession(context, request, progress)
        }
        publish(request.appId, result)
        onPhase(result)
        return result
    }

    /** Session + intent installs depend on "install unknown apps"; root/Shizuku do not. */
    fun canBypassDialogs(method: InstallMethod): Boolean = when (method) {
        InstallMethod.Shizuku -> ShizukuBridge.status.value.let { it.running && it.permissionGranted }
        InstallMethod.Root -> rootResult
        else -> true
    }

    suspend fun uninstall(packageName: String, onPhase: (InstallPhase) -> Unit = {}): InstallPhase {
        val phase = Installers.uninstall(appContext, packageName)
        publish(packageName, phase)
        onPhase(phase)
        return phase
    }

    fun deleteApk(request: InstallRequest) {
        runCatching { File(request.apkPath).delete() }
    }
}
