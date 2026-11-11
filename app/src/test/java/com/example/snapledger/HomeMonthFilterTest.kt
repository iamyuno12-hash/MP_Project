package com.example.snapledger

import com.example.snapledger.data.local.Expense
import com.example.snapledger.ui.common.toEpochMillis
import com.example.snapledger.ui.home.inMonthLatestFirst
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.YearMonth

/** 홈 화면 목록: 선택한 달의 내역만, 결제일시 최신순으로 보이는지 (폰 없이 PC에서 실행) */
class HomeMonthFilterTest {

    private fun expense(store: String, paidAt: LocalDateTime, createdAt: Long = 0) =
        Expense(storeName = store, paidAt = paidAt.toEpochMillis(), amount = 1_000, createdAt = createdAt)

    private val oct27 = expense("서울편한내과", LocalDateTime.of(2026, 10, 27, 10, 0), createdAt = 999)  // 11월에 늦게 저장
    private val nov01 = expense("11월 첫날", LocalDateTime.of(2026, 11, 1, 0, 0))
    private val nov12 = expense("11월 중순", LocalDateTime.of(2026, 11, 12, 9, 30))
    private val nov30 = expense("11월 말일", LocalDateTime.of(2026, 11, 30, 23, 59, 59))
    private val dec01 = expense("12월", LocalDateTime.of(2026, 12, 1, 0, 0))
    private val all = listOf(oct27, nov01, nov12, nov30, dec01)

    @Test
    fun 지난달_영수증은_이번달_목록에_안_나온다() {
        assertEquals(listOf(nov30, nov12, nov01), all.inMonthLatestFirst(YearMonth.of(2026, 11)))
    }

    @Test
    fun 지난달로_넘기면_지난달_영수증이_보인다() {
        assertEquals(listOf(oct27), all.inMonthLatestFirst(YearMonth.of(2026, 10)))
    }

    @Test
    fun 내역이_없는_달은_빈_목록() {
        assertEquals(emptyList<Expense>(), all.inMonthLatestFirst(YearMonth.of(2026, 9)))
    }
}
