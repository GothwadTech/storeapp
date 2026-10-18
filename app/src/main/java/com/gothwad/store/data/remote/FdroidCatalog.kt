package com.gothwad.store.data.remote

import android.os.Build
import android.util.Log
import com.gothwad.store.BuildConfig
import com.gothwad.store.data.model.AppDetailResponse
import com.gothwad.store.data.model.CatalogResponse
import com.gothwad.store.data.model.StoreApp
import com.gothwad.store.data.model.StoreRelease
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.sink
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Read-only client for F-Droid's public repository index (and IzzyOnDroid, the
 * standard companion F-Droid repository bundled in Droid-ify). APK bytes stay
 * on F-Droid / IzzyOnDroid; only the metadata is mirrored into the app.
 *
 * Key design choices modeled directly after Droid-ify (`IndexDownloader.kt`,
 * `RepositoryUpdater.kt`, `V1Syncable.kt`, `Repository.kt`) and the official
 * F-Droid client (`IndexV1Updater.kt`, `KnownRepos.kt`):
 * 1. Downloads `index-v1.jar` (compressed JAR/ZIP archive, ~8.8 MB for F-Droid
 *    and ~1.6 MB for IzzyOnDroid) instead of raw `index-v1.json` (~40+ MB),
 *    with automatic fallback to `index-v2.json` / `index-v1.json`.
 * 2. Races official HTTPS F-Droid mirrors (`f-droid.org`, `de.freedif.org`,
 *    `mirror.cyberbits.eu`, `ftp.fau.de`, `mirrors.dotsrc.org`, etc.) using a
 *    staggered Happy-Eyeballs strategy so a slow or throttled primary server
 *    never blocks loading.
 * 3. Runs downloads in a dedicated [SupervisorJob] scope so switching tabs or
 *    cancelling a single screen's coroutine never aborts an in-flight sync.
 * 4. Caches downloaded `.jar` indexes on disk and emits progressively through
 *    [catalogFlow] so the UI populates as soon as the fastest repo finishes
 *    and seamlessly upgrades when the full catalogue arrives.
 */
class FdroidCatalog(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        // Do not set a hard 120s callTimeout that kills multi-megabyte index downloads
        // mid-stream on mobile networks.
        .callTimeout(0, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build(),
    private val cacheDir: File? = null,
    private val indexUrl: String = INDEX_URL,
    private val repoUrl: String = REPO_URL,
    private val izzyRepoUrl: String = IZZY_REPO_URL,
) {
    private val catalogScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val stateMutex = Mutex()

    @Volatile private var cached: CatalogResponse? = null
    @Volatile private var fdroidCached: CatalogResponse? = null
    @Volatile private var izzyCached: CatalogResponse? = null
    @Volatile private var cachedAt: Long = 0L
    @Volatile private var activeSyncDeferred: Deferred<CatalogResponse>? = null
    @Volatile private var diskCacheChecked: Boolean = false

    private val _lastError = MutableStateFlow<String?>(null)

    /**
     * The last failure, so the UI can say "F-Droid is unavailable" instead of
     * pretending the store only ever had first-party listings.
     */
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _catalogFlow = MutableStateFlow<CatalogResponse?>(null)

    /**
     * Emits whenever the F-Droid catalogue is loaded from disk cache, updated
     * by IzzyOnDroid, or updated by the full F-Droid repository.
     */
    val catalogFlow: StateFlow<CatalogResponse?> = _catalogFlow.asStateFlow()

    private val effectiveCacheDir: File by lazy {
        val dir = cacheDir ?: File(System.getProperty("java.io.tmpdir", "/tmp"), "kiwistore-fdroid")
        runCatching { dir.mkdirs() }
        dir
    }

    private val fdroidDiskFile: File
        get() = File(effectiveCacheDir, "fdroid_index_v1.jar")

    private val izzyDiskFile: File
        get() = File(effectiveCacheDir, "izzy_index_v1.jar")

    suspend fun catalog(forceRefresh: Boolean = false): CatalogResponse {
        val now = System.currentTimeMillis()
        if (!forceRefresh) {
            cached?.let { existing ->
                if (now - cachedAt < CACHE_MILLIS && existing.apps.isNotEmpty()) {
                    return existing
                }
            }
            if (!diskCacheChecked) {
                val diskLoaded = loadFromDiskIfPresent()
                if (diskLoaded != null && diskLoaded.apps.isNotEmpty()) {
                    val isFresh = now - cachedAt < CACHE_MILLIS && fdroidCached != null
                    if (!isFresh) {
                        ensureSyncStarted(forceRefresh = false)
                    }
                    return diskLoaded
                }
            }
        }

        val deferred = ensureSyncStarted(forceRefresh = forceRefresh)
        return try {
            deferred.await()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            cached?.takeIf { it.apps.isNotEmpty() } ?: throw error
        }
    }

    suspend fun find(packageName: String): AppDetailResponse? {
        val raw = packageName.removePrefix("fdroid:")
        val catalog = runCatching { catalog() }.getOrNull() ?: cached ?: return null
        val app = catalog.apps.firstOrNull {
            it.packageName.equals(raw, ignoreCase = true) ||
                it.id.equals(packageName, ignoreCase = true)
        } ?: return null
        val liveRelease = catalog.releases.firstOrNull { it.appId == app.id }
        return AppDetailResponse(
            app = app,
            screenshots = app.screenshots,
            liveRelease = liveRelease,
            releases = listOfNotNull(liveRelease),
        )
    }

    private suspend fun loadFromDiskIfPresent(): CatalogResponse? = stateMutex.withLock {
        if (diskCacheChecked) return@withLock cached
        diskCacheChecked = true

        val abis = deviceAbis()
        val sdk = Build.VERSION.SDK_INT

        val diskFdroid = fdroidDiskFile.takeIf { it.exists() && it.length() > 1024L }?.let { file ->
            runCatching {
                FdroidIndexParser.parseFile(file, sdk, abis).toCatalog(repoUrl)
            }.onFailure { e ->
                Log.w(TAG, "Deleting corrupted cached F-Droid index ${file.name}: ${e.message}")
                runCatching { file.delete() }
            }.getOrNull()
        }

        val diskIzzy = izzyDiskFile.takeIf { it.exists() && it.length() > 1024L }?.let { file ->
            runCatching {
                FdroidIndexParser.parseFile(file, sdk, abis).toCatalog(izzyRepoUrl)
            }.onFailure { e ->
                Log.w(TAG, "Deleting corrupted cached IzzyOnDroid index ${file.name}: ${e.message}")
                runCatching { file.delete() }
            }.getOrNull()
        }

        if (diskFdroid == null && diskIzzy == null) return@withLock null

        if (diskFdroid != null) fdroidCached = diskFdroid
        if (diskIzzy != null) izzyCached = diskIzzy

        val merged = mergeCatalogs(fdroidCached, izzyCached)
        if (merged.apps.isNotEmpty()) {
            cached = merged
            val fdroidMod = fdroidDiskFile.takeIf { it.exists() }?.lastModified() ?: 0L
            val izzyMod = izzyDiskFile.takeIf { it.exists() }?.lastModified() ?: 0L
            cachedAt = when {
                fdroidMod > 0L && izzyMod > 0L -> minOf(fdroidMod, izzyMod)
                fdroidMod > 0L -> fdroidMod
                else -> izzyMod
            }
            _lastError.value = null
            _catalogFlow.value = merged
            Log.i(TAG, "Loaded ${merged.apps.size} F-Droid apps from disk cache.")
        }
        cached
    }

    private suspend fun ensureSyncStarted(forceRefresh: Boolean): Deferred<CatalogResponse> =
        stateMutex.withLock {
            val current = activeSyncDeferred
            if (!forceRefresh && current != null && current.isActive) {
                return@withLock current
            }

            val firstReady = CompletableDeferred<CatalogResponse>()
            activeSyncDeferred = firstReady

            catalogScope.launch {
                val errors = mutableListOf<String>()
                try {
                    coroutineScope {
                        val izzyJob = launch {
                            runCatching { fetchIzzyRepo() }
                                .onSuccess { izzy ->
                                    publishRepoUpdate(newFdroid = null, newIzzy = izzy, firstReady = firstReady)
                                }
                                .onFailure { e ->
                                    if (e is CancellationException) throw e
                                    Log.w(TAG, "IzzyOnDroid sync failed: ${e.message}")
                                    synchronized(errors) {
                                        errors += "IzzyOnDroid: ${e.message ?: e::class.java.simpleName}"
                                    }
                                }
                        }

                        val fdroidJob = launch {
                            runCatching { fetchOfficialFdroidRepo() }
                                .onSuccess { fdroid ->
                                    publishRepoUpdate(newFdroid = fdroid, newIzzy = null, firstReady = firstReady)
                                }
                                .onFailure { e ->
                                    if (e is CancellationException) throw e
                                    Log.e(TAG, "Official F-Droid sync failed across mirrors: ${e.message}", e)
                                    synchronized(errors) {
                                        errors += "F-Droid: ${e.message ?: e::class.java.simpleName}"
                                    }
                                }
                        }

                        izzyJob.join()
                        fdroidJob.join()
                    }

                    val finalCatalog = cached
                    if (finalCatalog != null && finalCatalog.apps.isNotEmpty()) {
                        cachedAt = System.currentTimeMillis()
                        _lastError.value = null
                        if (!firstReady.isCompleted) {
                            firstReady.complete(finalCatalog)
                        }
                    } else {
                        val combinedMessage = synchronized(errors) {
                            errors.joinToString(" | ").ifBlank { "F-Droid is unavailable right now." }
                        }
                        _lastError.value = combinedMessage
                        if (!firstReady.isCompleted) {
                            firstReady.completeExceptionally(IOException(combinedMessage))
                        }
                    }
                } catch (e: Exception) {
                    if (e !is CancellationException) {
                        val msg = e.message ?: "F-Droid is unavailable right now."
                        _lastError.value = msg
                        if (!firstReady.isCompleted) {
                            firstReady.completeExceptionally(e)
                        }
                    }
                } finally {
                    stateMutex.withLock {
                        if (activeSyncDeferred === firstReady) {
                            activeSyncDeferred = null
                        }
                    }
                }
            }

            firstReady
        }

    private suspend fun publishRepoUpdate(
        newFdroid: CatalogResponse?,
        newIzzy: CatalogResponse?,
        firstReady: CompletableDeferred<CatalogResponse>,
    ) {
        stateMutex.withLock {
            if (newFdroid != null && newFdroid.apps.isNotEmpty()) {
                fdroidCached = newFdroid
            }
            if (newIzzy != null && newIzzy.apps.isNotEmpty()) {
                izzyCached = newIzzy
            }
            val merged = mergeCatalogs(fdroidCached, izzyCached)
            if (merged.apps.isNotEmpty()) {
                cached = merged
                cachedAt = System.currentTimeMillis()
                _lastError.value = null
                _catalogFlow.value = merged
                if (!firstReady.isCompleted) {
                    firstReady.complete(merged)
                }
            }
        }
    }

    private suspend fun fetchOfficialFdroidRepo(): CatalogResponse {
        val customBase = repoUrl.trimEnd('/')
        val mirrors = buildList {
            add(customBase)
            for (m in FDROID_MIRRORS) {
                val clean = m.trimEnd('/')
                if (clean !in this) add(clean)
            }
        }

        val jarUrls = buildList {
            if (indexUrl.endsWith(".jar", ignoreCase = true)) {
                add(indexUrl)
            }
            for (m in mirrors) {
                val u = "$m/index-v1.jar"
                if (u !in this) add(u)
            }
        }

        val jarError = try {
            return raceMirrorsForIndex(
                urls = jarUrls,
                targetCacheFile = fdroidDiskFile,
                canonicalRepoUrl = repoUrl,
                staggerMillis = MIRROR_STAGGER_MS,
                maxConcurrent = 3,
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (e: Exception) {
            e
        }

        val fallbackUrls = buildList {
            if (!indexUrl.endsWith(".jar", ignoreCase = true)) add(indexUrl)
            for (m in mirrors.take(4)) {
                add("$m/index-v2.json")
                add("$m/index-v1.json")
            }
        }
        return try {
            raceMirrorsForIndex(
                urls = fallbackUrls,
                targetCacheFile = fdroidDiskFile,
                canonicalRepoUrl = repoUrl,
                staggerMillis = MIRROR_STAGGER_MS,
                maxConcurrent = 2,
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            throw jarError
        }
    }

    private suspend fun fetchIzzyRepo(): CatalogResponse {
        val base = izzyRepoUrl.trimEnd('/')
        val urls = listOf(
            "$base/index-v1.jar",
            "$base/index-v2.json",
            "$base/index-v1.json",
        )
        return raceMirrorsForIndex(
            urls = urls,
            targetCacheFile = izzyDiskFile,
            canonicalRepoUrl = izzyRepoUrl,
            staggerMillis = 4_000L,
            maxConcurrent = 2,
        )
    }

    private suspend fun raceMirrorsForIndex(
        urls: List<String>,
        targetCacheFile: File,
        canonicalRepoUrl: String,
        staggerMillis: Long,
        maxConcurrent: Int,
    ): CatalogResponse = coroutineScope {
        val resultChannel = Channel<Result<CatalogResponse>>(Channel.UNLIMITED)
        val inFlight = AtomicInteger(0)
        val parentScope = this
        val attemptCounter = AtomicInteger(0)

        val launcherJob = launch {
            for ((idx, url) in urls.withIndex()) {
                if (idx > 0) {
                    delay(staggerMillis)
                }
                while (inFlight.get() >= maxConcurrent) {
                    delay(250L)
                }
                inFlight.incrementAndGet()
                val attemptId = attemptCounter.incrementAndGet()
                parentScope.launch {
                    try {
                        val response = downloadAndParseIndex(
                            url = url,
                            targetCacheFile = targetCacheFile,
                            canonicalRepoUrl = canonicalRepoUrl,
                            attemptId = attemptId,
                        )
                        resultChannel.send(Result.success(response))
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (e: Exception) {
                        Log.w(TAG, "Mirror attempt failed ($url): ${e.message}")
                        resultChannel.send(Result.failure(e))
                    } finally {
                        inFlight.decrementAndGet()
                    }
                }
            }
        }

        var lastError: Exception? = null
        var failures = 0
        val total = urls.size
        while (failures < total) {
            val next = resultChannel.receive()
            if (next.isSuccess) {
                val winner = next.getOrThrow()
                launcherJob.cancel()
                coroutineContext[Job]?.cancelChildren()
                return@coroutineScope winner
            } else {
                failures++
                lastError = (next.exceptionOrNull() as? Exception) ?: lastError
            }
        }
        throw lastError ?: IOException("All F-Droid index mirrors failed.")
    }

    private fun downloadAndParseIndex(
        url: String,
        targetCacheFile: File,
        canonicalRepoUrl: String,
        attemptId: Int,
    ): CatalogResponse {
        val tempFile = File(
            effectiveCacheDir,
            "${targetCacheFile.name}.tmp.$attemptId.${Thread.currentThread().id}",
        )
        try {
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/java-archive, application/zip, application/json, */*")
                .header("User-Agent", "KiwiStore-Android/${BuildConfig.VERSION_NAME}")
                .build()

            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("F-Droid repository returned HTTP ${response.code} for $url")
                }
                val body = response.body ?: throw IOException("F-Droid repository returned an empty index.")
                tempFile.parentFile?.mkdirs()
                tempFile.sink().buffer().use { sink ->
                    sink.writeAll(body.source())
                }
            }

            if (!tempFile.exists() || tempFile.length() < 256L) {
                throw IOException("F-Droid index download from $url was truncated (${tempFile.length()} bytes).")
            }

            val index = FdroidIndexParser.parseFile(
                file = tempFile,
                sdkInt = Build.VERSION.SDK_INT,
                supportedAbis = deviceAbis(),
            )
            val catalog = index.toCatalog(canonicalRepoUrl)
            check(catalog.apps.isNotEmpty()) {
                "F-Droid index was read, but it has no APK compatible with Android ${Build.VERSION.SDK_INT}."
            }

            synchronized(this) {
                runCatching {
                    if (!tempFile.renameTo(targetCacheFile)) {
                        tempFile.copyTo(targetCacheFile, overwrite = true)
                        tempFile.delete()
                    }
                }
            }

            Log.i(TAG, "Loaded ${catalog.apps.size} apps from $url")
            return catalog
        } finally {
            runCatching { if (tempFile.exists()) tempFile.delete() }
        }
    }

    private fun mergeCatalogs(
        primary: CatalogResponse?,
        secondary: CatalogResponse?,
    ): CatalogResponse {
        if (primary == null && secondary == null) return CatalogResponse(source = "F-Droid")
        if (primary == null || primary.apps.isEmpty()) return secondary ?: CatalogResponse(source = "F-Droid")
        if (secondary == null || secondary.apps.isEmpty()) return primary

        val seenPackages = HashSet<String>(primary.apps.size + secondary.apps.size)
        val mergedApps = ArrayList<StoreApp>(primary.apps.size + secondary.apps.size)
        val validAppIds = HashSet<String>(primary.apps.size + secondary.apps.size)

        for (app in primary.apps) {
            if (seenPackages.add(app.packageName)) {
                mergedApps += app
                validAppIds += app.id
            }
        }
        for (app in secondary.apps) {
            if (seenPackages.add(app.packageName)) {
                mergedApps += app
                validAppIds += app.id
            }
        }
        mergedApps.sortByDescending { it.updatedAt }

        val releaseByAppId = LinkedHashMap<String, StoreRelease>(validAppIds.size)
        for (rel in secondary.releases) {
            if (rel.appId in validAppIds) releaseByAppId[rel.appId] = rel
        }
        for (rel in primary.releases) {
            if (rel.appId in validAppIds) releaseByAppId[rel.appId] = rel
        }

        return CatalogResponse(
            apps = mergedApps,
            releases = releaseByAppId.values.toList(),
            source = "F-Droid",
        )
    }

    private fun deviceAbis(): Set<String> =
        runCatching { Build.SUPPORTED_ABIS?.filter { it.isNotBlank() }?.toSet().orEmpty() }
            .getOrDefault(emptySet())

    companion object {
        private const val TAG = "FdroidCatalog"
        const val INDEX_URL = "https://f-droid.org/repo/index-v1.jar"
        const val REPO_URL = "https://f-droid.org/repo"
        const val IZZY_INDEX_URL = "https://apt.izzysoft.de/fdroid/repo/index-v1.jar"
        const val IZZY_REPO_URL = "https://apt.izzysoft.de/fdroid/repo"
        private const val CACHE_MILLIS = 6 * 60 * 60 * 1000L
        private const val MIRROR_STAGGER_MS = 3_000L

        /**
         * Official HTTPS mirrors announced in F-Droid's `repo.mirrors` metadata
         * and used by Droid-ify / F-Droid client.
         */
        val FDROID_MIRRORS: List<String> = listOf(
            "https://f-droid.org/repo",
            "https://de.freedif.org/fdroid/repo",
            "https://mirror.cyberbits.eu/fdroid/repo",
            "https://ftp.fau.de/fdroid/repo",
            "https://mirrors.dotsrc.org/fdroid/repo",
            "https://ftp.agdsn.de/fdroid/repo",
            "https://mirror.fcix.net/fdroid/repo",
            "https://mirror.freedif.org/fdroid/repo",
            "https://ftp.gwdg.de/pub/android/fdroid/repo",
            "https://ftp.lysator.liu.se/pub/fdroid/repo",
        )

        /**
         * Returns candidate download URLs for an APK or asset URL. If [url]
         * points to `https://f-droid.org/repo/...`, includes the official
         * HTTPS mirrors as automatic failover targets.
         */
        fun candidateUrls(url: String): List<String> {
            val clean = url.trim()
            val prefix = "$REPO_URL/"
            if (!clean.startsWith(prefix)) return listOf(clean)
            val relativePath = clean.removePrefix(prefix)
            return FDROID_MIRRORS.map { mirror -> "${mirror.trimEnd('/')}/$relativePath" }
        }
    }
}
