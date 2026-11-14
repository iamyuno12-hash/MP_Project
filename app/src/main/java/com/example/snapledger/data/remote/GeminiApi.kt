package com.example.snapledger.data.remote

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

/**
 * Gemini API (Google AI Studio) - 사진 + 질문을 보내면 답을 글로 돌려준다.
 * 문서: https://ai.google.dev/gemini-api/docs
 */
interface GeminiApi {
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Body body: GeminiRequest,
    ): GeminiResponse
}

// ---------- 요청 ----------

data class GeminiRequest(
    @SerializedName("contents") val contents: List<GeminiContent>,
)

data class GeminiContent(
    @SerializedName("role") val role: String = "user",
    @SerializedName("parts") val parts: List<GeminiPart>? = null,
)

/** text나 inlineData 중 하나만 채운다 (null인 칸은 JSON에 안 들어감) */
data class GeminiPart(
    @SerializedName("text") val text: String? = null,
    @SerializedName("inlineData") val inlineData: GeminiInlineData? = null,
    /** 응답에서만: 모델이 생각한 과정이면 true (답이 아니므로 건너뜀) */
    @SerializedName("thought") val thought: Boolean? = null,
)

data class GeminiInlineData(
    @SerializedName("mimeType") val mimeType: String,
    @SerializedName("data") val data: String,   // Base64
)

// ---------- 응답 ----------

// Gson은 JSON에 없는 칸을 기본값 대신 null로 채우므로 응답 쪽 칸은 모두 nullable로 둔다
data class GeminiResponse(
    @SerializedName("candidates") val candidates: List<GeminiCandidate>? = null,
) {
    /** 첫 번째 답의 글 부분만 이어 붙인 것 (생각 과정은 뺌) */
    val text: String
        get() = candidates?.firstOrNull()?.content?.parts.orEmpty()
            .filter { it.thought != true }
            .mapNotNull { it.text }
            .joinToString("")
}

data class GeminiCandidate(
    @SerializedName("content") val content: GeminiContent? = null,
    @SerializedName("finishReason") val finishReason: String? = null,
)

object GeminiApiClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    fun create(apiKey: String): GeminiApi {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("x-goog-api-key", apiKey)
                    .build()
                chain.proceed(request)
            }
            // BODY로 하면 사진 Base64가 Logcat에 통째로 찍히므로 BASIC
            .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(40, TimeUnit.SECONDS)   // 사진 분석은 몇 초~십몇 초 걸린다
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GeminiApi::class.java)
    }
}
