package com.vamshi.field.ui.theme

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A standardized modifier for all clickable cards in ALearning.
 * 
 * Provides a consistent "press-down" compression effect:
 * 1. Scales down slightly (default 0.96f)
 * 2. Drops elevation (shadow) to simulate a mechanical press
 * 3. Triggers the [onClick] action immediately on release
 */
fun Modifier.pressInteraction(
    shape: Shape,
    baseElevation: Dp = 2.dp,
    pressedScale: Float = 0.96f,
    onClick: () -> Unit
) = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        animationSpec = FieldMotion.fastSpatial(),
        label = "press_scale"
    )

    val elevation by animateDpAsState(
        targetValue = if (isPressed) 0.dp else baseElevation,
        animationSpec = FieldMotion.fastEffects(),
        label = "press_elevation"
    )

    this
        .scale(scale)
        .shadow(
            elevation = elevation,
            shape = shape,
            clip = false
        )
        .clickable(
            interactionSource = interactionSource,
            indication = null, // Disable ripple as we use custom scale/shadow feedback
            onClick = onClick
        )
}
