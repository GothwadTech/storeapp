package com.gothwad.store.data.remote

import com.gothwad.store.data.model.AppDetailResponse
import com.gothwad.store.data.model.CatalogResponse
import com.gothwad.store.data.model.StoreApp
import com.gothwad.store.data.model.StoreRelease
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Read-only client for F-Droid's public repository index. APK bytes stay on F-Droid. */
class FdroidCatalog(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build(),
) {
    @Volatile private var cached: CatalogResponse? = null
    @Volatile private var cachedAt: Long = 0

    suspend fun catalog(): CatalogResponse = withContext(Dispatchers.IO) {
        cached?.takeIf { System.currentTimeMillis() - cachedAt < CACHE_MILLIS }?.let { return@withContext it }
        val request = Request.Builder().url(INDEX_URL).header("Accept", "application/json").build()
        http.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "F-Droid repository returned HTTP ${response.code}" }
            val root = JSONObject(response.body?.string() ?: error("F-Droid repository returned an empty index."))
            val apps = root.optJSONObject("apps") ?: JSONObject()
            val packages = root.optJSONObject("packages") ?: JSONObject()
            val listings = mutableListOf<StoreApp>()
            val releases = mutableListOf<StoreRelease>()
            val keys = apps.keys()
            while (keys.hasNext()) {
                val packageName = keys.next()
                val meta = apps.optJSONObject(packageName) ?: continue
                val versions = packages.optJSONArray(packageName) ?: continue
                val compatible = (0 until versions.length()).mapNotNull { versions.optJSONObject(it) }
                    .filter { (it.optInt("minSdkVersion", 1)) <= android.os.Build.VERSION.SDK_INT }
                    .maxByOrNull { it.optLong("versionCode", 0L) } ?: continue
                val apkName = compatible.optString("apkName").takeIf { it.isNotBlank() } ?: continue
                val versionCode = compatible.optLong("versionCode", 0L)
                val name = meta.optString("name").ifBlank { packageName.substringAfterLast('.') }
                val summary = meta.optString("summary").ifBlank { meta.optString("description") }
                val id = "fdroid:$packageName"
                val app = StoreApp(
                    id = id,
                    packageName = packageName,
                    name = name,
                    developerName = meta.optString("authorName").ifBlank { meta.optString("authorEmail").ifBlank { "Open-source developer" } },
                    category = meta.optJSONArray("categories")?.optString(0).orEmpty(),
                    kind = "app",
                    status = "published",
                    shortDesc = summary,
                    fullDesc = meta.optString("description").ifBlank { summary },
                    iconUrl = meta.optString("icon").takeIf { it.isNotBlank() }?.let { "$REPO_URL/icons-640/$it" }.orEmpty(),
                    website = meta.optString("webSite").ifBlank { null },
                    source = "F-Droid",
                )
                val release = StoreRelease(
                    id = id,
                    appId = id,
                    versionCode = versionCode,
                    versionName = compatible.optString("versionName").ifBlank { versionCode.toString() },
                    apkProvider = "fdroid",
                    apkKey = apkName,
                    apkUrl = "$REPO_URL/$apkName",
                    apkSizeBytes = compatible.optLong("size", 0L),
                    apkSha256 = compatible.optString("hash").takeIf {
                        it.isNotBlank() && compatible.optString("hashType", "sha256").equals("sha256", ignoreCase = true)
                    },
                    minSdk = compatible.optInt("minSdkVersion", 1),
                    targetSdk = compatible.optInt("targetSdkVersion", 0).takeIf { it > 0 },
                    reviewStatus = "approved",
                    isLive = true,
                )
                listings += app
                releases += release
            }
            CatalogResponse(apps = listings, releases = releases, source = "F-Droid").also {
                cached = it
                cachedAt = System.currentTimeMillis()
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

    companion object {
        private const val INDEX_URL = "https://f-droid.org/repo/index-v1.json"
        private const val REPO_URL = "https://f-droid.org/repo"
        private const val CACHE_MILLIS = 6 * 60 * 60 * 1000L
    }
}
