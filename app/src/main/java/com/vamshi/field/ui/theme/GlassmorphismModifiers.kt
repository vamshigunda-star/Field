package com.vamshi.field.ui.theme

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

import androidx.compose.material3.MaterialTheme

fun Modifier.glassmorphismFallback(
    radius: Dp = 16.dp,
    fallbackColor: Color? = null
): Modifier = composed {
    val defaultFallback = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
    val resolvedFallback = fallbackColor ?: defaultFallback
    val tint = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        // Native blur available on API 31+
        this.blur(radius = radius, edgeTreatment = BlurredEdgeTreatment.Unbounded)
            .background(tint)
    } else {
        // Fallback for older devices
        this.background(resolvedFallback)
    }
}
