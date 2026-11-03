package com.example.snapledger.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.example.snapledger.ui.common.toWon
import kotlin.math.roundToInt

/**
 * 이번 달과 지난달의 '날짜별 누적 지출'을 겹쳐 그린 선 그래프 (토스 소비 그래프 방식).
 * - 파란 선: 이번 달 1일부터 기준일(이번 달이면 오늘)까지, 끝에 점
 * - 회색 선: 지난달 1일부터 말일까지, 기준일과 같은 날에 점
 * - 그래프를 누르거나 좌우로 끌면 그날까지의 두 달 금액을 위에 보여준다
 */
@Composable
fun SpendingCompareChart(comparison: MonthComparison, modifier: Modifier = Modifier) {
    var selectedDay by remember(comparison.month) { mutableStateOf<Int?>(null) }
    val current = comparison.current
    // 지난달 기록이 없으면 바닥에 붙은 0원 선만 남으므로 아예 그리지 않는다
    val previous = if (comparison.hasPrevious) comparison.previous else emptyList()
    // 가로축: 두 달 중 긴 달 기준 (지난달 선이 없으면 이번 달 길이)
    val dayCount = if (previous.isEmpty()) comparison.month.lengthOfMonth() else comparison.dayCount

    val accent = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surface
    val textMeasurer = rememberTextMeasurer()
    val axisStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)

    Column(modifier.fillMaxWidth()) {
        // 1) 기본은 비교 문구, 누르고 있으면 그날까지의 금액
        CompareHeader(comparison, selectedDay, current, previous)
        Spacer(Modifier.height(12.dp))

        // 2) 그래프
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .semantics { contentDescription = comparison.headline() }
                // 누르는 동안 / 좌우로 끄는 동안 그 위치의 날짜를 고른다 (세로로 끌면 화면 스크롤)
                .pointerInput(dayCount) {
                    detectTapGestures(onPress = { offset ->
                        selectedDay = dayAt(offset.x, size.width.toFloat(), ChartInset.toPx(), dayCount)
                        tryAwaitRelease()
                        selectedDay = null
                    })
                }
                .pointerInput(dayCount) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            selectedDay = dayAt(offset.x, size.width.toFloat(), ChartInset.toPx(), dayCount)
                        },
                        onDragEnd = { selectedDay = null },
                        onDragCancel = { selectedDay = null },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            selectedDay = dayAt(change.position.x, size.width.toFloat(), ChartInset.toPx(), dayCount)
                        },
                    )
                },
        ) {
            val axisLabelHeight = textMeasurer.measure("1일", axisStyle).size.height
            val gap = 6.dp.toPx()
            val dotRadius = 4.dp.toPx()
            val ring = 2.dp.toPx()
            val left = ChartInset.toPx()
            val right = size.width - ChartInset.toPx()
            val top = ChartInset.toPx()
            val baseline = size.height - axisLabelHeight - gap
            val maxValue = maxOf(current.maxOrNull() ?: 0L, previous.maxOrNull() ?: 0L).coerceAtLeast(1L)

            fun x(day: Int) = left + (right - left) * (day - 1) / (dayCount - 1).coerceAtLeast(1)
            fun y(value: Long) = baseline - (baseline - top) * (value.toFloat() / maxValue)

            drawLine(ChartColors.Baseline, Offset(0f, baseline), Offset(size.width, baseline), strokeWidth = 1.dp.toPx())

            // 지난달 (회색)
            if (previous.isNotEmpty()) {
                drawPath(linePath(previous, ::x, ::y), ChartColors.Previous, style = lineStroke())
            }
            // 이번 달 (파랑) + 아래 옅은 면
            if (current.isNotEmpty()) {
                drawPath(areaPath(current, ::x, ::y, baseline), accent.copy(alpha = 0.10f))
                drawPath(linePath(current, ::x, ::y), accent, style = lineStroke())
            }

            // 비교 지점 점: 지난달 같은 날, 이번 달 마지막 날
            val compareDay = comparison.compareDay.coerceAtMost(previous.size)
            if (previous.isNotEmpty() && compareDay >= 1) {
                drawDot(Offset(x(compareDay), y(previous[compareDay - 1])), ChartColors.Previous, surface, dotRadius, ring)
            }
            if (current.isNotEmpty()) {
                drawDot(Offset(x(current.size), y(current.last())), accent, surface, dotRadius, ring)
            }

            // 누르고 있는 날: 세로선 + 두 선 위의 점
            selectedDay?.let { d ->
                drawLine(ChartColors.Baseline, Offset(x(d), top), Offset(x(d), baseline), strokeWidth = 1.dp.toPx())
                previous.getOrNull(d - 1)?.let { drawDot(Offset(x(d), y(it)), ChartColors.Previous, surface, dotRadius, ring) }
                current.getOrNull(d - 1)?.let { drawDot(Offset(x(d), y(it)), accent, surface, dotRadius, ring) }
            }

            // x축: 1일, (이번 달이면) 오늘, 보고 있는 달의 말일
            val labelTop = baseline + gap / 2
            val first = textMeasurer.measure("1일", axisStyle)
            drawText(first, topLeft = Offset(0f, labelTop))
            val endDay = comparison.month.lengthOfMonth()
            val last = textMeasurer.measure("${endDay}일", axisStyle)
            val lastLeft = (x(endDay) + last.size.width / 2f).coerceAtMost(size.width) - last.size.width
            drawText(last, topLeft = Offset(lastLeft, labelTop))
            if (comparison.isOngoing && current.isNotEmpty()) {
                val today = textMeasurer.measure("오늘", axisStyle)
                val tx = x(current.size) - today.size.width / 2f
                // 양 끝 글자와 겹치면 생략
                if (tx > first.size.width + gap && tx + today.size.width < lastLeft - gap) {
                    drawText(today, topLeft = Offset(tx, labelTop))
                }
            }
        }

        // 3) 범례 (선 색 + 글자)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            LegendItem(accent, "${comparison.month.monthValue}월")
            if (previous.isNotEmpty()) LegendItem(ChartColors.Previous, "${comparison.previousMonth.monthValue}월")
        }
    }
}

/** 그래프 위 문구. 두 상태(기본·누르는 중)의 높이를 같게 해 그래프가 들썩이지 않게 한다 */
@Composable
private fun CompareHeader(comparison: MonthComparison, day: Int?, current: List<Long>, previous: List<Long>) {
    Column(Modifier.heightIn(min = 44.dp)) {
        if (day == null) {
            Text(comparison.headline(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "그래프를 누르면 날짜별 금액을 볼 수 있어요",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val month = comparison.month.monthValue
            val previousMonth = comparison.previousMonth.monthValue
            Text(
                current.getOrNull(day - 1)?.let { "${month}월 ${day}일까지 ${it.toWon()}" } ?: "${month}월 ${day}일은 아직이에요",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            previous.getOrNull(day - 1)?.let {
                Text(
                    "${previousMonth}월 ${day}일까지 ${it.toWon()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(width = 16.dp, height = 8.dp)) {
            drawLine(color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        }
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 그래프 안쪽 여백 = 점 반지름(4dp) + 테두리(2dp): 첫날·마지막 날의 점이 잘리지 않게 */
private val ChartInset = 6.dp

/** 손가락 x 위치 → 날짜(1일부터). 그래프를 그릴 때와 같은 여백을 뺀다 */
private fun dayAt(x: Float, width: Float, inset: Float, dayCount: Int): Int {
    val ratio = ((x - inset) / (width - inset * 2).coerceAtLeast(1f)).coerceIn(0f, 1f)
    return (ratio * (dayCount - 1)).roundToInt() + 1
}

/** 누적 금액 목록(index 0 = 1일)을 꺾은선 Path로 */
private fun linePath(values: List<Long>, x: (Int) -> Float, y: (Long) -> Float): Path = Path().apply {
    values.forEachIndexed { index, value ->
        val day = index + 1
        if (index == 0) moveTo(x(day), y(value)) else lineTo(x(day), y(value))
    }
}

/** 선 아래를 바닥선까지 채운 면 (옅은 색으로 칠함) */
private fun areaPath(values: List<Long>, x: (Int) -> Float, y: (Long) -> Float, baseline: Float): Path = Path().apply {
    moveTo(x(1), baseline)
    values.forEachIndexed { index, value -> lineTo(x(index + 1), y(value)) }
    lineTo(x(values.size), baseline)
    close()
}

private fun DrawScope.lineStroke() = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)

/** 바탕색 테두리(2dp)를 두른 점: 선과 겹쳐도 잘 보이게 */
private fun DrawScope.drawDot(center: Offset, color: Color, surface: Color, radius: Float, ring: Float) {
    drawCircle(surface, radius = radius + ring, center = center)
    drawCircle(color, radius = radius, center = center)
}
