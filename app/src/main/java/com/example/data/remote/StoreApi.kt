package com.example.data.remote

import com.example.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface StoreApi {
    @GET("store/apps")
    suspend fun catalog(@Query("kind") kind: String? = null,
                        @Query("category") category: String? = null,
                        @Query("q") query: String? = null): CatalogResponse
    @GET("store/categories")
    suspend fun categories(): CategoriesResponse
    @GET("store/apps/{id}")
    suspend fun detail(@Path("id") id: String): AppDetailResponse
}

class StoreRepository(private val api: StoreApi) {
    suspend fun catalog(kind: String?, category: String?, query: String): List<StoreApp> {
        val response = api.catalog(kind, category, query.trim().ifEmpty { null })
        check(response.source != "unconfigured") { "The Store database is not configured yet." }
        return response.apps.filter { it.status == "published" }
    }
    suspend fun categories(): List<StoreCategory> {
        val response = api.categories()
        check(response.source != "unconfigured") { "The Store database is not configured yet." }
        return response.categories
    }
    suspend fun detail(id: String): AppDetailResponse {
        val response = api.detail(id)
        check(response.app.status == "published") { "This app is no longer available." }
        return response
    }
}

object StoreNetwork {
    val baseUrl: HttpUrl = BuildConfig.STORE_API_BASE_URL.toHttpUrl().also {
        require(it.isHttps && it.encodedPath.endsWith("/")) {
            "STORE_API_BASE_URL must be an HTTPS API URL ending in /"
        }
    }
    val repository: StoreRepository by lazy {
        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS)
            .build()
        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        StoreRepository(Retrofit.Builder().baseUrl(baseUrl).client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi)).build()
            .create(StoreApi::class.java))
    }

    // The Worker performs download counting and redirects to GitHub/B2.
    // Do not trust an arbitrary APK host supplied in listing metadata.
    fun downloadUrl(releaseId: String): String = baseUrl.newBuilder()
        .addPathSegment("download").addPathSegment(releaseId).build().toString()
}
