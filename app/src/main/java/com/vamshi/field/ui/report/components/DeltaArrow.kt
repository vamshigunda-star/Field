package com.vamshi.field.ui.report.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.isSystemInDarkTheme
import com.vamshi.field.ui.theme.PerformanceGreenText
import com.vamshi.field.ui.theme.PerformanceGreenTextDark
import com.vamshi.field.ui.theme.PerformanceRedText
import com.vamshi.field.ui.theme.PerformanceRedTextDark

@Composable
fun DeltaArrow(deltaPercentile: Int?, modifier: Modifier = Modifier) {
    val isDark = isSystemInDarkTheme()
    val neutralColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    val positiveColor = if (isDark) PerformanceGreenTextDark else PerformanceGreenText
    val negativeColor = if (isDark) PerformanceRedTextDark else PerformanceRedText

    if (deltaPercentile == null) {
        Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Icon(
                Icons.AutoMirrored.Filled.TrendingFlat,
                contentDescription = null,
                tint = neutralColor,
                modifier = Modifier.size(14.dp)
            )
            Text("—", style = MaterialTheme.typography.labelSmall, color = neutralColor)
        }
        return
    }
    val (icon, color) = when {
        deltaPercentile > 0 -> Icons.AutoMirrored.Filled.TrendingUp to positiveColor
        deltaPercentile < 0 -> Icons.AutoMirrored.Filled.TrendingDown to negativeColor
        else -> Icons.AutoMirrored.Filled.TrendingFlat to neutralColor
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Text(
            text = if (deltaPercentile == 0) "0" else (if (deltaPercentile > 0) "+$deltaPercentile" else "$deltaPercentile"),
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}
