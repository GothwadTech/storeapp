package com.gothwad.store.data.remote

import android.util.Log
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
import kotlinx.coroutines.flow.StateFlow
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

    /**
     * Why the optional F-Droid catalogue is missing, if it is. Null means the
     * catalogue loaded fine.
     */
    val fdroidError: StateFlow<String?> = fdroid.lastError

    /**
     * The Kiwi Store catalogue only.
     *
     * Split out from [catalog] so the UI can show first-party listings right
     * away and add the F-Droid catalogue once its (large) index has been
     * downloaded — waiting for ~8 MB before rendering anything leaves the
     * user staring at a spinner.
     */
    suspend fun firstPartyCatalog(kind: String?, category: String?, query: String): CatalogResponse {
        val trimmed = query.trim()
        val response = call {
            if (trimmed.isNotEmpty()) api.search(trimmed, kind) else api.apps(kind, category)
        }
        assertConfigured(response.source)
        return response.published()
    }

    /** The Kiwi Store home bundle, without the optional upstream catalogue. */
    suspend fun firstPartyHome(kind: String?): CatalogResponse {
        val response = call { api.home(kind) }
        assertConfigured(response.source)
        return response.published()
    }

    /** Adds the optional F-Droid catalogue to an already loaded response. */
    suspend fun withFdroid(
        base: CatalogResponse,
        kind: String?,
        category: String?,
        query: String = "",
    ): CatalogResponse = combine(base, query.trim(), kind, category)

    /** The optional F-Droid catalogue on its own. */
    suspend fun fdroidCatalog(): CatalogResponse = fdroid.catalog()

    suspend fun catalog(kind: String?, category: String?, query: String): CatalogResponse {
        val base = firstPartyCatalog(kind, category, query)
        return combine(base, query.trim(), kind, category)
    }

    suspend fun home(kind: String?): CatalogResponse {
        val base = firstPartyHome(kind)
        return combine(base, "", kind, null)
    }

    suspend fun charts(kind: String?): CatalogResponse {
        val response = call { api.charts(kind) }
        assertConfigured(response.source)
        return response.published()
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
        val external = loadFdroid() ?: return gothwad
        val ownedPackages = gothwad.apps.mapTo(mutableSetOf()) { it.packageName }
        val q = query.trim().lowercase()
        val candidates = external.apps.filter { app ->
            app.packageName !in ownedPackages &&
                kindMatches(app, kind) &&
                categoryMatches(app, category) &&
                (q.isEmpty() || app.name.contains(q, true) || app.packageName.contains(q, true) ||
                    app.shortDesc.contains(q, true) || app.developerName.contains(q, true))
        }
        val ids = candidates.mapTo(mutableSetOf()) { it.id }
        return gothwad.copy(
            // Distinct by id: the UI uses the id as the list key, so a listing
            // that appears twice would crash the list.
            apps = (gothwad.apps + candidates).distinctBy { it.id },
            releases = gothwad.releases + external.releases.filter { it.appId in ids },
        )
    }

    /** F-Droid keeps games in a category and everything else in "app". */
    private fun kindMatches(app: StoreApp, kind: String?): Boolean =
        when (kind?.trim()?.lowercase()) {
            null, "", "both" -> true
            "app" -> app.kind == "app"
            "game" -> app.kind == "game"
            else -> true
        }

    /** Both catalogues use Kiwi Store slugs, so a plain comparison is enough. */
    private fun categoryMatches(app: StoreApp, category: String?): Boolean {
        val wanted = category?.trim().orEmpty()
        if (wanted.isEmpty()) return true
        return app.category.equals(wanted, ignoreCase = true)
    }

    /**
     * F-Droid is optional: a failure must never break the Kiwi catalogue, but
     * it must not be silent either — the reason stays visible on the
     * diagnostics screen instead of vanishing.
     */
    private suspend fun loadFdroid(): CatalogResponse? = try {
        fdroid.catalog()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        Log.w(TAG, "F-Droid catalogue unavailable: ${error.message}", error)
        null
    }

    private fun CatalogResponse.published(): CatalogResponse = copy(
        apps = apps.filter { it.status == "published" }.distinctBy { it.id },
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
            "This app is no longer available on Kiwi Store.",
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
            throw StoreException("Kiwi Store took too long to answer. Try again.", StoreException.Kind.TIMEOUT)
        } catch (e: IOException) {
            throw StoreException("Could not reach Kiwi Store. Check your connection and retry.", StoreException.Kind.OFFLINE)
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
            in 500..599 -> StoreException(serverMessage ?: "Kiwi Store is having trouble. Try again shortly.", StoreException.Kind.SERVER, code)
            else -> StoreException(serverMessage ?: "The Store request failed ($code).", StoreException.Kind.SERVER, code)
        }
    }

    private companion object {
        const val TAG = "StoreRepository"
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
