package com.example.snapledger.data.firebase

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.gms.tasks.Task
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** 로그인 창구. 화면(ViewModel)은 이 인터페이스만 사용한다. */
interface AuthRepository {
    /** 로그인한 사용자 uid. 로그아웃 상태면 null */
    val currentUserId: StateFlow<String?>
    /** Firebase에 연결된 진짜 로그인인지 (false면 테스트용 가짜 로그인) */
    val usesFirebase: Boolean
    suspend fun signInWithEmail(email: String, password: String): Result<Unit>
    suspend fun signUpWithEmail(email: String, password: String): Result<Unit>
    /** activityContext: 화면의 LocalContext.current (계정 선택 창을 띄우는 데 필요) */
    suspend fun signInWithGoogle(activityContext: Context): Result<Unit>
    fun signOut()
}

/** Firebase Authentication 구현 (이메일 회원가입/로그인 + Google 로그인) */
class FirebaseAuthRepository : AuthRepository {
    private val auth = FirebaseAuth.getInstance()
    private val user = MutableStateFlow(auth.currentUser?.uid)
    override val currentUserId: StateFlow<String?> = user
    override val usesFirebase = true

    init {
        auth.addAuthStateListener { user.value = it.currentUser?.uid }
    }

    override suspend fun signInWithEmail(email: String, password: String) = runAuth {
        auth.signInWithEmailAndPassword(email.trim(), password).awaitTask()
    }

    override suspend fun signUpWithEmail(email: String, password: String) = runAuth {
        auth.createUserWithEmailAndPassword(email.trim(), password).awaitTask()
    }

    override suspend fun signInWithGoogle(activityContext: Context) = runAuth {
        val webClientId = webClientId(activityContext)
            ?: throw IllegalStateException(
                "Google 로그인 설정이 없어요. Firebase에서 Google 로그인을 켠 뒤 google-services.json을 다시 받아 넣어 주세요"
            )

        // 1) Credential Manager로 Google 계정 선택 → ID 토큰 받기
        val option = GetSignInWithGoogleOption.Builder(webClientId).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val credential = CredentialManager.create(activityContext)
            .getCredential(activityContext, request)
            .credential

        if (credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            throw IllegalStateException("Google 계정 정보를 받지 못했어요")
        }
        val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken

        // 2) 받은 토큰으로 Firebase 로그인 (처음이면 자동으로 가입됨)
        auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).awaitTask()
    }

    override fun signOut() = auth.signOut()

    /** google-services 플러그인이 json에서 만들어 주는 웹 클라이언트 ID (없으면 null) */
    private fun webClientId(context: Context): String? {
        val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        return if (resId != 0) context.getString(resId) else null
    }

    private suspend fun runAuth(block: suspend () -> Unit): Result<Unit> =
        try {
            block()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(Exception(koreanMessage(e), e))
        }

    private fun koreanMessage(e: Exception): String = when (e) {
        is GetCredentialCancellationException -> "Google 로그인을 취소했어요"
        is NoCredentialException -> "이 기기에 Google 계정이 없어요. 설정에서 계정을 추가해 주세요"
        is FirebaseAuthUserCollisionException -> "이미 가입된 이메일이에요"
        is FirebaseAuthWeakPasswordException -> "비밀번호는 6자 이상이어야 해요"
        is FirebaseAuthInvalidUserException -> "가입되지 않은 이메일이에요"
        is FirebaseAuthInvalidCredentialsException -> "이메일 또는 비밀번호를 확인해 주세요"
        is FirebaseNetworkException -> "인터넷 연결을 확인해 주세요"
        else -> e.message ?: "로그인에 실패했어요"
    }
}

/** Firebase의 Task를 코루틴에서 기다리기 */
private suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
}

/**
 * Firebase 연결 전에 쓰는 테스트용 가짜 로그인.
 * google-services.json이 없으면 AppContainer가 자동으로 이걸 쓴다.
 */
class FakeAuthRepository : AuthRepository {
    private val user = MutableStateFlow<String?>(null)
    override val currentUserId: StateFlow<String?> = user
    override val usesFirebase = false

    override suspend fun signInWithEmail(email: String, password: String): Result<Unit> {
        if (!email.contains("@") || password.length < 6) {
            return Result.failure(IllegalArgumentException("이메일과 6자 이상 비밀번호를 입력하세요"))
        }
        user.value = "fake-$email"
        return Result.success(Unit)
    }

    override suspend fun signUpWithEmail(email: String, password: String) = signInWithEmail(email, password)

    override suspend fun signInWithGoogle(activityContext: Context): Result<Unit> =
        Result.failure(IllegalStateException("Google 로그인은 Firebase를 연결한 뒤 사용할 수 있어요"))

    override fun signOut() {
        user.value = null
    }
}
