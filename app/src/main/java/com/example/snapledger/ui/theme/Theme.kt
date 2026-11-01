package com.example.snapledger.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 와이어프레임 기준 색상
val Blue = Color(0xFF2F7DE1)
val BlueLight = Color(0xFFE6F0FC)
val Warning = Color(0xFFF5B82E)

private val LightColors = lightColorScheme(
    primary = Blue,
    onPrimary = Color.White,
    primaryContainer = BlueLight,
    onPrimaryContainer = Color(0xFF0B2E5C),
    background = Color.White,
    surface = Color.White,
)

@Composable
fun SnapLedgerTheme(content: @Composable () -> Unit) {
    // 다크 모드는 3~4단계에서 여유가 있으면 추가
    MaterialTheme(colorScheme = LightColors, typography = Typography, content = content)
}
