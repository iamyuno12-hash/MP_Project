package com.example.snapledger.ui.stats

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.snapledger.ui.common.toCompactWon
import com.example.snapledger.ui.common.toWon
import kotlin.math.PI

/**
 * 카테고리별 지출 도넛 그래프 (Compose Canvas).
 * 조각은 큰 순서로 12시 방향부터 시계 방향, 조각 사이에 2dp 틈. 가운데에는 총지출.
 * 처음 보이거나 기간이 바뀌면 0.6초 동안 그려지는 애니메이션.
 */
@Composable
fun CategoryDonutChart(shares: List<CategoryShare>, modifier: Modifier = Modifier) {
    val total = shares.sumOf { it.amount }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(shares) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMillis = 600))
    }
    val description = shares.joinToString(", ") { "${it.category.label} ${it.percentText}" }

    Box(modifier.size(200.dp), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = "카테고리별 지출 도넛 그래프. $description" },
        ) {
            if (total <= 0L) return@Canvas
            val strokeWidth = 28.dp.toPx()
            val diameter = size.minDimension - strokeWidth
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)
            // 조각 사이 2dp 틈을 각도로 바꾼 값 (조각이 하나면 틈 없음)
            val gapDegrees = if (shares.size > 1) (2.dp.toPx() / (diameter / 2f) * 180f / PI.toFloat()) else 0f
            val visibleEnd = 360f * progress.value

            var start = 0f
            shares.forEach { share ->
                val sweep = 360f * share.amount / total
                val drawSweep = minOf(sweep - gapDegrees, visibleEnd - start - gapDegrees / 2f)
                if (drawSweep > 0f) {
                    drawArc(
                        color = share.category.chartColor,
                        startAngle = -90f + start + gapDegrees / 2f,
                        sweepAngle = drawSweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
                    )
                }
                start += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("총지출", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(total.toCompactWon(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

/** 도넛 아래 표: 색 점 + 카테고리 + 비중 + 금액 (색만으로 구분하지 않도록 이름과 숫자를 같이 쓴다) */
@Composable
fun CategoryLegend(shares: List<CategoryShare>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        shares.forEach { share ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(share.category.chartColor, CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Text(share.category.label)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        share.percentText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(share.amount.toWon(), fontWeight = FontWeight.SemiBold)
            }
            HorizontalDivider()
        }
    }
}
