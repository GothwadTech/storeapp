package com.gothwad.store.service

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.gothwad.store.core.AppGraph
import com.gothwad.store.core.hexDigest
import com.gothwad.store.data.download.DownloadStatus
import com.gothwad.store.data.download.DownloadTask
import com.gothwad.store.data.install.InstallPhase
import com.gothwad.store.data.install.InstallRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.coroutines.coroutineContext

/**
 * Runs APK downloads behind a foreground notification, so downloading a large
 * game survives the user leaving the app. Progress is written to
 * [com.gothwad.store.data.download.DownloadStore] and mirrored in "Manage downloads".
 */
class ApkDownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = mutableMapOf<String, Job>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        AppGraph.ensure(applicationContext)
        DownloadNotifications.ensureChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        promoteToForeground()
        val id = intent?.getStringExtra(EXTRA_ID)
        when (intent?.action) {
            ACTION_START, ACTION_RESUME, ACTION_RETRY -> id?.let(::start)
            ACTION_PAUSE -> id?.let(::pause)
            ACTION_CANCEL -> id?.let(::cancel)
            ACTION_PAUSE_ALL -> jobs.keys.toList().forEach(::pause)
            ACTION_RESUME_ALL -> resumeAll()
            else -> stopIfIdle()
        }
        return START_NOT_STICKY
    }

    private fun promoteToForeground() {
        runCatching {
            ServiceCompat.startForeground(
                this,
                DownloadNotifications.SUMMARY_NOTIFICATION_ID,
                DownloadNotifications.summary(this, jobs.count { it.value.isActive }, 0),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
            )
        }
    }

    private fun start(taskId: String) {
        if (jobs[taskId]?.isActive == true) return
        val task = AppGraph.downloads.find(taskId)
        if (task == null) {
            // Nothing to download (the job was removed meanwhile): do not keep
            // a foreground service alive for it.
            stopIfIdle()
            return
        }
        jobs[taskId] = scope.launch {
            try {
                download(task)
            } catch (cancelled: CancellationException) {
                // Pause/cancel paths persist their own state.
            } catch (error: Throwable) {
                fail(task, error.message ?: "The download could not be completed.")
            }
        }
    }

    private fun pause(taskId: String) {
        jobs.remove(taskId)?.cancel()
        scope.launch {
            AppGraph.downloads.update(taskId) { it.copy(status = DownloadStatus.PAUSED, updatedAt = now()) }
            AppGraph.downloads.find(taskId)?.let { task ->
                if (AppGraph.settings.current.notifyDownloads) {
                    getSystemService(NotificationManager::class.java)
                        ?.notify(taskId.hashCode(), DownloadNotifications.paused(this@ApkDownloadService, task))
                }
            }
            stopIfIdle()
        }
    }

    private fun cancel(taskId: String) {
        jobs.remove(taskId)?.cancel()
        scope.launch {
            val task = AppGraph.downloads.find(taskId)
            task?.filePath?.let { path -> runCatching { File(path).delete() } }
            AppGraph.downloads.update(taskId) {
                it.copy(status = DownloadStatus.CANCELLED, bytesDownloaded = 0, filePath = null, updatedAt = now())
            }
            getSystemService(NotificationManager::class.java)?.cancel(taskId.hashCode())
            stopIfIdle()
        }
    }

    private fun resumeAll() {
        scope.launch {
            AppGraph.downloads.tasks.value
                .filter { it.status == DownloadStatus.PAUSED || it.status == DownloadStatus.FAILED }
                .forEach { start(it.id) }
        }
    }

    private suspend fun download(task: DownloadTask) {
        val store = AppGraph.downloads
        val target = File(store.apkDir, task.fileName.ifBlank { "${task.id}.apk" })
        val existing = if (target.exists()) target.length() else 0L
        // A finished job whose file is still on disk does not have to be
        // downloaded again, even when the publisher did not report a size.
        val alreadyComplete = existing > 0 && (
            (task.totalBytes > 0 && existing >= task.totalBytes) ||
                (task.totalBytes <= 0 && task.status == DownloadStatus.COMPLETED)
            )
        if (alreadyComplete) {
            store.update(task.id) {
                it.copy(
                    status = DownloadStatus.COMPLETED,
                    bytesDownloaded = existing,
                    filePath = target.absolutePath,
                    updatedAt = now(),
                )
            }
            finish(task.copy(bytesDownloaded = existing), target)
            return
        }

        store.update(task.id) {
            it.copy(
                status = DownloadStatus.RUNNING,
                bytesDownloaded = existing,
                filePath = target.absolutePath,
                error = null,
                updatedAt = now(),
            )
        }

        val builder = Request.Builder().url(task.url)
        if (existing > 0) builder.header("Range", "bytes=$existing-")
        val call = AppGraph.http.newCall(builder.build())
        call.execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("The Store server answered ${response.code} for this APK.")
            }
            val body = response.body ?: throw IOException("The Store returned an empty APK response.")
            val offset = if (response.code == 206) existing else 0L
            val contentLength = body.contentLength()
            val total = when {
                task.totalBytes > 0 -> task.totalBytes
                contentLength > 0 -> offset + contentLength
                else -> 0L
            }
            var written = offset
            var lastTick = 0L
            var lastBytes = written
            var lastTimestamp = System.currentTimeMillis()

            FileOutputStream(target, offset > 0).use { out ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(128 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read <= 0) break
                        out.write(buffer, 0, read)
                        written += read
                        val now = System.currentTimeMillis()
                        if (now - lastTick >= 400) {
                            lastTick = now
                            val elapsed = (now - lastTimestamp).coerceAtLeast(1L)
                            val speed = ((written - lastBytes) * 1000L) / elapsed
                            lastBytes = written
                            lastTimestamp = now
                            store.update(task.id, persist = false) {
                                it.copy(bytesDownloaded = written, totalBytes = total, status = DownloadStatus.RUNNING)
                            }
                            if (AppGraph.settings.current.notifyDownloads) {
                                getSystemService(NotificationManager::class.java)?.notify(
                                    task.id.hashCode(),
                                    DownloadNotifications.progress(
                                        this@ApkDownloadService,
                                        task.copy(bytesDownloaded = written, totalBytes = total),
                                        speed,
                                    ),
                                )
                            }
                        }
                    }
                    out.flush()
                }
            }

            val finalBytes = if (total > 0) total else written
            val completed = task.copy(
                bytesDownloaded = finalBytes,
                totalBytes = finalBytes,
                status = DownloadStatus.COMPLETED,
                filePath = target.absolutePath,
                downloadedAt = now(),
                updatedAt = now(),
                error = null,
            )
            store.upsert(completed)
            finish(completed, target)
        }
    }

    private suspend fun finish(task: DownloadTask, file: File) {
        val store = AppGraph.downloads
        val settings = AppGraph.settings.current

        val expected = task.sha256?.takeIf { it.isNotBlank() && it.length == 64 }
        if (settings.verifyChecksum && expected != null) {
            val actual = runCatching { file.hexDigest() }.getOrNull()
            if (actual != null && !actual.equals(expected, ignoreCase = true)) {
                runCatching { file.delete() }
                fail(
                    task,
                    "Checksum mismatch — the downloaded file is not the published build " +
                        "(expected ${expected.take(12)}…, got ${actual.take(12)}…). " +
                        "Nothing was installed; please retry.",
                )
                return
            }
        }

        val autoInstall = settings.autoInstallAfterDownload
        if (!settings.notifyDownloads) {
            getSystemService(NotificationManager::class.java)?.cancel(task.id.hashCode())
        } else {
            getSystemService(NotificationManager::class.java)
                ?.notify(task.id.hashCode(), DownloadNotifications.completed(this, task, autoInstall))
        }

        if (autoInstall) {
            val request = InstallRequest(
                appId = task.appId,
                appName = task.appName,
                packageName = task.packageName,
                versionName = task.versionName,
                versionCode = task.versionCode,
                apkPath = file.absolutePath,
                expectedSha256 = task.sha256,
                method = settings.installMethod,
                allowDowngrade = settings.allowDowngrade,
                verifyChecksum = false,
            )
            val phase = AppGraph.installer.install(this, request, allowUi = false) { update ->
                if (update is InstallPhase.Completed) {
                    scope.launch {
                        store.update(task.id) {
                            it.copy(status = DownloadStatus.INSTALLED, updatedAt = now(), error = null)
                        }
                        if (settings.deleteApkAfterInstall) runCatching { file.delete() }
                    }
                }
            }
            if (phase is InstallPhase.Failed) {
                store.update(task.id) { it.copy(error = phase.message, updatedAt = now()) }
            } else if (phase is InstallPhase.Completed) {
                store.update(task.id) { it.copy(status = DownloadStatus.INSTALLED, updatedAt = now()) }
                if (settings.deleteApkAfterInstall) runCatching { file.delete() }
            }
        }
        stopIfIdle()
    }

    private suspend fun fail(task: DownloadTask, message: String) {
        AppGraph.downloads.update(task.id) {
            it.copy(status = DownloadStatus.FAILED, error = message, updatedAt = now())
        }
        if (AppGraph.settings.current.notifyDownloads) {
            getSystemService(NotificationManager::class.java)
                ?.notify(task.id.hashCode(), DownloadNotifications.failed(this, task, message))
        }
        stopIfIdle()
    }

    private fun stopIfIdle() {
        if (jobs.values.none { it.isActive }) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onDestroy() {
        scope.coroutineContext.cancelChildren()
        super.onDestroy()
    }

    private fun now() = System.currentTimeMillis()

    companion object {
        const val ACTION_START = "com.gothwad.store.download.START"
        const val ACTION_PAUSE = "com.gothwad.store.download.PAUSE"
        const val ACTION_RESUME = "com.gothwad.store.download.RESUME"
        const val ACTION_CANCEL = "com.gothwad.store.download.CANCEL"
        const val ACTION_RETRY = "com.gothwad.store.download.RETRY"
        const val ACTION_PAUSE_ALL = "com.gothwad.store.download.PAUSE_ALL"
        const val ACTION_RESUME_ALL = "com.gothwad.store.download.RESUME_ALL"
        const val EXTRA_ID = "download_task_id"

        fun command(context: Context, action: String, taskId: String? = null) {
            val intent = Intent(context, ApkDownloadService::class.java).apply {
                this.action = action
                if (taskId != null) putExtra(EXTRA_ID, taskId)
            }
            // Starting a foreground service is only allowed while the app is in
            // the foreground (Android 12+ blocks background starts), so control
            // actions use a plain service start and fall back to updating the
            // job state directly when the service is not running any more.
            val needsForeground = action == ACTION_START || action == ACTION_RESUME || action == ACTION_RETRY
            val started = runCatching {
                if (needsForeground && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }.isSuccess
            if (!started && taskId != null) {
                AppGraph.appScope.launch {
                    runCatching {
                        when (action) {
                            ACTION_PAUSE -> AppGraph.downloads.update(taskId) {
                                it.copy(status = DownloadStatus.PAUSED, updatedAt = System.currentTimeMillis())
                            }
                            ACTION_CANCEL -> AppGraph.downloads.update(taskId) {
                                it.copy(
                                    status = DownloadStatus.CANCELLED,
                                    bytesDownloaded = 0,
                                    filePath = null,
                                    updatedAt = System.currentTimeMillis(),
                                )
                            }
                            ACTION_START, ACTION_RESUME, ACTION_RETRY -> {
                                // Android refused to start the download service from
                                // the background. Keep the job queued so the user can
                                // press Resume inside the app.
                                AppGraph.downloads.update(taskId) {
                                    it.copy(status = DownloadStatus.QUEUED, updatedAt = System.currentTimeMillis())
                                }
                                val task = AppGraph.downloads.find(taskId)
                                if (task != null && AppGraph.settings.current.notifyDownloads) {
                                    context.getSystemService(NotificationManager::class.java)
                                        ?.notify(
                                            taskId.hashCode(),
                                            DownloadNotifications.paused(context, task),
                                        )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
