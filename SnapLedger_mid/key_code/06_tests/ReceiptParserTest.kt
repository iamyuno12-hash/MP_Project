package com.example.snapledger

import com.example.snapledger.ocr.OcrRow
import com.example.snapledger.ocr.ReceiptParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

/**
 * 영수증 파서 단위 테스트. Android Studio에서 클래스 옆 ▶ 버튼으로 바로 실행된다(폰 필요 없음).
 *
 * 실제 영수증이 잘못 읽히면: Logcat에서 'Receipt' 태그로 검색해 읽은 줄 목록을 복사하고,
 * 아래처럼 테스트를 하나 추가한 뒤 통과하도록 ReceiptParser 규칙을 고친다.
 * → 최종 보고서 '테스트 방법: 자동'의 근거가 된다.
 */
class ReceiptParserTest {

    private val parser = ReceiptParser()

    @Test
    fun 편의점_영수증() {
        val lines = listOf(
            "[고객용]",
            "모퉁이 편의점 숭실대점",
            "사업자번호 123-45-67890",
            "TEL 02-820-0114",
            "2026-10-19 13:42:05",
            "삼각김밥 1 1,500",
            "바나나우유 2 3,300",
            "합계 4,800",
            "현금 5,000",
            "거스름돈 200",
        )
        val result = parser.parse(lines)

        assertEquals("모퉁이 편의점 숭실대점", result.storeName)
        assertEquals("123-45-67890", result.businessNumber)
        assertEquals(LocalDateTime.of(2026, 10, 19, 13, 42, 5), result.paidAt)
        assertEquals(4_800L, result.total)
    }

    @Test
    fun 상호_키워드와_두자리_연도() {
        val lines = listOf("카드매출전표", "상호: 온기 커피", "26.10.20", "14:05", "결제금액", "4,500원")
        val result = parser.parse(lines)

        assertEquals("온기 커피", result.storeName)
        assertEquals(LocalDateTime.of(2026, 10, 20, 14, 5), result.paidAt)
        assertEquals(4_500L, result.total)
    }

    @Test
    fun 상호_키워드가_없는_카드영수증_승인번호는_금액이_아니다() {
        val lines = listOf(
            "신용승인",
            "우리동네 분식",
            "서울시 동작구 상도로 369",
            "123-45-67890 대표 김숭실",
            "2026/10/29 12:31",
            "떡볶이 1 4,000",
            "순대 1 3,500",
            "합 계 7,500",
            "승인번호 30012345",
            "카드번호 5310-12**-****-1234",
        )
        val result = parser.parse(lines)

        assertEquals("우리동네 분식", result.storeName)
        assertEquals(7_500L, result.total)
    }

    /**
     * 실제 카드 영수증(바)을 휘어진 채로 찍었을 때 ML Kit이 읽은 모양을 본뜬 것 (개인정보는 바꿈).
     * 오른쪽 값이 왼쪽 항목 이름보다 한 줄 위로 밀려서 '상호' 옆에 대표자 이름이 붙고,
     * '결제금액' 위 줄에 금액이 있으며, 사업자번호가 하이픈 없이 찍혀 있다.
     */
    @Test
    fun 휘어진_카드영수증_항목과_값이_한줄씩_밀린_경우() {
        val lines = listOf(
            "1225바(BAR)",
            "130,000원",
            "신용카드(현대비자)",
            "현대카드",
            "결제방법",
            "매입사 41352600****052*",
            "일시불",
            "카드번호 일시불",
            "할부기간 11,818",
            "부가세액 118,182",
            "공급가액 00622430",
            "승인번호 2026-10-29 00:19:23",
            "승인일시 702771591",
            "가맹점번호",
            "130,000원",
            "결제금액",
            "1225바(BAR)",
            "상호 홍길동",
            "대표 5023866160",
            "사업자등록번호 010-1234-5678",
            "대표번호 경기 고양시 일산동구 일산로463번길",
            "주소 77-71층 일부호",
        )
        val result = parser.parse(lines)

        assertEquals("1225바(BAR)", result.storeName)        // 대표자 이름(홍길동)이 아니라 가게 이름
        assertEquals(130_000L, result.total)                 // 승인번호·가맹점번호·전화번호가 아니라 결제금액
        assertEquals(LocalDateTime.of(2026, 10, 29, 0, 19, 23), result.paidAt)
        assertEquals("502-38-66160", result.businessNumber)  // 하이픈 없는 10자리도 검증 공식으로 인식
    }

    @Test
    fun 크게_인쇄된_줄을_상호명으로() {
        val rows = listOf(
            OcrRow("[매장용]", height = 20),
            OcrRow("오늘도 맛있게", height = 20),
            OcrRow("할매순대국", height = 45),   // 다른 줄보다 2배 넘게 큰 글씨
            OcrRow("서울 동작구 상도로 1", height = 20),
            OcrRow("2026-10-29 12:00", height = 20),
            OcrRow("합계 9,000", height = 20),
        )
        assertEquals("할매순대국", parser.parseRows(rows).storeName)
    }

    @Test
    fun 합계_키워드가_없으면_쉼표나_원이_붙은_금액중_최댓값() {
        val lines = listOf("든든 김밥", "김밥 2 6,000", "라면 1 4,500", "승인 12345678", "10,500")
        assertEquals(10_500L, parser.findTotal(lines))
    }

    @Test
    fun 할인합계와_부가세는_결제금액이_아니다() {
        val lines = listOf("할인합계 500", "부가세 436", "합계 4,800")
        assertEquals(4_800L, parser.findTotal(lines))
    }

    @Test
    fun 전화번호와_카드번호는_금액이_아니다() {
        val lines = listOf("TEL 02-1234-5678", "카드번호 1234-56**-****-7890")
        assertNull(parser.findTotal(lines))
    }

    @Test
    fun 날짜가_없으면_null() {
        assertNull(parser.findDateTime(listOf("합계 4,800")))
    }
}
