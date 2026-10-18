package com.gothwad.store.data.remote

import android.os.Build
import com.gothwad.store.BuildConfig
import com.gothwad.store.data.model.AppDetailResponse
import com.gothwad.store.data.model.CatalogResponse
import com.squareup.moshi.JsonReader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Read-only client for F-Droid's public repository index. APK bytes stay on
 * F-Droid; only the metadata is mirrored into the app.
 *
 * The index is roughly 8 MB, so it is streamed through Moshi instead of being
 * materialised as one giant string — otherwise the app runs out of heap on
 * mid-range devices and the catalogue silently disappears.
 */
class FdroidCatalog(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(120, TimeUnit.SECONDS)
        .build(),
) {
    @Volatile private var cached: CatalogResponse? = null
    @Volatile private var cachedAt: Long = 0

    /** One download at a time: home, apps and games all ask for the index. */
    private val mutex = Mutex()

    private val _lastError = MutableStateFlow<String?>(null)

    /**
     * The last failure, so the UI can say "F-Droid is unavailable" instead of
     * pretending the store only ever had first-party listings.
     */
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    suspend fun catalog(): CatalogResponse = withContext(Dispatchers.IO) {
        cached?.takeIf { System.currentTimeMillis() - cachedAt < CACHE_MILLIS }?.let { return@withContext it }
        mutex.withLock {
            // Another caller may have finished the download while we waited.
            cached?.takeIf { System.currentTimeMillis() - cachedAt < CACHE_MILLIS }
                ?: try {
                    download()
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: Exception) {
                    _lastError.value = error.message ?: "F-Droid is unavailable right now."
                    throw error
                }
        }
    }

    suspend fun find(packageName: String): AppDetailResponse? {
        val catalog = catalog()
        val app = catalog.apps.firstOrNull { it.packageName == packageName } ?: return null
        return AppDetailResponse(
            app = app,
            liveRelease = catalog.releases.firstOrNull { it.appId == app.id },
        )
    }

    private fun download(): CatalogResponse {
        val request = Request.Builder()
            .url(INDEX_URL)
            .header("Accept", "application/json")
            .header("User-Agent", "KiwiStore-Android/${BuildConfig.VERSION_NAME}")
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("F-Droid repository returned HTTP ${response.code}")
            val body = response.body ?: error("F-Droid repository returned an empty index.")
            val index = FdroidIndexParser.parse(JsonReader.of(body.source()), Build.VERSION.SDK_INT)
            val catalog = index.toCatalog(REPO_URL)
            check(catalog.apps.isNotEmpty()) {
                "F-Droid index was read, but it has no APK compatible with Android ${Build.VERSION.SDK_INT}."
            }
            return catalog.also { parsed ->
                cached = parsed
                cachedAt = System.currentTimeMillis()
                _lastError.value = null
            }
        }
    }

    companion object {
        const val INDEX_URL = "https://f-droid.org/repo/index-v1.json"
        const val REPO_URL = "https://f-droid.org/repo"
        private const val CACHE_MILLIS = 6 * 60 * 60 * 1000L
    }
}
