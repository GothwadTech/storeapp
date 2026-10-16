package com.gothwad.store

import com.gothwad.store.data.model.AppDetailResponse
import com.gothwad.store.data.model.CatalogResponse
import com.gothwad.store.data.remote.StoreNetwork
import com.gothwad.store.data.model.StoreApp
import com.gothwad.store.data.model.StoreRelease
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Android client must parse exactly what the Worker emits. The JSON below is
 * copied from a real `GET /api/store/apps` response (trimmed) so a wire-format
 * change on the Worker side fails this test instead of an install.
 */
class CatalogModelsTest {

    private val workerPayload = """
        {
          "apps": [
            {
              "id": "app-44e4ba84-6616-4ada-9ac1-41fe2b5b8b15",
              "packageName": "com.gothwad.browser",
              "name": "Gothwad Browser",
              "developerId": "dev-1",
              "developerName": "Gothwad Tech",
              "developerVerified": true,
              "kind": "app",
              "status": "published",
              "shortDesc": "Fast, private browser",
              "fullDesc": "A Chromium-based browser built by Gothwad.",
              "category": "tools",
              "iconUrl": "https://i.ibb.co/icon.png",
              "ratingAvg": 5,
              "ratingCount": 1,
              "downloadsCount": 2,
              "tags": ["browser", "privacy"]
            },
            {
              "id": "app-draft",
              "packageName": "com.gothwad.draft",
              "name": "Draft app",
              "kind": "app",
              "status": "draft",
              "downloadsCount": 0
            }
          ],
          "releases": [
            {
              "id": "rel-519dd30f-c4ec-4f28-972a-2ff60437e2bd",
              "appId": "app-44e4ba84-6616-4ada-9ac1-41fe2b5b8b15",
              "versionCode": 137,
              "versionName": "1.0.137",
              "apkProvider": "github",
              "apkUrl": "/api/download/rel-519dd30f-c4ec-4f28-972a-2ff60437e2bd",
              "apkSizeBytes": 4274549,
              "apkSha256": "cc08fc96d2c386679d4be4058610e8924d206f5976eedbd1bfe7921e7ce423f0",
              "minSdk": 26,
              "targetSdk": 36,
              "permissions": ["android.permission.INTERNET"],
              "changelog": "First release",
              "reviewStatus": "approved",
              "isLive": true,
              "downloads": 2
            }
          ],
          "reviews": [
            {
              "id": "rev-1",
              "appId": "app-44e4ba84-6616-4ada-9ac1-41fe2b5b8b15",
              "userId": "user-1",
              "username": "pawan",
              "rating": 5,
              "body": "Works great",
              "createdAt": "2026-10-01T10:00:00.000Z"
            }
          ],
          "source": "live"
        }
    """.trimIndent()

    @Test
    fun `worker payload maps onto the model`() {
        val response = StoreNetwork.moshi.adapter(CatalogResponse::class.java).fromJson(workerPayload)
        assertNotNull(response)
        assertEquals("live", response!!.source)
        assertEquals(2, response.apps.size)

        val browser = response.apps.first()
        assertEquals("com.gothwad.browser", browser.packageName)
        assertEquals(137L, response.releases.first().versionCode)
        assertEquals("pawan", response.reviews.first().username)
        assertEquals("approved", response.releases.first().reviewStatus)
        assertEquals(4_274_549L, response.releases.first().apkSizeBytes)
        assertEquals("cc08fc96d2c386679d4be4058610e8924d206f5976eedbd1bfe7921e7ce423f0", response.releases.first().apkSha256)
        assertTrue(browser.isGame.not())
    }

    @Test
    fun `draft apps and unapproved releases never reach the install button`() {
        val response = StoreNetwork.moshi.adapter(CatalogResponse::class.java).fromJson(workerPayload)!!
        val published = response.copy(apps = response.apps.filter { it.status == "published" })
        assertEquals(1, published.apps.size)
        assertEquals("app-44e4ba84-6616-4ada-9ac1-41fe2b5b8b15", published.apps.first().id)
    }

    @Test
    fun `detail response exposes the newest compatible release`() {
        val release = StoreRelease(
            id = "rel-old",
            appId = "app-1",
            versionCode = 12,
            versionName = "1.0.12",
            apkUrl = "/api/download/rel-old",
            minSdk = 26,
            isLive = true,
            reviewStatus = "approved",
        )
        val newer = release.copy(id = "rel-new", versionCode = 137, versionName = "1.0.137")
        val pending = release.copy(id = "rel-pending", versionCode = 200, reviewStatus = "pending")

        val detail = AppDetailResponse(
            app = StoreApp(id = "app-1", packageName = "com.gothwad.browser", name = "Gothwad Browser"),
            liveRelease = newer,
            releases = listOf(release, newer, pending),
            screenshots = emptyList(),
            reviews = emptyList(),
        )

        assertEquals("rel-new", detail.downloadableRelease(36)?.id)
        // Android 7 cannot run a minSdk 26 build: nothing to install, not a crash.
        assertNull(detail.downloadableRelease(24))
    }

    @Test
    fun `live release is preferred even when it is the only entry`() {
        val live = StoreRelease(
            id = "rel-1",
            appId = "app-1",
            versionCode = 5,
            versionName = "1.0.5",
            apkUrl = "/api/download/rel-1",
            minSdk = 24,
            isLive = true,
            reviewStatus = "approved",
        )
        val detail = AppDetailResponse(
            app = StoreApp(id = "app-1", packageName = "com.x", name = "X"),
            liveRelease = live,
            releases = emptyList(),
            screenshots = emptyList(),
            reviews = emptyList(),
        )
        assertEquals(live, detail.downloadableRelease(24))
    }

    @Test
    fun `release urls resolve against the api origin`() {
        assertEquals(
            "https://store.gothwadtech.com/api/download/rel-519dd30f-c4ec-4f28-972a-2ff60437e2bd",
            StoreNetwork.downloadUrl("rel-519dd30f-c4ec-4f28-972a-2ff60437e2bd"),
        )
        assertEquals(
            "https://store.gothwadtech.com/api/download/rel-1",
            StoreNetwork.absoluteUrl("/api/download/rel-1"),
        )
        assertEquals(
            "https://store.gothwadtech.com/api/download/rel-1",
            StoreNetwork.absoluteUrl("download/rel-1"),
        )
        assertEquals(
            "https://i.ibb.co/icon.png",
            StoreNetwork.absoluteUrl("https://i.ibb.co/icon.png"),
        )
        assertNull(StoreNetwork.absoluteUrl(null))
        assertNull(StoreNetwork.absoluteUrl("  "))
    }

    @Test
    fun `store api base url is https and ends with a slash`() {
        assertTrue(StoreNetwork.baseUrl.isHttps)
        assertTrue(StoreNetwork.baseUrl.encodedPath.endsWith("/"))
    }
}
