package com.gothwad.store.data.model

/**
 * Wire contract of the Gothwad Store Cloudflare Worker (`/api/store/...`).
 * Field names mirror `worker/src/index.ts` exactly — do not rename casually,
 * the Android client eats the same JSON as the web storefront.
 */
data class StoreApp(
    val id: String,
    val packageName: String,
    val name: String,
    val developerId: String = "",
    val developerName: String = "",
    val developerVerified: Boolean? = null,
    val category: String = "",
    val kind: String = "app",
    val status: String = "draft",
    val shortDesc: String = "",
    val fullDesc: String = "",
    val iconUrl: String = "",
    val iconColor: String = "#2f7d4f",
    val featureGraphicUrl: String? = null,
    val screenshots: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val supportEmail: String = "",
    val website: String? = null,
    val privacyPolicyUrl: String? = null,
    val ratingAvg: Double = 0.0,
    val ratingCount: Int = 0,
    val downloadsCount: Long = 0,
    val featured: String? = null,
    val createdAt: String = "",
    val updatedAt: String = "",
) {
    val isGame: Boolean get() = kind == "game"
    val hasRating: Boolean get() = ratingCount > 0
}

data class StoreRelease(
    val id: String,
    val appId: String,
    val versionCode: Long = 0,
    val versionName: String = "",
    val apkProvider: String = "github",
    val apkKey: String = "",
    val apkUrl: String = "",
    val apkSizeBytes: Long = 0,
    val apkSha256: String? = null,
    val minSdk: Int? = null,
    val targetSdk: Int? = null,
    val permissions: List<String> = emptyList(),
    val changelog: String = "",
    val reviewStatus: String = "pending",
    val reviewNote: String? = null,
    val isLive: Boolean = false,
    val createdAt: String = "",
    val downloads: Long = 0,
)

data class StoreReview(
    val id: String,
    val appId: String = "",
    val userId: String = "",
    val username: String = "",
    val rating: Int = 0,
    val body: String = "",
    val developerReply: String? = null,
    val developerReplyAt: String? = null,
    val createdAt: String = "",
)

data class StoreCategory(
    val slug: String,
    val name: String,
    val kind: String = "both",
    val icon: String = "LayoutGrid",
)

// ── Responses ───────────────────────────────────────────────────────────────
data class CatalogResponse(
    val apps: List<StoreApp> = emptyList(),
    val releases: List<StoreRelease> = emptyList(),
    val reviews: List<StoreReview> = emptyList(),
    val source: String? = null,
    val message: String? = null,
)

data class CategoriesResponse(
    val categories: List<StoreCategory> = emptyList(),
    val source: String? = null,
)

data class AppDetailResponse(
    val app: StoreApp,
    val screenshots: List<String> = emptyList(),
    val liveRelease: StoreRelease? = null,
    val releases: List<StoreRelease> = emptyList(),
    val reviews: List<StoreReview> = emptyList(),
) {
    /** Every release that this device can actually install. */
    fun installableReleases(sdk: Int): List<StoreRelease> =
        (listOfNotNull(liveRelease) + releases)
            .filter {
                it.appId == app.id &&
                    it.isLive &&
                    it.reviewStatus == "approved" &&
                    it.apkUrl.isNotBlank() &&
                    (it.minSdk ?: 1) <= sdk
            }
            .distinctBy { it.id }
            .sortedByDescending { it.versionCode }

    /** The release an Install button should download: newest compatible one. */
    fun downloadableRelease(sdk: Int): StoreRelease? = installableReleases(sdk).firstOrNull()
}

// ── Request bodies ──────────────────────────────────────────────────────────
data class ReviewBody(val rating: Int = 0, val body: String = "")
data class ReportBody(val reason: String = "Other", val details: String = "")
data class LibraryBody(val versionCode: Long? = null)

data class IdListResponse(val appIds: List<String> = emptyList())
data class OkResponse(val ok: Boolean = false, val wishlisted: Boolean? = null)
data class ReviewResponse(val review: StoreReview? = null)
data class ApiErrorBody(val error: String? = null, val message: String? = null)

// ── Gothwad Accounts ────────────────────────────────────────────────────────
data class GothwadUser(
    val id: String,
    val username: String = "",
    val name: String = "",
    val email: String = "",
    val picture: String? = null,
) {
    val displayName: String get() = name.ifBlank { username.ifBlank { "Gothwad user" } }
    val initial: String get() = displayName.trim().take(1).uppercase().ifBlank { "G" }
}

data class GothwadDeveloper(
    val id: String = "",
    val username: String = "",
    val displayName: String = "",
    val orgName: String = "",
    val verified: Boolean = false,
) {
    val label: String get() = orgName.ifBlank { displayName.ifBlank { username } }
}

data class MeResponse(
    val user: GothwadUser? = null,
    val developer: GothwadDeveloper? = null,
    val isAdmin: Boolean = false,
)

data class TokenResponse(
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val user: GothwadUser? = null,
    val expiresIn: Long? = null,
)
