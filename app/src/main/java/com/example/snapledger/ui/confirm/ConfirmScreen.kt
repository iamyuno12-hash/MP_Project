package com.example.snapledger.ui.confirm

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.example.snapledger.AppContainer
import com.example.snapledger.data.local.Expense
import com.example.snapledger.model.Category
import com.example.snapledger.ui.common.DATE_TIME_FORMAT
import com.example.snapledger.ui.common.appViewModel
import com.example.snapledger.ui.common.toDateTimeText
import com.example.snapledger.ui.common.toEpochMillis
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDateTime

/**
 * 인식 결과 확인 + 직접 입력 + 기존 내역 수정을 한 화면에서 처리한다.
 * expenseId = -1 이면 새 내역 (촬영 화면이 남긴 pendingDraft가 있으면 그 값으로 채움)
 */
class ConfirmViewModel(private val container: AppContainer, private val expenseId: Long) : ViewModel() {
    private val repository = container.expenseRepository
    private val classifier = container.categoryClassifier

    var storeName by mutableStateOf("")
    var dateText by mutableStateOf(LocalDateTime.now().format(DATE_TIME_FORMAT))
    var amountText by mutableStateOf("")
    var memo by mutableStateOf("")
    var category by mutableStateOf(Category.ETC)
        private set
    var autoClassified by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    var imagePath: String? = null
        private set
    private var businessNumber: String? = null
    private var createdAt: Long = System.currentTimeMillis()
    private var userPickedCategory = false
    private var duplicateWarned = false

    val isEditing = expenseId >= 0
    val fromReceipt: Boolean get() = imagePath != null && !isEditing

    init {
        if (isEditing) {
            viewModelScope.launch { repository.get(expenseId)?.let(::fillFrom) }
        } else {
            container.pendingDraft?.let { draft ->
                container.pendingDraft = null
                imagePath = draft.imagePath
                businessNumber = draft.parsed.businessNumber
                storeName = draft.parsed.storeName.orEmpty()
                draft.parsed.paidAt?.let { dateText = it.format(DATE_TIME_FORMAT) }
                draft.parsed.total?.let { amountText = it.toString() }
                if (storeName.isNotBlank()) {
                    viewModelScope.launch {
                        category = classifier.classify(storeName, businessNumber)
                        autoClassified = true
                    }
                }
            }
        }
    }

    private fun fillFrom(expense: Expense) {
        storeName = expense.storeName
        dateText = expense.paidAt.toDateTimeText()
        amountText = expense.amount.toString()
        memo = expense.memo
        category = expense.category
        imagePath = expense.receiptImagePath
        businessNumber = expense.businessNumber
        createdAt = expense.createdAt
    }

    fun selectCategory(newCategory: Category) {
        category = newCategory
        autoClassified = false
        userPickedCategory = true
    }

    val storeError get() = storeName.isBlank()
    val amountError get() = amountText.toLongOrNull()?.let { it <= 0 } ?: true
    val dateError get() = parseDate() == null

    private fun parseDate(): LocalDateTime? =
        try {
            LocalDateTime.parse(dateText.trim(), DATE_TIME_FORMAT)
        } catch (e: Exception) {
            null
        }

    fun save(onSaved: () -> Unit) {
        val paidAt = parseDate()
        val amount = amountText.toLongOrNull()
        if (storeName.isBlank() || paidAt == null || amount == null || amount <= 0) {
            message = "빨갛게 표시된 칸을 확인해 주세요"
            return
        }
        val expense = Expense(
            id = if (isEditing) expenseId else 0,
            storeName = storeName.trim(),
            businessNumber = businessNumber,
            paidAt = paidAt.toEpochMillis(),
            amount = amount,
            category = category,
            memo = memo.trim(),
            receiptImagePath = imagePath,
            createdAt = createdAt,
        )
        viewModelScope.launch {
            // 중복 방지: 같은 내역이 있으면 한 번 경고하고, 다시 누르면 저장
            if (!duplicateWarned && repository.isDuplicate(expense)) {
                duplicateWarned = true
                message = "같은 가게·일시·금액의 내역이 이미 있어요. 그래도 저장하려면 한 번 더 누르세요"
                return@launch
            }
            try {
                if (userPickedCategory) {
                    classifier.remember(expense.storeName, expense.businessNumber, expense.category)
                }
                repository.save(expense)
                onSaved()
            } catch (e: Exception) {
                Log.e("ConfirmViewModel", "저장 실패", e)
                message = "저장하지 못했어요: ${e.message}"
            }
        }
    }
}

@Composable
fun ConfirmScreen(expenseId: Long, onBack: () -> Unit, onSaved: () -> Unit) {
    val vm = appViewModel { ConfirmViewModel(it, expenseId) }
    val context = LocalContext.current
    val onSavedWithToast = {
        Toast.makeText(context, "저장했어요", Toast.LENGTH_SHORT).show()
        onSaved()
    }
    val title = when {
        vm.isEditing -> "내역 수정"
        vm.fromReceipt -> "인식 결과 확인"
        else -> "직접 입력"
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로") }
            Text(title, style = MaterialTheme.typography.titleLarge)
        }

        vm.imagePath?.let { path ->
            // TODO(2단계): ML Kit 줄 좌표로 읽은 위치에 박스 표시
            AsyncImage(
                model = File(path),
                contentDescription = "영수증 원본",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().height(180.dp).padding(vertical = 8.dp),
            )
        }

        OutlinedTextField(
            value = vm.storeName,
            onValueChange = { vm.storeName = it },
            label = { Text("상호명") },
            isError = vm.storeError,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = vm.dateText,
            onValueChange = { vm.dateText = it },
            label = { Text("결제일시") },
            supportingText = { Text("예: 2026.10.19 13:42") },
            isError = vm.dateError,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = vm.amountText,
            onValueChange = { input -> vm.amountText = input.filter { it.isDigit() } },
            label = { Text("금액 (원)") },
            isError = vm.amountError,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        CategoryPicker(
            selected = vm.category,
            autoClassified = vm.autoClassified,
            onSelect = vm::selectCategory,
        )

        OutlinedTextField(
            value = vm.memo,
            onValueChange = { vm.memo = it },
            label = { Text("메모") },
            modifier = Modifier.fillMaxWidth(),
        )

        vm.message?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("취소") }
            Button(onClick = { vm.save(onSavedWithToast) }, modifier = Modifier.weight(1f)) { Text("저장") }
        }
    }
}

@Composable
private fun CategoryPicker(selected: Category, autoClassified: Boolean, onSelect: (Category) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Text("카테고리", style = MaterialTheme.typography.bodySmall)
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(selected.label + if (autoClassified) "  (자동 분류)" else "")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Category.entries.forEach { category ->
                DropdownMenuItem(
                    text = { Text(category.label) },
                    onClick = {
                        onSelect(category)
                        expanded = false
                    },
                )
            }
        }
    }
}
