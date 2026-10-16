package com.gothwad.store.data.download

import android.content.Context
import com.gothwad.store.data.remote.StoreNetwork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * In-memory + on-disk list of download jobs. A single writer (the download
 * service) keeps this consistent; UI reads the [tasks] flow.
 */
class DownloadStore(context: Context) {

    private val file = File(context.filesDir, "downloads.json")
    private val mutex = Mutex()
    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    /** APK payload directory (app-private external storage → no permission). */
    val apkDir: File = File(context.getExternalFilesDir(null) ?: context.filesDir, "apk").apply { mkdirs() }

    suspend fun load() = withContext(Dispatchers.IO) {
        mutex.withLock {
            val raw = runCatching { if (file.exists()) file.readText() else "" }.getOrDefault("")
            val parsed: List<DownloadTask> = runCatching {
                if (raw.isBlank()) emptyList()
                else StoreNetwork.moshi.adapter<List<DownloadTask>>(TASKS).fromJson(raw).orEmpty()
            }.getOrDefault(emptyList())
            // A job that was running when the process died cannot be resumed
            // silently; show it as paused so the user can press Resume.
            _tasks.value = parsed.map { task ->
                if (task.status == DownloadStatus.RUNNING) task.copy(status = DownloadStatus.PAUSED) else task
            }.sortedByDescending { it.updatedAt }
        }
    }

    fun snapshot(): List<DownloadTask> = _tasks.value

    fun find(id: String): DownloadTask? = _tasks.value.firstOrNull { it.id == id }

    suspend fun upsert(task: DownloadTask) {
        _tasks.update { current ->
            val index = current.indexOfFirst { it.id == task.id }
            val next = if (index >= 0) current.toMutableList().also { it[index] = task }
            else (current + task)
            next.sortedByDescending { it.updatedAt }
        }
        persist()
    }

    /** [persist] is false for high-frequency progress ticks (see the service). */
    suspend fun update(id: String, persist: Boolean = true, block: (DownloadTask) -> DownloadTask) {
        var updated: DownloadTask? = null
        _tasks.update { current ->
            current.map { task ->
                if (task.id == id) block(task).also { updated = it } else task
            }.sortedByDescending { it.updatedAt }
        }
        if (updated != null && persist) persist()
    }

    suspend fun remove(id: String) {
        _tasks.update { current -> current.filterNot { it.id == id } }
        persist()
    }

    suspend fun clearFinished() {
        _tasks.update { current ->
            current.filter { it.status == DownloadStatus.RUNNING || it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.PAUSED }
        }
        persist()
    }

    suspend fun clearAll() {
        _tasks.value = emptyList()
        persist()
    }

    fun deleteFiles(tasks: List<DownloadTask>) {
        tasks.forEach { task ->
            task.filePath?.let { path -> runCatching { File(path).delete() } }
        }
    }

    /** Removes orphan APKs that no job references any more. */
    fun pruneOrphanFiles(): Long {
        val referenced = _tasks.value.mapNotNull { it.filePath }.toSet()
        var freed = 0L
        apkDir.listFiles()?.forEach { candidate ->
            if (candidate.absolutePath !in referenced) {
                freed += candidate.length()
                runCatching { candidate.delete() }
            }
        }
        return freed
    }

    fun usedBytes(): Long = _tasks.value.sumOf { task ->
        task.filePath?.let { path -> runCatching { File(path).length() }.getOrDefault(0L) } ?: 0L
    }

    private suspend fun persist(): Unit = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching {
                file.writeText(
                    StoreNetwork.moshi.adapter<List<DownloadTask>>(TASKS).toJson(_tasks.value),
                )
            }
        }
    }

    private companion object {
        val TASKS = com.squareup.moshi.Types.newParameterizedType(List::class.java, DownloadTask::class.java)
    }
}
