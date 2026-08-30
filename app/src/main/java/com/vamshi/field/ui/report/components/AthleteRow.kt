package com.vamshi.field.ui.report.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vamshi.field.domain.model.reports.LeaderboardRow
import com.vamshi.field.ui.theme.ElectricBlue

@Composable
fun AthleteLeaderRow(
    row: LeaderboardRow,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()

    val rankBadgeBg = when (row.rank) {
        1 -> if (isDark) ElectricBlue.copy(alpha = 0.25f) else ElectricBlue.copy(alpha = 0.12f)
        2 -> if (isDark) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
        3 -> if (isDark) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDark) 0.4f else 0.4f)
    }

    val rankBadgeFg = when (row.rank) {
        1 -> ElectricBlue
        2 -> MaterialTheme.colorScheme.onSurface
        3 -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isDark) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.40f else 0.65f)
        ),
        shadowElevation = 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Rank Squircle Badge (Clean Numeral, No Trophy)
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = rankBadgeBg,
                modifier = Modifier.size(38.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${row.rank}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = rankBadgeFg
                    )
                }
            }

            // Athlete Info (Bold Name + Subtitle)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = row.athleteName,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.5.sp),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (row.flagged) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(MaterialTheme.colorScheme.error, CircleShape)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val scoreText = if (row.rawScore != null) "${formatScore(row.rawScore)} ${row.unit}" else "Absent"
                    Text(
                        text = scoreText,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val classificationText = row.classificationLabel?.takeIf { it.isNotBlank() }
                        ?: row.classification?.let { zoneLabel(it) }
                    if (!classificationText.isNullOrBlank()) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                        Surface(
                            shape = RoundedCornerShape(5.dp),
                            color = if (isDark) ElectricBlue.copy(alpha = 0.15f) else ElectricBlue.copy(alpha = 0.08f)
                        ) {
                            Text(
                                text = classificationText,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = ElectricBlue,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.5.dp)
                            )
                        }
                    }
                }
            }

            // Trailing Section (Trend + Percentile)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DeltaArrow(deltaPercentile = row.deltaPercentile)

                if (row.percentile != null) {
                    PercentileChip(percentile = row.percentile)
                }
            }
        }
    }
}

private fun formatScore(s: Double): String =
    if (s % 1.0 == 0.0) s.toInt().toString() else String.format("%.1f", s)
