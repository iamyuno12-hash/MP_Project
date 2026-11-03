package com.example.snapledger.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.snapledger.AppContainer
import com.example.snapledger.SnapLedgerApplication
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDateTime
import java.time.Year
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 화면에서 ViewModel을 만들 때 쓰는 도우미.
 * 예) val vm = appViewModel { HomeViewModel(it.expenseRepository) }
 */
@Composable
inline fun <reified VM : ViewModel> appViewModel(crossinline create: (AppContainer) -> VM): VM {
    val container = (LocalContext.current.applicationContext as SnapLedgerApplication).container
    return viewModel<VM>(factory = viewModelFactory { initializer { create(container) } })
}

private val wonFormat = NumberFormat.getNumberInstance(Locale.KOREA)
fun Long.toWon(): String = "${wonFormat.format(this)}원"

/**
 * 그래프 눈금·막대 위에 쓰는 짧은 금액. 예) 9,800원 · 4.5만 · 123만 · 1.2억
 * 1만 미만은 그대로, 100만 미만은 소수 한 자리 '만', 1억 미만은 정수 '만', 그 이상은 '억'
 */
fun Long.toCompactWon(): String {
    val abs = kotlin.math.abs(this)
    return when {
        abs < 10_000 -> toWon()
        abs < 1_000_000 -> "${oneDecimal(this / 10_000.0)}만"
        abs < 100_000_000 -> "${wonFormat.format(Math.round(this / 10_000.0))}만"
        else -> "${oneDecimal(this / 100_000_000.0)}억"
    }
}

/** 소수 첫째 자리까지 반올림하고, .0이면 떼어 낸다 (4.56 → "4.6", 5.0 → "5") */
private fun oneDecimal(value: Double): String {
    val tenths = Math.round(value * 10)
    val whole = tenths / 10
    val fraction = kotlin.math.abs(tenths % 10)
    return if (fraction == 0L) whole.toString() else "$whole.$fraction"
}

val DATE_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm")

fun Long.toLocalDateTime(): LocalDateTime =
    LocalDateTime.ofInstant(Instant.ofEpochMilli(this), ZoneId.systemDefault())

fun LocalDateTime.toEpochMillis(): Long =
    atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

fun Long.toDateTimeText(): String = toLocalDateTime().format(DATE_TIME_FORMAT)

/** 해당 월의 시작~끝 시각 (epoch millis) */
fun YearMonth.millisRange(): Pair<Long, Long> {
    val start = atDay(1).atStartOfDay().toEpochMillis()
    val end = plusMonths(1).atDay(1).atStartOfDay().toEpochMillis() - 1
    return start to end
}

/** 해당 연도의 시작~끝 시각 (epoch millis) */
fun Year.millisRange(): Pair<Long, Long> {
    val start = atDay(1).atStartOfDay().toEpochMillis()
    val end = plusYears(1).atDay(1).atStartOfDay().toEpochMillis() - 1
    return start to end
}
