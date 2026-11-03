package com.example.snapledger.ui.stats

import com.example.snapledger.data.local.Expense
import com.example.snapledger.model.Category
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.floor

/** 카테고리 하나의 합계와 비중(%) */
data class CategoryShare(val category: Category, val amount: Long, val percent: Int) {
    /** 0%로 반올림돼도 쓴 돈이 있으면 '1% 미만'으로 */
    val percentText: String get() = if (percent == 0 && amount > 0) "1% 미만" else "$percent%"
}

/** 한 해의 요약: 1~12월 합계와 월 평균 */
data class YearSummary(
    val year: Int,
    val monthly: List<Long>,        // index 0 = 1월
    val monthlyAverage: Long,       // 지난 달 수로 나눈 평균 (올해면 이번 달까지)
) {
    val total: Long get() = monthly.sum()

    /** 가장 많이 쓴 달 (1~12). 내역이 없으면 null */
    val peakMonth: Int?
        get() = monthly.withIndex().filter { it.value > 0 }.maxByOrNull { it.value }?.let { it.index + 1 }
}

/**
 * 통계 화면에 쓰는 계산 모음.
 * 안드로이드에 의존하지 않는 순수 Kotlin이라 PC에서 단위 테스트로 바로 확인할 수 있다 (StatsCalculatorTest).
 * 결제일시(paidAt)는 zone(기기 시간대) 기준 날짜로 나눈다.
 */
object StatsCalculator {

    fun inMonth(expenses: List<Expense>, month: YearMonth, zone: ZoneId): List<Expense> =
        expenses.filter { YearMonth.from(it.paidAt.toLocalDate(zone)) == month }

    fun inYear(expenses: List<Expense>, year: Int, zone: ZoneId): List<Expense> =
        expenses.filter { it.paidAt.toLocalDate(zone).year == year }

    /** 카테고리별 합계를 큰 순서로. 비중은 반올림해도 합이 정확히 100이 되도록 맞춘다 */
    fun categoryShares(expenses: List<Expense>): List<CategoryShare> {
        val sorted = expenses.groupBy { it.category }
            .mapValues { (_, items) -> items.sumOf { it.amount } }
            .filterValues { it > 0 }
            .entries
            .sortedWith(compareByDescending<Map.Entry<Category, Long>> { it.value }.thenBy { it.key.ordinal })
        val percents = roundPercents(sorted.map { it.value })
        return sorted.mapIndexed { i, entry -> CategoryShare(entry.key, entry.value, percents[i]) }
    }

    /**
     * 비중(%)을 정수로 반올림하되 합이 100이 되게 한다 (최대 나머지 방식).
     * 그냥 내림하면 33+33+33=99처럼 합이 100이 안 돼서, 소수점이 큰 쪽부터 1씩 더해 준다.
     */
    fun roundPercents(values: List<Long>): List<Int> {
        val total = values.sum()
        if (total <= 0) return values.map { 0 }
        val exact = values.map { it * 100.0 / total }
        val result = exact.map { floor(it).toInt() }.toMutableList()
        val remaining = (100 - result.sum()).coerceAtLeast(0)
        exact.indices
            .sortedByDescending { exact[it] - result[it] }
            .take(remaining)
            .forEach { result[it] += 1 }
        return result
    }

    /** 한 해의 월별 합계와 월 평균. today는 '올해는 이번 달까지만 평균'을 내는 데 쓴다 */
    fun yearSummary(expenses: List<Expense>, year: Int, today: LocalDate, zone: ZoneId): YearSummary {
        val monthly = LongArray(12)
        expenses.forEach { expense ->
            val date = expense.paidAt.toLocalDate(zone)
            if (date.year == year) monthly[date.monthValue - 1] += expense.amount
        }
        val monthsElapsed = when {
            year < today.year -> 12
            year == today.year -> today.monthValue
            else -> 0
        }
        val total = monthly.sum()
        return YearSummary(
            year = year,
            monthly = monthly.toList(),
            monthlyAverage = if (monthsElapsed > 0) total / monthsElapsed else 0L,
        )
    }

    private fun Long.toLocalDate(zone: ZoneId): LocalDate = Instant.ofEpochMilli(this).atZone(zone).toLocalDate()
}
