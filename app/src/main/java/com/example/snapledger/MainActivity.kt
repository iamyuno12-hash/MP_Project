package com.example.snapledger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.snapledger.ui.navigation.AppRoot
import com.example.snapledger.ui.theme.SnapLedgerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SnapLedgerTheme {
                // 새로 켰을 때만 '로그인 상태 유지'를 끈 사용자를 로그아웃시킨다
                AppRoot(freshStart = savedInstanceState == null)
            }
        }
    }
}
