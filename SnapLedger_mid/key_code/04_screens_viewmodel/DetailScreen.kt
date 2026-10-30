package com.example.snapledger.ui.detail

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.example.snapledger.data.local.Expense
import com.example.snapledger.data.repository.ExpenseRepository
import com.example.snapledger.ui.common.appViewModel
import com.example.snapledger.ui.common.toDateTimeText
import com.example.snapledger.ui.common.toWon
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class DetailViewModel(private val repository: ExpenseRepository, private val expenseId: Long) : ViewModel() {
    val expense: StateFlow<Expense?> = repository.observe(expenseId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.delete(expenseId)
            onDeleted()
        }
    }
}

@Composable
fun DetailScreen(expenseId: Long, onBack: () -> Unit, onEdit: (Long) -> Unit) {
    val vm = appViewModel { DetailViewModel(it.expenseRepository, expenseId) }
    val expense by vm.expense.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로") }
            Text("내역 상세", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = { onEdit(expenseId) }, enabled = expense != null) { Text("수정") }
        }

        val item = expense
        if (item == null) {
            Text("내역을 불러오는 중이거나 삭제된 내역이에요", modifier = Modifier.padding(top = 24.dp))
            return@Column
        }

        item.receiptImagePath?.let { path ->
            // TODO(3단계): 눌러서 확대 보기
            AsyncImage(
                model = File(path),
                contentDescription = "영수증 원본",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().height(220.dp).padding(vertical = 8.dp),
            )
        }

        InfoRow("상호명", item.storeName)
        InfoRow("결제일시", item.paidAt.toDateTimeText())
        InfoRow("금액", item.amount.toWon())
        InfoRow("카테고리", item.category.label)
        InfoRow("메모", item.memo.ifBlank { "-" })
        item.businessNumber?.let { InfoRow("사업자번호", it) }

        Spacer(Modifier.height(24.dp))
        OutlinedButton(
            onClick = { vm.delete(onBack) },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("내역 삭제") }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value)
    }
    HorizontalDivider()
}
