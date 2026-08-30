package com.vamshi.field.ui.leaderboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vamshi.field.domain.usecase.testing.LeaderboardEntry
import com.vamshi.field.ui.components.AppTopBar
import com.vamshi.field.ui.theme.ElectricBlue
import androidx.compose.foundation.isSystemInDarkTheme
import com.vamshi.field.ui.theme.PerformanceGreen
import com.vamshi.field.ui.theme.PerformanceGreenDark
import com.vamshi.field.ui.theme.PerformanceGreenText
import com.vamshi.field.ui.theme.PerformanceGreenTextDark
import com.vamshi.field.ui.theme.PerformanceRed
import com.vamshi.field.ui.theme.PerformanceRedDark
import com.vamshi.field.ui.theme.PerformanceRedText
import com.vamshi.field.ui.theme.PerformanceRedTextDark
import com.vamshi.field.ui.theme.PerformanceYellow
import com.vamshi.field.ui.theme.PerformanceYellowDark
import com.vamshi.field.ui.theme.PerformanceYellowText
import com.vamshi.field.ui.theme.PerformanceYellowTextDark
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.vamshi.field.ui.components.tour.TestingTourDialog

@Composable
fun LeaderboardScreen(
    onNavigateBack: () -> Unit,
    viewModel: LeaderboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showTestingTour by remember { mutableStateOf(false) }

    LeaderboardContent(
        uiState = uiState,
        onOpenTestingTour = { showTestingTour = true },
        onAction = { action ->
            when (action) {
                is LeaderboardAction.OnNavigateBack -> onNavigateBack()
                else -> viewModel.onAction(action)
            }
        }
    )

    if (showTestingTour) {
        TestingTourDialog(
            onDismiss = { showTestingTour = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaderboardContent(
    uiState: LeaderboardUiState,
    onOpenTestingTour: () -> Unit = {},
    onAction: (LeaderboardAction) -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            AppTopBar(
                title = if (uiState.mode == "event") "Event Leaderboard" else "All-Time Leaderboard",
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = { onAction(LeaderboardAction.OnNavigateBack) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenTestingTour) {
                        Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = "Testing Guide")
                    }
                }
            )
        }
    ) { padding ->
        when {
            uiState.isLoading -> LoadingState()
            uiState.errorMessage != null && uiState.tests.isEmpty() -> ErrorState(
                message = uiState.errorMessage,
                onDismiss = { onAction(LeaderboardAction.OnDismissError) }
            )
            uiState.tests.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No tests available")
                }
            }
            else -> {
                LeaderboardBody(uiState = uiState, onAction = onAction, padding = padding)
            }
        }
    }
}

@Composable
private fun LoadingState(message: String = "Loading...") {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.dp
            )
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ErrorState(
    message: String,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Default.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Text(
                message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
            OutlinedButton(onClick = onDismiss) {
                Text("Dismiss")
            }
        }
    }
}

@Composable
private fun LeaderboardBody(
    uiState: LeaderboardUiState,
    onAction: (LeaderboardAction) -> Unit,
    padding: PaddingValues
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
    ) {
        // Test selection tabs
        ScrollableTabRow(
            selectedTabIndex = uiState.tests.indexOfFirst { it.id == uiState.selectedTestId }.coerceAtLeast(0)
        ) {
            uiState.tests.forEach { test ->
                Tab(
                    selected = test.id == uiState.selectedTestId,
                    onClick = { onAction(LeaderboardAction.OnSelectTest(test.id)) },
                    text = { Text(test.name, maxLines = 1) }
                )
            }
        }

        // Leaderboard entries
        val leaderboard = uiState.leaderboard
        if (leaderboard == null) {
            LoadingState()
        } else if (leaderboard.entries.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("No results for this test")
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(leaderboard.entries) { entry ->
                    LeaderboardEntryRow(entry = entry)
                }
            }
        }
    }
}

@Composable
private fun LeaderboardEntryRow(entry: LeaderboardEntry) {
    val isDark = isSystemInDarkTheme()

    val rankBadgeBg = when (entry.rank) {
        1 -> if (isDark) ElectricBlue.copy(alpha = 0.25f) else ElectricBlue.copy(alpha = 0.12f)
        2 -> if (isDark) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
        3 -> if (isDark) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDark) 0.4f else 0.4f)
    }

    val rankBadgeFg = when (entry.rank) {
        1 -> ElectricBlue
        2 -> MaterialTheme.colorScheme.onSurface
        3 -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isDark) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.40f else 0.65f)
        ),
        shadowElevation = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Rank Squircle Badge (Clean Numeral, No Trophy)
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = rankBadgeBg,
                modifier = Modifier.size(38.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${entry.rank}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = rankBadgeFg
                    )
                }
            }

            // Athlete Info (Bold Name + Subtitle)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = entry.athleteName,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.5.sp),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val scoreStr = if (entry.rawScore % 1.0 == 0.0) entry.rawScore.toInt().toString() else String.format(java.util.Locale.US, "%.1f", entry.rawScore)
                    Text(
                        text = "$scoreStr ${entry.unit}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (!entry.classification.isNullOrBlank()) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                        Surface(
                            shape = RoundedCornerShape(5.dp),
                            color = if (isDark) ElectricBlue.copy(alpha = 0.15f) else ElectricBlue.copy(alpha = 0.08f)
                        ) {
                            Text(
                                text = entry.classification,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = ElectricBlue,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.5.dp)
                            )
                        }
                    }
                }
            }

            // Trend Arrow
            entry.isImproved?.let { improved ->
                val improveColor = if (isDark) PerformanceGreenTextDark else PerformanceGreenText
                val declineColor = if (isDark) PerformanceRedTextDark else PerformanceRedText
                Icon(
                    imageVector = if (improved) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                    contentDescription = if (improved) "Improved" else "Declined",
                    tint = if (improved) improveColor else declineColor,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Percentile Pill
            entry.percentile?.let { p ->
                val (bgColor, textColor) = when {
                    p >= 60 -> if (isDark) PerformanceGreenDark to PerformanceGreenTextDark else PerformanceGreen to PerformanceGreenText
                    p >= 30 -> if (isDark) PerformanceYellowDark to PerformanceYellowTextDark else PerformanceYellow to PerformanceYellowText
                    else -> if (isDark) PerformanceRedDark to PerformanceRedTextDark else PerformanceRed to PerformanceRedText
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = bgColor
                ) {
                    Text(
                        text = "$p%",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 11.5.sp,
                        color = textColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}
