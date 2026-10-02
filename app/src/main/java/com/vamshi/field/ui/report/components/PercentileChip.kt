package com.vamshi.field.ui.report.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vamshi.field.domain.model.reports.Classification
import com.vamshi.field.domain.usecase.reports.ClassifyPercentileUseCase
import androidx.compose.foundation.isSystemInDarkTheme
import com.vamshi.field.ui.theme.performanceZoneColors

private val classifyPercentile = ClassifyPercentileUseCase()

@Composable
fun PercentileChip(percentile: Int?, modifier: Modifier = Modifier) {
    val isDark = isSystemInDarkTheme()
    val label = percentile?.let { "${ordinal(it)}" } ?: "—"
    val zone = performanceZoneColors(classifyPercentile(percentile), isDark)
    val bg = zone.background
    val fg = zone.text
    Text(
        text = label,
        modifier = modifier
            .background(bg, RoundedCornerShape(999.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        style = MaterialTheme.typography.labelSmall,
        color = fg,
        fontWeight = FontWeight.SemiBold
    )
}

private fun ordinal(n: Int): String {
    val suffix = when {
        n % 100 in 11..13 -> "th"
        n % 10 == 1 -> "st"
        n % 10 == 2 -> "nd"
        n % 10 == 3 -> "rd"
        else -> "th"
    }
    return "$n$suffix"
}
