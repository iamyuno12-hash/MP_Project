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
