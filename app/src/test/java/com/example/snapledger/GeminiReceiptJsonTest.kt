package com.example.snapledger

import com.example.snapledger.model.Category
import com.example.snapledger.ocr.GeminiReceiptJson
import com.example.snapledger.ocr.ParsedReceipt
import com.example.snapledger.ocr.mergedWith
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

/** Gemini 답(JSON)을 앱 값으로 바꾸는 부분 테스트 (인터넷·폰 없이 실행) */
class GeminiReceiptJsonTest {

    @Test
    fun 정상_응답() {
        val answer = """
            {"storeName": "서울편한내과의원", "businessNumber": "123-45-67890",
             "paidAt": "2026-10-27 10:12", "total": 40000, "category": "LIVING"}
        """.trimIndent()
        val result = GeminiReceiptJson.parse(answer)!!

        assertEquals("서울편한내과의원", result.parsed.storeName)
        assertEquals("123-45-67890", result.parsed.businessNumber)
        assertEquals(LocalDateTime.of(2026, 10, 27, 10, 12), result.parsed.paidAt)
        assertEquals(40_000L, result.parsed.total)
        assertEquals(Category.LIVING, result.category)
    }

    @Test
    fun 코드블록과_설명이_붙어도_읽는다() {
        val answer = "다음은 결과입니다.\n```json\n{\"storeName\":\"온기 커피\",\"total\":\"4,500원\",\"category\":\"cafe\"}\n```"
        val result = GeminiReceiptJson.parse(answer)!!

        assertEquals("온기 커피", result.parsed.storeName)
        assertEquals(4_500L, result.parsed.total)
        assertEquals(Category.CAFE, result.category)   // 소문자로 와도 인식
    }

    @Test
    fun null_값과_이상한_값은_버린다() {
        val answer = """{"storeName": null, "businessNumber": "모름", "paidAt": "null", "total": 0, "category": "PET"}"""
        assertNull(GeminiReceiptJson.parse(answer))   // 쓸 수 있는 값이 하나도 없음
    }

    @Test
    fun 하이픈_없는_사업자번호는_검증공식_통과할때만() {
        val ok = GeminiReceiptJson.parse("""{"storeName":"바","businessNumber":"5023866160"}""")!!
        assertEquals("502-38-66160", ok.parsed.businessNumber)

        val bad = GeminiReceiptJson.parse("""{"storeName":"바","businessNumber":"1234567890"}""")!!
        assertNull(bad.parsed.businessNumber)
    }

    @Test
    fun 시간이_없는_날짜는_자정() {
        val result = GeminiReceiptJson.parse("""{"paidAt":"2026-11-03"}""")!!
        assertEquals(LocalDateTime.of(2026, 11, 3, 0, 0), result.parsed.paidAt)
    }

    @Test
    fun JSON이_아니면_null() {
        assertNull(GeminiReceiptJson.parse("영수증을 읽을 수 없습니다"))
    }

    @Test
    fun AI값_우선_못찾은_칸은_MLKit값() {
        val mlKit = ParsedReceipt("번호", "123-45-67890", LocalDateTime.of(2026, 10, 27, 10, 0), 55_513_272)
        val ai = ParsedReceipt("서울편한내과의원", null, null, 40_000)
        val merged = mlKit.mergedWith(ai)

        assertEquals("서울편한내과의원", merged.storeName)
        assertEquals("123-45-67890", merged.businessNumber)
        assertEquals(LocalDateTime.of(2026, 10, 27, 10, 0), merged.paidAt)
        assertEquals(40_000L, merged.total)
        assertEquals(mlKit, mlKit.mergedWith(null))   // AI 실패면 ML Kit 그대로
    }
}
