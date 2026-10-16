package com.gothwad.store.data.install

import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The four ways Gothwad Store can install an APK. Every path reports through
 * [InstallPhase] so the UI never has to guess what happened.
 */
object Installers {

    // ── 1. PackageInstaller session (default, Play-Store-like) ─────────────
    suspend fun installViaSession(
        context: Context,
        request: InstallRequest,
        onProgress: (Float) -> Unit,
    ): InstallPhase = withContext(Dispatchers.IO) {
        val file = File(request.apkPath)
        if (!file.exists()) return@withContext InstallPhase.Failed(
            "The APK file is missing.",
            "Download the app again from its Store page.",
        )
        try {
            val installer = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL,
            ).apply {
                setAppPackageName(request.packageName)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    setInstallLocation(android.content.pm.PackageInfo.INSTALL_LOCATION_AUTO)
                }
            }
            val sessionId = installer.createSession(params)
            val session = installer.openSession(sessionId)
            try {
                val total = file.length()
                session.openWrite("gothwad-store-base.apk", 0, total).use { sink ->
                    file.inputStream().use { input ->
                        val buffer = ByteArray(128 * 1024)
                        var copied = 0L
                        var last = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            sink.write(buffer, 0, read)
                            copied += read
                            val now = System.currentTimeMillis()
                            if (now - last > 150) {
                                last = now
                                onProgress((copied.toFloat() / total.toFloat()).coerceIn(0f, 1f))
                            }
                        }
                        sink.flush()
                    }
                    session.fsync(sink)
                }
                val statusReceiver = PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    Intent(context, InstallResultReceiver::class.java)
                        .setPackage(context.packageName)
                        .putExtra(InstallResultReceiver.EXTRA_APP_ID, request.appId)
                        .putExtra(InstallResultReceiver.EXTRA_APP_NAME, request.appName)
                        .putExtra(InstallResultReceiver.EXTRA_PACKAGE, request.packageName)
                        .putExtra(InstallResultReceiver.EXTRA_VERSION_CODE, request.versionCode),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                )
                session.commit(statusReceiver.intentSender)
                onProgress(1f)
                InstallPhase.AwaitingUser("Confirm the install in Android's dialog.")
            } catch (error: Throwable) {
                runCatching { session.abandon() }
                InstallPhase.Failed(
                    error.message ?: "Android's installer rejected the session.",
                    "If the dialog never appears, allow Gothwad Store to install unknown apps.",
                )
            }
        } catch (error: SecurityException) {
            InstallPhase.Failed(
                "Android blocked the installer session.",
                "Settings → Apps → Gothwad Store → Install unknown apps → Allow.",
            )
        } catch (error: Throwable) {
            InstallPhase.Failed(error.message ?: "Could not open an installer session.")
        }
    }

    // ── 2. Classic install-intent hand-off (Install unknown apps) ──────────
    fun installViaIntent(context: Context, request: InstallRequest): InstallPhase {
        val file = File(request.apkPath)
        if (!file.exists()) return InstallPhase.Failed(
            "The APK file is missing.",
            "Download the app again from its Store page.",
        )
        val uri: Uri = runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrElse {
            // Last resort: a file:// URI still works on some older devices.
            Uri.fromFile(file)
        }
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(viewIntent)
            InstallPhase.AwaitingUser("Android's package installer is opening.")
        } catch (_: ActivityNotFoundException) {
            val legacy = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                data = uri
                putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                putExtra(Intent.EXTRA_RETURN_RESULT, true)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(legacy)
                InstallPhase.AwaitingUser("Android's package installer is opening.")
            } catch (_: ActivityNotFoundException) {
                InstallPhase.Failed(
                    "This device has no package installer available for APK files.",
                    "Enable “Install unknown apps” for Gothwad Store, or use the Shizuku/root installer.",
                )
            }
        }
    }

    // ── 3. Root (su) ───────────────────────────────────────────────────────
    suspend fun installViaRoot(
        request: InstallRequest,
        allowDowngrade: Boolean,
        onProgress: (Float) -> Unit,
    ): InstallPhase = PmInstall.install(
        runner = PmInstall.rootRunner,
        apk = File(request.apkPath),
        packageName = request.packageName,
        allowDowngrade = allowDowngrade,
        stream = false,
        onProgress = onProgress,
    )

    // ── 4. Shizuku / Sui (ADB or root identity) ────────────────────────────
    suspend fun installViaShizuku(
        request: InstallRequest,
        allowDowngrade: Boolean,
        onProgress: (Float) -> Unit,
    ): InstallPhase = PmInstall.install(
        runner = PmInstall.shizukuRunner(),
        apk = File(request.apkPath),
        packageName = request.packageName,
        allowDowngrade = allowDowngrade,
        stream = true,
        onProgress = onProgress,
    )

    /** PackageInstaller uninstall (works without the user leaving the app). */
    suspend fun uninstall(context: Context, packageName: String): InstallPhase =
        withContext(Dispatchers.IO) {
            try {
                val receiver = PendingIntent.getBroadcast(
                    context,
                    packageName.hashCode(),
                    Intent(context, InstallResultReceiver::class.java)
                        .setPackage(context.packageName)
                        .putExtra(InstallResultReceiver.EXTRA_PACKAGE, packageName)
                        .putExtra(InstallResultReceiver.EXTRA_APP_NAME, packageName)
                        .putExtra(InstallResultReceiver.EXTRA_UNINSTALL, true),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                )
                context.packageManager.packageInstaller
                    .uninstall(packageName, receiver.intentSender)
                InstallPhase.AwaitingUser("Confirm the uninstall in Android's dialog.")
            } catch (error: Throwable) {
                InstallPhase.Failed(error.message ?: "Could not start the uninstall.")
            }
        }

    fun openInstallPermissionSettings(context: Context) {
        val intents = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                add(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}"),
                    ),
                )
            }
            add(Intent(Settings.ACTION_SECURITY_SETTINGS))
            add(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
        }
        intents.forEach { intent ->
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
                return
            } catch (_: ActivityNotFoundException) {
                // try the next candidate
            } catch (_: Throwable) {
                // ignore and try the next candidate
            }
        }
    }
}
