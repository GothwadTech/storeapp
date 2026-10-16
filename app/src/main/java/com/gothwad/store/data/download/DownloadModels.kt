package com.gothwad.store.data.download

import com.gothwad.store.core.Format
import com.gothwad.store.data.install.InstallMethod

enum class DownloadStatus { QUEUED, RUNNING, PAUSED, COMPLETED, INSTALLED, FAILED, CANCELLED }

/**
 * One APK job. Kept as a plain constructor-only data class so it can be
 * serialised to disk for the "Manage downloads" screen; everything computed
 * lives in the extension properties below.
 */
data class DownloadTask(
    val id: String,
    val appId: String = "",
    val appName: String = "",
    val packageName: String = "",
    val developerName: String = "",
    val iconUrl: String = "",
    val versionName: String = "",
    val versionCode: Long = 0,
    val url: String = "",
    val fileName: String = "",
    val totalBytes: Long = 0,
    val sha256: String? = null,
    val minSdk: Int? = null,
    val permissions: List<String> = emptyList(),
    val changelog: String = "",
    val method: InstallMethod = InstallMethod.Session,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val bytesDownloaded: Long = 0,
    val error: String? = null,
    val filePath: String? = null,
    val mimeOrProvider: String? = null,
    val autoInstall: Boolean = false,
    val downloadedAt: Long = 0,
    val updatedAt: Long = 0,
)

val DownloadTask.progress: Float
    get() = when {
        status == DownloadStatus.COMPLETED || status == DownloadStatus.INSTALLED -> 1f
        totalBytes > 0 -> (bytesDownloaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
        else -> 0f
    }

val DownloadTask.sizeLabel: String
    get() = Format.bytes(if (totalBytes > 0) totalBytes else bytesDownloaded)

val DownloadTask.downloadedLabel: String
    get() = "${Format.bytes(bytesDownloaded)} / ${Format.bytes(totalBytes)}"

val DownloadTask.isActive: Boolean
    get() = status == DownloadStatus.RUNNING || status == DownloadStatus.QUEUED

val DownloadTask.isFinished: Boolean
    get() = status == DownloadStatus.COMPLETED || status == DownloadStatus.INSTALLED

val DownloadTask.isInstalled: Boolean
    get() = status == DownloadStatus.INSTALLED

val DownloadTask.canResume: Boolean
    get() = status == DownloadStatus.PAUSED || status == DownloadStatus.FAILED || status == DownloadStatus.CANCELLED

val DownloadTask.canInstall: Boolean
    get() = (status == DownloadStatus.COMPLETED || status == DownloadStatus.FAILED) &&
        filePath != null && bytesDownloaded >= totalBytes && totalBytes > 0

val DownloadTask.needsRetry: Boolean
    get() = status == DownloadStatus.FAILED || (status == DownloadStatus.CANCELLED && bytesDownloaded < totalBytes)
