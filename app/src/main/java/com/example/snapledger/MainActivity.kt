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
                AppRoot()
            }
        }
    }
}
