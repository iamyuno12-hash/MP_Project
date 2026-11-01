package com.example.snapledger.ui.login

import android.content.Context
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.snapledger.data.firebase.AuthRepository
import com.example.snapledger.ui.common.appViewModel
import kotlinx.coroutines.launch

class SignUpViewModel(private val auth: AuthRepository) : ViewModel() {
    var email by mutableStateOf("")
    var password by mutableStateOf("")
    var passwordConfirm by mutableStateOf("")
    var error by mutableStateOf<String?>(null)
        private set
    var loading by mutableStateOf(false)
        private set

    // 입력 칸 아래에 바로 보여줄 검사 결과 (아직 입력 안 했으면 표시하지 않음)
    val emailError get() = email.isNotEmpty() && !email.contains("@")
    val passwordError get() = password.isNotEmpty() && password.length < 6
    val confirmError get() = passwordConfirm.isNotEmpty() && passwordConfirm != password

    fun signUp(onSuccess: () -> Unit) {
        error = when {
            !email.contains("@") -> "이메일 형식을 확인해 주세요"
            password.length < 6 -> "비밀번호는 6자 이상이어야 해요"
            password != passwordConfirm -> "비밀번호가 서로 달라요"
            else -> null
        }
        if (error != null) return
        submit(onSuccess) { auth.signUpWithEmail(email, password) }
    }

    fun signUpWithGoogle(activityContext: Context, onSuccess: () -> Unit) =
        submit(onSuccess) { auth.signInWithGoogle(activityContext) }

    private fun submit(onSuccess: () -> Unit, action: suspend () -> Result<Unit>) {
        viewModelScope.launch {
            loading = true
            error = null
            action()
                .onSuccess { onSuccess() }
                .onFailure { error = it.message ?: "가입에 실패했어요" }
            loading = false
        }
    }
}

@Composable
fun SignUpScreen(onBack: () -> Unit, onSignedUp: () -> Unit) {
    val vm = appViewModel { SignUpViewModel(it.authRepository) }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로") }
            Text("회원가입", style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(24.dp))

        Column(Modifier.padding(horizontal = 8.dp)) {
            OutlinedTextField(
                value = vm.email,
                onValueChange = { vm.email = it },
                label = { Text("이메일") },
                isError = vm.emailError,
                supportingText = { if (vm.emailError) Text("이메일 형식이 아니에요") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = vm.password,
                onValueChange = { vm.password = it },
                label = { Text("비밀번호 (6자 이상)") },
                isError = vm.passwordError,
                supportingText = { if (vm.passwordError) Text("6자 이상 입력해 주세요") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = vm.passwordConfirm,
                onValueChange = { vm.passwordConfirm = it },
                label = { Text("비밀번호 확인") },
                isError = vm.confirmError,
                supportingText = { if (vm.confirmError) Text("비밀번호가 서로 달라요") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )

            vm.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
            }

            Button(
                onClick = { vm.signUp(onSignedUp) },
                enabled = !vm.loading,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { Text("가입하기") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { vm.signUpWithGoogle(context, onSignedUp) },
                enabled = !vm.loading,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { Text("Google 계정으로 가입") }
        }
    }
}
