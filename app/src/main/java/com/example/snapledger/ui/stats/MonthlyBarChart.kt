package com.example.snapledger.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.example.snapledger.ui.common.toCompactWon
import com.example.snapledger.ui.common.toWon

/**
 * 연도별 통계의 1~12월 지출 막대 그래프 (Compose Canvas).
 * 선택한 달은 진한 파랑 + 금액, 나머지 달은 연한 파랑. 막대를 누르면 onSelect(달)을 부른다.
 *
 * @param monthly 1~12월 합계 (index 0 = 1월)
 * @param selectedMonth 강조할 달 (1~12), 없으면 null
 */
@Composable
fun MonthlyBarChart(
    monthly: List<Long>,
    selectedMonth: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val axisStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val valueStyle = MaterialTheme.typography.labelMedium.copy(
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.SemiBold,
    )
    val accent = MaterialTheme.colorScheme.primary
    val currentOnSelect by rememberUpdatedState(onSelect)
    val maxValue = (monthly.maxOrNull() ?: 0L).coerceAtLeast(1L)
    val description = monthly.withIndex().joinToString(", ") { "${it.index + 1}월 ${it.value.toWon()}" }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
            .semantics { contentDescription = "월별 지출 막대 그래프. $description" }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val slot = size.width / 12f
                    currentOnSelect((offset.x / slot).toInt().coerceIn(0, 11) + 1)
                }
            },
    ) {
        val slot = size.width / 12f
        val barWidth = minOf(24.dp.toPx(), slot * 0.6f)
        val corner = 4.dp.toPx()
        val gap = 4.dp.toPx()
        // 선택한 달의 숫자는 굵은 글씨로 쓰므로 두 글씨 중 큰 높이만큼 아래 공간을 비워 둔다
        val axisLabelHeight = maxOf(
            textMeasurer.measure("12", axisStyle).size.height,
            textMeasurer.measure("12", valueStyle).size.height,
        )
        val valueLabelHeight = textMeasurer.measure("0", valueStyle).size.height
        val chartTop = valueLabelHeight + gap
        val baseline = size.height - axisLabelHeight - gap * 2
        val chartHeight = baseline - chartTop

        drawLine(ChartColors.Baseline, Offset(0f, baseline), Offset(size.width, baseline), strokeWidth = 1.dp.toPx())

        monthly.forEachIndexed { index, value ->
            val month = index + 1
            val selected = month == selectedMonth
            val centerX = slot * index + slot / 2f

            if (value > 0) {
                // 아주 작은 금액도 보이도록 최소 높이는 모서리 반지름만큼
                val barHeight = (value.toFloat() / maxValue * chartHeight).coerceAtLeast(corner)
                val top = baseline - barHeight
                val bar = Path().apply {
                    addRoundRect(
                        RoundRect(
                            rect = Rect(centerX - barWidth / 2f, top, centerX + barWidth / 2f, baseline),
                            topLeft = CornerRadius(corner),
                            topRight = CornerRadius(corner),
                            bottomRight = CornerRadius.Zero,
                            bottomLeft = CornerRadius.Zero,
                        )
                    )
                }
                drawPath(bar, if (selected) accent else ChartColors.BarMuted)

                if (selected) {
                    val label = textMeasurer.measure(value.toCompactWon(), valueStyle)
                    val x = (centerX - label.size.width / 2f).coerceIn(0f, size.width - label.size.width)
                    drawText(label, topLeft = Offset(x, top - gap - label.size.height))
                }
            }

            val axisLabel = textMeasurer.measure("$month", if (selected) valueStyle else axisStyle)
            drawText(axisLabel, topLeft = Offset(centerX - axisLabel.size.width / 2f, baseline + gap))
        }
    }
}
