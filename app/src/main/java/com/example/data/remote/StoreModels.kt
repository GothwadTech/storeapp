package com.example.data.remote

// Worker camelCase wire contract; never use demo AppItem defaults for live data.
data class StoreApp(
    val id: String,
    val packageName: String,
    val name: String,
    val developerName: String,
    val category: String,
    val kind: String,
    val status: String,
    val shortDesc: String = "",
    val fullDesc: String = "",
    val iconUrl: String = "",
    val screenshots: List<String> = emptyList(),
    val ratingAvg: Double = 0.0,
    val ratingCount: Int = 0,
    val downloadsCount: Long = 0
)

data class StoreRelease(
    val id: String,
    val appId: String,
    val versionCode: Long,
    val versionName: String,
    val apkUrl: String = "",
    val apkSizeBytes: Long = 0,
    val apkSha256: String? = null,
    val minSdk: Int? = null,
    val permissions: List<String> = emptyList(),
    val changelog: String = "",
    val reviewStatus: String,
    val isLive: Boolean = false
)

data class StoreReview(
    val id: String,
    val appId: String,
    val username: String,
    val rating: Int,
    val body: String = "",
    val developerReply: String? = null
)

data class StoreCategory(val slug: String, val name: String, val kind: String)
data class CatalogResponse(
    val apps: List<StoreApp> = emptyList(),
    val source: String? = null
)
data class CategoriesResponse(
    val categories: List<StoreCategory> = emptyList(),
    val source: String? = null
)
data class AppDetailResponse(
    val app: StoreApp,
    val liveRelease: StoreRelease? = null,
    val releases: List<StoreRelease> = emptyList(),
    val reviews: List<StoreReview> = emptyList()
) {
    fun downloadableRelease(sdk: Int): StoreRelease? =
        (listOfNotNull(liveRelease) + releases).filter {
            it.appId == app.id && it.isLive && it.reviewStatus == "approved" &&
                it.apkUrl.isNotBlank() && (it.minSdk ?: 1) <= sdk
        }.maxByOrNull { it.versionCode }
}
