package com.vamshi.field.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import kotlin.math.roundToInt
import com.vamshi.field.ui.theme.FieldMotion

/**
 * A headline number that counts to its new value on [FieldMotion.slowSpatial], so a
 * changed result reads as a change rather than a swap. The first value shows immediately:
 * opening a screen should not animate numbers up from zero.
 *
 * Colour is the caller's: for zone-coloured numbers pass `performanceZoneColors(...).text`.
 */
@Composable
fun HeroNumber(
    value: Int,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    suffix: String = "",
    fontWeight: FontWeight? = null
) {
    val animated = remember { Animatable(value.toFloat()) }
    val spec = FieldMotion.slowSpatial<Float>()
    LaunchedEffect(value) {
        animated.animateTo(value.toFloat(), spec)
    }
    Text(
        text = "${animated.value.roundToInt()}$suffix",
        style = style.copy(fontFeatureSettings = "tnum"),
        color = color,
        fontWeight = fontWeight,
        modifier = modifier
    )
}
