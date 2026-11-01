package com.example.snapledger.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.snapledger.data.firebase.AuthRepository
import com.example.snapledger.data.local.SettingsStore
import com.example.snapledger.ui.common.appViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsViewModel(private val settings: SettingsStore, private val auth: AuthRepository) : ViewModel() {
    var budgetText by mutableStateOf("")
    var message by mutableStateOf<String?>(null)
        private set

    init {
        viewModelScope.launch {
            val budget = settings.monthlyBudget.first()
            if (budget > 0) budgetText = budget.toString()
        }
    }

    fun saveBudget() {
        val budget = budgetText.toLongOrNull() ?: 0L
        viewModelScope.launch {
            settings.setMonthlyBudget(budget)
            message = if (budget > 0) "월 예산을 저장했어요" else "월 예산을 해제했어요"
        }
    }

    fun signOut() = auth.signOut()
}

@Composable
fun SettingsScreen(onLoggedOut: () -> Unit) {
    val vm = appViewModel { SettingsViewModel(it.settingsStore, it.authRepository) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("설정", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))

        Text("월 예산", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = vm.budgetText,
            onValueChange = { input -> vm.budgetText = input.filter { it.isDigit() } },
            label = { Text("금액 (원)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        // TODO(3단계): 80%·100% 도달 시 알림 (POST_NOTIFICATIONS 권한 요청 포함)
        Button(onClick = vm::saveBudget, modifier = Modifier.padding(top = 8.dp)) { Text("저장") }
        vm.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        Text("백업", style = MaterialTheme.typography.titleMedium)
        // TODO(백엔드, 2단계): Firestore 백업/복원
        Text("Firestore 백업은 2단계에서 연결 예정", color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(Modifier.height(24.dp))
        OutlinedButton(
            onClick = {
                vm.signOut()
                onLoggedOut()
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("로그아웃") }
    }
}
