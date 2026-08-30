package com.vamshi.field.ui.components.charts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vamshi.field.domain.usecase.testing.AthleteRadarData
import com.vamshi.field.domain.usecase.testing.RadarAxisScore
import com.vamshi.field.ui.theme.AquaCyan
import com.vamshi.field.ui.theme.ElectricBlue
import com.vamshi.field.ui.theme.PerformanceGreenText
import com.vamshi.field.ui.theme.PerformanceGreenTextDark
import com.vamshi.field.ui.theme.PerformanceGreyText
import com.vamshi.field.ui.theme.PerformanceGreyTextDark
import com.vamshi.field.ui.theme.PerformanceRedText
import com.vamshi.field.ui.theme.PerformanceRedTextDark
import com.vamshi.field.ui.theme.PerformanceYellowText
import com.vamshi.field.ui.theme.PerformanceYellowTextDark
import com.vamshi.field.ui.theme.getCategoryVisual
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Modern, High-Polish Athletic Radar / Skill Matrix Chart.
 *
 * Features:
 * - 100% Theme Harmonized with Field brand design system (ElectricBlue, AquaCyan, DynamicOrange).
 * - Smooth entrance interpolation animation via [Animatable].
 * - Interactive spoke & vertex tap inspection with haptic feedback.
 * - Multi-layered concentric depth bands & distinct 50% Median benchmark ring.
 * - Breathing glow effect for top strength vertex.
 * - Angle-aware perimeter typography & category visual integration.
 * - Polished Bento summary footer with quick strength/focus breakdown.
 */
@Composable
fun RadarChart(
    data: AthleteRadarData,
    modifier: Modifier = Modifier,
    onCategoryClick: ((categoryId: String) -> Unit)? = null
) {
    val scores = data.axisScores
    if (scores.size < 3) return

    val isDark = isSystemInDarkTheme()
    val textMeasurer = rememberTextMeasurer()
    val haptic = LocalHapticFeedback.current

    // Theme Color Tokens
    val cardBackground = MaterialTheme.colorScheme.surface
    val textColor = MaterialTheme.colorScheme.onSurface
    val secondaryText = MaterialTheme.colorScheme.onSurfaceVariant
    val primaryColor = MaterialTheme.colorScheme.primary
    val outlineVariant = MaterialTheme.colorScheme.outlineVariant

    // Canonical Performance Status Colors
    val green = if (isDark) PerformanceGreenTextDark else PerformanceGreenText
    val yellow = if (isDark) PerformanceYellowTextDark else PerformanceYellowText
    val red = if (isDark) PerformanceRedTextDark else PerformanceRedText
    val grey = if (isDark) PerformanceGreyTextDark else PerformanceGreyText

    // Modern Grid & Axis Styling Tokens
    val gridStrokeColor = if (isDark) Color(0xFF334155) else outlineVariant.copy(alpha = 0.8f)
    val outerBoundaryColor = if (isDark) Color(0xFF475569) else Color(0xFF94A3B8)
    val medianRingColor = if (isDark) AquaCyan.copy(alpha = 0.85f) else ElectricBlue.copy(alpha = 0.75f)
    val spokeColor = if (isDark) Color(0xFF334155).copy(alpha = 0.9f) else Color(0xFFE2E8F0)

    val gradientPrimary = if (isDark) ElectricBlue else Color(0xFF1A73E8)
    val gradientAccent = if (isDark) AquaCyan else Color(0xFF00B4D8)

    // Interactive Selection State
    var selectedIndex by remember(data.individualId) { mutableStateOf<Int?>(null) }

    // Fluid Entrance Animation
    val animProgress = remember(data.individualId) { Animatable(0f) }
    LaunchedEffect(data.individualId, scores) {
        animProgress.snapTo(0f)
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 750, easing = FastOutSlowInEasing)
        )
    }

    // Breathing Pulse for Top Strength Marker
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 10f,
        targetValue = 15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    // Statistical Indices
    val strongestIndex = remember(scores) {
        scores
            .filter { it.testCount > 0 }
            .maxByOrNull { it.normalizedScore }
            ?.let { strongest -> scores.indexOf(strongest) }
            ?: -1
    }

    val focusIndex = remember(scores) {
        val scored = scores.filter { it.testCount > 0 }
        if (scored.size >= 2) {
            val maxScore = scored.maxOf { it.normalizedScore }
            val minScore = scored.minOf { it.normalizedScore }
            if (minScore < maxScore) {
                scores.indexOfFirst { it.testCount > 0 && it.normalizedScore == minScore }
            } else -1
        } else -1
    }

    val average = remember(scores) {
        val tested = scores.filter { it.testCount > 0 }
        if (tested.isEmpty()) {
            0
        } else {
            (tested.map { it.normalizedScore }.average() * 100).roundToInt()
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = cardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp, bottom = 18.dp, start = 18.dp, end = 18.dp)
        ) {
            // ---------------------------------------------------------
            // 1. HEADER (Title, Subtitle & Athletic Average Badge)
            // ---------------------------------------------------------
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(ElectricBlue)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Skill Matrix",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                            color = textColor
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${scores.size}-dimension athletic percentile profile",
                        style = MaterialTheme.typography.bodySmall,
                        color = secondaryText
                    )
                }

                // Average Score Pill
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isDark) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else Color(0xFFEEF4FF),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.TrendingUp,
                            contentDescription = null,
                            tint = primaryColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${average}th",
                            color = primaryColor,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "avg",
                            color = secondaryText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ---------------------------------------------------------
            // 2. RADAR CANVAS (Interactive, Animated & Theme-Aligned)
            // ---------------------------------------------------------
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(390.dp)
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(scores, data.individualId) {
                            detectTapGestures { tapOffset ->
                                val n = scores.size
                                if (n < 3) return@detectTapGestures

                                val center = Offset(
                                    x = size.width / 2f,
                                    y = size.height / 2f
                                )
                                val labelSpace = 48.dp.toPx()
                                val radius = (min(size.width, size.height) / 2f) - labelSpace
                                if (radius <= 0f) return@detectTapGestures

                                val dx = tapOffset.x - center.x
                                val dy = tapOffset.y - center.y
                                val dist = sqrt(dx * dx + dy * dy)

                                // If tapped within reasonable interaction radius
                                if (dist <= radius * 1.35f && dist >= 10.dp.toPx()) {
                                    // Calculate angle from center (-PI to +PI)
                                    var touchAngle = atan2(dy, dx) + (Math.PI.toFloat() / 2f)
                                    if (touchAngle < 0) touchAngle += (2f * Math.PI.toFloat())

                                    val angleStep = (2f * Math.PI.toFloat()) / n
                                    val closestIndex = ((touchAngle + (angleStep / 2f)) / angleStep).toInt() % n

                                    if (selectedIndex == closestIndex) {
                                        selectedIndex = null
                                    } else {
                                        selectedIndex = closestIndex
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                } else {
                                    selectedIndex = null
                                }
                            }
                        }
                ) {
                    val n = scores.size
                    if (n < 3) return@Canvas

                    val center = Offset(
                        x = size.width / 2f,
                        y = size.height / 2f
                    )

                    // Generous clearance for clean perimeter labels
                    val labelSpace = 48.dp.toPx()
                    val radius = (min(size.width, size.height) / 2f) - labelSpace
                    if (radius <= 10f) return@Canvas

                    val angleStep = (2f * Math.PI.toFloat()) / n
                    fun angle(index: Int): Float = index * angleStep - Math.PI.toFloat() / 2f

                    fun point(index: Int, value: Float): Offset {
                        val a = angle(index)
                        val clamped = value.coerceIn(0f, 1f)
                        return Offset(
                            x = center.x + radius * clamped * cos(a),
                            y = center.y + radius * clamped * sin(a)
                        )
                    }

                    fun buildPolygonPath(level: Float): Path {
                        val path = Path()
                        repeat(n) { i ->
                            val p = point(i, level)
                            if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                        }
                        path.close()
                        return path
                    }

                    // A. Concentric Depth Bands & High-Tech Polygon Grid
                    val levels = listOf(0.25f, 0.50f, 0.75f, 1.00f)

                    // Subtle filled polygon depth bands
                    levels.asReversed().forEachIndexed { revIdx, level ->
                        val polyPath = buildPolygonPath(level)
                        val bandAlpha = when (revIdx) {
                            0 -> if (isDark) 0.05f else 0.03f
                            1 -> if (isDark) 0.08f else 0.05f
                            2 -> if (isDark) 0.12f else 0.07f
                            else -> if (isDark) 0.16f else 0.09f
                        }
                        drawPath(
                            path = polyPath,
                            color = primaryColor.copy(alpha = bandAlpha),
                            style = Fill
                        )
                    }

                    // Grid Ring Strokes
                    levels.forEachIndexed { levelIndex, level ->
                        val polyPath = buildPolygonPath(level)
                        val isOuter = levelIndex == 3
                        val isMedian = levelIndex == 1

                        when {
                            isOuter -> {
                                // 100% Boundary
                                drawPath(
                                    path = polyPath,
                                    color = outerBoundaryColor,
                                    style = Stroke(width = 1.6.dp.toPx())
                                )
                            }
                            isMedian -> {
                                // 50% Median Benchmark Ring
                                drawPath(
                                    path = polyPath,
                                    color = medianRingColor,
                                    style = Stroke(
                                        width = 1.8.dp.toPx(),
                                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
                                    )
                                )
                            }
                            else -> {
                                // 25% and 75% subtle rings
                                drawPath(
                                    path = polyPath,
                                    color = gridStrokeColor,
                                    style = Stroke(
                                        width = 1.dp.toPx(),
                                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))
                                    )
                                )
                            }
                        }
                    }

                    // B. Radial Spokes & Anchor Tips
                    repeat(n) { i ->
                        val end = point(i, 1f)
                        val isSelected = selectedIndex == i

                        drawLine(
                            color = if (isSelected) primaryColor else spokeColor,
                            start = center,
                            end = end,
                            strokeWidth = if (isSelected) 2.2.dp.toPx() else 1.dp.toPx()
                        )

                        // Outer anchor node
                        drawCircle(
                            color = if (isSelected) primaryColor else outerBoundaryColor.copy(alpha = 0.8f),
                            radius = if (isSelected) 3.5.dp.toPx() else 2.dp.toPx(),
                            center = end
                        )
                    }

                    // Selected Sector Highlight Beam
                    selectedIndex?.let { selIdx ->
                        val beamEnd = point(selIdx, 1f)
                        val beamRadius = maxOf(radius * 1.1f, 1f)
                        drawLine(
                            brush = Brush.radialGradient(
                                colors = listOf(primaryColor.copy(alpha = 0.5f), Color.Transparent),
                                center = center,
                                radius = beamRadius
                            ),
                            start = center,
                            end = beamEnd,
                            strokeWidth = 14.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }

                    // C. Data Area Geometry & Gradient Fill
                    val currentProgress = animProgress.value
                    if (currentProgress > 0.005f) {
                        val dataPath = Path()
                        scores.forEachIndexed { index, score ->
                            val rawVal = if (score.testCount > 0) score.normalizedScore else 0.04f
                            val animatedVal = maxOf(rawVal * currentProgress, 0.03f)
                            val p = point(index, animatedVal)
                            if (index == 0) dataPath.moveTo(p.x, p.y) else dataPath.lineTo(p.x, p.y)
                        }
                        dataPath.close()

                        // Glowing Radial Gradient Mesh Fill (safeguard radius > 0)
                        val dataRadius = maxOf(radius * currentProgress, 1f)
                        drawPath(
                            path = dataPath,
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    gradientAccent.copy(alpha = if (isDark) 0.40f else 0.28f),
                                    gradientPrimary.copy(alpha = if (isDark) 0.25f else 0.16f),
                                    Color.Transparent
                                ),
                                center = center,
                                radius = dataRadius
                            )
                        )

                        // D. Multi-Segment Gradient Data Outline
                        scores.forEachIndexed { index, _ ->
                            val nextIndex = (index + 1) % n
                            val v1 = maxOf((if (scores[index].testCount > 0) scores[index].normalizedScore else 0.04f) * currentProgress, 0.03f)
                            val v2 = maxOf((if (scores[nextIndex].testCount > 0) scores[nextIndex].normalizedScore else 0.04f) * currentProgress, 0.03f)

                            val p1 = point(index, v1)
                            val p2 = point(nextIndex, v2)

                            val dist = sqrt((p1.x - p2.x) * (p1.x - p2.x) + (p1.y - p2.y) * (p1.y - p2.y))
                            val c1 = if (scores[index].testCount > 0) performanceColor(scores[index].normalizedScore, green, yellow, red) else grey
                            val c2 = if (scores[nextIndex].testCount > 0) performanceColor(scores[nextIndex].normalizedScore, green, yellow, red) else grey

                            if (dist > 1.5f) {
                                drawLine(
                                    brush = Brush.linearGradient(
                                        colors = listOf(c1, c2),
                                        start = p1,
                                        end = p2
                                    ),
                                    start = p1,
                                    end = p2,
                                    strokeWidth = 3.dp.toPx(),
                                    cap = StrokeCap.Round
                                )
                            } else {
                                drawLine(
                                    color = c1,
                                    start = p1,
                                    end = p2,
                                    strokeWidth = 3.dp.toPx(),
                                    cap = StrokeCap.Round
                                )
                            }
                        }
                    }

                    // E. Vertices, Highlights & Breathing Pulses
                    scores.forEachIndexed { index, score ->
                        val v = maxOf((if (score.testCount > 0) score.normalizedScore else 0.04f) * currentProgress, 0.03f)
                        val p = point(index, v)
                        val isSelected = selectedIndex == index

                        when {
                            index == strongestIndex -> {
                                // Animated breathing glow for top strength
                                val glowRadius = maxOf(pulseScale.dp.toPx(), 1f)
                                drawCircle(
                                    color = green.copy(alpha = pulseAlpha),
                                    radius = glowRadius,
                                    center = p
                                )
                                drawCircle(color = Color.White, radius = 6.dp.toPx(), center = p)
                                drawCircle(color = green, radius = 4.5.dp.toPx(), center = p)
                            }
                            index == focusIndex -> {
                                // Glowing amber/red core for focus area
                                drawCircle(
                                    color = red.copy(alpha = 0.25f),
                                    radius = 11.dp.toPx(),
                                    center = p
                                )
                                drawCircle(color = Color.White, radius = 5.5.dp.toPx(), center = p)
                                drawCircle(color = red, radius = 4.dp.toPx(), center = p)
                            }
                            isSelected -> {
                                // Selected spoke vertex halo
                                drawCircle(
                                    color = primaryColor.copy(alpha = 0.35f),
                                    radius = 12.dp.toPx(),
                                    center = p
                                )
                                drawCircle(color = Color.White, radius = 6.5.dp.toPx(), center = p)
                                drawCircle(color = primaryColor, radius = 4.5.dp.toPx(), center = p)
                            }
                            score.testCount > 0 -> {
                                val pointColor = performanceColor(score.normalizedScore, green, yellow, red)
                                drawCircle(color = Color.White, radius = 4.5.dp.toPx(), center = p)
                                drawCircle(color = pointColor, radius = 3.2.dp.toPx(), center = p)
                            }
                            else -> {
                                drawCircle(color = grey.copy(alpha = 0.6f), radius = 2.5.dp.toPx(), center = p)
                            }
                        }
                    }

                    // F. Perimeter Typography & Category Labels
                    val labelStyle = TextStyle(
                        color = textColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        lineHeight = 13.sp
                    )

                    scores.forEachIndexed { index, score ->
                        val a = angle(index)
                        val labelRadius = radius + 22.dp.toPx()
                        val labelCenter = Offset(
                            center.x + labelRadius * cos(a),
                            center.y + labelRadius * sin(a)
                        )

                        val label = formatRadarLabel(score.label)
                        val scoreText = if (score.testCount > 0) {
                            "${(score.normalizedScore * 100).roundToInt()}%"
                        } else {
                            "—"
                        }

                        val isSelected = selectedIndex == index
                        val scoreColor = when {
                            isSelected -> primaryColor
                            index == strongestIndex -> green
                            index == focusIndex -> red
                            score.testCount > 0 -> performanceColor(score.normalizedScore, green, yellow, red)
                            else -> secondaryText.copy(alpha = 0.5f)
                        }

                        val nameLayout = textMeasurer.measure(
                            text = label,
                            style = if (isSelected) labelStyle.copy(fontWeight = FontWeight.ExtraBold, color = primaryColor) else labelStyle
                        )
                        val scoreLayout = textMeasurer.measure(
                            text = scoreText,
                            style = TextStyle(
                                color = scoreColor,
                                fontSize = if (isSelected) 12.sp else 11.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        )

                        val totalHeight = nameLayout.size.height + scoreLayout.size.height + 2.dp.toPx()
                        val left = labelCenter.x - nameLayout.size.width / 2f
                        val top = labelCenter.y - totalHeight / 2f

                        drawText(
                            textLayoutResult = nameLayout,
                            topLeft = Offset(left, top)
                        )

                        drawText(
                            textLayoutResult = scoreLayout,
                            topLeft = Offset(
                                labelCenter.x - scoreLayout.size.width / 2f,
                                top + nameLayout.size.height + 2.dp.toPx()
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ---------------------------------------------------------
            // 3. DYNAMIC INTERACTIVE INSPECTOR OR BENTO FOOTER
            // ---------------------------------------------------------
            AnimatedVisibility(
                visible = selectedIndex != null,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 }
            ) {
                selectedIndex?.let { selIdx ->
                    val selScore = scores.getOrNull(selIdx)
                    if (selScore != null) {
                        val visual = getCategoryVisual(selScore.label, selScore.axis)
                        val classification = getScoreClassification(selScore.normalizedScore, selScore.testCount)
                        val scorePct = if (selScore.testCount > 0) "${(selScore.normalizedScore * 100).roundToInt()}th Percentile" else "Not Tested"

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    selScore.categoryId?.let { onCategoryClick?.invoke(it) }
                                },
                            shape = RoundedCornerShape(16.dp),
                            color = if (isDark) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f) else Color(0xFFF1F5F9),
                            border = CardDefaults.outlinedCardBorder()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Category Icon Badge
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(visual.containerColor.copy(alpha = if (isDark) 0.35f else 0.9f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = visual.icon,
                                        contentDescription = null,
                                        tint = visual.accentColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = selScore.label,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = textColor
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = scorePct,
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = if (selScore.testCount > 0) performanceColor(selScore.normalizedScore, green, yellow, red) else secondaryText
                                        )
                                        Text(
                                            text = " • $classification",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = secondaryText
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = { selectedIndex = null },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close",
                                        tint = secondaryText,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Maps normalized score to canonical classification label.
 */
private fun getScoreClassification(normalizedScore: Float, testCount: Int): String {
    if (testCount <= 0) return "Untested"
    val pct = normalizedScore * 100f
    return when {
        pct >= 85f -> "Elite"
        pct >= 70f -> "Advanced"
        pct >= 45f -> "Proficient"
        pct >= 25f -> "Developing"
        else -> "Needs Focus"
    }
}

/**
 * Maps percentile performance (0.0 to 1.0) to canonical ALearning text/foreground performance colors:
 * - >= 75% (0.75f): Green
 * - 40% - 74% (0.40f - 0.74f): Yellow/Amber
 * - < 40% (< 0.40f): Red
 */
private fun performanceColor(
    normalizedScore: Float,
    green: Color,
    yellow: Color,
    red: Color
): Color {
    val percentage = normalizedScore * 100f
    return when {
        percentage >= 75f -> green
        percentage >= 40f -> yellow
        else -> red
    }
}

/**
 * Formats standard 10-dimension category names into balanced 2-line wraps where appropriate.
 */
private fun formatRadarLabel(name: String): String {
    return when (name.trim()) {
        "Cardiovascular Endurance" -> "Cardio\nEndurance"
        "Muscular Strength" -> "Muscular\nStrength"
        "Muscular Endurance" -> "Muscular\nEndurance"
        "Reaction Time" -> "Reaction\nTime"
        else -> {
            if (name.length > 12 && name.contains(" ")) {
                val words = name.split(" ")
                val mid = (words.size + 1) / 2
                words.take(mid).joinToString(" ") + "\n" + words.drop(mid).joinToString(" ")
            } else {
                name
            }
        }
    }
}


