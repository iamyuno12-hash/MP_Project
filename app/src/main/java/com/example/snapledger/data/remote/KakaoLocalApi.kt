package com.example.snapledger.data.remote

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

/** 카카오 로컬 API - 키워드로 장소 검색 */
interface KakaoLocalApi {
    @GET("v2/local/search/keyword.json")
    suspend fun searchKeyword(
        @Query("query") query: String,
        @Query("size") size: Int = 1,
    ): KakaoKeywordResponse
}

data class KakaoKeywordResponse(
    @SerializedName("documents") val documents: List<KakaoPlace> = emptyList(),
)

data class KakaoPlace(
    @SerializedName("place_name") val placeName: String = "",
    @SerializedName("category_name") val categoryName: String = "",          // 예: 음식점 > 분식
    @SerializedName("category_group_code") val categoryGroupCode: String = "", // 예: FD6
)

object KakaoApiClient {
    private const val BASE_URL = "https://dapi.kakao.com/"

    fun create(restApiKey: String): KakaoLocalApi {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("Authorization", "KakaoAK $restApiKey")
                    .build()
                chain.proceed(request)
            }
            .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
            .build()

        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(KakaoLocalApi::class.java)
    }
}
