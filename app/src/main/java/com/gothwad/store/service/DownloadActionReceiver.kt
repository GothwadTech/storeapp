package com.gothwad.store.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.gothwad.store.MainActivity
import com.gothwad.store.R
import com.gothwad.store.core.AppGraph

/**
 * Handles the notification actions on a download (pause / resume / retry) and
 * the "Install" action, which re-opens the store on the install sheet.
 */
class DownloadActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        AppGraph.ensure(context.applicationContext)
        val taskId = intent.getStringExtra(EXTRA_ID)
        when (intent.action) {
            ACTION_PAUSE -> ApkDownloadService.command(context, ApkDownloadService.ACTION_PAUSE, taskId)
            ACTION_RESUME -> ApkDownloadService.command(context, ApkDownloadService.ACTION_RESUME, taskId)
            ACTION_RETRY -> ApkDownloadService.command(context, ApkDownloadService.ACTION_RETRY, taskId)
            ACTION_CANCEL -> ApkDownloadService.command(context, ApkDownloadService.ACTION_CANCEL, taskId)
            ACTION_INSTALL -> {
                val open = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(MainActivity.EXTRA_ROUTE, "install")
                    putExtra(MainActivity.EXTRA_TASK_ID, taskId)
                }
                runCatching { context.startActivity(open) }.onFailure {
                    runCatching {
                        context.getSystemService(android.app.NotificationManager::class.java)?.notify(
                            0x6702,
                            DownloadNotifications.installResult(
                                context,
                                context.getString(R.string.app_name),
                                "Open Kiwi Store and tap Install in Manage downloads.",
                            ),
                        )
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_ID = "download_task_id"
        const val ACTION_PAUSE = "com.gothwad.store.notify.PAUSE"
        const val ACTION_RESUME = "com.gothwad.store.notify.RESUME"
        const val ACTION_RETRY = "com.gothwad.store.notify.RETRY"
        const val ACTION_CANCEL = "com.gothwad.store.notify.CANCEL"
        const val ACTION_INSTALL = "com.gothwad.store.notify.INSTALL"
    }
}
