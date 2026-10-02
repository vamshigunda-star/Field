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
import androidx.compose.foundation.isSystemInDarkTheme
import com.vamshi.field.ui.theme.performanceZoneColors

data class ZoneColors(val bg: Color, val fg: Color)

// Maps engine Classification onto the three performance categories (grey = no data, not a category).
// HEALTHY is the mid (Yellow 40–79) zone — never blue.
fun zoneColors(c: Classification, isDark: Boolean = false): ZoneColors =
    performanceZoneColors(c, isDark).let { ZoneColors(it.background, it.text) }

fun zoneLabel(c: Classification): String = when (c) {
    Classification.SUPERIOR -> "Superior"
    Classification.HEALTHY -> "Healthy"
    Classification.NEEDS_IMPROVEMENT -> "Needs Improvement"
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
