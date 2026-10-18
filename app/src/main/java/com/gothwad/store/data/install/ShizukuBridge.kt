package com.gothwad.store.data.install

import android.content.Context
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.File
import java.io.OutputStream

data class ShizukuStatus(
    val installed: Boolean = false,
    val running: Boolean = false,
    val permissionGranted: Boolean = false,
    val version: Int = 0,
    val uid: Int = -1,
    val preV11: Boolean = false,
) {
    /** uid 0 means Shizuku/Sui is running with root, 2000 means ADB (shell). */
    val backend: String
        get() = when {
            !running -> "Not running"
            uid == 0 -> "Root (via Sui/Shizuku)"
            uid == 2000 -> "ADB / shell"
            uid > 0 -> "uid $uid"
            else -> "Unknown"
        }
}

/**
 * Thin, failure-tolerant wrapper around the Shizuku API.
 *
 * Installing uses `IShizukuService#newProcess`, which runs the command with the
 * ADB (shell) or root identity, so `pm install` needs no dialogs. Everything is
 * wrapped in runCatching: Shizuku can die at any moment and the store must keep
 * working with the built-in installer instead.
 */
object ShizukuBridge {

    const val PERMISSION_REQUEST_CODE = 0x5A1

    private val _status = MutableStateFlow(ShizukuStatus())
    val status: StateFlow<ShizukuStatus> = _status.asStateFlow()

    private var listenersRegistered = false

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        refresh(permissionOverride = grantResult == PackageManager.PERMISSION_GRANTED)
    }
    private val binderReceivedListener = Shizuku.OnBinderReceivedListener { refresh() }
    private val binderDeadListener = Shizuku.OnBinderDeadListener { refresh() }

    /** Safe to call from every process start-up. */
    fun start(context: Context) {
        if (!listenersRegistered) {
            runCatching {
                Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
                Shizuku.addBinderDeadListener(binderDeadListener)
                Shizuku.addRequestPermissionResultListener(permissionListener)
            }
            listenersRegistered = true
        }
        refresh()
    }

    fun stop() {
        if (!listenersRegistered) return
        runCatching {
            Shizuku.removeBinderReceivedListener(binderReceivedListener)
            Shizuku.removeBinderDeadListener(binderDeadListener)
            Shizuku.removeRequestPermissionResultListener(permissionListener)
        }
        listenersRegistered = false
    }

    fun isInstalled(context: Context): Boolean = runCatching {
        context.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
        true
    }.getOrDefault(false)

    fun refresh(permissionOverride: Boolean? = null) {
        val installed = isInstalledOnDevice()
        val running = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        val preV11 = runCatching { Shizuku.isPreV11() }.getOrDefault(false)
        val version = runCatching { Shizuku.getVersion() }.getOrDefault(0)
        val uid = runCatching { Shizuku.getUid() }.getOrDefault(-1)
        val granted = when {
            !running -> false
            preV11 -> true // pre-v11 Shizuku granted permission at start-up
            permissionOverride == true -> true
            else -> runCatching { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }
                .getOrDefault(false)
        }
        _status.value = ShizukuStatus(
            installed = installed,
            running = running,
            permissionGranted = granted,
            version = version,
            uid = uid,
            preV11 = preV11,
        )
    }

    private fun isInstalledOnDevice(): Boolean = runCatching {
        Shizuku.getVersion() > 0
    }.getOrDefault(false)

    fun requestPermission() {
        runCatching { Shizuku.requestPermission(PERMISSION_REQUEST_CODE) }
    }

    fun shouldShowRationale(): Boolean =
        runCatching { Shizuku.shouldShowRequestPermissionRationale() }.getOrDefault(false)

    /**
     * Runs `cmd` as shell/root and returns exit code + combined output.
     * [stdinFile] is streamed to the process when provided (used for
     * `pm install -S <size>`, the same trick `adb install` uses).
     */
    suspend fun runCommand(
        cmd: Array<String>,
        stdinFile: File? = null,
        onProgress: ((Long, Long) -> Unit)? = null,
    ): ShellResult = withContext(Dispatchers.IO) {
        val binder: IBinder? = runCatching { Shizuku.getBinder() }.getOrNull()
            ?: return@withContext ShellResult(-1, "", "Shizuku is not connected.")
        val service = runCatching {
            val stub = Class.forName("moe.shizuku.server.IShizukuService\$Stub")
            stub.getMethod("asInterface", IBinder::class.java).invoke(null, binder)
        }.getOrNull() ?: return@withContext ShellResult(-1, "", "Shizuku service interface is unavailable.")

        val remote = runCatching {
            service.javaClass
                .getMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
                .invoke(service, cmd, null, null)
        }.getOrNull() ?: return@withContext ShellResult(
            -1, "", "Shizuku refused to start a privileged process. Open Shizuku and grant Kiwi Store access.",
        )

        val out = StringBuilder()
        val err = StringBuilder()
        try {
            val stdout = ParcelFileDescriptor.AutoCloseInputStream(
                remote.javaClass.getMethod("getInputStream").invoke(remote) as ParcelFileDescriptor,
            )
            val stderr = ParcelFileDescriptor.AutoCloseInputStream(
                remote.javaClass.getMethod("getErrorStream").invoke(remote) as ParcelFileDescriptor,
            )
            val stdin = ParcelFileDescriptor.AutoCloseOutputStream(
                remote.javaClass.getMethod("getOutputStream").invoke(remote) as ParcelFileDescriptor,
            )
            coroutineScope {
                val reader = async {
                    stdout.bufferedReader().use { it.forEachLine { line -> out.appendLine(line) } }
                    stderr.bufferedReader().use { it.forEachLine { line -> err.appendLine(line) } }
                }
                if (stdinFile != null) {
                    stdin.use { sink -> pipeFile(stdinFile, sink, onProgress) }
                } else {
                    runCatching { stdin.close() }
                }
                reader.await()
            }
            val exit = runCatching {
                remote.javaClass.getMethod("waitFor").invoke(remote) as Int
            }.getOrDefault(-1)
            ShellResult(exit, out.toString(), err.toString())
        } catch (error: Throwable) {
            ShellResult(-1, out.toString(), error.message ?: "Shizuku command failed.")
        }
    }

    private fun pipeFile(source: File, sink: OutputStream, onProgress: ((Long, Long) -> Unit)?) {
        val total = source.length()
        var copied = 0L
        source.inputStream().use { input ->
            val buffer = ByteArray(128 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                sink.write(buffer, 0, read)
                copied += read
                onProgress?.invoke(copied, total)
            }
            sink.flush()
        }
    }

    fun currentUidLabel(): String = when (runCatching { Shizuku.getUid() }.getOrDefault(-1)) {
        0 -> "root"
        2000 -> "shell"
        else -> "unknown"
    }
}

data class ShellResult(val exitCode: Int, val stdout: String, val stderr: String) {
    val combined: String get() = listOf(stdout, stderr).filter { it.isNotBlank() }.joinToString("\n").trim()
    val ok: Boolean get() = exitCode == 0 && !combined.contains("Failure", ignoreCase = true)
}
