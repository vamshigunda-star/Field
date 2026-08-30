package com.vamshi.field.ui.components.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
fun ProgressLineChart(
    dataPoints: List<Float>, // Percentiles or normalized scores (0 to 1)
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    if (dataPoints.size < 2) return

    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp)
            .padding(vertical = 16.dp, horizontal = 8.dp)
    ) {
        val width = size.width
        val height = size.height
        val stepX = width / (dataPoints.size - 1)

        // Draw background grid lines
        for (i in 0..4) {
            val y = height * (i / 4f)
            drawLine(gridColor, Offset(0f, y), Offset(width, y), strokeWidth = 1.dp.toPx())
        }

        val path = Path()
        dataPoints.forEachIndexed { i, score ->
            val x = i * stepX
            val y = height * (1f - score.coerceIn(0f, 1f))
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        drawPath(path, color, style = Stroke(width = 2.dp.toPx()))

        // Points
        dataPoints.forEachIndexed { i, score ->
            val x = i * stepX
            val y = height * (1f - score.coerceIn(0f, 1f))
            drawCircle(color, radius = 4.dp.toPx(), center = Offset(x, y))
            drawCircle(Color.White, radius = 2.dp.toPx(), center = Offset(x, y))
        }
    }
}
