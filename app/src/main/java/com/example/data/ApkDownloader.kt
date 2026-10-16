package com.example.data

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import com.example.data.remote.StoreNetwork
import com.example.data.remote.StoreRelease

/** System Downloads owns persistence, progress and completion notifications.
 * No silent installs and no library/install success recorded on enqueue.
 */
object ApkDownloader {
    fun enqueue(context: Context, release: StoreRelease, appName: String): Long {
        val fileName = release.id.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .take(100) + "-${release.versionCode}-${System.currentTimeMillis()}.apk"
        val request = DownloadManager.Request(Uri.parse(StoreNetwork.downloadUrl(release.id)))
            .setTitle("$appName ${release.versionName}")
            .setDescription("Open the completed APK from Downloads to install.")
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            .setAllowedOverMetered(true)
        return context.getSystemService(DownloadManager::class.java).enqueue(request)
    }
}
