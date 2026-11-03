package com.example.snapledger.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.snapledger.data.local.Expense
import com.example.snapledger.data.repository.ExpenseRepository
import com.example.snapledger.ui.common.appViewModel
import com.example.snapledger.ui.common.millisRange
import com.example.snapledger.ui.common.toWon
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.Year
import java.time.YearMonth
import java.time.ZoneId

/** 통계를 볼 단위 */
enum class StatsMode(val label: String) {
    MONTH("월별"),
    YEAR("연도별"),
}

/**
 * 지금 보고 있는 기간.
 * 연도별 모드에서는 month.year만 쓰지만, 월별로 돌아왔을 때 같은 달을 보여주려고 달까지 들고 있다.
 */
data class StatsPeriod(val mode: StatsMode, val month: YearMonth)

data class StatsUiState(
    val period: StatsPeriod = StatsPeriod(StatsMode.MONTH, YearMonth.now()),
    val total: Long = 0,
    val count: Int = 0,
    val shares: List<CategoryShare> = emptyList(),
    val year: YearSummary? = null,          // 연도별 모드에서만 채움
    val canGoNext: Boolean = false,         // 다음 기간이 미래면 false
) {
    val mode: StatsMode get() = period.mode
    val title: String
        get() = when (mode) {
            StatsMode.MONTH -> "${period.month.year}년 ${period.month.monthValue}월"
            StatsMode.YEAR -> "${period.month.year}년"
        }
}

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModel(repository: ExpenseRepository) : ViewModel() {
    private val zone: ZoneId = ZoneId.systemDefault()
    private val period = MutableStateFlow(StatsPeriod(StatsMode.MONTH, YearMonth.now()))

    /** 기간이 바뀌면 그 기간의 내역을 다시 구독한다 (DB가 바뀌어도 자동으로 다시 계산) */
    val uiState: StateFlow<StatsUiState> = period
        .flatMapLatest { p ->
            val (from, to) = queryRange(p)
            repository.observeBetween(from, to).map { list -> buildState(p, list) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    fun setMode(mode: StatsMode) = period.update { it.copy(mode = mode) }

    fun previous() = period.update { p ->
        p.copy(month = if (p.mode == StatsMode.MONTH) p.month.minusMonths(1) else p.month.minusYears(1))
    }

    fun next() = period.update { p ->
        if (!canGoNext(p, LocalDate.now())) return@update p
        val moved = if (p.mode == StatsMode.MONTH) p.month.plusMonths(1) else p.month.plusYears(1)
        p.copy(month = minOf(moved, YearMonth.now()))   // 연도를 넘길 때 미래 달로 가지 않게
    }

    private fun queryRange(p: StatsPeriod): Pair<Long, Long> = when (p.mode) {
        StatsMode.MONTH -> p.month.millisRange()
        StatsMode.YEAR -> Year.of(p.month.year).millisRange()
    }

    private fun buildState(p: StatsPeriod, list: List<Expense>): StatsUiState {
        val today = LocalDate.now()
        val inPeriod = when (p.mode) {
            StatsMode.MONTH -> StatsCalculator.inMonth(list, p.month, zone)
            StatsMode.YEAR -> StatsCalculator.inYear(list, p.month.year, zone)
        }
        return StatsUiState(
            period = p,
            total = inPeriod.sumOf { it.amount },
            count = inPeriod.size,
            shares = StatsCalculator.categoryShares(inPeriod),
            year = if (p.mode == StatsMode.YEAR) StatsCalculator.yearSummary(inPeriod, p.month.year, today, zone) else null,
            canGoNext = canGoNext(p, today),
        )
    }

    private fun canGoNext(p: StatsPeriod, today: LocalDate): Boolean = when (p.mode) {
        StatsMode.MONTH -> p.month < YearMonth.from(today)
        StatsMode.YEAR -> p.month.year < today.year
    }
}

@Composable
fun StatsScreen() {
    val vm = appViewModel { StatsViewModel(it.expenseRepository) }
    val state by vm.uiState.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("통계", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            ModeToggle(selected = state.mode, onSelect = vm::setMode)
        }
        PeriodSelector(
            title = state.title,
            canGoNext = state.canGoNext,
            onPrevious = vm::previous,
            onNext = vm::next,
        )

        Text(
            if (state.mode == StatsMode.MONTH) "${state.period.month.monthValue}월 총지출" else "${state.period.month.year}년 총지출",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(state.total.toWon(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        val year = state.year
        Text(
            if (year != null) "월 평균 ${year.monthlyAverage.toWon()} · ${state.count}건" else "${state.count}건",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (state.total == 0L) {
            Spacer(Modifier.height(24.dp))
            Text("이 기간에 기록된 내역이 없어요", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }

        Spacer(Modifier.height(24.dp))
        Text("카테고리별 지출", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        CategoryList(state.shares)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeToggle(selected: StatsMode, onSelect: (StatsMode) -> Unit) {
    SingleChoiceSegmentedButtonRow {
        StatsMode.entries.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = mode == selected,
                onClick = { onSelect(mode) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = StatsMode.entries.size),
                icon = {},
            ) { Text(mode.label) }
        }
    }
}

/** < 2026년 11월 > : 이전·다음 기간 이동 */
@Composable
private fun PeriodSelector(title: String, canGoNext: Boolean, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "이전 기간")
        }
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        IconButton(onClick = onNext, enabled = canGoNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "다음 기간")
        }
    }
}

@Composable
private fun CategoryList(shares: List<CategoryShare>) {
    shares.forEach { share ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(share.category.label)
            Text("${share.percentText}  ·  ${share.amount.toWon()}")
        }
        HorizontalDivider()
    }
}
