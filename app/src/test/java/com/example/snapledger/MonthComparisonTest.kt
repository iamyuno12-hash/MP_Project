package com.example.snapledger

import com.example.snapledger.data.local.Expense
import com.example.snapledger.ui.stats.StatsCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

/** 지난달 대비 누적 지출 비교 테스트 (폰 없이 PC에서 실행) */
class MonthComparisonTest {

    private val zone = ZoneId.of("Asia/Seoul")

    private fun expense(year: Int, month: Int, day: Int, amount: Long) = Expense(
        storeName = "가게",
        paidAt = LocalDateTime.of(year, month, day, 12, 0).atZone(zone).toInstant().toEpochMilli(),
        amount = amount,
    )

    @Test
    fun 날짜별_누적_금액() {
        val list = listOf(
            expense(2026, 11, 1, 1_000),
            expense(2026, 11, 3, 2_000),
            expense(2026, 11, 3, 500),
            expense(2026, 10, 2, 9_999),   // 다른 달은 빠져야 함
        )
        val cumulative = StatsCalculator.cumulativeByDay(list, YearMonth.of(2026, 11), zone, upToDay = 5)
        assertEquals(listOf(1_000L, 1_000L, 3_500L, 3_500L, 3_500L), cumulative)
    }

    @Test
    fun 이번_달은_지난달_같은_날까지와_비교() {
        val list = listOf(
            expense(2026, 10, 1, 1_000),
            expense(2026, 10, 3, 2_000),
            expense(2026, 10, 20, 50_000),   // 지난달 이맘때 이후라 비교에서 빠짐
            expense(2026, 11, 1, 1_500),
            expense(2026, 11, 2, 2_000),
        )
        val comparison = StatsCalculator.compareWithPreviousMonth(
            list, YearMonth.of(2026, 11), today = LocalDate.of(2026, 11, 3), zone = zone,
        )

        assertTrue(comparison.isOngoing)
        assertEquals(3, comparison.compareDay)
        assertEquals(3, comparison.current.size)          // 오늘(3일)까지만 그린다
        assertEquals(31, comparison.previous.size)        // 지난달은 말일까지
        assertEquals(31, comparison.dayCount)             // 가로축은 긴 달(10월 31일) 기준
        assertEquals(3_500L, comparison.currentToDate)
        assertEquals(3_000L, comparison.previousToDate)
        assertEquals(500L, comparison.difference)
        assertEquals("지난달 이맘때보다 500원 더 썼어요", comparison.headline())
    }

    @Test
    fun 지난_달을_볼_때는_한_달_전체끼리_비교() {
        val list = listOf(
            expense(2026, 9, 10, 300_000),
            expense(2026, 10, 5, 120_000),
            expense(2026, 10, 25, 46_000),
        )
        val comparison = StatsCalculator.compareWithPreviousMonth(
            list, YearMonth.of(2026, 10), today = LocalDate.of(2026, 11, 3), zone = zone,
        )

        assertFalse(comparison.isOngoing)
        assertEquals(31, comparison.compareDay)
        assertEquals(166_000L, comparison.currentToDate)
        assertEquals(300_000L, comparison.previousToDate)
        assertEquals("지난달보다 134,000원 덜 썼어요", comparison.headline())
    }

    @Test
    fun 지난달이_더_짧으면_말일까지와_비교() {
        // 3월 31일까지 vs 2월(28일) 전체
        val list = listOf(
            expense(2026, 2, 28, 10_000),
            expense(2026, 3, 31, 10_000),
        )
        val comparison = StatsCalculator.compareWithPreviousMonth(
            list, YearMonth.of(2026, 3), today = LocalDate.of(2026, 4, 10), zone = zone,
        )
        assertEquals(10_000L, comparison.previousToDate)
        assertEquals("지난달과 똑같이 썼어요", comparison.headline())
        assertEquals(0L, comparison.difference)
    }

    @Test
    fun 아직_오지_않은_날짜의_내역은_오늘에_더한다() {
        // 오늘은 11월 3일인데 날짜를 11월 20일로 잘못 읽은 영수증
        val list = listOf(expense(2026, 11, 2, 1_000), expense(2026, 11, 20, 4_000))
        val comparison = StatsCalculator.compareWithPreviousMonth(
            list, YearMonth.of(2026, 11), today = LocalDate.of(2026, 11, 3), zone = zone,
        )
        assertEquals(listOf(0L, 1_000L, 5_000L), comparison.current)   // 끝점 = 이번 달 총지출
    }

    @Test
    fun 지난달_기록이_없으면_비교하지_않는다() {
        val list = listOf(expense(2026, 11, 1, 1_000))
        val comparison = StatsCalculator.compareWithPreviousMonth(
            list, YearMonth.of(2026, 11), today = LocalDate.of(2026, 11, 3), zone = zone,
        )
        assertFalse(comparison.hasPrevious)
        assertEquals("10월 기록이 없어 비교할 수 없어요", comparison.headline())
    }
}
