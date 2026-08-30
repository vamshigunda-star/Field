package com.vamshi.field.ui.report.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vamshi.field.domain.model.reports.NormBandsForAge
import com.vamshi.field.ui.theme.SportOrange
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

enum class ChartRangeFilter(val label: String) {
    LAST_10("Last 10"),
    LAST_25("Last 25"),
    ONE_YEAR("1 Year"),
    ALL("All");

    fun filter(points: List<ChartPoint>): List<ChartPoint> {
        if (points.isEmpty()) return emptyList()
        return when (this) {
            LAST_10 -> points.takeLast(10)
            LAST_25 -> points.takeLast(25)
            ONE_YEAR -> {
                val oneYearAgo = System.currentTimeMillis() - (365L * 24 * 60 * 60 * 1000)
                val filtered = points.filter { it.date >= oneYearAgo }
                if (filtered.size >= 2) filtered else points.takeLast(10)
            }
            ALL -> points
        }
    }
}

data class ChartPoint(
    val date: Long,
    val rawScore: Double,
    val percentile: Int? = null,
    val classificationLabel: String? = null
)

@Composable
fun NormBandLineChart(
    points: List<ChartPoint>,
    bands: List<NormBandsForAge>,
    modifier: Modifier = Modifier,
    isHigherBetter: Boolean = true,
    unit: String = "",
    lineColor: Color = SportOrange,
    selectedRange: ChartRangeFilter = ChartRangeFilter.ALL,
    superiorColor: Color = if (isSystemInDarkTheme()) Color(0xFF4ADE80) else Color(0xFF1B5E20),
    healthyColor: Color = if (isSystemInDarkTheme()) Color(0xFF60A5FA) else Color(0xFF0D47A1),
    needsColor: Color = if (isSystemInDarkTheme()) Color(0xFFF87171) else Color(0xFFB71C1C)
) {
    val filteredPoints = remember(points, selectedRange) {
        selectedRange.filter(points)
    }

    if (filteredPoints.isEmpty()) return

    val isDark = isSystemInDarkTheme()
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.35f else 0.50f)
    val surfaceColor = MaterialTheme.colorScheme.surface
    val outlineColor = MaterialTheme.colorScheme.outlineVariant

    val textMeasurer = rememberTextMeasurer()
    val shortDateFormatter = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }
    val yearDateFormatter = remember { SimpleDateFormat("MMM yy", Locale.getDefault()) }
    val fullDateFormatter = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    var scrubbedIndex by remember { mutableStateOf<Int?>(null) }

    val avgSuperior = remember(bands) {
        val list = bands.mapNotNull { it.superiorMin }
        if (list.isNotEmpty()) list.average().takeIf { !it.isNaN() } else null
    }
    val avgHealthy = remember(bands) {
        val list = bands.mapNotNull { it.healthyMin }
        if (list.isNotEmpty()) list.average().takeIf { !it.isNaN() } else null
    }

    val axisLabelStyle = remember(onSurfaceVariant) {
        TextStyle(
            color = onSurfaceVariant.copy(alpha = 0.8f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End
        )
    }

    val dateLabelStyle = remember(onSurfaceVariant) {
        TextStyle(
            color = onSurfaceVariant.copy(alpha = 0.8f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
    }

    val benchmarkBadgeStyle = remember(isDark) {
        TextStyle(
            color = if (isDark) Color.White else Color.Black,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Start
        )
    }

    val scrubTooltipStyle = remember(onSurface) {
        TextStyle(
            color = onSurface,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
    }

    val density = LocalDensity.current
    val leftPaddingPx = with(density) { 44.dp.toPx() }
    val rightPaddingPx = with(density) { 16.dp.toPx() }
    val topPaddingPx = with(density) { 24.dp.toPx() }
    val bottomPaddingPx = with(density) { 32.dp.toPx() }
    val dashEffect = remember { PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(260.dp)
            .pointerInput(filteredPoints) {
                detectTapGestures(
                    onPress = { offset ->
                        val chartWidth = size.width.toFloat() - leftPaddingPx - rightPaddingPx
                        if (chartWidth > 0f && filteredPoints.isNotEmpty()) {
                            val touchX = offset.x
                            val nearest = filteredPoints.indices.minByOrNull { i ->
                                val ptX = leftPaddingPx + if (filteredPoints.size <= 1) {
                                    chartWidth / 2f
                                } else {
                                    (i.toFloat() / (filteredPoints.size - 1)) * chartWidth
                                }
                                abs(ptX - touchX)
                            }
                            scrubbedIndex = nearest
                            tryAwaitRelease()
                            scrubbedIndex = null
                        }
                    }
                )
            }
            .pointerInput(filteredPoints) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val chartWidth = size.width.toFloat() - leftPaddingPx - rightPaddingPx
                        if (chartWidth > 0f && filteredPoints.isNotEmpty()) {
                            val touchX = offset.x
                            scrubbedIndex = filteredPoints.indices.minByOrNull { i ->
                                val ptX = leftPaddingPx + if (filteredPoints.size <= 1) {
                                    chartWidth / 2f
                                } else {
                                    (i.toFloat() / (filteredPoints.size - 1)) * chartWidth
                                }
                                abs(ptX - touchX)
                            }
                        }
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val chartWidth = size.width.toFloat() - leftPaddingPx - rightPaddingPx
                        if (chartWidth > 0f && filteredPoints.isNotEmpty()) {
                            val touchX = change.position.x
                            scrubbedIndex = filteredPoints.indices.minByOrNull { i ->
                                val ptX = leftPaddingPx + if (filteredPoints.size <= 1) {
                                    chartWidth / 2f
                                } else {
                                    (i.toFloat() / (filteredPoints.size - 1)) * chartWidth
                                }
                                abs(ptX - touchX)
                            }
                        }
                    },
                    onDragEnd = { scrubbedIndex = null },
                    onDragCancel = { scrubbedIndex = null }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val totalWidth = size.width
            val totalHeight = size.height
            val chartWidth = totalWidth - leftPaddingPx - rightPaddingPx
            val chartHeight = totalHeight - topPaddingPx - bottomPaddingPx

            if (chartWidth <= 0f || chartHeight <= 0f) return@Canvas

            // Compute Y Extents
            val scoreValues = filteredPoints.map { it.rawScore }
            val benchmarkValues = listOfNotNull(avgSuperior, avgHealthy)
            val allValues = scoreValues + benchmarkValues
            val rawMaxY = allValues.maxOrNull() ?: 10.0
            val effectiveMinY = 0.0
            val rangeSpan = rawMaxY.let { if (it <= 0.001) 10.0 else it }
            val yPadding = rangeSpan * 0.15
            val effectiveMaxY = rawMaxY + yPadding
            val effectiveSpan = (effectiveMaxY - effectiveMinY).coerceAtLeast(1.0)

            fun getY(score: Double): Float {
                val ratio = ((score - effectiveMinY) / effectiveSpan).toFloat().coerceIn(0f, 1f)
                return topPaddingPx + (chartHeight * (1f - ratio))
            }

            fun getX(index: Int): Float {
                return if (filteredPoints.size <= 1) {
                    leftPaddingPx + chartWidth / 2f
                } else {
                    leftPaddingPx + ((index.toFloat() / (filteredPoints.size - 1)) * chartWidth)
                }
            }

            // 1. Horizontal Grid Lines & Y-Axis Labels
            val ySteps = 4
            for (i in 0..ySteps) {
                val score = effectiveMinY + (effectiveSpan * (i.toDouble() / ySteps))
                val y = getY(score)

                drawLine(
                    color = gridColor,
                    start = Offset(leftPaddingPx, y),
                    end = Offset(leftPaddingPx + chartWidth, y),
                    strokeWidth = 1.dp.toPx()
                )

                val rounded = score.roundToInt()
                val scoreText = if (abs(score - rounded) < 0.1 || abs(score) >= 100) rounded.toString() else String.format(Locale.US, "%.1f", score)
                val labelText = if (i == ySteps && unit.isNotBlank()) "$scoreText $unit" else scoreText
                val measured = textMeasurer.measure(labelText, axisLabelStyle)
                drawText(
                    textLayoutResult = measured,
                    topLeft = Offset(leftPaddingPx - measured.size.width - 6.dp.toPx(), y - measured.size.height / 2f)
                )
            }

            // 2. Benchmark Lines & Sticky Badges
            if (avgSuperior != null) {
                val supY = getY(avgSuperior)
                drawLine(
                    color = superiorColor.copy(alpha = 0.7f),
                    start = Offset(leftPaddingPx, supY),
                    end = Offset(leftPaddingPx + chartWidth, supY),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = dashEffect
                )
                val badgeText = "Superior (${if (avgSuperior % 1.0 == 0.0) avgSuperior.toInt().toString() else String.format(Locale.US, "%.1f", avgSuperior)})"
                val measuredBadge = textMeasurer.measure(badgeText, benchmarkBadgeStyle.copy(color = superiorColor))
                val pillWidth = measuredBadge.size.width + 10.dp.toPx()
                val pillHeight = measuredBadge.size.height + 4.dp.toPx()
                val pillX = leftPaddingPx + 8.dp.toPx()
                val pillY = (supY - pillHeight - 2.dp.toPx()).coerceAtLeast(topPaddingPx)

                drawRoundRect(
                    color = surfaceColor.copy(alpha = 0.9f),
                    topLeft = Offset(pillX, pillY),
                    size = Size(pillWidth, pillHeight),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                )
                drawText(measuredBadge, topLeft = Offset(pillX + 5.dp.toPx(), pillY + 2.dp.toPx()))
            }

            if (avgHealthy != null) {
                val healthyY = getY(avgHealthy)
                drawLine(
                    color = healthyColor.copy(alpha = 0.7f),
                    start = Offset(leftPaddingPx, healthyY),
                    end = Offset(leftPaddingPx + chartWidth, healthyY),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = dashEffect
                )
                val badgeText = "Healthy (${if (avgHealthy % 1.0 == 0.0) avgHealthy.toInt().toString() else String.format(Locale.US, "%.1f", avgHealthy)})"
                val measuredBadge = textMeasurer.measure(badgeText, benchmarkBadgeStyle.copy(color = healthyColor))
                val pillWidth = measuredBadge.size.width + 10.dp.toPx()
                val pillHeight = measuredBadge.size.height + 4.dp.toPx()
                val pillX = leftPaddingPx + 8.dp.toPx()
                val pillY = (healthyY + 2.dp.toPx()).coerceAtMost(topPaddingPx + chartHeight - pillHeight)

                drawRoundRect(
                    color = surfaceColor.copy(alpha = 0.9f),
                    topLeft = Offset(pillX, pillY),
                    size = Size(pillWidth, pillHeight),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                )
                drawText(measuredBadge, topLeft = Offset(pillX + 5.dp.toPx(), pillY + 2.dp.toPx()))
            }

            // 3. Adaptive Horizontal X-Axis Date Ticks & Vertical Grid
            val timeSpan = (filteredPoints.last().date - filteredPoints.first().date).coerceAtLeast(0L)
            val isLongSpan = timeSpan > (180L * 24 * 60 * 60 * 1000)
            val formatter = if (isLongSpan) yearDateFormatter else shortDateFormatter

            val tickIndices = when {
                filteredPoints.size <= 4 -> filteredPoints.indices.toList()
                else -> {
                    val n = filteredPoints.size
                    listOf(0, n / 3, (n * 2) / 3, n - 1).distinct()
                }
            }

            tickIndices.forEach { idx ->
                val x = getX(idx)
                // Draw vertical grid line
                drawLine(
                    color = gridColor,
                    start = Offset(x, topPaddingPx),
                    end = Offset(x, topPaddingPx + chartHeight),
                    strokeWidth = 1.dp.toPx()
                )

                val dateStr = formatter.format(Date(filteredPoints[idx].date))
                val measuredDate = textMeasurer.measure(dateStr, dateLabelStyle)
                val labelY = topPaddingPx + chartHeight + 8.dp.toPx()

                // Clamp X position within bounds
                var labelX = x - measuredDate.size.width / 2f
                if (idx == 0) {
                    labelX = labelX.coerceAtLeast(leftPaddingPx - 4.dp.toPx())
                } else if (idx == filteredPoints.lastIndex) {
                    labelX = labelX.coerceAtMost(leftPaddingPx + chartWidth - measuredDate.size.width + 4.dp.toPx())
                }

                drawText(measuredDate, topLeft = Offset(labelX, labelY))
            }

            // 4. Data Line & Gradient Area Fill
            if (filteredPoints.isNotEmpty()) {
                val linePath = Path()
                val fillPath = Path()
                filteredPoints.forEachIndexed { index, point ->
                    val x = getX(index)
                    val y = getY(point.rawScore)
                    if (index == 0) {
                        linePath.moveTo(x, y)
                        fillPath.moveTo(x, topPaddingPx + chartHeight)
                        fillPath.lineTo(x, y)
                    } else {
                        val prevX = getX(index - 1)
                        val prevY = getY(filteredPoints[index - 1].rawScore)
                        val ctrlX = prevX + (x - prevX) / 2f
                        linePath.cubicTo(ctrlX, prevY, ctrlX, y, x, y)
                        fillPath.cubicTo(ctrlX, prevY, ctrlX, y, x, y)
                    }
                }
                fillPath.lineTo(getX(filteredPoints.lastIndex), topPaddingPx + chartHeight)
                fillPath.close()

                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            lineColor.copy(alpha = if (isDark) 0.35f else 0.22f),
                            lineColor.copy(alpha = 0.02f)
                        ),
                        startY = topPaddingPx,
                        endY = topPaddingPx + chartHeight
                    )
                )
                drawPath(
                    path = linePath,
                    color = lineColor,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            // 5. Data Points Density-Aware Rendering
            val pointCount = filteredPoints.size
            filteredPoints.forEachIndexed { index, point ->
                val x = getX(index)
                val y = getY(point.rawScore)
                val isLatest = index == filteredPoints.lastIndex

                when {
                    pointCount <= 20 -> {
                        drawCircle(color = lineColor.copy(alpha = 0.30f), radius = 7.dp.toPx(), center = Offset(x, y))
                        drawCircle(color = lineColor, radius = 4.5.dp.toPx(), center = Offset(x, y))
                        drawCircle(color = Color.White, radius = 2.dp.toPx(), center = Offset(x, y))
                    }
                    pointCount in 21..45 -> {
                        drawCircle(color = lineColor, radius = 3.dp.toPx(), center = Offset(x, y))
                        if (isLatest) {
                            drawCircle(color = Color.White, radius = 1.5.dp.toPx(), center = Offset(x, y))
                        }
                    }
                    else -> {
                        // High density (>45 points): only highlight the latest point
                        if (isLatest) {
                            drawCircle(color = lineColor.copy(alpha = 0.35f), radius = 6.dp.toPx(), center = Offset(x, y))
                            drawCircle(color = lineColor, radius = 4.dp.toPx(), center = Offset(x, y))
                            drawCircle(color = Color.White, radius = 2.dp.toPx(), center = Offset(x, y))
                        }
                    }
                }
            }

            // 6. Interactive Touch Scrubbing & Callout Cursor
            scrubbedIndex?.let { idx ->
                if (idx in filteredPoints.indices) {
                    val activePt = filteredPoints[idx]
                    val scrubX = getX(idx)
                    val scrubY = getY(activePt.rawScore)

                    // Vertical Hairline Cursor
                    drawLine(
                        color = lineColor.copy(alpha = 0.8f),
                        start = Offset(scrubX, topPaddingPx),
                        end = Offset(scrubX, topPaddingPx + chartHeight),
                        strokeWidth = 1.5.dp.toPx(),
                        pathEffect = dashEffect
                    )

                    // Highlight Active Point Circle
                    drawCircle(color = lineColor.copy(alpha = 0.40f), radius = 9.dp.toPx(), center = Offset(scrubX, scrubY))
                    drawCircle(color = lineColor, radius = 5.5.dp.toPx(), center = Offset(scrubX, scrubY))
                    drawCircle(color = Color.White, radius = 2.5.dp.toPx(), center = Offset(scrubX, scrubY))

                    // Floating Tooltip Badge
                    val scoreStr = if (activePt.rawScore % 1.0 == 0.0) activePt.rawScore.toInt().toString() else String.format(Locale.US, "%.1f", activePt.rawScore)
                    val dateFormatted = fullDateFormatter.format(Date(activePt.date))
                    val pctStr = activePt.percentile?.let { " · p$it" } ?: ""
                    val tooltipText = "$dateFormatted · $scoreStr $unit$pctStr"

                    val measuredTooltip = textMeasurer.measure(tooltipText, scrubTooltipStyle)
                    val tipPaddingH = 8.dp.toPx()
                    val tipPaddingV = 4.dp.toPx()
                    val tipWidth = measuredTooltip.size.width + (tipPaddingH * 2)
                    val tipHeight = measuredTooltip.size.height + (tipPaddingV * 2)

                    var tipX = scrubX - tipWidth / 2f
                    tipX = tipX.coerceIn(leftPaddingPx, leftPaddingPx + chartWidth - tipWidth)
                    val tipY = (scrubY - tipHeight - 10.dp.toPx()).coerceAtLeast(topPaddingPx)

                    drawRoundRect(
                        color = surfaceColor,
                        topLeft = Offset(tipX, tipY),
                        size = Size(tipWidth, tipHeight),
                        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                    )
                    drawRoundRect(
                        color = outlineColor.copy(alpha = 0.6f),
                        topLeft = Offset(tipX, tipY),
                        size = Size(tipWidth, tipHeight),
                        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                        style = Stroke(width = 1.dp.toPx())
                    )
                    drawText(
                        textLayoutResult = measuredTooltip,
                        topLeft = Offset(tipX + tipPaddingH, tipY + tipPaddingV)
                    )
                }
            }
        }
    }
}
