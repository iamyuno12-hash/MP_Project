package com.example.snapledger.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.snapledger.data.firebase.AuthRepository
import com.example.snapledger.data.local.SessionStore
import com.example.snapledger.data.local.SettingsStore
import com.example.snapledger.data.repository.ExpenseSync
import com.example.snapledger.ui.common.appViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class SettingsViewModel(
    private val settings: SettingsStore,
    private val auth: AuthRepository,
    private val session: SessionStore,
    private val sync: ExpenseSync,
) : ViewModel() {
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

    val accountEmail: String? get() = auth.currentEmail
    val usesFirebase: Boolean get() = auth.usesFirebase

    /** 로그인 상태 유지 (자동 로그인) */
    val keepSignedIn: StateFlow<Boolean> =
        session.keepSignedIn.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun setKeepSignedIn(keep: Boolean) {
        viewModelScope.launch { session.setKeepSignedIn(keep) }
    }

    // ---------- Firestore 백업 ----------

    val backupAvailable: Boolean get() = sync.isAvailable
    var backupBusy by mutableStateOf(false)
        private set
    var backupMessage by mutableStateOf<String?>(null)
        private set

    /** 아직 클라우드에 안 올라간 내역 수 */
    val unsyncedCount: StateFlow<Int> =
        sync.observeUnsyncedCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun backupNow() = runBackup { n -> if (n > 0) "${n}건을 백업했어요" else "이미 모두 백업돼 있어요" }

    fun restore() = runBackup(restore = true) { n -> if (n > 0) "${n}건을 가져왔어요" else "새로 가져올 내역이 없어요" }

    private fun runBackup(restore: Boolean = false, done: (Int) -> String) {
        if (backupBusy) return
        viewModelScope.launch {
            backupBusy = true
            backupMessage = null
            backupMessage = try {
                // 인터넷이 없으면 Firestore가 연결될 때까지 기다리므로 시간 제한을 둔다
                val count = withTimeout(20_000) { if (restore) sync.restore() else sync.backupAll() }
                done(count)
            } catch (e: TimeoutCancellationException) {
                "인터넷 연결을 확인해 주세요"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                "실패했어요: ${e.message}"
            } finally {
                backupBusy = false
            }
        }
    }

    /** 저장된 로그인 정보까지 지운 뒤 onDone (로그인 화면으로 이동) */
    fun signOut(onDone: () -> Unit) {
        viewModelScope.launch {
            auth.signOut()
            onDone()
        }
    }
}

@Composable
fun SettingsScreen(onLoggedOut: () -> Unit) {
    val vm = appViewModel { SettingsViewModel(it.settingsStore, it.authRepository, it.sessionStore, it.expenseSync) }
    val keepSignedIn by vm.keepSignedIn.collectAsStateWithLifecycle()
    val unsyncedCount by vm.unsyncedCount.collectAsStateWithLifecycle()

    // 항목이 늘어 작은 화면에서는 넘칠 수 있으므로 스크롤
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
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

        Text("클라우드 백업", style = MaterialTheme.typography.titleMedium)
        if (vm.backupAvailable) {
            Text(
                if (unsyncedCount == 0) "모든 내역이 백업돼 있어요 (저장할 때마다 자동 백업)"
                else "백업 안 된 내역 ${unsyncedCount}건 (인터넷이 연결되면 자동으로 올라가요)",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = vm::backupNow, enabled = !vm.backupBusy, modifier = Modifier.weight(1f)) {
                    Text("지금 백업")
                }
                OutlinedButton(onClick = vm::restore, enabled = !vm.backupBusy, modifier = Modifier.weight(1f)) {
                    Text("백업에서 가져오기")
                }
            }
            if (vm.backupBusy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            vm.backupMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            Text(
                "영수증 사진은 기기에만 저장되고, 내역 값만 백업돼요",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                if (vm.usesFirebase) "로그인하면 백업할 수 있어요"
                else "테스트 모드에서는 백업할 수 없어요. Firebase를 연결하고 로그인하면 자동으로 백업돼요",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        Text("계정", style = MaterialTheme.typography.titleMedium)
        Text(
            (vm.accountEmail ?: "로그인 정보 없음") + if (vm.usesFirebase) "" else " (테스트 모드)",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = keepSignedIn, role = Role.Switch, onValueChange = vm::setKeepSignedIn),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("자동 로그인")
                Text(
                    "앱을 다시 열어도 로그인 화면 없이 바로 시작해요",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = keepSignedIn, onCheckedChange = null)
        }

        Spacer(Modifier.height(24.dp))
        OutlinedButton(
            onClick = { vm.signOut(onLoggedOut) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("로그아웃") }
    }
}
