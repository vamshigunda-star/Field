package com.vamshi.field.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.vamshi.field.ui.theme.FieldMotion

/**
 * The one full-screen loading state. Screens used to carry their own private copy, each with a
 * slightly different stroke and label colour.
 *
 * Material 3 Expressive's `LoadingIndicator` only exists in material3 1.5.0 alphas. If the app
 * moves to a version that ships it stable, swap the indicator here and every screen follows.
 */
@Composable
fun FieldLoadingState(
    message: String? = null,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.dp
            )
            if (message != null) {
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Determinate progress bar that springs to each new value
 * on [FieldMotion.defaultSpatial] instead of jumping. Same swap point as [FieldLoadingState] for a future wavy indicator.
 */
@Composable
fun FieldProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = ProgressIndicatorDefaults.linearTrackColor,
    strokeCap: StrokeCap = ProgressIndicatorDefaults.LinearStrokeCap
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = FieldMotion.defaultSpatial(),
        label = "field_progress"
    )
    LinearProgressIndicator(
        progress = { animated },
        modifier = modifier,
        color = color,
        trackColor = trackColor,
        strokeCap = strokeCap
    )
}
