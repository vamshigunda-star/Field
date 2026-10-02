package com.vamshi.field.ui.report.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vamshi.field.domain.model.reports.Classification
import com.vamshi.field.domain.model.reports.Distribution
import com.vamshi.field.ui.theme.performanceZoneColors

import androidx.compose.material3.MaterialTheme

@Composable
fun DistributionBar(
    distribution: Distribution,
    modifier: Modifier = Modifier,
    height: Dp = 10.dp
) {
    val total = distribution.total.coerceAtLeast(1)
    val trackBg = MaterialTheme.colorScheme.surfaceVariant
    val superiorColor = performanceZoneColors(Classification.SUPERIOR).text
    val healthyColor = performanceZoneColors(Classification.HEALTHY).text
    val needsColor = performanceZoneColors(Classification.NEEDS_IMPROVEMENT).text
    val noDataColor = MaterialTheme.colorScheme.outlineVariant

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(999.dp))
            .background(trackBg)
    ) {
        if (distribution.superior > 0) {
            Box(
                modifier = Modifier
                    .weight(distribution.superior.toFloat() / total)
                    .background(superiorColor)
            )
        }
        if (distribution.healthy > 0) {
            Box(
                modifier = Modifier
                    .weight(distribution.healthy.toFloat() / total)
                    .background(healthyColor)
            )
        }
        if (distribution.needsImprovement > 0) {
            Box(
                modifier = Modifier
                    .weight(distribution.needsImprovement.toFloat() / total)
                    .background(needsColor)
            )
        }
        if (distribution.noData > 0) {
            Box(
                modifier = Modifier
                    .weight(distribution.noData.toFloat() / total)
                    .background(noDataColor)
            )
        }
    }
}
