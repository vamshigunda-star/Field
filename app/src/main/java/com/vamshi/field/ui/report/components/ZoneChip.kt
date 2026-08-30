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

data class ZoneColors(val bg: Color, val fg: Color)

// Maps engine Classification onto the four-zone color contract.
// HEALTHY is the mid (Yellow 30–59) zone — never blue.
fun zoneColors(c: Classification, isDark: Boolean = false): ZoneColors = when (c) {
    Classification.SUPERIOR -> if (isDark) ZoneColors(PerformanceGreenDark, PerformanceGreenTextDark) else ZoneColors(PerformanceGreen, PerformanceGreenText)
    Classification.HEALTHY -> if (isDark) ZoneColors(PerformanceYellowDark, PerformanceYellowTextDark) else ZoneColors(PerformanceYellow, PerformanceYellowText)
    Classification.NEEDS_IMPROVEMENT -> if (isDark) ZoneColors(PerformanceRedDark, PerformanceRedTextDark) else ZoneColors(PerformanceRed, PerformanceRedText)
    Classification.NO_DATA -> if (isDark) ZoneColors(PerformanceGreyDark, PerformanceGreyTextDark) else ZoneColors(PerformanceGrey, PerformanceGreyText)
}

fun zoneLabel(c: Classification): String = when (c) {
    Classification.SUPERIOR -> "Superior"
    Classification.HEALTHY -> "Healthy"
    Classification.NEEDS_IMPROVEMENT -> "Needs Imp."
    Classification.NO_DATA -> "—"
}

@Composable
fun ZoneChip(
    classification: Classification,
    modifier: Modifier = Modifier,
    label: String = zoneLabel(classification)
) {
    val isDark = isSystemInDarkTheme()
    val colors = zoneColors(classification, isDark)
    Text(
        text = label,
        modifier = modifier
            .background(colors.bg, RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelSmall,
        color = colors.fg,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
fun PerformanceYellowChip(text: String, modifier: Modifier = Modifier) {
    val isDark = isSystemInDarkTheme()
    val bg = if (isDark) PerformanceYellowDark else PerformanceYellow
    val fg = if (isDark) PerformanceYellowTextDark else PerformanceYellowText
    Text(
        text = text,
        modifier = modifier
            .background(bg, RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelSmall,
        color = fg,
        fontWeight = FontWeight.SemiBold
    )
}
