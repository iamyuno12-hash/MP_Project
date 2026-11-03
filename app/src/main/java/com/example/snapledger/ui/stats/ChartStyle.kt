package com.example.snapledger.ui.stats

import androidx.compose.ui.graphics.Color

/**
 * 그래프 공통 색. 앱이 라이트 테마만 쓰므로 흰 바탕 기준으로 골랐다.
 * 글자는 이 색을 쓰지 않고 MaterialTheme의 글자색(onSurface, onSurfaceVariant)을 쓴다.
 */
object ChartColors {
    /** 바닥선 (1dp 가는 선) */
    val Baseline = Color(0xFFC3C2B7)

    /** 강조하지 않은 막대: 강조색(primary 파랑)과 같은 계열의 연한 단계 */
    val BarMuted = Color(0xFF86B6EF)
}
