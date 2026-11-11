package com.example.snapledger.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.snapledger.data.local.SettingsStore
import com.example.snapledger.data.repository.ExpenseRepository
import com.example.snapledger.ui.common.appViewModel
import com.example.snapledger.ui.common.millisRange
import com.example.snapledger.ui.common.toDateTimeText
import com.example.snapledger.ui.common.toWon
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.YearMonth

data class HomeUiState(
    val month: YearMonth = YearMonth.now(),
    val monthTotal: Long = 0,
    val budget: Long = 0,
    val expenses: List<Expense> = emptyList(),   // 선택한 달의 내역, 결제일시 최신순
) {
    val isCurrentMonth: Boolean get() = month == YearMonth.now()
    /** 다음 달 버튼: 이번 달까지만 이동 */
    val canGoNext: Boolean get() = month < YearMonth.now()
}

/** 결제일이 [month]에 속하는 내역만 최신 결제일시 순으로 (단위 테스트용으로 분리) */
fun List<Expense>.inMonthLatestFirst(month: YearMonth): List<Expense> {
    val (from, to) = month.millisRange()
    return filter { it.paidAt in from..to }.sortedByDescending { it.paidAt }
}

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    repository: ExpenseRepository,
    settings: SettingsStore,
    private val month: MutableStateFlow<YearMonth>,   // AppContainer.homeMonth (저장 화면과 공유)
) : ViewModel() {

    private val monthExpenses = month.flatMapLatest { m ->
        val (from, to) = m.millisRange()
        repository.observeBetween(from, to).map { m to it.inMonthLatestFirst(m) }
    }

    val uiState: StateFlow<HomeUiState> =
        combine(monthExpenses, settings.monthlyBudget) { (m, list), budget ->
            HomeUiState(
                month = m,
                monthTotal = list.sumOf { it.amount },
                budget = budget,
                expenses = list,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState(month = month.value))

    fun previous() { month.value = month.value.minusMonths(1) }

    fun next() { if (month.value < YearMonth.now()) month.value = month.value.plusMonths(1) }

    fun goToCurrentMonth() { month.value = YearMonth.now() }
}

@Composable
fun HomeScreen(onCaptureClick: () -> Unit, onExpenseClick: (Long) -> Unit) {
    val vm = appViewModel { HomeViewModel(it.expenseRepository, it.settingsStore, it.homeMonth) }
    val state by vm.uiState.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            MonthSelector(
                state = state,
                onPrevious = vm::previous,
                onNext = vm::next,
                onToday = vm::goToCurrentMonth,
            )
            SummaryCard(state)
            Spacer(Modifier.height(16.dp))
            Text(
                if (state.isCurrentMonth) "최근 내역" else "${state.month.monthValue}월 내역",
                style = MaterialTheme.typography.titleMedium,
            )

            if (state.expenses.isEmpty()) {
                Text(
                    if (state.isCurrentMonth) "이번 달 내역이 아직 없어요. 아래 버튼으로 영수증을 찍어 보세요."
                    else "${state.month.monthValue}월에 기록된 내역이 없어요.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 24.dp),
                )
            }
            LazyColumn(Modifier.fillMaxWidth()) {
                items(state.expenses, key = { it.id }) { expense ->
                    ExpenseRow(expense, onClick = { onExpenseClick(expense.id) })
                    HorizontalDivider()
                }
                item { Spacer(Modifier.height(80.dp)) } // 버튼에 가리지 않도록
            }
        }

        ExtendedFloatingActionButton(
            onClick = onCaptureClick,
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("영수증 촬영") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }
}

/** ‹ 2026년 10월 ›  (통계 화면의 기간 선택과 같은 모양) */
@Composable
private fun MonthSelector(state: HomeUiState, onPrevious: () -> Unit, onNext: () -> Unit, onToday: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "이전 달")
        }
        Text(
            "${state.month.year}년 ${state.month.monthValue}월",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        IconButton(onClick = onNext, enabled = state.canGoNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "다음 달")
        }
        Spacer(Modifier.weight(1f))
        if (!state.isCurrentMonth) {
            TextButton(onClick = onToday) { Text("이번 달") }
        }
    }
}

@Composable
private fun SummaryCard(state: HomeUiState) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                if (state.isCurrentMonth) "이번 달 지출" else "${state.month.monthValue}월 지출",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(state.monthTotal.toWon(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            if (state.budget > 0) {
                val ratio = (state.monthTotal.toFloat() / state.budget).coerceIn(0f, 1f)
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(progress = { ratio }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(4.dp))
                Text(
                    "예산 ${state.budget.toWon()} 중 ${(state.monthTotal * 100 / state.budget)}% 사용",
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                Text("설정에서 월 예산을 정할 수 있어요", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ExpenseRow(expense: Expense, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(expense.storeName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(
                "${expense.category.label} · ${expense.paidAt.toDateTimeText()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(expense.amount.toWon(), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
    }
}
