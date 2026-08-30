package com.vamshi.field.ui.report.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vamshi.field.domain.model.reports.Classification
import com.vamshi.field.domain.usecase.reports.ClassifyPercentileUseCase
import com.vamshi.field.ui.theme.PerformanceGreen
import com.vamshi.field.ui.theme.PerformanceGreenText
import com.vamshi.field.ui.theme.PerformanceGrey
import com.vamshi.field.ui.theme.PerformanceGreyText
import com.vamshi.field.ui.theme.PerformanceRed
import com.vamshi.field.ui.theme.PerformanceRedText
import com.vamshi.field.ui.theme.PerformanceYellow
import com.vamshi.field.ui.theme.PerformanceYellowText

import androidx.compose.foundation.isSystemInDarkTheme
import com.vamshi.field.ui.theme.PerformanceGreenDark
import com.vamshi.field.ui.theme.PerformanceGreenTextDark
import com.vamshi.field.ui.theme.PerformanceGreyDark
import com.vamshi.field.ui.theme.PerformanceGreyTextDark
import com.vamshi.field.ui.theme.PerformanceRedDark
import com.vamshi.field.ui.theme.PerformanceRedTextDark
import com.vamshi.field.ui.theme.PerformanceYellowDark
import com.vamshi.field.ui.theme.PerformanceYellowTextDark

private val classifyPercentile = ClassifyPercentileUseCase()

@Composable
fun PercentileChip(percentile: Int?, modifier: Modifier = Modifier) {
    val isDark = isSystemInDarkTheme()
    val label = percentile?.let { "${ordinal(it)}" } ?: "—"
    val (bg, fg) = percentileChipColors(classifyPercentile(percentile), isDark)
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

private fun percentileChipColors(classification: Classification, isDark: Boolean): Pair<Color, Color> = when (classification) {
    Classification.NO_DATA -> if (isDark) PerformanceGreyDark to PerformanceGreyTextDark else PerformanceGrey to PerformanceGreyText
    Classification.SUPERIOR -> if (isDark) PerformanceGreenDark to PerformanceGreenTextDark else PerformanceGreen to PerformanceGreenText
    Classification.HEALTHY -> if (isDark) PerformanceYellowDark to PerformanceYellowTextDark else PerformanceYellow to PerformanceYellowText
    Classification.NEEDS_IMPROVEMENT -> if (isDark) PerformanceRedDark to PerformanceRedTextDark else PerformanceRed to PerformanceRedText
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
