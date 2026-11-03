package com.example.snapledger.ui.stats

import androidx.compose.ui.graphics.Color
import com.example.snapledger.model.Category

/**
 * 그래프 공통 색. 앱이 라이트 테마만 쓰므로 흰 바탕 기준으로 골랐다.
 * 글자는 이 색을 쓰지 않고 MaterialTheme의 글자색(onSurface, onSurfaceVariant)을 쓴다.
 */
object ChartColors {
    /** 바닥선 (1dp 가는 선) */
    val Baseline = Color(0xFFC3C2B7)

    /** 강조하지 않은 막대: 강조색(primary 파랑)과 같은 계열의 연한 단계 */
    val BarMuted = Color(0xFF86B6EF)

    /** 비교 대상(지난달) 선: 강조색 옆에서 한발 물러나 보이는 회색 (흰 바탕 대비 3:1 이상) */
    val Previous = Color(0xFF898781)
}

/**
 * 카테고리마다 고정된 색. 금액 순위가 바뀌어도 같은 카테고리는 늘 같은 색이다.
 * 7가지 색은 색약(적록·청황) 시뮬레이션에서도 이웃한 색끼리 구분되는지 검사했고,
 * '기타'는 분류되지 않은 것이라 회색으로 둔다.
 * 노랑·분홍·청록은 흰 바탕에서 대비가 낮으므로 도넛 아래 표(이름·비중·금액)를 항상 같이 보여준다.
 */
val Category.chartColor: Color
    get() = when (this) {
        Category.FOOD -> Color(0xFF2A78D6)         // 파랑
        Category.CAFE -> Color(0xFFEB6834)         // 주황
        Category.CONVENIENCE -> Color(0xFF1BAF7A)  // 청록
        Category.TRANSPORT -> Color(0xFFEDA100)    // 노랑
        Category.SHOPPING -> Color(0xFFE87BA4)     // 분홍
        Category.LIVING -> Color(0xFF008300)       // 초록
        Category.CULTURE -> Color(0xFF6250D6)      // 보라
        Category.ETC -> Color(0xFF898781)          // 회색
    }
