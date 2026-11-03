package com.example.snapledger

import com.example.snapledger.ui.navigation.Routes
import com.example.snapledger.ui.navigation.StartDecision
import com.example.snapledger.ui.navigation.StartRoute
import org.junit.Assert.assertEquals
import org.junit.Test

/** 자동 로그인: 앱을 켰을 때 시작 화면 결정 테스트 */
class StartRouteTest {

    @Test
    fun 로그인_안_했으면_로그인_화면() {
        assertEquals(
            StartDecision(Routes.LOGIN, signOutFirst = false),
            StartRoute.decide(isLoggedIn = false, keepSignedIn = true, freshStart = true),
        )
    }

    @Test
    fun 로그인_유지를_켰으면_바로_홈() {
        assertEquals(
            StartDecision(Routes.HOME, signOutFirst = false),
            StartRoute.decide(isLoggedIn = true, keepSignedIn = true, freshStart = true),
        )
    }

    @Test
    fun 로그인_유지를_껐으면_새로_켤_때_로그아웃() {
        assertEquals(
            StartDecision(Routes.LOGIN, signOutFirst = true),
            StartRoute.decide(isLoggedIn = true, keepSignedIn = false, freshStart = true),
        )
    }

    @Test
    fun 화면_회전이나_복원일_때는_로그아웃하지_않음() {
        assertEquals(
            StartDecision(Routes.HOME, signOutFirst = false),
            StartRoute.decide(isLoggedIn = true, keepSignedIn = false, freshStart = false),
        )
    }
}
