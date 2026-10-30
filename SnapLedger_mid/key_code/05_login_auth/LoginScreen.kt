package com.example.snapledger.ui.login

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.snapledger.data.firebase.AuthRepository
import com.example.snapledger.ui.common.appViewModel
import kotlinx.coroutines.launch

class LoginViewModel(private val auth: AuthRepository) : ViewModel() {
    var email by mutableStateOf("")
    var password by mutableStateOf("")
    var error by mutableStateOf<String?>(null)
        private set
    var loading by mutableStateOf(false)
        private set

    val isLoggedIn: Boolean get() = auth.currentUserId.value != null
    val usesFirebase: Boolean get() = auth.usesFirebase

    fun login(onSuccess: () -> Unit) {
        if (email.isBlank() || password.isBlank()) {
            error = "이메일과 비밀번호를 입력해 주세요"
            return
        }
        submit(onSuccess) { auth.signInWithEmail(email, password) }
    }

    fun loginWithGoogle(activityContext: Context, onSuccess: () -> Unit) =
        submit(onSuccess) { auth.signInWithGoogle(activityContext) }

    private fun submit(onSuccess: () -> Unit, action: suspend () -> Result<Unit>) {
        viewModelScope.launch {
            loading = true
            error = null
            action()
                .onSuccess { onSuccess() }
                .onFailure { error = it.message ?: "로그인에 실패했어요" }
            loading = false
        }
    }
}

@Composable
fun LoginScreen(onLoggedIn: () -> Unit, onSignUpClick: () -> Unit) {
    val vm = appViewModel { LoginViewModel(it.authRepository) }
    val context = LocalContext.current

    // 이미 로그인한 상태면 바로 홈으로 (앱을 다시 켰을 때)
    LaunchedEffect(Unit) { if (vm.isLoggedIn) onLoggedIn() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppLogo()
        Spacer(Modifier.height(16.dp))
        Text("찰칵가계부", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("영수증 한 장이면 기록 끝", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = vm.email,
            onValueChange = { vm.email = it },
            label = { Text("이메일") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = vm.password,
            onValueChange = { vm.password = it },
            label = { Text("비밀번호") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
        vm.error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(16.dp))

        Button(
            onClick = { vm.login(onLoggedIn) },
            enabled = !vm.loading,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) { Text("로그인") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { vm.loginWithGoogle(context, onLoggedIn) },
            enabled = !vm.loading,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) { Text("Google 계정으로 계속") }

        TextButton(onClick = onSignUpClick) { Text("처음이신가요? 회원가입") }

        if (!vm.usesFirebase) {
            Spacer(Modifier.height(8.dp))
            Text(
                "테스트 모드: Firebase 연결 전이라 아무 이메일로나 로그인돼요",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 와이어프레임의 동그란 영수증 아이콘 (임시) */
@Composable
fun AppLogo() {
    Box(
        modifier = Modifier
            .size(80.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text("🧾", fontSize = 36.sp)
    }
}
