package com.gothwad.store.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.gothwad.store.MainActivity
import com.gothwad.store.R
import com.gothwad.store.core.Format
import com.gothwad.store.data.download.DownloadTask
import com.gothwad.store.data.download.progress

object DownloadNotifications {

    const val CHANNEL_DOWNLOADS = "gothwad_downloads"
    const val CHANNEL_INSTALLS = "gothwad_installs"
    private const val SUMMARY_ID = 0x6701

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_DOWNLOADS,
                context.getString(R.string.download_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.download_channel_description)
                setShowBadge(false)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_INSTALLS,
                context.getString(R.string.install_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = context.getString(R.string.install_channel_description) },
        )
    }

    private fun openApp(context: Context, route: String, taskId: String? = null): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_ROUTE, route)
            if (taskId != null) putExtra(MainActivity.EXTRA_TASK_ID, taskId)
        }
        return PendingIntent.getActivity(
            context,
            (route + (taskId ?: "")).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun action(context: Context, taskId: String, action: String, label: String, icon: Int): NotificationCompat.Action {
        val intent = Intent(context, DownloadActionReceiver::class.java).apply {
            this.action = action
            putExtra(DownloadActionReceiver.EXTRA_ID, taskId)
        }
        val pending = PendingIntent.getBroadcast(
            context,
            (taskId + action).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Action(icon, label, pending)
    }

    fun progress(context: Context, task: DownloadTask, speed: Long): Notification {
        val percent = (task.progress * 100).toInt()
        val builder = NotificationCompat.Builder(context, CHANNEL_DOWNLOADS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(task.appName.ifBlank { "Downloading" })
            .setContentText("${task.versionName} · $percent% · ${Format.bytes(speed)}/s")
            .setProgress(100, percent, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp(context, "downloads", task.id))
            .addAction(action(context, task.id, DownloadActionReceiver.ACTION_PAUSE, "Pause", android.R.drawable.ic_media_pause))
        return builder.build()
    }

    fun paused(context: Context, task: DownloadTask): Notification =
        NotificationCompat.Builder(context, CHANNEL_DOWNLOADS)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Paused · ${task.appName}")
            .setContentText(task.versionName)
            .setProgress(100, (task.progress * 100).toInt(), false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp(context, "downloads", task.id))
            .addAction(action(context, task.id, DownloadActionReceiver.ACTION_RESUME, "Resume", android.R.drawable.ic_media_play))
            .build()

    fun completed(context: Context, task: DownloadTask, autoInstall: Boolean): Notification {
        val builder = NotificationCompat.Builder(context, CHANNEL_DOWNLOADS)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("${task.appName} downloaded")
            .setContentText(
                if (autoInstall) "Starting the installer…"
                else "Tap to install · ${task.versionName}",
            )
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setContentIntent(openApp(context, if (autoInstall) "downloads" else "install", task.id))
        if (!autoInstall) {
            builder.addAction(
                action(context, task.id, DownloadActionReceiver.ACTION_INSTALL, "Install", android.R.drawable.ic_menu_add),
            )
        }
        return builder.build()
    }

    fun failed(context: Context, task: DownloadTask, message: String): Notification =
        NotificationCompat.Builder(context, CHANNEL_DOWNLOADS)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("${task.appName} download failed")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .setContentIntent(openApp(context, "downloads", task.id))
            .addAction(action(context, task.id, DownloadActionReceiver.ACTION_RETRY, "Retry", android.R.drawable.ic_menu_rotate))
            .build()

    /**
     * Install result. [confirmIntent] carries Android's own "do you want to
     * install this?" dialog so the user can finish an install that could not be
     * shown while the app was in the background.
     */
    fun installResult(
        context: Context,
        appName: String,
        message: String,
        confirmIntent: Intent? = null,
    ): Notification {
        val contentIntent = if (confirmIntent != null) {
            PendingIntent.getActivity(
                context,
                (appName + "confirm").hashCode(),
                confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        } else {
            openApp(context, "downloads")
        }
        return NotificationCompat.Builder(context, CHANNEL_INSTALLS)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(appName)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()
    }

    fun summary(context: Context, active: Int, completed: Int): Notification =
        NotificationCompat.Builder(context, CHANNEL_DOWNLOADS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Kiwi Store downloads")
            .setContentText("$active active · $completed finished")
            .setGroup("gothwad_downloads")
            .setGroupSummary(true)
            .setOngoing(active > 0)
            .setContentIntent(openApp(context, "downloads"))
            .build()

    const val SUMMARY_NOTIFICATION_ID = SUMMARY_ID
}
