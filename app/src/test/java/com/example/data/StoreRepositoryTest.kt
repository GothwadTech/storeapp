package com.example.data

import com.example.data.remote.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class StoreRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: StoreRepository

    @Before fun setup() {
        server = MockWebServer()
        server.start()
        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        repository = StoreRepository(Retrofit.Builder().baseUrl(server.url("/api/"))
            .addConverterFactory(MoshiConverterFactory.create(moshi)).build().create(StoreApi::class.java))
    }
    @After fun teardown() { server.shutdown() }
    private fun reply(json: String, status: Int = 200) {
        server.enqueue(MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(json))
    }
    private fun app(status: String = "published") = """{
        "id":"app-1","packageName":"com.gothwad.notes","name":"Notes",
        "developerName":"Gothwad","category":"tools","kind":"app","status":"$status"
    }"""

    @Test fun `catalog uses Worker path and query parameters and excludes unpublished listings`() = runTest {
        reply("""{"source":"live","apps":[${app()},${app("draft")}]}""")
        val apps = repository.catalog("app", "tools", "  notes & tasks  ")
        assertEquals(1, apps.size)
        assertEquals("Notes", apps.single().name)
        val request = server.takeRequest()
        assertEquals("/api/store/apps", request.requestUrl!!.encodedPath)
        assertEquals("app", request.requestUrl!!.queryParameter("kind"))
        assertEquals("tools", request.requestUrl!!.queryParameter("category"))
        assertEquals("notes & tasks", request.requestUrl!!.queryParameter("q"))
    }

    @Test fun `unconfigured backend is an error not empty success or demo fallback`() = runTest {
        reply("""{"source":"unconfigured","apps":[]}""")
        try {
            repository.catalog(null, null, "")
            fail("Expected unconfigured error")
        } catch (e: IllegalStateException) { assertTrue(e.message!!.contains("not configured")) }
    }

    @Test fun `empty live catalog is valid`() = runTest {
        reply("""{"source":"live","apps":[]}""")
        assertTrue(repository.catalog(null, null, "").isEmpty())
        assertNull(server.takeRequest().requestUrl!!.queryParameter("q"))
    }

    @Test fun `categories follow shared schema`() = runTest {
        reply("""{"categories":[{"slug":"tools","name":"Tools","kind":"both","icon":"Grid"}]}""")
        assertEquals(StoreCategory("tools", "Tools", "both"), repository.categories().single())
        assertEquals("/api/store/categories", server.takeRequest().requestUrl!!.encodedPath)
    }

    @Test fun `detail parses live release screenshots reviews and nullable fields`() = runTest {
        reply("""{"app":${app()},"liveRelease":{
            "id":"rel-1","appId":"app-1","versionCode":2,"versionName":"1.1",
            "apkUrl":"/api/download/rel-1","apkSizeBytes":1048576,"apkSha256":null,
            "minSdk":24,"permissions":[],"reviewStatus":"approved","isLive":true
        },"reviews":[{"id":"rev-1","appId":"app-1","username":"reader","rating":5,"body":"Nice"}]}""")
        val detail = repository.detail("app-1")
        assertEquals("/api/store/apps/app-1", server.takeRequest().requestUrl!!.encodedPath)
        assertEquals("rel-1", detail.downloadableRelease(24)?.id)
        assertNull(detail.liveRelease!!.apkSha256)
        assertEquals("Nice", detail.reviews.single().body)
    }

    @Test fun `404 propagates instead of inventing a listing`() = runTest {
        reply("""{"error":"not_found","message":"App not found"}""", 404)
        try { repository.detail("missing"); fail("Expected HTTP error") }
        catch (e: HttpException) { assertEquals(404, e.code()) }
    }

    @Test fun `download selection requires matching app approval live file and device compatibility`() {
        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        val listing = moshi.adapter(StoreApp::class.java).fromJson(app())!!
        val good = StoreRelease("good", "app-1", 2, "1.1", "/api/download/good",
            minSdk = 24, reviewStatus = "approved", isLive = true)
        val detail = AppDetailResponse(listing, releases = listOf(
            good,
            good.copy(id = "pending", versionCode = 10, reviewStatus = "pending"),
            good.copy(id = "not-live", versionCode = 11, isLive = false),
            good.copy(id = "new-android", versionCode = 12, minSdk = 36),
            good.copy(id = "other-app", versionCode = 13, appId = "other"),
            good.copy(id = "no-file", versionCode = 14, apkUrl = "")
        ))
        assertEquals("good", detail.downloadableRelease(24)?.id)
        assertNull(detail.downloadableRelease(23))
        assertEquals("new-android", detail.downloadableRelease(36)?.id)
    }
}
