package com.vamshi.field.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SportsScore
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.vamshi.field.domain.model.standards.RadarAxis

/**
 * Visual styling definition for a fitness test category aligned with the Field brand design system.
 */
data class CategoryVisual(
    val accentColor: Color = ElectricBlue,
    val containerColor: Color = BlueIconBg,
    val icon: ImageVector
)

/**
 * Resolves the [CategoryVisual] styling (signature brand accent color, container tint, and category icon)
 * for a category name and optional [RadarAxis].
 */
fun getCategoryVisual(name: String, radarAxis: RadarAxis? = null): CategoryVisual {
    val normalized = name.lowercase().trim()

    // 1. Resolve category icon by RadarAxis if available
    val icon = radarAxis?.let { axis ->
        when (axis) {
            RadarAxis.SPEED -> Icons.Default.Speed
            RadarAxis.AGILITY -> Icons.AutoMirrored.Filled.AltRoute
            RadarAxis.STRENGTH -> Icons.Default.FitnessCenter
            RadarAxis.ENDURANCE -> Icons.Default.Repeat
            RadarAxis.FLEXIBILITY -> Icons.Default.SelfImprovement
            RadarAxis.BALANCE -> Icons.Default.AccessibilityNew
        }
    } ?: run {
        // 2. Match by category name keywords
        when {
            normalized.contains("cardio") || normalized.contains("aerobic") || normalized.contains("heart") ->
                Icons.Default.Favorite

            normalized.contains("muscular strength") || (normalized.contains("strength") && !normalized.contains("endurance")) ->
                Icons.Default.FitnessCenter

            normalized.contains("muscular endurance") || normalized.contains("endurance") || normalized.contains("stamina") ->
                Icons.Default.Repeat

            normalized.contains("flexibility") || normalized.contains("mobility") || normalized.contains("stretch") ->
                Icons.Default.SelfImprovement

            normalized.contains("speed") || normalized.contains("sprint") || normalized.contains("acceleration") ->
                Icons.AutoMirrored.Filled.DirectionsRun

            normalized.contains("agility") || normalized.contains("direction") || normalized.contains("quickness") ->
                Icons.AutoMirrored.Filled.AltRoute

            normalized.contains("power") || normalized.contains("explosive") || normalized.contains("jump") ->
                Icons.Default.ElectricBolt

            normalized.contains("balance") || normalized.contains("stability") || normalized.contains("coordination") ->
                Icons.Default.AccessibilityNew

            else ->
                Icons.Default.SportsScore
        }
    }

    return CategoryVisual(
        accentColor = ElectricBlue,
        containerColor = BlueIconBg,
        icon = icon
    )
}
