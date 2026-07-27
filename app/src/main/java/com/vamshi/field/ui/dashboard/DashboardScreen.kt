package com.vamshi.field.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vamshi.field.R
import com.vamshi.field.domain.model.testing.TestingEvent
import com.vamshi.field.ui.theme.*
import com.vamshi.field.ui.theme.PeachIconBg
import com.vamshi.field.ui.theme.BlueIconBg
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Local-only tint for the light header's icon-button chips. Not promoted to Color.kt per design system rules. */
private val HeaderIconChipBg = Color(0xFFF1F5FF)

@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier,
    onNavigateToRoster: () -> Unit,
    onNavigateToTestLibrary: () -> Unit,
    onNavigateToRecommendations: () -> Unit,
    onNavigateToNewTest: () -> Unit,
    onNavigateToCreateEvent: () -> Unit,
    onNavigateToQuickTest: () -> Unit,
    onNavigateToIndividualTest: () -> Unit,
    onNavigateToTestingGrid: (String, String) -> Unit,
    onNavigateToLeaderboard: (eventId: String, groupId: String, mode: String) -> Unit,
    onNavigateToReports: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToSignIn: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.navigateToSignIn) {
        if (uiState.navigateToSignIn) {
            viewModel.onAction(DashboardAction.NavigationConsumed)
            onNavigateToSignIn()
        }
    }

    DashboardContent(
        modifier = modifier,
        uiState = uiState,
        onAction = {
            when (it) {
                DashboardAction.OnCreateEventClick -> onNavigateToCreateEvent()
                DashboardAction.OnQuickTestClick -> onNavigateToQuickTest()
                DashboardAction.OnIndividualTestClick -> onNavigateToIndividualTest()
                DashboardAction.OnRosterClick -> onNavigateToRoster()
                DashboardAction.OnTestLibraryClick -> onNavigateToTestLibrary()
                DashboardAction.OnRecommendationsClick -> onNavigateToRecommendations()
                DashboardAction.OnNewTestClick -> onNavigateToNewTest()
                is DashboardAction.OnEventClick -> onNavigateToTestingGrid(it.eventId, it.groupId)
                is DashboardAction.OnPickLeaderboardEvent -> {
                    viewModel.onAction(DashboardAction.OnDismissLeaderboardPicker)
                    onNavigateToLeaderboard(it.eventId, it.groupId, "event")
                }
                DashboardAction.OnAnalyticsClick -> onNavigateToReports()
                DashboardAction.OnSeeAllEventsClick -> onNavigateToReports()
                DashboardAction.OnSettingsClick -> onNavigateToSettings()
                else -> viewModel.onAction(it)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardContent(
    modifier: Modifier = Modifier,
    uiState: DashboardUiState,
    onAction: (DashboardAction) -> Unit
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            DashboardHeader(
                onSettingsClick = { onAction(DashboardAction.OnSettingsClick) },
                onSignOutClick = { onAction(DashboardAction.OnSignOutClick) }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 16.dp,
                bottom = 80.dp
            ),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                ContextHeaderCard(
                    athleteCount = uiState.activeAthletes,
                    eventCount = uiState.scheduledTestCount,
                    coachFirstName = uiState.coachFirstName,
                    coachLastName = uiState.coachLastName
                )
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                HeroCard {
                    onAction(DashboardAction.OnCreateEventClick)
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                PrimaryActionCard(
                    title = "Individual Test",
                    subtitle = "Test a registered athlete for analytics",
                    icon = Icons.Default.Person,
                    accentColor = SportBlue,
                    accentContainerColor = BlueIconBg,
                    buttonLabel = "Start",
                    buttonIcon = Icons.Default.PlayArrow,
                    onClick = { onAction(DashboardAction.OnIndividualTestClick) }
                )
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "Quick Actions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            item {
                QuickActionCard(
                    icon = Icons.Default.Bolt,
                    label = "Quick Test",
                    tint = SportOrange,
                    iconBg = PeachIconBg,
                    onClick = { onAction(DashboardAction.OnQuickTestClick) }
                )
            }
            item {
                QuickActionCard(
                    icon = Icons.Default.Group,
                    label = "Roster",
                    tint = SportBlue,
                    iconBg = BlueIconBg,
                    onClick = { onAction(DashboardAction.OnRosterClick) }
                )
            }
            item {
                QuickActionCard(
                    icon = Icons.AutoMirrored.Filled.LibraryBooks,
                    label = "Tests Library",
                    tint = MaterialTheme.colorScheme.primary,
                    iconBg = BlueIconBg,
                    onClick = { onAction(DashboardAction.OnTestLibraryClick) }
                )
            }
            item {
                QuickActionCard(
                    icon = Icons.Default.EmojiEvents,
                    label = "Leaderboard",
                    tint = SportOrange,
                    iconBg = PeachIconBg,
                    onClick = { onAction(DashboardAction.OnLeaderboardClick) }
                )
            }
            item {
                QuickActionCard(
                    icon = Icons.AutoMirrored.Filled.Assignment,
                    label = "Recommendations",
                    tint = SportBlue,
                    iconBg = BlueIconBg,
                    onClick = { onAction(DashboardAction.OnRecommendationsClick) }
                )
            }
            item {
                QuickActionCard(
                    icon = Icons.Default.AddCircleOutline,
                    label = "New Test",
                    tint = SportOrange,
                    iconBg = PeachIconBg,
                    onClick = { onAction(DashboardAction.OnNewTestClick) }
                )
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Recent Events",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    TextButton(onClick = { onAction(DashboardAction.OnSeeAllEventsClick) }) {
                        Text("See All")
                    }
                }
            }

            if (uiState.recentEvents.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(
                        icon = Icons.Default.Event,
                        title = "No recent events",
                        message = "Create one to get started!",
                        actionLabel = "Create Event",
                        onAction = { onAction(DashboardAction.OnCreateEventClick) }
                    )
                }
            } else {
                items(
                    items = uiState.recentEvents,
                    span = { GridItemSpan(maxLineSpan) }
                ) { event ->
                    RecentEventItem(
                        event = event,
                        onClick = {
                            event.groupId?.let {
                                onAction(DashboardAction.OnEventClick(event.id, it))
                            }
                        }
                    )
                }
            }
        }
    }

    if (uiState.showLeaderboardPicker) {
        LeaderboardEventPickerSheet(
            events = uiState.recentEvents.filter { it.groupId != null },
            onPick = { event -> onAction(DashboardAction.OnPickLeaderboardEvent(event.id, event.groupId!!)) },
            onDismiss = { onAction(DashboardAction.OnDismissLeaderboardPicker) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LeaderboardEventPickerSheet(
    events: List<TestingEvent>,
    onPick: (TestingEvent) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                "Choose an event",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 12.dp)
            )
            if (events.isEmpty()) {
                Text(
                    "No events yet — create one to see a leaderboard.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            } else {
                events.forEach { event ->
                    Surface(
                        onClick = { onPick(event) },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.EmojiEvents,
                                contentDescription = null,
                                tint = SportOrange,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = "${formatEventMetadata(event.date)} • ${event.name}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Lightweight, left-aligned Dashboard header. Deliberately does NOT reuse the shared
 * [com.vamshi.field.ui.components.AppTopBar] (solid-primary, centered, white title)
 * since that contract is shared across other screens — this is a Dashboard-local visual
 * treatment only, with no navigation/behavior change.
 */
@Composable
private fun DashboardHeader(
    onSettingsClick: () -> Unit,
    onSignOutClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceWhite)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.logo),
                    contentDescription = null,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = buildAnnotatedString {
                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, color = TextPrimary)) {
                            append("Field")
                        }
                        withStyle(style = SpanStyle(fontWeight = FontWeight.Normal, color = TextSecondary)) {
                            append(" — Testing")
                        }
                    },
                    style = MaterialTheme.typography.titleLarge
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DashboardHeaderIconButton(
                    icon = Icons.Default.Settings,
                    contentDescription = "Settings",
                    onClick = onSettingsClick
                )
                DashboardHeaderIconButton(
                    icon = Icons.AutoMirrored.Filled.Logout,
                    contentDescription = "Sign out",
                    onClick = onSignOutClick
                )
            }
        }
        HorizontalDivider(color = OutlineGrey, thickness = 1.dp)
    }
}

@Composable
private fun DashboardHeaderIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(HeaderIconChipBg, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = ElectricBlue,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun ContextHeaderCard(
    athleteCount: Int,
    eventCount: Int,
    coachFirstName: String,
    coachLastName: String
) {
    val greetingPrefix = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            else -> "Good evening"
        }
    }
    val displayName = listOf(coachFirstName, coachLastName)
        .filter { it.isNotBlank() }
        .joinToString(" ")
        .ifBlank { "Coach" }
    val greeting = "$greetingPrefix, $displayName"
    val dateStr = remember {
        SimpleDateFormat("EEEE, d MMM", Locale.getDefault())
            .format(Date())
    }

    val headerGradient = remember {
        Brush.linearGradient(
            colors = listOf(ElectricBlue, lerp(ElectricBlue, AquaCyan, 0.35f))
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 3.dp, shape = RoundedCornerShape(24.dp), clip = false),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(headerGradient)
                .padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            Text(
                dateStr.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = SportOrange,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                greeting,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "$athleteCount Athletes  •  $eventCount Events",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f)
            )
        }
    }
}

@Composable
private fun HeroCard(onClick: () -> Unit) {
    PrimaryActionCard(
        title = "Start Group Testing Event",
        subtitle = "Create and launch a test session",
        icon = Icons.Default.PlayArrow,
        accentColor = SportOrange,
        accentContainerColor = SportOrangeContainer,
        buttonLabel = "Start",
        buttonIcon = Icons.Default.PlayArrow,
        onClick = onClick
    )
}

@Composable
private fun PrimaryActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    accentContainerColor: Color,
    buttonLabel: String,
    buttonIcon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .pressInteraction(
                shape = RoundedCornerShape(24.dp),
                baseElevation = 2.dp,
                onClick = onClick
            ),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp) // Handled by pressInteraction
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 18.dp, vertical = 16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(accentContainerColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Surface(
                shape = RoundedCornerShape(50),
                color = accentColor
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        buttonIcon,
                        contentDescription = buttonLabel,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        buttonLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}



@Composable
private fun QuickActionCard(
    icon: ImageVector,
    label: String,
    tint: Color,
    iconBg: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .height(104.dp)
            .pressInteraction(
                shape = RoundedCornerShape(24.dp),
                baseElevation = 4.dp,
                onClick = onClick
            ),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(iconBg, shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = label,
                    tint = tint,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecentEventItem(event: TestingEvent, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .pressInteraction(
                shape = RoundedCornerShape(20.dp),
                baseElevation = 2.dp,
                onClick = onClick
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Event,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = event.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatEventMetadata(event.date),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Details",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatEventMetadata(timestamp: Long): String {
    val calendar = Calendar.getInstance().apply { timeInMillis = timestamp }
    val today = Calendar.getInstance()
    val yesterday = Calendar.getInstance().apply { add(Calendar.DATE, -1) }

    val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

    val dayString = when {
        calendar.get(Calendar.YEAR) == today.get(Calendar.YEAR) && calendar.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) -> "Today"
        calendar.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR) && calendar.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR) -> "Yesterday"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(calendar.time)
    }

    return "$dayString • ${timeFormat.format(calendar.time)}"
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (actionLabel != null && onAction != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onAction,
                    shape = MaterialTheme.shapes.large
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Preview
@Composable
private fun DashboardContentPreview() {
    FieldTheme {
        DashboardContent(
            uiState = DashboardUiState(
                activeAthletes = 25,
                scheduledTestCount = 4,
                recentEvents = listOf(
                    TestingEvent(
                        id = "1",
                        name = "Max-Out Day",
                        date = System.currentTimeMillis() - 86400000,
                        groupId = "1",
                    ),
                    TestingEvent(
                        id = "2",
                        name = "Combine Prep",
                        date = System.currentTimeMillis() - 86400000 * 2,
                        groupId = "2",
                    )
                )
            ),
            onAction = {}
        )
    }
}
