package com.gothwad.store.data.install

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import com.gothwad.store.core.AppGraph
import com.gothwad.store.data.download.DownloadStatus
import com.gothwad.store.service.DownloadNotifications
import kotlinx.coroutines.launch

/**
 * Receives the result of a PackageInstaller session (install *and* uninstall).
 * When the system asks us to show its own confirmation dialog, that intent is
 * launched here — or parked in a notification if the app is in the background
 * and Android refuses the activity launch.
 */
class InstallResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        AppGraph.ensure(context.applicationContext)
        val appId = intent.getStringExtra(EXTRA_APP_ID) ?: intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
        val appName = intent.getStringExtra(EXTRA_APP_NAME) ?: "App"
        val packageName = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
        val versionCode = intent.getLongExtra(EXTRA_VERSION_CODE, 0L)
        val uninstall = intent.getBooleanExtra(EXTRA_UNINSTALL, false)
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, Int.MIN_VALUE)
        val rawMessage = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)

        when (status) {
            PackageInstaller.STATUS_SUCCESS -> finish(
                context,
                appId,
                appName,
                packageName,
                versionCode,
                if (uninstall) InstallPhase.Completed("$appName was uninstalled.")
                else InstallPhase.Completed("$appName was installed."),
                uninstall,
            )

            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if (confirm == null) {
                    finish(
                        context, appId, appName, packageName, versionCode,
                        InstallPhase.Failed("Android asked for confirmation but sent no dialog."), uninstall,
                    )
                    return
                }
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val launched = runCatching { context.startActivity(confirm) }.isSuccess
                if (!launched) {
                    notify(
                        context, packageName, appName,
                        "Tap to finish installing $appName.",
                        DownloadNotifications.installResult(
                            context, appName, "Tap to finish installing $appName.", confirm,
                        ),
                    )
                }
                finish(
                    context, appId, appName, packageName, versionCode,
                    InstallPhase.AwaitingUser(
                        if (launched) "Confirm the install in Android's dialog."
                        else "Open the Gothwad Store notification to finish installing.",
                    ),
                    uninstall,
                )
            }

            PackageInstaller.STATUS_FAILURE_ABORTED -> finish(
                context, appId, appName, packageName, versionCode,
                InstallPhase.Failed("The install was cancelled."), uninstall,
            )

            PackageInstaller.STATUS_FAILURE_BLOCKED -> finish(
                context, appId, appName, packageName, versionCode,
                InstallPhase.Failed(
                    "Android blocked this install.",
                    "Allow “Install unknown apps” for Gothwad Store and check Play Protect.",
                ),
                uninstall,
            )

            PackageInstaller.STATUS_FAILURE_CONFLICT -> finish(
                context, appId, appName, packageName, versionCode,
                InstallPhase.Failed(
                    "A different version of this app is already installed.",
                    "Uninstall the installed copy, then install it again from Gothwad Store.",
                ),
                uninstall,
            )

            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> finish(
                context, appId, appName, packageName, versionCode,
                InstallPhase.Failed("This APK is not compatible with your device."), uninstall,
            )

            PackageInstaller.STATUS_FAILURE_INVALID -> finish(
                context, appId, appName, packageName, versionCode,
                InstallPhase.Failed(
                    "The downloaded APK is invalid.",
                    "Delete the download and fetch it again from Gothwad Store.",
                ),
                uninstall,
            )

            PackageInstaller.STATUS_FAILURE_STORAGE -> finish(
                context, appId, appName, packageName, versionCode,
                InstallPhase.Failed("Not enough storage to install this app."), uninstall,
            )

            else -> finish(
                context, appId, appName, packageName, versionCode,
                InstallPhase.Failed(rawMessage ?: "The install failed (status $status)."), uninstall,
            )
        }
    }

    private fun finish(
        context: Context,
        appId: String,
        appName: String,
        packageName: String,
        versionCode: Long,
        phase: InstallPhase,
        uninstall: Boolean,
    ) {
        AppGraph.installer.publish(appId.ifBlank { packageName }, phase)
        AppGraph.appScope.launch {
            AppGraph.downloads.find(appId)?.let { task ->
                when (phase) {
                    is InstallPhase.Completed -> {
                        AppGraph.downloads.update(appId) {
                            it.copy(
                                status = if (uninstall) DownloadStatus.COMPLETED else DownloadStatus.INSTALLED,
                                updatedAt = System.currentTimeMillis(),
                                error = null,
                            )
                        }
                        if (!uninstall && packageName.isNotBlank()) {
                            AppGraph.library.recordInstalled(
                                packageName = packageName,
                                versionCode = versionCode,
                                appId = task.appId,
                            )
                        }
                    }
                    is InstallPhase.Failed -> AppGraph.downloads.update(appId) {
                        it.copy(error = phase.message, updatedAt = System.currentTimeMillis())
                    }
                    else -> Unit
                }
            }
        }
        if (phase is InstallPhase.Failed) {
            notify(
                context, packageName, appName, phase.message,
                DownloadNotifications.installResult(context, appName, phase.message),
            )
        }
    }

    private fun notify(
        context: Context,
        packageName: String,
        appName: String,
        message: String,
        notification: android.app.Notification,
    ) {
        runCatching {
            context.getSystemService(NotificationManager::class.java)
                ?.notify((packageName.ifBlank { appName }).hashCode() + 71, notification)
        }
    }

    companion object {
        const val EXTRA_APP_ID = "app_id"
        const val EXTRA_APP_NAME = "app_name"
        const val EXTRA_PACKAGE = "package_name"
        const val EXTRA_VERSION_CODE = "version_code"
        const val EXTRA_UNINSTALL = "uninstall"
    }
}
