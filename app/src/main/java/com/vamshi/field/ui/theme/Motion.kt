package com.vamshi.field.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring

/**
 * Field's motion tokens: Material 3 Expressive's spring scheme.
 *
 * material3 1.4.0 (the stable line in our BOM) ships the expressive scheme, but
 * `MaterialTheme.motionScheme`, `MotionScheme.expressive()` and `MaterialExpressiveTheme` are all
 * Kotlin-`internal` there. These values are copied from that release's
 * `androidx.compose.material3.tokens.ExpressiveMotionTokens`, so the motion matches the spec
 * exactly. When a stable material3 makes `MaterialTheme.motionScheme` public, delegate to it here.
 *
 * Spatial specs move things (position, scale) and may overshoot; effects specs change colour
 * or alpha and never overshoot.
 */
object FieldMotion {
    fun <T> fastSpatial(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.6f, stiffness = 800f)
    fun <T> defaultSpatial(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.8f, stiffness = 380f)
    fun <T> slowSpatial(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.8f, stiffness = 200f)

    fun <T> fastEffects(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = 3800f)
    fun <T> defaultEffects(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = 1600f)
    fun <T> slowEffects(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = 800f)
}
