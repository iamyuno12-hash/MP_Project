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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import com.example.snapledger.data.local.SettingsStore
import com.example.snapledger.data.repository.ExpenseRepository
import com.example.snapledger.ui.common.appViewModel
import com.example.snapledger.ui.common.millisRange
import com.example.snapledger.ui.common.toDateTimeText
import com.example.snapledger.ui.common.toWon
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.YearMonth

data class HomeUiState(
    val month: YearMonth = YearMonth.now(),
    val monthTotal: Long = 0,
    val budget: Long = 0,
    val expenses: List<Expense> = emptyList(),   // 최근 저장한 순서 (날짜와 관계없이)
)

class HomeViewModel(repository: ExpenseRepository, settings: SettingsStore) : ViewModel() {
    private val month = YearMonth.now()
    private val range = month.millisRange()

    val uiState: StateFlow<HomeUiState> =
        combine(
            repository.observeBetween(range.first, range.second),   // 이번 달 합계용
            repository.observeAll(),                                // 최근 내역 목록용
            settings.monthlyBudget,
        ) { monthList, all, budget ->
            HomeUiState(
                month = month,
                monthTotal = monthList.sumOf { it.amount },
                budget = budget,
                // 결제일이 이번 달이 아니어도(지난 영수증, 날짜 오인식) 방금 저장한 내역이 맨 위에 보이도록
                expenses = all.sortedByDescending { it.createdAt }.take(50),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())
}

@Composable
fun HomeScreen(onCaptureClick: () -> Unit, onExpenseClick: (Long) -> Unit) {
    val vm = appViewModel { HomeViewModel(it.expenseRepository, it.settingsStore) }
    val state by vm.uiState.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Text(
                "${state.month.year}년 ${state.month.monthValue}월",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(vertical = 16.dp),
            )
            SummaryCard(state)
            Spacer(Modifier.height(16.dp))
            Text("최근 내역", style = MaterialTheme.typography.titleMedium)

            if (state.expenses.isEmpty()) {
                Text(
                    "아직 내역이 없어요. 아래 버튼으로 영수증을 찍어 보세요.",
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

@Composable
private fun SummaryCard(state: HomeUiState) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("이번 달 지출", style = MaterialTheme.typography.bodyMedium)
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
