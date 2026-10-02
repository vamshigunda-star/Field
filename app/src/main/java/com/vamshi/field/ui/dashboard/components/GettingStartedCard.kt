package com.vamshi.field.ui.dashboard.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vamshi.field.ui.theme.*
import com.vamshi.field.ui.components.FieldProgressBar

data class GettingStartedStep(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val isCompleted: Boolean,
    val onClick: () -> Unit
)

@Composable
fun GettingStartedCard(
    modifier: Modifier = Modifier,
    activeAthletes: Int,
    scheduledTestCount: Int,
    onNavigateToRoster: () -> Unit,
    onNavigateToTestLibrary: () -> Unit,
    onNavigateToCreateEvent: () -> Unit,
    onDismiss: () -> Unit
) {
    val steps = listOf(
        GettingStartedStep(
            id = "roster",
            title = "1. Add athletes to roster",
            subtitle = if (activeAthletes > 0) "$activeAthletes athletes registered" else "Create groups & add profiles with medical alerts",
            icon = Icons.Default.Groups,
            isCompleted = activeAthletes > 0,
            onClick = onNavigateToRoster
        ),
        GettingStartedStep(
            id = "test_library",
            title = "2. Explore the Test Library",
            subtitle = "Browse standardized protocols and custom test authoring",
            icon = Icons.Default.MenuBook,
            isCompleted = false,
            onClick = onNavigateToTestLibrary
        ),
        GettingStartedStep(
            id = "create_event",
            title = "3. Create a testing event",
            subtitle = if (scheduledTestCount > 0) "$scheduledTestCount events created" else "Set up squads, select tests, and start live testing",
            icon = Icons.Default.SportsScore,
            isCompleted = scheduledTestCount > 0,
            onClick = onNavigateToCreateEvent
        )
    )

    val completedCount = steps.count { it.isCompleted }
    val progress by animateFloatAsState(
        targetValue = if (steps.isNotEmpty()) completedCount.toFloat() / steps.size.toFloat() else 0f,
        label = "GettingStartedProgress"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(SportOrangeContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.RocketLaunch,
                            contentDescription = null,
                            tint = SportOrange,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Getting Started with Field",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "$completedCount of ${steps.size} steps completed",
                            style = MaterialTheme.typography.labelSmall,
                            color = SportOrange,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Progress Bar
            FieldProgressBar(
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = SportOrange,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Step Rows
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                steps.forEach { step ->
                    StepRow(step = step)
                }
            }
        }
    }
}

@Composable
private fun StepRow(step: GettingStartedStep) {
    Surface(
        onClick = step.onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (step.isCompleted) {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
        },
        border = if (step.isCompleted) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
    ) {
        val isDark = isSystemInDarkTheme()
        val checkBg = if (isDark) PerformanceGreenDark.copy(alpha = 0.25f) else PerformanceGreen.copy(alpha = 0.15f)
        val checkTint = if (isDark) PerformanceGreenTextDark else PerformanceGreen
        val incompleteBg = MaterialTheme.colorScheme.primaryContainer
        val incompleteTint = MaterialTheme.colorScheme.primary
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(
                        if (step.isCompleted) checkBg else incompleteBg,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (step.isCompleted) Icons.Default.CheckCircle else step.icon,
                    contentDescription = null,
                    tint = if (step.isCompleted) checkTint else incompleteTint,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = step.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (step.isCompleted) {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                Text(
                    text = step.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
