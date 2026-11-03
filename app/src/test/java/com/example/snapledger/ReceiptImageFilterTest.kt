package com.example.snapledger

import com.example.snapledger.ocr.ParsedReceipt
import com.example.snapledger.ocr.ReceiptImageFilter
import com.example.snapledger.ocr.RecognitionScorer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/** 영수증 이미지 보정 필터와 인식 결과 선택 테스트 (폰 없이 PC에서 실행) */
class ReceiptImageFilterTest {

    private val width = 240
    private val height = 120

    /**
     * 가상의 영수증: 왼쪽은 밝은 종이(200), 오른쪽으로 갈수록 그림자가 져서 90까지 어두워진다.
     * 3px 두께의 세로 글자 획을 30px마다 그었고, 획은 그 자리 종이 밝기의 40%다.
     */
    private fun shadowedReceipt(): IntArray = IntArray(width * height) { i ->
        val x = i % width
        val paper = 200 - (110 * x / (width - 1))
        if (x % 30 in 10..12) paper * 40 / 100 else paper
    }

    private fun isStroke(x: Int) = x % 30 in 10..12

    @Test
    fun 그림자가_져도_종이는_하얗고_글자는_검게() {
        val src = shadowedReceipt()
        // 보정 전: 밝은 쪽 글자(80)가 그림자 쪽 종이(90)보다 어두워서 하나의 기준으로 글자/종이를 가를 수 없다
        assertTrue(src[60 * width + 10] < src[60 * width + width - 1])

        val out = ReceiptImageFilter.normalize(src, width, height)

        for (y in listOf(20, 60, 100)) {
            for (x in 0 until width) {
                val v = out[y * width + x]
                if (isStroke(x)) assertTrue("글자 획 ($x,$y) = $v", v < 100)
                else if ((0..2).none { d -> isStroke(x - d) || isStroke(x + d) }) assertTrue("종이 ($x,$y) = $v", v > 200)
            }
        }
    }

    @Test
    fun 검은_띠_안의_흰_글자는_사라지지_않는다() {
        // 위 1/3은 검은 띠(30)에 흰 글자(220), 나머지는 흰 종이(200)
        val src = IntArray(width * height) { i ->
            val x = i % width
            val y = i / width
            when {
                y < height / 3 && x % 30 in 10..12 -> 220
                y < height / 3 -> 30
                else -> 200
            }
        }
        val out = ReceiptImageFilter.normalize(src, width, height)

        val band = out[20 * width + 25]
        val letter = out[20 * width + 11]
        assertTrue("띠는 어둡게 남아야 함: $band", band < 128)
        assertTrue("띠 안 글자는 밝게 남아야 함: $letter", letter > 200)
    }

    @Test
    fun 빈_종이는_흰색으로() {
        val out = ReceiptImageFilter.normalize(IntArray(width * height) { 180 }, width, height)
        assertTrue(out.all { it == 255 })
    }

    @Test
    fun 인식용_크기_맞추기() {
        // 좁은 변 500px → 1000px이 되도록 2배
        assertEquals(2.0, ReceiptImageFilter.ocrScale(500, 1200), 1e-9)
        // 적당한 크기는 그대로
        assertEquals(1.0, ReceiptImageFilter.ocrScale(1200, 2400), 1e-9)
        // 넓은 변 6000px → 3000px로
        assertEquals(0.5, ReceiptImageFilter.ocrScale(2000, 6000), 1e-9)
        // 키울 때도 넓은 변 3000px을 넘지 않음
        assertEquals(1.5, ReceiptImageFilter.ocrScale(600, 2000), 1e-9)
    }

    @Test
    fun 회색조_변환() {
        assertEquals(255, ReceiptImageFilter.luminance(0xFFFFFFFF.toInt()))
        assertEquals(0, ReceiptImageFilter.luminance(0xFF000000.toInt()))
        assertEquals(149, ReceiptImageFilter.luminance(0xFF00FF00.toInt()))   // 초록은 밝게
        assertEquals(29, ReceiptImageFilter.luminance(0xFF0000FF.toInt()))    // 파랑은 어둡게
    }

    @Test
    fun 값을_더_많이_찾은_결과를_고른다() {
        val lines = listOf("모퉁이 편의점", "합계 4,800")
        val full = ParsedReceipt("모퉁이 편의점", "123-45-67890", LocalDateTime.of(2026, 10, 19, 13, 42), 4_800)
        val noTotal = full.copy(total = null)

        val fullScore = RecognitionScorer.score(full, lines)
        val noTotalScore = RecognitionScorer.score(noTotal, lines)
        assertTrue(fullScore > noTotalScore)
        assertTrue(RecognitionScorer.preferProcessed(originalScore = noTotalScore, processedScore = fullScore))
        assertFalse(RecognitionScorer.preferProcessed(originalScore = fullScore, processedScore = noTotalScore))
        // 같으면 보정 이미지
        assertTrue(RecognitionScorer.preferProcessed(originalScore = fullScore, processedScore = fullScore))
    }
}
