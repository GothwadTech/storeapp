package com.gothwad.store.data.remote

import com.gothwad.store.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/** Raised for every store failure so screens never show raw exceptions. */
class StoreException(
    message: String,
    val kind: Kind,
    val httpCode: Int? = null,
) : Exception(message) {
    enum class Kind { OFFLINE, TIMEOUT, SERVER, NOT_FOUND, NOT_PUBLISHED, UNCONFIGURED, UNAUTHORIZED, BAD_RESPONSE, UNKNOWN }

    val isRetryable: Boolean
        get() = kind == Kind.OFFLINE || kind == Kind.TIMEOUT || kind == Kind.SERVER
}

/**
 * Single HTTP stack for the app. The Android client only ever talks to the
 * public Worker; Supabase keys stay on the server.
 */
object StoreNetwork {

    val baseUrl: HttpUrl = BuildConfig.STORE_API_BASE_URL.toHttpUrl().also {
        require(it.isHttps && it.encodedPath.endsWith("/")) {
            "STORE_API_BASE_URL must be an HTTPS API URL ending in /"
        }
    }

    val moshi: Moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    /** Supplies the current Gothwad Accounts access token, if any. */
    @Volatile
    var tokenProvider: () -> String? = { null }

    private val authInterceptor = Interceptor { chain ->
        val token = tokenProvider()
        val request = chain.request().newBuilder()
            .header("Accept", "application/json")
            .header("User-Agent", "KiwiStore-Android/${BuildConfig.VERSION_NAME}")
            .apply { if (!token.isNullOrBlank()) header("Authorization", "Bearer $token") }
            .build()
        chain.proceed(request)
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(authInterceptor)
            .build()
    }

    /** Long-lived client for APK streaming (no call timeout). */
    val downloadClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor(authInterceptor)
            .build()
    }

    val api: StoreApi by lazy {
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi).asLenient())
            .build()
            .create(StoreApi::class.java)
    }

    /**
     * Counted download redirect. The Worker increments the counter and 302s to
     * the real file host (GitHub releases / Backblaze B2) so listing metadata
     * can never point the client at an arbitrary host.
     */
    fun downloadUrl(releaseId: String): String = baseUrl.newBuilder()
        .addPathSegment("download")
        .addPathSegment(releaseId)
        .build()
        .toString()

    /**
     * Resolves the Worker's relative `apkUrl` (`/api/download/{id}`) and any
     * other server-provided path against the API origin. Absolute URLs are
     * passed through untouched.
     */
    fun absoluteUrl(pathOrUrl: String?): String? {
        val value = pathOrUrl?.trim().orEmpty()
        if (value.isEmpty()) return null
        if (value.startsWith("http://") || value.startsWith("https://")) return value
        if (value.startsWith("/")) {
            val builder = baseUrl.newBuilder().encodedPath(value.substringBefore('?'))
            value.substringAfter('?', "").takeIf { it.isNotEmpty() }?.let { builder.encodedQuery(it) }
            return builder.build().toString()
        }
        return baseUrl.resolve(value)?.toString()
    }

    internal fun peek(response: Response): String = response.body?.string().orEmpty()
}
