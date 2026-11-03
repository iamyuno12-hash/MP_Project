package com.example.snapledger.ui.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.snapledger.data.firebase.AuthRepository
import com.example.snapledger.data.local.SessionStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** 앱을 켰을 때 처음 보여줄 화면과, 그 전에 로그아웃해야 하는지 */
data class StartDecision(val route: String, val signOutFirst: Boolean)

object StartRoute {
    /**
     * @param isLoggedIn 기기에 로그인 상태가 남아 있는지
     * @param keepSignedIn 로그인 상태 유지(자동 로그인)를 켰는지
     * @param freshStart 앱을 새로 켰는지 (false면 화면 회전·메모리 정리 후 복원 → 쓰던 상태 그대로)
     */
    fun decide(isLoggedIn: Boolean, keepSignedIn: Boolean, freshStart: Boolean): StartDecision = when {
        !isLoggedIn -> StartDecision(Routes.LOGIN, signOutFirst = false)
        keepSignedIn || !freshStart -> StartDecision(Routes.HOME, signOutFirst = false)
        // 유지하지 않기로 했으면, 앱을 새로 켤 때 로그아웃하고 로그인 화면부터
        else -> StartDecision(Routes.LOGIN, signOutFirst = true)
    }
}

/**
 * 자동 로그인: 기기에 저장된 로그인 상태를 불러와 시작 화면을 정한다.
 * 정해지기 전(startRoute == null)에는 AppRoot가 로고만 보여준다 (로그인 화면이 깜빡 보이지 않게).
 */
class StartViewModel(
    private val auth: AuthRepository,
    private val session: SessionStore,
    freshStart: Boolean,
) : ViewModel() {

    var startRoute by mutableStateOf<String?>(null)
        private set

    init {
        viewModelScope.launch {
            auth.restoreSession()
            val decision = StartRoute.decide(
                isLoggedIn = auth.currentUserId.value != null,
                keepSignedIn = session.keepSignedIn.first(),
                freshStart = freshStart,
            )
            if (decision.signOutFirst) auth.signOut()
            startRoute = decision.route
        }
    }
}
