package com.gothwad.store.data.download

import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.gothwad.store.data.install.InstallMethod
import com.gothwad.store.data.install.InstallPhase
import com.gothwad.store.data.install.InstallRequest
import com.gothwad.store.data.model.StoreApp
import com.gothwad.store.data.model.StoreRelease
import com.gothwad.store.data.remote.StoreNetwork
import com.gothwad.store.service.ApkDownloadService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * User-facing download API: queue an APK, watch it, pause/resume, then hand it
 * to the installer. The heavy lifting lives in [ApkDownloadService].
 */
class ApkDownloader(
    private val context: Context,
    private val store: DownloadStore,
) {

    val tasks: StateFlow<List<DownloadTask>> = store.tasks
    val apkDir: File get() = store.apkDir

    fun taskFor(appId: String): DownloadTask? = store.find(appId)

    suspend fun enqueue(
        app: StoreApp,
        release: StoreRelease,
        method: InstallMethod,
        autoInstall: Boolean,
    ): DownloadTask {
        val fileName = buildString {
            append(app.packageName.ifBlank { app.id }.replace(Regex("[^A-Za-z0-9._-]"), "_"))
            append("-")
            append(release.versionName.ifBlank { release.versionCode.toString() })
            append(".apk")
        }
        val existing = store.find(app.id)
        val task = DownloadTask(
            id = app.id,
            appId = app.id,
            appName = app.name,
            packageName = app.packageName,
            developerName = app.developerName,
            iconUrl = app.iconUrl,
            versionName = release.versionName.ifBlank { release.versionCode.toString() },
            versionCode = release.versionCode,
            url = StoreNetwork.downloadUrl(release.id),
            fileName = fileName,
            totalBytes = release.apkSizeBytes,
            sha256 = release.apkSha256,
            minSdk = release.minSdk,
            permissions = release.permissions,
            changelog = release.changelog,
            method = method,
            status = DownloadStatus.QUEUED,
            bytesDownloaded = 0,
            error = null,
            filePath = existing?.takeIf { it.versionCode == release.versionCode }?.filePath,
            autoInstall = autoInstall,
            downloadedAt = 0,
            updatedAt = System.currentTimeMillis(),
        )
        existing?.takeIf { it.versionCode != release.versionCode }?.filePath?.let { stale ->
            runCatching { File(stale).delete() }
        }
        store.upsert(task)
        ApkDownloadService.command(context, ApkDownloadService.ACTION_START, task.id)
        return task
    }

    fun pause(id: String) = ApkDownloadService.command(context, ApkDownloadService.ACTION_PAUSE, id)
    fun resume(id: String) = ApkDownloadService.command(context, ApkDownloadService.ACTION_RESUME, id)
    fun retry(id: String) = ApkDownloadService.command(context, ApkDownloadService.ACTION_RETRY, id)
    fun cancel(id: String) = ApkDownloadService.command(context, ApkDownloadService.ACTION_CANCEL, id)
    fun pauseAll() = ApkDownloadService.command(context, ApkDownloadService.ACTION_PAUSE_ALL)
    fun resumeAll() = ApkDownloadService.command(context, ApkDownloadService.ACTION_RESUME_ALL)

    suspend fun remove(id: String) {
        val task = store.find(id)
        task?.filePath?.let { path -> runCatching { File(path).delete() } }
        store.remove(id)
    }

    suspend fun clearFinished() = store.clearFinished()

    suspend fun deleteTaskFile(task: DownloadTask) {
        task.filePath?.let { path -> runCatching { File(path).delete() } }
        store.update(task.id) { it.copy(filePath = null) }
    }

    fun usedBytes(): Long = store.usedBytes()
    fun pruneOrphans(): Long = store.pruneOrphanFiles()

    /** Opens the downloaded APK with whatever package installer the user has. */
    fun openApk(task: DownloadTask): Boolean {
        val path = task.filePath ?: return false
        val file = File(path)
        if (!file.exists()) return false
        val uri = runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrElse { Uri.fromFile(file) }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    fun shareApk(task: DownloadTask): Boolean {
        val path = task.filePath ?: return false
        val file = File(path)
        if (!file.exists()) return false
        val uri = runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrElse { Uri.fromFile(file) }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.android.package-archive"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "${task.appName} ${task.versionName}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Share ${task.appName} APK").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching { context.startActivity(chooser) }.isSuccess
    }

    /** Copies the APK into the public Downloads folder (legacy branch on API ≤ 28). */
    suspend fun exportToDownloads(task: DownloadTask): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val path = task.filePath ?: error("This download has no APK file any more.")
            val source = File(path)
            if (!source.exists()) error("The APK file is missing.")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, source.name)
                    put(MediaStore.Downloads.MIME_TYPE, "application/vnd.android.package-archive")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: error("Android refused to create the file in Downloads.")
                resolver.openOutputStream(uri)?.use { output ->
                    source.inputStream().use { input -> input.copyTo(output) }
                } ?: error("Android refused to write the exported APK.")
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                "Saved to Downloads/${source.name}"
            } else {
                @Suppress("DEPRECATION")
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!dir.exists() && !dir.mkdirs()) error("Could not create the Downloads folder.")
                val target = File(dir, source.name)
                source.copyTo(target, overwrite = true)
                "Saved to Downloads/${target.name}"
            }
        }
    }

    fun installRequest(task: DownloadTask, method: InstallMethod, allowDowngrade: Boolean): InstallRequest =
        InstallRequest(
            appId = task.appId,
            appName = task.appName,
            packageName = task.packageName,
            versionName = task.versionName,
            versionCode = task.versionCode,
            apkPath = task.filePath.orEmpty(),
            expectedSha256 = task.sha256,
            method = method,
            allowDowngrade = allowDowngrade,
            verifyChecksum = false,
        )

    suspend fun markInstalled(task: DownloadTask, phase: InstallPhase) {
        if (phase is InstallPhase.Completed) {
            store.update(task.id) {
                it.copy(status = DownloadStatus.INSTALLED, updatedAt = System.currentTimeMillis(), error = null)
            }
        }
    }
}
