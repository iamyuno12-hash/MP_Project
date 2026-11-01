package com.example.snapledger.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.snapledger.data.repository.ExpenseRepository
import com.example.snapledger.model.Category
import com.example.snapledger.ui.common.appViewModel
import com.example.snapledger.ui.common.millisRange
import com.example.snapledger.ui.common.toWon
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.YearMonth

data class CategoryShare(val category: Category, val amount: Long, val percent: Int)

data class StatsUiState(
    val month: YearMonth = YearMonth.now(),
    val total: Long = 0,
    val shares: List<CategoryShare> = emptyList(),
)

class StatsViewModel(repository: ExpenseRepository) : ViewModel() {
    private val month = YearMonth.now()
    private val range = month.millisRange()

    val uiState: StateFlow<StatsUiState> = repository.observeBetween(range.first, range.second)
        .map { list ->
            val total = list.sumOf { it.amount }
            val shares = list.groupBy { it.category }
                .map { (category, items) ->
                    val sum = items.sumOf { it.amount }
                    CategoryShare(category, sum, if (total > 0) (sum * 100 / total).toInt() else 0)
                }
                .sortedByDescending { it.amount }
            StatsUiState(month, total, shares)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())
}

@Composable
fun StatsScreen() {
    val vm = appViewModel { StatsViewModel(it.expenseRepository) }
    val state by vm.uiState.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("통계 · ${state.month.monthValue}월", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(state.total.toWon(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("이번 달 총지출", color = MaterialTheme.colorScheme.onSurfaceVariant)

        // TODO(프론트, 3단계): Compose Canvas로 카테고리 도넛 차트 + 일별 막대 차트
        Spacer(Modifier.height(16.dp))
        state.shares.forEach { share ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(share.category.label)
                Text("${share.percent}%  ·  ${share.amount.toWon()}")
            }
            HorizontalDivider()
        }
        if (state.shares.isEmpty()) {
            Text("이번 달 내역이 없어요", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
