package com.vamshi.field.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.vamshi.field.domain.model.reports.Classification
import com.vamshi.field.domain.model.reports.PerformanceThresholds

/**
 * Background, foreground and border for one performance zone, already resolved for the
 * active theme.
 */
data class PerformanceZoneColors(
    val background: Color,
    val text: Color,
    val border: Color,
    val label: String
)

/**
 * The one place the UI turns a percentile into colour.
 *
 * Every screen used to inline its own thresholds, and they drifted apart: the testing grid
 * coloured at 60/30, the radar at 75/40, the group-overview bands at 70/35, while the
 * reports used the domain rule of 80/40. The same athlete could read Yellow on one screen
 * and Red on the next. Thresholds come from [PerformanceThresholds]; colours come from the
 * tokens in Color.kt. Add a new zone-coloured surface by calling this, not by re-deriving.
 *
 * [alpha] exists because the grid and QuickTest cells wash the light-theme background out to
 * 0.7 for density. Dark-theme tokens are already muted, so it is only applied to the light set.
 */
@Composable
fun performanceZoneColors(percentile: Int?, alpha: Float = 1f): PerformanceZoneColors {
    val isDark = isSystemInDarkTheme()
    return when (PerformanceThresholds.classify(percentile)) {
        Classification.SUPERIOR -> if (isDark) {
            PerformanceZoneColors(PerformanceGreenDark, PerformanceGreenTextDark, PerformanceGreenBorderDark, "Superior")
        } else {
            PerformanceZoneColors(PerformanceGreen.copy(alpha = alpha), PerformanceGreenText, PerformanceGreenBorder, "Superior")
        }
        Classification.HEALTHY -> if (isDark) {
            PerformanceZoneColors(PerformanceYellowDark, PerformanceYellowTextDark, PerformanceYellowBorderDark, "Healthy")
        } else {
            PerformanceZoneColors(PerformanceYellow.copy(alpha = alpha), PerformanceYellowText, PerformanceYellowBorder, "Healthy")
        }
        Classification.NEEDS_IMPROVEMENT -> if (isDark) {
            PerformanceZoneColors(PerformanceRedDark, PerformanceRedTextDark, PerformanceRedBorderDark, "Needs Improvement")
        } else {
            PerformanceZoneColors(PerformanceRed.copy(alpha = alpha), PerformanceRedText, PerformanceRedBorder, "Needs Improvement")
        }
        Classification.NO_DATA -> if (isDark) {
            PerformanceZoneColors(PerformanceGreyDark, PerformanceGreyTextDark, PerformanceGreyBorderDark, "No Norm")
        } else {
            PerformanceZoneColors(PerformanceGrey, PerformanceGreyText, PerformanceGreyBorder, "No Norm")
        }
    }
}
