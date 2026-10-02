package com.vamshi.field.ui.report.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Tiny trend line. Each value is normalized to [0, 1] (1 = best).
 * Empty list renders nothing; size 1 draws a single dot.
 */
@Composable
fun MiniSparkline(
    points: List<Float>,
    modifier: Modifier = Modifier.size(width = 80.dp, height = 24.dp),
    // Neutral on purpose: the line shows shape; zone meaning is carried by the value beside it.
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Canvas(modifier = modifier) {
        if (points.isEmpty()) return@Canvas
        val w = size.width
        val h = size.height
        if (points.size == 1) {
            drawCircle(color, radius = 3.dp.toPx(), center = Offset(w / 2f, h / 2f))
            return@Canvas
        }
        val stepX = w / (points.size - 1)
        val path = Path()
        points.forEachIndexed { i, v ->
            val x = i * stepX
            val y = h * (1f - v.coerceIn(0f, 1f))
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(width = 1.5.dp.toPx()))
        // last point dot
        val last = points.last()
        drawCircle(color, radius = 2.5.dp.toPx(), center = Offset(w, h * (1f - last.coerceIn(0f, 1f))))
    }
}
