package com.example.snapledger

import com.example.snapledger.data.local.Expense
import com.example.snapledger.model.Category
import com.example.snapledger.ui.common.toCompactWon
import com.example.snapledger.ui.stats.CategoryShare
import com.example.snapledger.ui.stats.StatsCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

/** 통계 계산 단위 테스트 (폰 없이 PC에서 실행) */
class StatsCalculatorTest {

    private val zone = ZoneId.of("Asia/Seoul")

    private fun expense(at: LocalDateTime, amount: Long, category: Category = Category.FOOD) = Expense(
        storeName = "가게",
        paidAt = at.atZone(zone).toInstant().toEpochMilli(),
        amount = amount,
        category = category,
    )

    @Test
    fun 카테고리_비중은_큰_순서이고_합이_100() {
        val list = listOf(
            expense(LocalDateTime.of(2026, 11, 1, 12, 0), 1_000, Category.CONVENIENCE),
            expense(LocalDateTime.of(2026, 11, 2, 12, 0), 1_000, Category.FOOD),
            expense(LocalDateTime.of(2026, 11, 3, 12, 0), 1_000, Category.CAFE),
        )
        val shares = StatsCalculator.categoryShares(list)

        // 금액이 같으면 카테고리 정의 순서(식비 → 카페 → 편의점)
        assertEquals(listOf(Category.FOOD, Category.CAFE, Category.CONVENIENCE), shares.map { it.category })
        // 33.3%씩이지만 그냥 내리면 99%가 되므로 하나는 34%
        assertEquals(listOf(34, 33, 33), shares.map { it.percent })
        assertEquals(100, shares.sumOf { it.percent })
    }

    @Test
    fun 비중_반올림() {
        assertEquals(listOf(50, 30, 20), StatsCalculator.roundPercents(listOf(5_000, 3_000, 2_000)))
        assertEquals(listOf(67, 33), StatsCalculator.roundPercents(listOf(2_000, 1_000)))
        assertEquals(listOf(0, 0), StatsCalculator.roundPercents(listOf(0, 0)))
    }

    @Test
    fun 아주_작은_비중은_1퍼센트_미만으로_표시() {
        val percents = StatsCalculator.roundPercents(listOf(999_000, 1_000))
        assertEquals(listOf(100, 0), percents)
        assertEquals("1% 미만", CategoryShare(Category.CAFE, 1_000, percents[1]).percentText)
        assertEquals("100%", CategoryShare(Category.FOOD, 999_000, percents[0]).percentText)
    }

    @Test
    fun 달의_경계는_기기_시간대_기준() {
        val lastMomentOfOctober = expense(LocalDateTime.of(2026, 10, 31, 23, 59, 59), 1_000)
        val firstMomentOfNovember = expense(LocalDateTime.of(2026, 11, 1, 0, 0), 2_000)
        val list = listOf(lastMomentOfOctober, firstMomentOfNovember)

        assertEquals(listOf(firstMomentOfNovember), StatsCalculator.inMonth(list, YearMonth.of(2026, 11), zone))
        assertEquals(listOf(lastMomentOfOctober), StatsCalculator.inMonth(list, YearMonth.of(2026, 10), zone))
    }

    @Test
    fun 올해_요약은_이번_달까지로_월평균() {
        val list = listOf(
            expense(LocalDateTime.of(2026, 1, 10, 9, 0), 100_000),
            expense(LocalDateTime.of(2026, 3, 5, 9, 0), 200_000),
            expense(LocalDateTime.of(2026, 3, 20, 9, 0), 50_000),
            expense(LocalDateTime.of(2025, 3, 20, 9, 0), 999_000),   // 작년 내역은 빠져야 함
        )
        val summary = StatsCalculator.yearSummary(list, 2026, today = LocalDate.of(2026, 5, 15), zone = zone)

        assertEquals(100_000L, summary.monthly[0])
        assertEquals(250_000L, summary.monthly[2])
        assertEquals(0L, summary.monthly[4])
        assertEquals(350_000L, summary.total)
        assertEquals(70_000L, summary.monthlyAverage)   // 1~5월, 5개월로 나눔
        assertEquals(3, summary.peakMonth)
    }

    @Test
    fun 지난해_요약은_12개월로_월평균() {
        val list = listOf(expense(LocalDateTime.of(2025, 7, 1, 9, 0), 120_000))
        val summary = StatsCalculator.yearSummary(list, 2025, today = LocalDate.of(2026, 5, 15), zone = zone)

        assertEquals(10_000L, summary.monthlyAverage)
        assertEquals(7, summary.peakMonth)
    }

    @Test
    fun 내역이_없으면_가장_많이_쓴_달도_없음() {
        val summary = StatsCalculator.yearSummary(emptyList(), 2026, today = LocalDate.of(2026, 5, 15), zone = zone)
        assertNull(summary.peakMonth)
        assertEquals(0L, summary.monthlyAverage)
    }

    @Test
    fun 그래프용_짧은_금액() {
        assertEquals("9,800원", 9_800L.toCompactWon())
        assertEquals("4.6만", 45_600L.toCompactWon())
        assertEquals("5만", 50_000L.toCompactWon())
        assertEquals("123만", 1_234_567L.toCompactWon())
        assertEquals("1,235만", 12_345_678L.toCompactWon())
        assertEquals("1억", 100_000_000L.toCompactWon())
        assertEquals("1.2억", 123_000_000L.toCompactWon())
    }
}
