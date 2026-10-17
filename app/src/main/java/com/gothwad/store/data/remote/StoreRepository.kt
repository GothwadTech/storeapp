package com.gothwad.store.data.remote

import com.gothwad.store.data.model.ApiErrorBody
import com.gothwad.store.data.model.CatalogResponse
import com.gothwad.store.data.model.AppDetailResponse
import com.gothwad.store.data.model.LibraryBody
import com.gothwad.store.data.model.MeResponse
import com.gothwad.store.data.model.ReportBody
import com.gothwad.store.data.model.ReviewBody
import com.gothwad.store.data.model.StoreApp
import com.gothwad.store.data.model.StoreCategory
import com.gothwad.store.data.model.StoreRelease
import com.gothwad.store.data.model.StoreReview
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * All Worker reads/writes for the storefront. Publishes only what the store
 * backend is willing to publish: `status == "published"` for listings and
 * `isLive && reviewStatus == "approved"` for releases.
 */
class StoreRepository(
    private val api: StoreApi = StoreNetwork.api,
    private val sdkProvider: () -> Int = { android.os.Build.VERSION.SDK_INT },
    private val fdroid: FdroidCatalog = FdroidCatalog(),
) {

    suspend fun catalog(kind: String?, category: String?, query: String): CatalogResponse {
        val trimmed = query.trim()
        val response = call {
            if (trimmed.isNotEmpty()) api.search(trimmed, kind) else api.apps(kind, category)
        }
        assertConfigured(response.source)
        return combine(response.published(), trimmed, kind, category)
    }

    suspend fun charts(kind: String?): CatalogResponse {
        val response = call { api.charts(kind) }
        assertConfigured(response.source)
        return response.published()
    }

    suspend fun home(kind: String?): CatalogResponse {
        val response = call { api.home(kind) }
        assertConfigured(response.source)
        return combine(response.published(), "", null, null)
    }

    /**
     * Newest installable release per app from a catalogue bundle. The Worker
     * already returns the releases with every listing, so list screens can show
     * a working Install button without a second request.
     */
    fun releaseIndex(response: CatalogResponse): Map<String, StoreRelease> =
        response.releases
            .filter {
                it.isLive && it.reviewStatus == "approved" && it.apkUrl.isNotBlank() &&
                    (it.minSdk ?: 1) <= sdkProvider()
            }
            .groupBy { it.appId }
            .mapValues { (_, releases) -> releases.maxByOrNull { it.versionCode }!! }

    private suspend fun combine(
        gothwad: CatalogResponse,
        query: String,
        kind: String?,
        category: String?,
    ): CatalogResponse {
        // The first-party catalogue remains usable if the optional upstream is unavailable.
        val external = runCatching { fdroid.catalog() }.getOrNull() ?: return gothwad
        val ownedPackages = gothwad.apps.mapTo(mutableSetOf()) { it.packageName }
        val q = query.trim().lowercase()
        val candidates = external.apps.filter { app ->
            app.packageName !in ownedPackages &&
                (kind.isNullOrBlank() || kind == "app" || kind == "both") &&
                (category.isNullOrBlank() || app.category.equals(category, ignoreCase = true)) &&
                (q.isEmpty() || app.name.contains(q, true) || app.packageName.contains(q, true) ||
                    app.shortDesc.contains(q, true) || app.developerName.contains(q, true))
        }
        val ids = candidates.mapTo(mutableSetOf()) { it.id }
        return gothwad.copy(
            apps = gothwad.apps + candidates,
            releases = gothwad.releases + external.releases.filter { it.appId in ids },
        )
    }

    private fun CatalogResponse.published(): CatalogResponse = copy(
        apps = apps.filter { it.status == "published" },
    )

    /** Cheap reachability probe used by the diagnostics screen and CI smoke tests. */
    suspend fun pingHealth(): Boolean = withContext(Dispatchers.IO) {
        runCatching { api.health().string().contains("ok", ignoreCase = true) }.getOrDefault(false)
    }

    suspend fun categories(): List<StoreCategory> {
        val response = call { api.categories() }
        assertConfigured(response.source)
        return response.categories.sortedBy { it.name.lowercase() }
    }

    suspend fun detail(id: String): AppDetailResponse {
        if (id.startsWith("fdroid:")) {
            return fdroid.find(id.removePrefix("fdroid:"))
                ?: throw StoreException("This F-Droid app is no longer in its repository index.", StoreException.Kind.NOT_FOUND)
        }
        val response = call { api.detail(id) }
        if (response.app.status != "published") throw StoreException(
            "This app is no longer available on Gothwad Store.",
            StoreException.Kind.NOT_PUBLISHED,
        )
        return response.copy(reviews = response.reviews.sortedByDescending { it.createdAt })
    }

    /** Releases this device could install, newest first. */
    fun installable(detail: AppDetailResponse): List<StoreRelease> = detail.installableReleases(sdkProvider())

    suspend fun newestRelease(id: String): StoreRelease? = installable(detail(id)).firstOrNull()

    // ── Account-scoped calls ───────────────────────────────────────────────
    suspend fun me(): MeResponse? = try {
        call { api.me() }
    } catch (e: StoreException) {
        if (e.kind == StoreException.Kind.UNAUTHORIZED) null else throw e
    }

    suspend fun libraryIds(): List<String> = call { api.library() }.appIds
    suspend fun wishlistIds(): List<String> = call { api.wishlist() }.appIds

    suspend fun addToLibrary(appId: String, versionCode: Long) = call {
        api.addToLibrary(appId, JsonBody.library(versionCode))
    }

    suspend fun addWishlist(appId: String) = call { api.addWishlist(appId) }
    suspend fun removeWishlist(appId: String) = call { api.removeWishlist(appId) }

    suspend fun submitReview(appId: String, rating: Int, body: String): StoreReview? =
        call { api.postReview(appId, JsonBody.review(rating, body)) }.review

    suspend fun deleteReview(appId: String) = call { api.deleteReview(appId) }

    suspend fun report(appId: String, reason: String, details: String) =
        call { api.report(appId, JsonBody.report(reason, details)) }

    // ── Helpers ────────────────────────────────────────────────────────────
    private fun assertConfigured(source: String?) {
        if (source == "unconfigured") throw StoreException(
            "The Store database is not configured yet. Ask the developer to run the Worker setup.",
            StoreException.Kind.UNCONFIGURED,
        )
    }

    private suspend fun <T> call(block: suspend () -> T): T = withContext(Dispatchers.IO) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            throw e.toStoreException()
        } catch (e: UnknownHostException) {
            throw StoreException("No connection. Check your network and try again.", StoreException.Kind.OFFLINE)
        } catch (e: SocketTimeoutException) {
            throw StoreException("Gothwad Store took too long to answer. Try again.", StoreException.Kind.TIMEOUT)
        } catch (e: IOException) {
            throw StoreException("Could not reach Gothwad Store. Check your connection and retry.", StoreException.Kind.OFFLINE)
        } catch (e: StoreException) {
            throw e
        } catch (e: Exception) {
            throw StoreException(
                e.message?.takeIf { it.isNotBlank() } ?: "Could not read the Store response.",
                StoreException.Kind.BAD_RESPONSE,
            )
        }
    }

    private fun HttpException.toStoreException(): StoreException {
        val code = code()
        val serverMessage = runCatching {
            val raw = response()?.errorBody()?.string().orEmpty()
            if (raw.isBlank()) null
            else StoreNetwork.moshi.adapter(ApiErrorBody::class.java).fromJson(raw)?.message
        }.getOrNull()
        return when (code) {
            401 -> StoreException(serverMessage ?: "Please sign in with Gothwad.", StoreException.Kind.UNAUTHORIZED, code)
            403 -> StoreException(serverMessage ?: "You do not have access to this action.", StoreException.Kind.SERVER, code)
            404 -> StoreException(serverMessage ?: "This app is no longer available.", StoreException.Kind.NOT_FOUND, code)
            503 -> StoreException(serverMessage ?: "The Store service is temporarily unavailable.", StoreException.Kind.UNCONFIGURED, code)
            in 500..599 -> StoreException(serverMessage ?: "Gothwad Store is having trouble. Try again shortly.", StoreException.Kind.SERVER, code)
            else -> StoreException(serverMessage ?: "The Store request failed ($code).", StoreException.Kind.SERVER, code)
        }
    }
}

/** Small typed request bodies; Moshi's reflection adapter serialises them. */
object JsonBody {
    private val mediaType = "application/json; charset=utf-8".toMediaType()

    fun review(rating: Int, body: String): RequestBody =
        encode(ReviewBody::class.java, ReviewBody(rating, body))

    fun report(reason: String, details: String): RequestBody =
        encode(ReportBody::class.java, ReportBody(reason, details))

    fun library(versionCode: Long): RequestBody =
        encode(LibraryBody::class.java, LibraryBody(versionCode))

    private fun <T> encode(type: Class<T>, value: T): RequestBody =
        StoreNetwork.moshi.adapter(type).toJson(value).toRequestBody(mediaType)
}
