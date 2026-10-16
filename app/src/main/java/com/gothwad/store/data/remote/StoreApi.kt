package com.gothwad.store.data.remote

import com.gothwad.store.data.model.AppDetailResponse
import com.gothwad.store.data.model.CatalogResponse
import com.gothwad.store.data.model.CategoriesResponse
import com.gothwad.store.data.model.IdListResponse
import com.gothwad.store.data.model.MeResponse
import com.gothwad.store.data.model.OkResponse
import com.gothwad.store.data.model.ReviewResponse
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming
import retrofit2.http.Url

/** Public + authenticated Worker routes used by the Android client. */
interface StoreApi {

    @GET("health")
    suspend fun health(): ResponseBody

    @GET("me")
    suspend fun me(): MeResponse

    @GET("store/home")
    suspend fun home(
        @Query("kind") kind: String? = null,
        @Query("category") category: String? = null,
    ): CatalogResponse

    @GET("store/apps")
    suspend fun apps(
        @Query("kind") kind: String? = null,
        @Query("category") category: String? = null,
        @Query("q") query: String? = null,
    ): CatalogResponse

    @GET("store/charts")
    suspend fun charts(
        @Query("kind") kind: String? = null,
        @Query("category") category: String? = null,
    ): CatalogResponse

    @GET("store/search")
    suspend fun search(
        @Query("q") query: String,
        @Query("kind") kind: String? = null,
    ): CatalogResponse

    @GET("store/categories")
    suspend fun categories(): CategoriesResponse

    @GET("store/apps/{id}")
    suspend fun detail(@Path("id") id: String): AppDetailResponse

    @GET("store/library")
    suspend fun library(): IdListResponse

    @GET("store/wishlist")
    suspend fun wishlist(): IdListResponse

    @POST("store/apps/{id}/library")
    suspend fun addToLibrary(@Path("id") id: String, @Body body: RequestBody): OkResponse

    @POST("store/apps/{id}/wishlist")
    suspend fun addWishlist(@Path("id") id: String): OkResponse

    @DELETE("store/apps/{id}/wishlist")
    suspend fun removeWishlist(@Path("id") id: String): OkResponse

    @POST("store/apps/{id}/reviews")
    suspend fun postReview(@Path("id") id: String, @Body body: RequestBody): ReviewResponse

    @DELETE("store/apps/{id}/reviews")
    suspend fun deleteReview(@Path("id") id: String): OkResponse

    @POST("store/apps/{id}/report")
    suspend fun report(@Path("id") id: String, @Body body: RequestBody): ResponseBody

    @Streaming
    @GET
    suspend fun download(@Url url: String): ResponseBody
}
