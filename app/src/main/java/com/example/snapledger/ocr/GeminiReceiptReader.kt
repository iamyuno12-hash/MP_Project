package com.example.snapledger.ocr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.example.snapledger.data.remote.GeminiApi
import com.example.snapledger.data.remote.GeminiContent
import com.example.snapledger.data.remote.GeminiInlineData
import com.example.snapledger.data.remote.GeminiPart
import com.example.snapledger.data.remote.GeminiRequest
import com.example.snapledger.model.Category
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64

/** Gemini가 읽은 결과: 영수증 값 + Gemini가 추천한 카테고리 */
data class AiReceipt(
    val parsed: ParsedReceipt,
    val category: Category? = null,
)

/**
 * Gemini(멀티모달 AI)로 영수증 사진을 직접 읽는다.
 * ML Kit + 규칙 파서는 영수증 모양이 특이하면 값을 놓치는데, Gemini는 사진 전체를 보고
 * "이 영수증의 가게 이름·결제일시·최종 결제금액"을 문맥으로 판단하므로 더 잘 맞힌다.
 *
 * 인터넷이 없거나 키가 없거나 실패하면 null → 기존 ML Kit 결과만 쓴다 (앱은 그대로 동작).
 */
class GeminiReceiptReader(
    private val api: GeminiApi?,
    private val model: String,
    private val parser: ReceiptParser,
) {
    /** local.properties에 GEMINI_API_KEY가 있을 때만 켜진다 */
    val isEnabled: Boolean get() = api != null

    suspend fun read(image: File): AiReceipt? {
        val api = api ?: return null
        return try {
            val base64 = withContext(Dispatchers.Default) { encodeForUpload(image) }
            val request = GeminiRequest(
                contents = listOf(
                    GeminiContent(
                        parts = listOf(
                            GeminiPart(inlineData = GeminiInlineData("image/jpeg", base64)),
                            GeminiPart(text = PROMPT),
                        ),
                    ),
                ),
            )
            val answer = api.generateContent(model, request).text
            Log.d("Receipt", "Gemini 응답: $answer")
            GeminiReceiptJson.parse(answer, parser)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("Receipt", "Gemini 인식 실패, ML Kit 결과만 사용: ${e.message}")
            null
        } catch (e: OutOfMemoryError) {
            Log.w("Receipt", "Gemini 업로드용 이미지 만들다 메모리 부족", e)
            null
        }
    }

    /** 긴 변 1600px로 줄여 JPEG → Base64 (원본 그대로 보내면 느리고 데이터도 많이 씀) */
    private fun encodeForUpload(file: File): String {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2

        val bitmap = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: throw IllegalStateException("영수증 사진을 열 수 없어요")
        try {
            val scale = MAX_SIDE.toFloat() / maxOf(bitmap.width, bitmap.height)
            val resized = if (scale < 1f) {
                Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
            } else {
                bitmap
            }
            val bytes = ByteArrayOutputStream().use { out ->
                resized.compress(Bitmap.CompressFormat.JPEG, 85, out)
                out.toByteArray()
            }
            if (resized !== bitmap) resized.recycle()
            return Base64.getEncoder().encodeToString(bytes)
        } finally {
            bitmap.recycle()
        }
    }

    companion object {
        private const val MAX_SIDE = 1600

        private val PROMPT = """
            이 사진은 한국 영수증(카드 전표 포함)입니다. 아래 값을 찾아 JSON 하나로만 답하세요. 설명이나 코드블록 없이 JSON만.
            {
              "storeName": 가게(가맹점) 이름. 대표자 이름·주소·카드사 이름이 아님. 모르면 null,
              "businessNumber": 사업자등록번호 "000-00-00000" 형식. 모르면 null,
              "paidAt": 결제(승인) 일시 "yyyy-MM-dd HH:mm". 시간이 없으면 "yyyy-MM-dd". 모르면 null,
              "total": 최종 결제 금액(원, 숫자만). 승인번호·카드번호·전화번호·부가세·할인액이 아님. 모르면 null,
              "category": ${Category.entries.joinToString(", ") { "\"${it.name}\"(${it.label})" }} 중 하나
            }
        """.trimIndent()
    }
}

/** Gemini가 돌려준 글에서 JSON을 꺼내 앱에서 쓰는 값으로 바꾼다 (폰 없이 단위 테스트 가능) */
object GeminiReceiptJson {

    fun parse(answer: String, parser: ReceiptParser = ReceiptParser()): AiReceipt? {
        val json = extractJsonObject(answer) ?: return null

        val storeName = json.string("storeName")?.trim()?.takeIf { it.length in 1..40 }
        // 형식·검증은 기존 규칙 파서에 맡긴다 (사업자번호 검증 공식, 여러 날짜 형식)
        val businessNumber = json.string("businessNumber")?.let { parser.findBusinessNumber(listOf(it)) }
        val paidAt = json.string("paidAt")?.let { parser.findDateTime(listOf(it)) }
        val total = json.long("total")?.takeIf { it in 1..99_999_999 }
        val category = json.string("category")?.trim()?.uppercase()
            ?.let { name -> Category.entries.find { it.name == name } }

        val parsed = ParsedReceipt(storeName, businessNumber, paidAt, total)
        if (parsed == ParsedReceipt()) return null   // 아무것도 못 읽음
        return AiReceipt(parsed, category)
    }

    /** ```json ... ``` 이나 앞뒤 설명이 붙어 와도 첫 { 부터 마지막 } 까지를 JSON으로 읽는다 */
    private fun extractJsonObject(answer: String): JsonObject? {
        val start = answer.indexOf('{')
        val end = answer.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return try {
            JsonParser.parseString(answer.substring(start, end + 1)).takeIf { it.isJsonObject }?.asJsonObject
        } catch (e: Exception) {
            null
        }
    }

    private fun JsonObject.value(key: String): JsonElement? =
        get(key)?.takeUnless { it.isJsonNull }

    private fun JsonObject.string(key: String): String? =
        value(key)?.takeIf { it.isJsonPrimitive }?.asString?.takeUnless { it.isBlank() || it == "null" }

    /** 4800, "4800", "4,800원" 모두 4800으로 */
    private fun JsonObject.long(key: String): Long? {
        val raw = value(key)?.takeIf { it.isJsonPrimitive }?.asString ?: return null
        return raw.substringBefore('.').filter { it.isDigit() }.toLongOrNull()
    }
}

/** AI가 찾은 값을 우선 쓰고, AI가 못 찾은 칸만 ML Kit 규칙 파서 값으로 채운다 */
fun ParsedReceipt.mergedWith(ai: ParsedReceipt?): ParsedReceipt =
    if (ai == null) this
    else ParsedReceipt(
        storeName = ai.storeName ?: storeName,
        businessNumber = ai.businessNumber ?: businessNumber,
        paidAt = ai.paidAt ?: paidAt,
        total = ai.total ?: total,
    )
