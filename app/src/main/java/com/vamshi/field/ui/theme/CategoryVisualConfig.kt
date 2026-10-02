package com.vamshi.field.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SportsHandball
import androidx.compose.material.icons.filled.SportsScore
import androidx.compose.material.icons.filled.TouchApp
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
    // Name first, axis as fallback. Several categories share an axis (Cardiovascular and
    // Muscular Endurance are both ENDURANCE), so resolving by axis first gave them one icon.
    val icon = iconForCategoryName(name) ?: radarAxis?.let(::iconForAxis) ?: Icons.Default.SportsScore

    return CategoryVisual(
        accentColor = ElectricBlue,
        containerColor = BlueIconBg,
        icon = icon
    )
}

private fun iconForCategoryName(name: String): ImageVector? {
    val normalized = name.lowercase().trim()
    return when {
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

        normalized.contains("coordination") ->
            Icons.Default.SportsHandball

        normalized.contains("balance") || normalized.contains("stability") ->
            Icons.Default.AccessibilityNew

        normalized.contains("reaction") ->
            Icons.Default.TouchApp

        normalized.contains("body composition") || normalized.contains("anthropometr") ->
            Icons.Default.MonitorWeight

        else -> null
    }
}

private fun iconForAxis(axis: RadarAxis): ImageVector = when (axis) {
    RadarAxis.SPEED -> Icons.Default.Speed
    RadarAxis.AGILITY -> Icons.AutoMirrored.Filled.AltRoute
    RadarAxis.STRENGTH -> Icons.Default.FitnessCenter
    RadarAxis.ENDURANCE -> Icons.Default.Repeat
    RadarAxis.FLEXIBILITY -> Icons.Default.SelfImprovement
    RadarAxis.BALANCE -> Icons.Default.AccessibilityNew
}
