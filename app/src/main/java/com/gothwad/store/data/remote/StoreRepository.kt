package com.gothwad.store.data.remote

import android.util.Log
import com.gothwad.store.data.model.ApiErrorBody
import com.gothwad.store.data.model.AppDetailResponse
import com.gothwad.store.data.model.CatalogResponse
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
     * Emits whenever the F-Droid + IzzyOnDroid catalogue is loaded from disk
     * cache or updated over the network.
     */
    val fdroidUpdates: StateFlow<CatalogResponse?> = fdroid.catalogFlow

    /**
     * The Kiwi Store catalogue only.
     *
     * Split out from [catalog] so the UI can show first-party listings right
     * away and add the F-Droid catalogue once its index has been downloaded.
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

    /**
     * Synchronously merges [gothwad] first-party listings with an already-loaded
     * [external] F-Droid catalogue, applying [kind], [category], and [query]
     * filters and relevance ranking for search.
     */
    fun mergeWithExternal(
        gothwad: CatalogResponse,
        external: CatalogResponse,
        kind: String?,
        category: String?,
        query: String = "",
    ): CatalogResponse {
        if (external.apps.isEmpty()) return gothwad
        val ownedPackages = gothwad.apps.mapTo(mutableSetOf()) { it.packageName }
        val q = query.trim().lowercase()

        val baseSequence = external.apps
            .asSequence()
            .filter { app ->
                app.packageName !in ownedPackages &&
                    kindMatches(app, kind) &&
                    categoryMatches(app, category)
            }

        val candidates = if (q.isEmpty()) {
            baseSequence.take(MAX_FDROID_APPS).toList()
        } else {
            baseSequence
                .mapNotNull { app ->
                    val score = searchScore(app, q)
                    if (score > 0) app to score else null
                }
                .sortedWith(
                    compareByDescending<Pair<StoreApp, Int>> { it.second }
                        .thenByDescending { it.first.updatedAt },
                )
                .map { it.first }
                .take(MAX_FDROID_APPS)
                .toList()
        }

        val ids = candidates.mapTo(mutableSetOf()) { it.id }
        return gothwad.copy(
            // Distinct by id: the UI uses the id as the list key, so a listing
            // that appears twice would crash the list.
            apps = (gothwad.apps + candidates).distinctBy { it.id },
            releases = gothwad.releases + external.releases.filter { it.appId in ids },
        )
    }

    /** The optional F-Droid catalogue on its own. */
    suspend fun fdroidCatalog(forceRefresh: Boolean = false): CatalogResponse =
        fdroid.catalog(forceRefresh)

    suspend fun catalog(kind: String?, category: String?, query: String): CatalogResponse {
        val base = runCatching { firstPartyCatalog(kind, category, query) }
            .getOrDefault(CatalogResponse())
        return combine(base, query.trim(), kind, category)
    }

    suspend fun home(kind: String?): CatalogResponse {
        val base = runCatching { firstPartyHome(kind) }
            .getOrDefault(CatalogResponse())
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
        val external = loadFdroid() ?: return gothwad
        return mergeWithExternal(gothwad, external, kind, category, query)
    }

    private fun searchScore(app: StoreApp, needle: String): Int {
        val nameLower = app.name.lowercase()
        val pkgLower = app.packageName.lowercase()
        val shortLower = app.shortDesc.lowercase()
        val devLower = app.developerName.lowercase()

        if (nameLower == needle || pkgLower == needle) return 100
        if (nameLower.startsWith(needle)) return 85
        val words = nameLower.split(' ', '-', '_', '.', ':')
        if (words.any { it == needle }) return 75
        if (words.any { it.startsWith(needle) }) return 65
        if (nameLower.contains(needle)) return 55
        if (pkgLower.contains(needle)) return 45
        if (app.tags.any { it.lowercase().contains(needle) }) return 35
        if (devLower.contains(needle)) return 30
        if (shortLower.contains(needle)) return 20
        if (app.fullDesc.lowercase().contains(needle)) return 10
        return 0
    }

    /** F-Droid keeps games in a category and everything else in "app". */
    private fun kindMatches(app: StoreApp, kind: String?): Boolean =
        when (kind?.trim()?.lowercase()) {
            null, "", "both" -> true
            "app" -> app.kind == "app"
            "game" -> app.kind == "game"
            else -> true
        }

    /**
     * Matches an app against a category slug or display name, checking both
     * `app.category`, `app.tags`, and the normalized Kiwi Store / F-Droid
     * category equivalence groups.
     */
    internal fun categoryMatches(app: StoreApp, category: String?): Boolean {
        val wanted = normalizeCategory(category)
        if (wanted.isEmpty()) return true
        val equivalents = CATEGORY_EQUIVALENTS[wanted] ?: setOf(wanted)
        if (normalizeCategory(app.category) in equivalents) return true
        return app.tags.any { normalizeCategory(it) in equivalents }
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
        val upstream = runCatching {
            val response = call { api.categories() }
            assertConfigured(response.source)
            response.categories
        }.getOrDefault(emptyList())

        val existingSlugs = upstream.mapTo(HashSet()) { normalizeCategory(it.slug) }
        val merged = ArrayList<StoreCategory>(upstream.size + DEFAULT_CATEGORIES.size)
        merged.addAll(upstream)
        for (fallback in DEFAULT_CATEGORIES) {
            if (normalizeCategory(fallback.slug) !in existingSlugs) {
                merged += fallback
            }
        }
        return merged.sortedBy { it.name.lowercase() }
    }

    suspend fun detail(id: String): AppDetailResponse {
        if (id.startsWith("fdroid:")) {
            return fdroid.find(id.removePrefix("fdroid:"))
                ?: throw StoreException("This F-Droid app is no longer in its repository index.", StoreException.Kind.NOT_FOUND)
        }
        val firstParty = runCatching {
            val response = call { api.detail(id) }
            if (response.app.status != "published") throw StoreException(
                "This app is no longer available on Kiwi Store.",
                StoreException.Kind.NOT_PUBLISHED,
            )
            response.copy(reviews = response.reviews.sortedByDescending { it.createdAt })
        }
        firstParty.getOrNull()?.let { return it }
        fdroid.find(id)?.let { return it }
        throw (firstParty.exceptionOrNull()
            ?: StoreException("This app is no longer available.", StoreException.Kind.NOT_FOUND))
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

    companion object {
        private const val TAG = "StoreRepository"
        private const val MAX_FDROID_APPS = 500

        private val DEFAULT_CATEGORIES = listOf(
            StoreCategory(slug = "tools", name = "Tools & Utilities", kind = "app"),
            StoreCategory(slug = "browser", name = "Browsers & Internet", kind = "app"),
            StoreCategory(slug = "communication", name = "Communication", kind = "app"),
            StoreCategory(slug = "productivity", name = "Productivity", kind = "app"),
            StoreCategory(slug = "education", name = "Science & Education", kind = "app"),
            StoreCategory(slug = "music", name = "Music & Audio", kind = "app"),
            StoreCategory(slug = "photo", name = "Photography & Graphics", kind = "app"),
            StoreCategory(slug = "finance", name = "Money & Finance", kind = "app"),
            StoreCategory(slug = "health", name = "Sports & Health", kind = "app"),
            StoreCategory(slug = "maps", name = "Maps & Navigation", kind = "app"),
            StoreCategory(slug = "entertainment", name = "Games & Entertainment", kind = "both"),
        )

        private val CATEGORY_EQUIVALENTS: Map<String, Set<String>> = mapOf(
            "games" to setOf("games", "game", "entertainment"),
            "game" to setOf("games", "game", "entertainment"),
            "entertainment" to setOf("entertainment", "games", "game", "video", "multimedia"),
            "browser" to setOf("browser", "internet", "web", "communication"),
            "internet" to setOf("internet", "browser", "web", "communication"),
            "communication" to setOf("communication", "messaging", "phone-sms", "internet", "social", "connectivity"),
            "social" to setOf("social", "communication", "messaging", "internet"),
            "tools" to setOf("tools", "system", "security", "development", "theming", "automation", "connectivity", "navigation", "xposed"),
            "security" to setOf("security", "tools", "privacy", "system"),
            "productivity" to setOf("productivity", "reading", "writing", "office", "time", "calendar-agenda", "note", "task"),
            "books" to setOf("books", "reading", "writing", "education", "science-education", "religion"),
            "news" to setOf("news", "reading", "internet", "entertainment", "news-magazines", "readers-news"),
            "education" to setOf("education", "science-education", "reading", "books", "religion"),
            "music" to setOf("music", "multimedia", "audio", "music-audio"),
            "video" to setOf("video", "multimedia", "entertainment", "music"),
            "photo" to setOf("photo", "photography", "graphics", "images", "wallpapers"),
            "photography" to setOf("photography", "photo", "graphics", "images", "wallpapers"),
            "finance" to setOf("finance", "money", "shopping", "business"),
            "shopping" to setOf("shopping", "money", "finance"),
            "health" to setOf("health", "sports-health", "lifestyle", "food", "entertainment"),
            "lifestyle" to setOf("lifestyle", "sports-health", "health", "food"),
            "food" to setOf("food", "lifestyle", "health", "entertainment"),
            "maps" to setOf("maps", "navigation", "navigation-maps", "travel", "tools"),
            "travel" to setOf("travel", "navigation", "maps"),
        )

        private fun normalizeCategory(value: String?): String =
            value?.trim()?.lowercase()?.replace(Regex("[^a-z0-9]+"), "-")?.trim('-').orEmpty()
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
