package com.example.snapledger.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.snapledger.ui.capture.CaptureScreen
import com.example.snapledger.ui.common.appViewModel
import com.example.snapledger.ui.confirm.ConfirmScreen
import com.example.snapledger.ui.detail.DetailScreen
import com.example.snapledger.ui.home.HomeScreen
import com.example.snapledger.ui.login.AppLogo
import com.example.snapledger.ui.login.LoginScreen
import com.example.snapledger.ui.login.SignUpScreen
import com.example.snapledger.ui.settings.SettingsScreen
import com.example.snapledger.ui.stats.StatsScreen

/** 화면 주소 모음. 화면을 추가하면 여기와 아래 NavHost에 한 줄씩 추가한다. */
object Routes {
    const val LOGIN = "login"
    const val SIGNUP = "signup"
    const val HOME = "home"
    const val CAPTURE = "capture"
    const val CONFIRM = "confirm?expenseId={expenseId}"
    const val DETAIL = "detail/{expenseId}"
    const val STATS = "stats"
    const val SETTINGS = "settings"

    /** expenseId = -1 이면 새 내역(영수증 인식 결과 또는 직접 입력), 아니면 기존 내역 수정 */
    fun confirm(expenseId: Long = -1L) = "confirm?expenseId=$expenseId"
    fun detail(expenseId: Long) = "detail/$expenseId"
}

private data class BottomTab(val route: String, val label: String, val icon: ImageVector)

private val bottomTabs = listOf(
    BottomTab(Routes.HOME, "홈", Icons.Filled.Home),
    BottomTab(Routes.STATS, "통계", Icons.Filled.DateRange),
    BottomTab(Routes.SETTINGS, "설정", Icons.Filled.Settings),
)

/**
 * @param freshStart 앱을 새로 켰는지 (MainActivity의 savedInstanceState == null).
 *   화면 회전이나 메모리 정리 후 복원일 때는 로그인 상태 유지 설정과 관계없이 로그아웃하지 않는다.
 */
@Composable
fun AppRoot(freshStart: Boolean = true, navController: NavHostController = rememberNavController()) {
    // 자동 로그인: 저장된 로그인 상태를 확인하는 동안(보통 순식간) 로고만 보여준다
    val startVm = appViewModel { StartViewModel(it.authRepository, it.sessionStore, freshStart) }
    val startRoute = startVm.startRoute
    if (startRoute == null) {
        StartSplash()
        return
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = bottomTabs.any { it.route == currentRoute }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomTabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(Routes.HOME) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startRoute,   // 로그인 상태면 HOME, 아니면 LOGIN
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Routes.LOGIN) {
                LoginScreen(
                    onLoggedIn = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.LOGIN) { inclusive = true }
                        }
                    },
                    onSignUpClick = { navController.navigate(Routes.SIGNUP) },
                )
            }
            composable(Routes.SIGNUP) {
                SignUpScreen(
                    onBack = { navController.popBackStack() },
                    onSignedUp = {
                        // 가입하면 로그인·회원가입 화면을 모두 지우고 홈으로
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.LOGIN) { inclusive = true }
                        }
                    },
                )
            }
            composable(Routes.HOME) {
                HomeScreen(
                    onCaptureClick = { navController.navigate(Routes.CAPTURE) },
                    onExpenseClick = { id -> navController.navigate(Routes.detail(id)) },
                )
            }
            composable(Routes.CAPTURE) {
                CaptureScreen(
                    onClose = { navController.popBackStack() },
                    onNext = {
                        // 촬영 화면은 뒤로 가기 목록에서 빼고 확인 화면으로 이동
                        navController.navigate(Routes.confirm()) {
                            popUpTo(Routes.CAPTURE) { inclusive = true }
                        }
                    },
                )
            }
            composable(
                route = Routes.CONFIRM,
                arguments = listOf(navArgument("expenseId") {
                    type = NavType.LongType
                    defaultValue = -1L
                }),
            ) { entry ->
                ConfirmScreen(
                    expenseId = entry.arguments?.getLong("expenseId") ?: -1L,
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() },
                )
            }
            composable(
                route = Routes.DETAIL,
                arguments = listOf(navArgument("expenseId") { type = NavType.LongType }),
            ) { entry ->
                DetailScreen(
                    expenseId = entry.arguments?.getLong("expenseId") ?: -1L,
                    onBack = { navController.popBackStack() },
                    onEdit = { id -> navController.navigate(Routes.confirm(id)) },
                )
            }
            composable(Routes.STATS) { StatsScreen() }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onLoggedOut = {
                        navController.navigate(Routes.LOGIN) {
                            popUpTo(navController.graph.id) { inclusive = true }
                        }
                    },
                )
            }
        }
    }
}

/** 시작 화면을 정하는 동안 잠깐 보이는 로고 */
@Composable
private fun StartSplash() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { AppLogo() }
}
