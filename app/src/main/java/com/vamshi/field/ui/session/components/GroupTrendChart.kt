package com.vamshi.field.ui.session.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
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
import com.vamshi.field.ui.theme.ElectricBlue
import com.vamshi.field.ui.theme.SportOrange
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.ui.platform.LocalLocale

data class GroupTrendLinePoint(
    val date: Long,
    val averagePercentile: Float,
    val isCurrentSession: Boolean = false,
)

@Composable
fun GroupTrendChart(
    points: List<Pair<Long, Float>>,
    modifier: Modifier = Modifier,
    currentSessionDate: Long? = null,
    unit: String = "",
    initiallyExpanded: Boolean = true,
) {
    var isExpanded by remember { mutableStateOf(initiallyExpanded) }
    val isDark = isSystemInDarkTheme()

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(1.dp),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.35f else 0.50f)
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Header: Title, Subtitle, Delta KPI Badge, and Collapse/Expand Toggle
            TrendChartHeader(
                points = points,
                isExpanded = isExpanded,
                onToggle = { isExpanded = !isExpanded }
            )

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                ) {
                    if (points.size < 2) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ShowChart,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.size(32.dp)
                                )
                                Text(
                                    text = "Need at least 2 sessions for historical trend analysis",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        val trendPoints = remember(points, currentSessionDate) {
                            points.map { (date, pctile) ->
                                GroupTrendLinePoint(
                                    date = date,
                                    averagePercentile = pctile,
                                    isCurrentSession = (currentSessionDate != null) && (date == currentSessionDate)
                                )
                            }
                        }

                        GroupTrendCanvasLinePlot(
                            points = trendPoints,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(210.dp)
                        )

                        Spacer(Modifier.height(10.dp))

                        // Chart Legend & Benchmark Guide
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                LegendItem(
                                    color = SportOrange,
                                    label = "Group Mean"
                                )
                                LegendItem(
                                    color = ElectricBlue,
                                    label = "Current Event Pin"
                                )
                            }
                            Text(
                                text = "50% Median = Age/Sex Norm Benchmark Reference",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrendChartHeader(
    points: List<Pair<Long, Float>>,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    val deltaInfo = remember(points) {
        if (points.size >= 2) {
            val latest = points.last().second
            val previous = points[points.size - 2].second
            val delta = latest - previous
            Triple(latest, delta, delta >= 0)
        } else null
    }

    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "trendChevron"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Group Historical Progression",
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.5.sp),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Cohort mean percentile across testing events",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (deltaInfo != null) {
                val (_, delta, isPositive) = deltaInfo
                val deltaAbs = abs(delta)
                val isZero = deltaAbs < 0.1f

                val (badgeBg, badgeColor, icon) = when {
                    isZero -> Triple(
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.onSurfaceVariant,
                        Icons.AutoMirrored.Filled.TrendingFlat
                    )
                    isPositive -> Triple(
                        Color(0xFFDCFCE7),
                        Color(0xFF15803D),
                        Icons.AutoMirrored.Filled.TrendingUp
                    )
                    else -> Triple(
                        Color(0xFFFEE2E2),
                        Color(0xFFB91C1C),
                        Icons.AutoMirrored.Filled.TrendingDown
                    )
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = badgeBg
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = badgeColor,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = if (isZero) "Stable" else String.format(LocalLocale.current.platformLocale, "%s%.1f%%", if (isPositive) "+" else "-", deltaAbs),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = badgeColor
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(18.dp)
                        .rotate(chevronRotation)
                )
            }
        }
    }
}

@Composable
private fun GroupTrendCanvasLinePlot(
    points: List<GroupTrendLinePoint>,
    modifier: Modifier = Modifier,
) {
    if (points.isEmpty()) return

    val isDark = isSystemInDarkTheme()
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.35f else 0.50f)
    val surfaceColor = MaterialTheme.colorScheme.surface
    val outlineColor = MaterialTheme.colorScheme.outlineVariant
    val textMeasurer = rememberTextMeasurer()

    val shortDateFormatter = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }
    val fullDateFormatter = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    var scrubbedIndex by remember { mutableStateOf<Int?>(null) }

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
            color = if (isDark) Color(0xFF93C5FD) else Color(0xFF1D4ED8),
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
            .pointerInput(points) {
                detectTapGestures(
                    onPress = { offset ->
                        val chartWidth = size.width.toFloat() - leftPaddingPx - rightPaddingPx
                        if (chartWidth > 0f && points.isNotEmpty()) {
                            val touchX = offset.x
                            val nearest = points.indices.minByOrNull { i ->
                                val ptX = leftPaddingPx + if (points.size <= 1) {
                                    chartWidth / 2f
                                } else {
                                    (i.toFloat() / (points.size - 1)) * chartWidth
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
            .pointerInput(points) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val chartWidth = size.width.toFloat() - leftPaddingPx - rightPaddingPx
                        if (chartWidth > 0f && points.isNotEmpty()) {
                            val touchX = offset.x
                            scrubbedIndex = points.indices.minByOrNull { i ->
                                val ptX = leftPaddingPx + if (points.size <= 1) {
                                    chartWidth / 2f
                                } else {
                                    (i.toFloat() / (points.size - 1)) * chartWidth
                                }
                                abs(ptX - touchX)
                            }
                        }
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val chartWidth = size.width.toFloat() - leftPaddingPx - rightPaddingPx
                        if (chartWidth > 0f && points.isNotEmpty()) {
                            val touchX = change.position.x
                            scrubbedIndex = points.indices.minByOrNull { i ->
                                val ptX = leftPaddingPx + if (points.size <= 1) {
                                    chartWidth / 2f
                                } else {
                                    (i.toFloat() / (points.size - 1)) * chartWidth
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

            // Fixed Y-scale for group percentile: 0% to 100%
            val effectiveMinY = 0.0
            val effectiveSpan = 100.0

            fun getY(score: Float): Float {
                val ratio = ((score - effectiveMinY) / effectiveSpan).toFloat().coerceIn(0f, 1f)
                return topPaddingPx + (chartHeight * (1f - ratio))
            }

            fun getX(index: Int): Float {
                return if (points.size <= 1) {
                    leftPaddingPx + chartWidth / 2f
                } else {
                    leftPaddingPx + ((index.toFloat() / (points.size - 1)) * chartWidth)
                }
            }

            // 1. Horizontal Grid Lines & Y-Axis Percentage Labels
            val ySteps = 4
            for (i in 0..ySteps) {
                val score = effectiveMinY + (effectiveSpan * (i.toDouble() / ySteps))
                val y = getY(score.toFloat())

                drawLine(
                    color = gridColor,
                    start = Offset(leftPaddingPx, y),
                    end = Offset(leftPaddingPx + chartWidth, y),
                    strokeWidth = 1.dp.toPx()
                )

                val labelText = "${score.toInt()}%"
                val measured = textMeasurer.measure(labelText, axisLabelStyle)
                drawText(
                    textLayoutResult = measured,
                    topLeft = Offset(leftPaddingPx - measured.size.width - 6.dp.toPx(), y - measured.size.height / 2f)
                )
            }

            // 2. Benchmark 50% Median Reference Line with Badge
            val medianY = getY(50f)
            drawLine(
                color = ElectricBlue.copy(alpha = 0.65f),
                start = Offset(leftPaddingPx, medianY),
                end = Offset(leftPaddingPx + chartWidth, medianY),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = dashEffect
            )
            val badgeText = "50% Median"
            val measuredBadge = textMeasurer.measure(badgeText, benchmarkBadgeStyle)
            val pillWidth = measuredBadge.size.width + 10.dp.toPx()
            val pillHeight = measuredBadge.size.height + 4.dp.toPx()
            val pillX = leftPaddingPx + 8.dp.toPx()
            val pillY = (medianY - pillHeight - 2.dp.toPx()).coerceAtLeast(topPaddingPx)

            drawRoundRect(
                color = surfaceColor.copy(alpha = 0.90f),
                topLeft = Offset(pillX, pillY),
                size = Size(pillWidth, pillHeight),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
            )
            drawText(measuredBadge, topLeft = Offset(pillX + 5.dp.toPx(), pillY + 2.dp.toPx()))

            // 3. Adaptive Horizontal X-Axis Date Ticks & Vertical Grid
            val tickIndices = when {
                points.size <= 4 -> points.indices.toList()
                else -> {
                    val n = points.size
                    listOf(0, n / 3, (n * 2) / 3, n - 1).distinct()
                }
            }

            tickIndices.forEach { idx ->
                val x = getX(idx)
                drawLine(
                    color = gridColor,
                    start = Offset(x, topPaddingPx),
                    end = Offset(x, topPaddingPx + chartHeight),
                    strokeWidth = 1.dp.toPx()
                )

                val dateStr = shortDateFormatter.format(Date(points[idx].date))
                val measuredDate = textMeasurer.measure(dateStr, dateLabelStyle)
                val labelY = topPaddingPx + chartHeight + 8.dp.toPx()

                var labelX = x - measuredDate.size.width / 2f
                if (idx == 0) {
                    labelX = labelX.coerceAtLeast(leftPaddingPx - 4.dp.toPx())
                } else if (idx == points.lastIndex) {
                    labelX = labelX.coerceAtMost(leftPaddingPx + chartWidth - measuredDate.size.width + 4.dp.toPx())
                }

                drawText(measuredDate, topLeft = Offset(labelX, labelY))
            }

            // 4. Data Line & Gradient Area Fill
            if (points.isNotEmpty()) {
                val linePath = Path()
                val fillPath = Path()
                points.forEachIndexed { index, point ->
                    val x = getX(index)
                    val y = getY(point.averagePercentile)
                    if (index == 0) {
                        linePath.moveTo(x, y)
                        fillPath.moveTo(x, topPaddingPx + chartHeight)
                        fillPath.lineTo(x, y)
                    } else {
                        val prevX = getX(index - 1)
                        val prevY = getY(points[index - 1].averagePercentile)
                        val ctrlX = prevX + (x - prevX) / 2f
                        linePath.cubicTo(ctrlX, prevY, ctrlX, y, x, y)
                        fillPath.cubicTo(ctrlX, prevY, ctrlX, y, x, y)
                    }
                }

                fillPath.lineTo(getX(points.lastIndex), topPaddingPx + chartHeight)
                fillPath.close()

                val fillBrush = Brush.verticalGradient(
                    colors = listOf(SportOrange.copy(alpha = 0.25f), Color.Transparent),
                    startY = topPaddingPx,
                    endY = topPaddingPx + chartHeight
                )
                drawPath(fillPath, brush = fillBrush)

                drawPath(
                    path = linePath,
                    color = SportOrange,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )

                // 5. Data Points & Active Pin Halos
                points.forEachIndexed { index, point ->
                    val x = getX(index)
                    val y = getY(point.averagePercentile)

                    if (point.isCurrentSession) {
                        drawCircle(
                            color = ElectricBlue.copy(alpha = 0.35f),
                            radius = 9.dp.toPx(),
                            center = Offset(x, y)
                        )
                        drawCircle(
                            color = ElectricBlue,
                            radius = 5.dp.toPx(),
                            center = Offset(x, y)
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 2.5.dp.toPx(),
                            center = Offset(x, y)
                        )
                    } else {
                        drawCircle(
                            color = SportOrange,
                            radius = 4.dp.toPx(),
                            center = Offset(x, y)
                        )
                        drawCircle(
                            color = surfaceColor,
                            radius = 2.dp.toPx(),
                            center = Offset(x, y)
                        )
                    }
                }
            }

            // 6. Interactive Scrub Crosshair & Floating Tooltip
            scrubbedIndex?.let { idx ->
                if (idx in points.indices) {
                    val pt = points[idx]
                    val x = getX(idx)
                    val y = getY(pt.averagePercentile)

                    drawLine(
                        color = onSurface.copy(alpha = 0.6f),
                        start = Offset(x, topPaddingPx),
                        end = Offset(x, topPaddingPx + chartHeight),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = dashEffect
                    )

                    drawCircle(
                        color = SportOrange.copy(alpha = 0.3f),
                        radius = 11.dp.toPx(),
                        center = Offset(x, y)
                    )
                    drawCircle(
                        color = SportOrange,
                        radius = 5.5.dp.toPx(),
                        center = Offset(x, y)
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 3.dp.toPx(),
                        center = Offset(x, y)
                    )

                    val dateText = fullDateFormatter.format(Date(pt.date))
                    val pctileText = "${pt.averagePercentile.roundToInt()}% Mean"
                    val tooltipLine = if (pt.isCurrentSession) "$pctileText • Active Event" else "$pctileText • $dateText"

                    val measuredTooltip = textMeasurer.measure(tooltipLine, scrubTooltipStyle)
                    val tooltipPadding = 8.dp.toPx()
                    val tooltipW = measuredTooltip.size.width + tooltipPadding * 2
                    val tooltipH = measuredTooltip.size.height + tooltipPadding

                    var tooltipX = x - tooltipW / 2f
                    tooltipX = tooltipX.coerceIn(leftPaddingPx, leftPaddingPx + chartWidth - tooltipW)

                    val tooltipY = if (y - tooltipH - 12.dp.toPx() >= topPaddingPx) {
                        y - tooltipH - 12.dp.toPx()
                    } else {
                        y + 12.dp.toPx()
                    }

                    drawRoundRect(
                        color = surfaceColor,
                        topLeft = Offset(tooltipX, tooltipY),
                        size = Size(tooltipW, tooltipH),
                        cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                    )
                    drawRoundRect(
                        color = outlineColor.copy(alpha = 0.8f),
                        topLeft = Offset(tooltipX, tooltipY),
                        size = Size(tooltipW, tooltipH),
                        cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
                        style = Stroke(width = 1.dp.toPx())
                    )

                    drawText(
                        textLayoutResult = measuredTooltip,
                        topLeft = Offset(tooltipX + tooltipPadding, tooltipY + tooltipPadding / 2f)
                    )
                }
            }
        }
    }
}

@Composable
private fun LegendItem(
    color: Color,
    label: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, RoundedCornerShape(3.dp))
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold
        )
    }
}
