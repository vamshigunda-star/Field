package com.vamshi.field.ui.components.testing

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ripple
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import com.vamshi.field.domain.model.standards.FitnessTest
import com.vamshi.field.domain.model.standards.RadarAxis
import com.vamshi.field.ui.theme.*
import com.vamshi.field.ui.theme.getCategoryVisual

/**
 * Vertical accordion category header shared across every test-selection/browsing screen
 * (Create Testing Event, Quick/Individual Test setup, Test Library, Recommendations).
 * [selectedCount] is omitted (no badge) for screens with no selection concept — pass 0.
 * [totalCount] is null when the count isn't known yet (e.g. a category whose tests are
 * only fetched lazily on expand) — the "N tests" label is hidden rather than showing a
 * misleading "0 tests" for a section that simply hasn't loaded.
 * [subtitle] is an optional muted hint line under the name (e.g. "Tap to view recommended
 * tests") for screens where [totalCount] can't be shown up front — helps a collapsed,
 * count-less header still read as an actionable control rather than a plain label.
 */
@Composable
fun CategoryAccordionHeader(
    name: String,
    totalCount: Int?,
    isExpanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    radarAxis: RadarAxis? = null,
    accentColorOverride: Color? = null,
    iconOverride: ImageVector? = null,
    selectedCount: Int = 0,
    subtitle: String? = null,
    isDocked: Boolean = false
) {
    val visual = remember(name, radarAxis) { getCategoryVisual(name, radarAxis) }
    val accentColor = accentColorOverride ?: visual.accentColor
    val icon = iconOverride ?: visual.icon
    val isDark = isSystemInDarkTheme()

    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "chevronRotation"
    )
    val backgroundColor by animateColorAsState(
        targetValue = when {
            isDocked && isExpanded -> if (isDark) accentColor.copy(alpha = 0.08f) else accentColor.copy(alpha = 0.035f)
            isExpanded -> if (isDark) accentColor.copy(alpha = 0.08f) else accentColor.copy(alpha = 0.035f)
            else -> MaterialTheme.colorScheme.surface
        },
        animationSpec = tween(durationMillis = 180),
        label = "categoryHeaderBackground"
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            isDocked -> Color.Transparent
            isExpanded -> accentColor.copy(alpha = if (isDark) 0.45f else 0.35f)
            else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.35f else 0.50f)
        },
        animationSpec = tween(durationMillis = 180),
        label = "categoryHeaderBorder"
    )

    Surface(
        onClick = onClick,
        shape = if (isDocked && isExpanded) RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp) else RoundedCornerShape(16.dp),
        color = backgroundColor,
        shadowElevation = if (!isDocked && isExpanded) 1.5.dp else 0.dp,
        border = if (isDocked) null else BorderStroke(width = 1.dp, color = borderColor),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Category Icon Squircle Badge
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = accentColor.copy(alpha = if (isDark) 0.18f else 0.10f),
                modifier = Modifier.size(40.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = accentColor
                    )
                }
            }

            // Category Title & Metadata
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.5.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (totalCount != null) {
                            Text(
                                text = "$totalCount tests",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (selectedCount > 0 && totalCount != null) {
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                        }

                        if (selectedCount > 0) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = accentColor.copy(alpha = if (isDark) 0.22f else 0.12f)
                            ) {
                                Text(
                                    text = "$selectedCount selected",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = accentColor,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Minimalist Modern Chevron Indicator
            Surface(
                shape = CircleShape,
                color = if (isExpanded) accentColor.copy(alpha = if (isDark) 0.20f else 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(30.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        modifier = Modifier
                            .size(19.dp)
                            .rotate(chevronRotation),
                        tint = if (isExpanded) accentColor else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Discrete selection card for individual fitness tests inside category accordions.
 * Features bold typography, discrete rounded tile borders, high-contrast selected state, and badge chips.
 */
@Composable
fun TestSelectionCard(
    test: FitnessTest,
    isSelected: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    val isDark = isSystemInDarkTheme()

    val targetBgColor by animateColorAsState(
        targetValue = when {
            isSelected -> accentColor.copy(alpha = if (isDark) 0.16f else 0.08f)
            else -> if (isDark) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface
        },
        animationSpec = tween(durationMillis = 150),
        label = "testCardBg"
    )

    val targetBorderColor by animateColorAsState(
        targetValue = when {
            isSelected -> accentColor.copy(alpha = if (isDark) 0.85f else 0.75f)
            else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.40f else 0.65f)
        },
        animationSpec = tween(durationMillis = 150),
        label = "testCardBorder"
    )

    Surface(
        onClick = onToggle,
        shape = RoundedCornerShape(12.dp),
        color = targetBgColor,
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = targetBorderColor
        ),
        shadowElevation = if (isSelected) 1.dp else 0.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = null,
                colors = CheckboxDefaults.colors(
                    checkedColor = accentColor,
                    uncheckedColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)
                )
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = test.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Unit Chip Pill
                    Surface(
                        shape = RoundedCornerShape(5.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDark) 0.6f else 0.75f),
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = test.unit,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    // Criterion Chip Pill
                    val badgeBg = if (test.isHigherBetter) {
                        if (isDark) PerformanceGreenDark else PerformanceGreen
                    } else {
                        if (isDark) PerformanceRedDark else PerformanceRed
                    }
                    val badgeBorder = if (test.isHigherBetter) {
                        if (isDark) PerformanceGreenBorderDark else PerformanceGreenBorder
                    } else {
                        if (isDark) PerformanceRedBorderDark else PerformanceRedBorder
                    }
                    val textColor = if (test.isHigherBetter) {
                        if (isDark) PerformanceGreenTextDark else PerformanceGreenText
                    } else {
                        if (isDark) PerformanceRedTextDark else PerformanceRedText
                    }
                    Surface(
                        shape = RoundedCornerShape(5.dp),
                        color = badgeBg,
                        border = BorderStroke(0.5.dp, badgeBorder)
                    ) {
                        Text(
                            text = if (test.isHigherBetter) "Higher is Better" else "Lower is Better",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = textColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

/** Checkbox test row shared by screens that let the coach pick tests. Delegates to [TestSelectionCard]. */
@Composable
fun TestSelectionRow(
    test: FitnessTest,
    isSelected: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    TestSelectionCard(
        test = test,
        isSelected = isSelected,
        onToggle = onToggle,
        modifier = modifier
    )
}
