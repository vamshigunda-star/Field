@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.vamshi.field.ui.testing.stopwatch

import com.vamshi.field.ui.util.countOf
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vamshi.field.domain.model.standards.TimingMode
import com.vamshi.field.ui.components.AppTopBar
import com.vamshi.field.ui.components.AppTopBarSubtitleColor
import com.vamshi.field.ui.components.InlineErrorBanner
import com.vamshi.field.ui.theme.*

import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.alpha

import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import com.vamshi.field.ui.components.FieldLoadingState
import com.vamshi.field.ui.components.FieldProgressBar

@Composable
fun StopwatchScreen(
    onNavigateBack: () -> Unit,
    viewModel: StopwatchViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Intercept system back so unsaved drafts trigger the discard dialog before leaving.
    BackHandler(enabled = uiState.hasPendingChanges) {
        viewModel.onAction(StopwatchAction.OnRequestBack)
    }

    LaunchedEffect(viewModel) {
        viewModel.uiEvents.collect { event ->
            when (event) {
                is StopwatchUiEvent.ScrollToAthlete -> {
                    // Position in the list as drawn — [header, completed…, header, upcoming…] —
                    // not in roster order, which pointed at the wrong row once anyone had finished.
                    val index = lazyIndexOf(uiState, event.athleteId)
                    if (index >= 0) {
                        val alreadyVisible = listState.layoutInfo.visibleItemsInfo.any { it.index == index }
                        if (!alreadyVisible) {
                            listState.animateScrollToItem(index)
                        }
                    }
                }
                is StopwatchUiEvent.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(event.message)
                }
            }
        }
    }

    StopwatchContent(
        uiState = uiState,
        allAthletes = uiState.allAthletes,
        listState = listState,
        snackbarHostState = snackbarHostState,
        onAction = { action ->
            when (action) {
                is StopwatchAction.OnNavigateBack -> {
                    if (uiState.hasPendingChanges) {
                        viewModel.onAction(StopwatchAction.OnRequestBack)
                    } else {
                        onNavigateBack()
                    }
                }
                else -> viewModel.onAction(action)
            }
        }
    )

    // No auto-exit on completion: it popped the screen on the final STOP, so the last time was never
    // seen. The completion card's Finish Session button is the way out.

    if (uiState.showDiscardDialog) {
        DiscardPendingDialog(
            count = uiState.pendingResults.size,
            onConfirm = {
                viewModel.onAction(StopwatchAction.OnConfirmDiscard)
                onNavigateBack()
            },
            onDismiss = { viewModel.onAction(StopwatchAction.OnDismissDiscard) }
        )
    }
}

private fun lazyIndexOf(uiState: StopwatchUiState, athleteId: String): Int {
    val completed = uiState.completedAthletes
    val upcoming = uiState.upcomingAthletes
    val completedHeader = if (completed.isNotEmpty()) 1 else 0
    val inCompleted = completed.indexOfFirst { it.athleteId == athleteId }
    if (inCompleted >= 0) return completedHeader + inCompleted
    val inUpcoming = upcoming.indexOfFirst { it.athleteId == athleteId }
    if (inUpcoming >= 0) return completedHeader + completed.size + 1 + inUpcoming
    return -1
}

@Composable
private fun DiscardPendingDialog(count: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Discard unsaved times?") },
        text = {
            Text(
                "${countOf(count, "time")} not submitted. Leaving now will discard " +
                    "${if (count == 1) "it" else "them"}."
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Discard", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Keep editing") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StopwatchContent(
    uiState: StopwatchUiState,
    allAthletes: List<AthleteQueueItem>,
    listState: LazyListState,
    snackbarHostState: SnackbarHostState,
    onAction: (StopwatchAction) -> Unit
) {
    Scaffold(
        topBar = {
            StopwatchTopBar(uiState = uiState, onAction = onAction)
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (uiState.hasPendingChanges) {
                SubmitBar(
                    pendingCount = uiState.pendingResults.size,
                    isSubmitting = uiState.isSubmitting,
                    onSubmit = { onAction(StopwatchAction.OnRequestSubmit) }
                )
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            if (uiState.errorMessage != null && uiState.sessionLoaded) {
                InlineErrorBanner(
                    message = uiState.errorMessage,
                    onDismiss = { onAction(StopwatchAction.OnDismissError) }
                )
            }
            when {
                uiState.isLoading -> FieldLoadingState()
                !uiState.sessionLoaded -> ErrorBox(uiState.errorMessage ?: "Something went wrong", onAction, PaddingValues(0.dp))
                uiState.mode == TimingMode.INDIVIDUAL -> IndividualModeContent(uiState, allAthletes, listState, onAction, PaddingValues(0.dp))
                uiState.isSessionComplete -> {
                    // GROUP_START, all heats run: summary instead of a silent exit.
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CompletionSummaryCard(
                            completedCount = uiState.totalCount / uiState.trialsPerAthlete.coerceAtLeast(1) - uiState.pendingReviewCount,
                            absentCount = uiState.absentCount,
                            pendingCount = uiState.pendingReviewCount,
                            totalCount = uiState.totalCount / uiState.trialsPerAthlete.coerceAtLeast(1) + uiState.absentCount,
                            onFinish = { onAction(StopwatchAction.OnNavigateBack) }
                        )
                    }
                }
                uiState.mode == TimingMode.GROUP_START -> GroupStartModeContent(uiState, onAction, PaddingValues(0.dp))
            }
        }
    }

    if (uiState.editingAthleteId != null) {
        // Group Heats never builds allAthletes, so its edit dialog used to find nobody and not open.
        val source = if (uiState.mode == TimingMode.GROUP_START) uiState.heatAthletes else allAthletes
        val athlete = source.find { it.athleteId == uiState.editingAthleteId }
        if (athlete != null) {
            val initialTimeMs = if (uiState.editingResultId != null) {
                athlete.displayTrials.find { it.resultId == uiState.editingResultId }?.timeMs ?: 0L
            } else {
                athlete.capturedTimeMs ?: 0L
            }
            EditTimeDialog(
                athleteName = athlete.name,
                initialTimeMs = initialTimeMs,
                onDismiss = { onAction(StopwatchAction.OnCloseEditDialog) },
                onSave = { newTime -> onAction(StopwatchAction.OnSaveEditedTime(athlete.athleteId, newTime, uiState.editingResultId)) },
                onClear = { onAction(StopwatchAction.OnClearEntry(athlete.athleteId, uiState.editingResultId)) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StopwatchTopBar(uiState: StopwatchUiState, onAction: (StopwatchAction) -> Unit) {
    Column {
        AppTopBar(
            title = {
                Column {
                    Text(uiState.testName, style = MaterialTheme.typography.headlineMedium)
                    Text(
                        buildSessionContextLine(uiState),
                        style = MaterialTheme.typography.labelMedium,
                        color = AppTopBarSubtitleColor
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = { onAction(StopwatchAction.OnNavigateBack) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )

        // Mode Switcher Segmented Control (only when session is loaded)
        if (uiState.sessionLoaded) {
            StopwatchModeSelector(
                currentMode = uiState.mode,
                enabled = uiState.stopwatchPhase != StopwatchPhase.RUNNING,
                onSelectMode = { onAction(StopwatchAction.OnToggleTimingMode(it)) }
            )
        }

        SaveProgressIndicator(completedCount = uiState.completedCount, totalCount = uiState.totalCount)
    }
}

@Composable
private fun StopwatchModeSelector(
    currentMode: TimingMode,
    enabled: Boolean,
    onSelectMode: (TimingMode) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ModeTabButton(
                title = "Individual",
                icon = Icons.Default.Person,
                isSelected = currentMode == TimingMode.INDIVIDUAL,
                enabled = enabled,
                onClick = { onSelectMode(TimingMode.INDIVIDUAL) },
                modifier = Modifier.weight(1f)
            )
            ModeTabButton(
                title = "Group Heats",
                icon = Icons.Default.Groups,
                isSelected = currentMode == TimingMode.GROUP_START,
                enabled = enabled,
                onClick = { onSelectMode(TimingMode.GROUP_START) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ModeTabButton(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
        shadowElevation = if (isSelected) 2.dp else 0.dp,
        modifier = modifier.height(36.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun buildSessionContextLine(uiState: StopwatchUiState): String {
    val athleteCount = if (uiState.mode == TimingMode.INDIVIDUAL) uiState.allAthletes.size else uiState.heatAthletes.size
    val base = "${countOf(athleteCount, "Athlete")} • ${countOf(uiState.trialsPerAthlete, "Trial")}"
    return if (!uiState.groupName.isNullOrBlank()) "${uiState.groupName} • $base" else base
}

@Composable
private fun SaveProgressIndicator(completedCount: Int, totalCount: Int) {
    val isDark = isSystemInDarkTheme()
    val progress = if (totalCount > 0) completedCount.toFloat() / totalCount else 0f
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        FieldProgressBar(
            progress = progress,
            modifier = Modifier.weight(1f).height(8.dp).padding(end = 12.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
            strokeCap = StrokeCap.Round
        )
        val successBg = if (isDark) PerformanceGreenDark else PerformanceGreen.copy(alpha = 0.15f)
        val successBorder = if (isDark) PerformanceGreenBorderDark else PerformanceGreen
        val successText = if (isDark) PerformanceGreenTextDark else PerformanceGreenText
        Surface(
            color = successBg,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, successBorder)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp), tint = successText)
                Text(
                    "$completedCount of $totalCount Saved",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = successText
                )
            }
        }
    }
}

@Composable
private fun SubmitBar(pendingCount: Int, isSubmitting: Boolean, onSubmit: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primary,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${countOf(pendingCount, "unsaved score")}",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Tap submit to save to the database",
                    color = Color.White.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Button(
                onClick = onSubmit,
                enabled = !isSubmitting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Saving…", fontWeight = FontWeight.Bold)
                } else {
                    Text("Submit All", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ErrorBox(message: String, onAction: (StopwatchAction) -> Unit, padding: PaddingValues) {
    Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(message, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(16.dp))
            Button(onClick = { onAction(StopwatchAction.OnDismissError) }) { Text("Dismiss") }
        }
    }
}

@Composable
private fun IndividualModeContent(
    uiState: StopwatchUiState,
    allAthletes: List<AthleteQueueItem>,
    listState: LazyListState,
    onAction: (StopwatchAction) -> Unit,
    padding: PaddingValues
) {
    val currentAthlete = allAthletes.find { it.athleteId == uiState.selectedAthleteId }
    val haptic = LocalHapticFeedback.current
    val isRunning = uiState.stopwatchPhase == StopwatchPhase.RUNNING
    val lastTrial = uiState.lastTrial.takeIf { !isRunning }
    val selectedIsDone = currentAthlete?.status == AthleteStatus.COMPLETED

    Column(modifier = Modifier.fillMaxSize().padding(padding)) {
        // Sticky control panel: timer, result of the run just stopped, who is next, start/stop.
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            StopwatchDisplay(elapsedMs = uiState.elapsedMs)

            if (lastTrial != null) {
                Spacer(Modifier.height(4.dp))
                LastTrialCard(trial = lastTrial, onUndo = { onAction(StopwatchAction.OnUndoLastTrial) })
            }

            Spacer(Modifier.height(6.dp))

            if (currentAthlete != null) {
                // After a save the selection has already moved on, so name them as next.
                val prefix = if (lastTrial != null) "Up next: " else ""
                Text(
                    text = prefix + currentAthlete.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = if (selectedIsDone) "All ${countOf(currentAthlete.totalTrials, "trial")} recorded · tap a time to edit"
                    else "Trial ${currentAthlete.currentTrial} of ${currentAthlete.totalTrials}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
            } else if (!uiState.isSessionComplete) {
                Text(
                    text = "Select an athlete to begin",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(8.dp))

            if (isRunning || currentAthlete != null) {
                val isEnabled = isRunning || (!selectedIsDone && !uiState.isSubmitting)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .alpha(if (isEnabled) 1f else 0.5f)
                        .acceleratorClick(
                            onClick = {
                                if (isEnabled) {
                                    if (isRunning) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onAction(StopwatchAction.OnStartStop)
                                }
                            }
                        ),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isRunning) MaterialTheme.colorScheme.error else SportOrange
                    )
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Text(
                            if (isRunning) "STOP" else "START",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
            if (isRunning) {
                TextButton(onClick = { onAction(StopwatchAction.OnCancelRun) }) {
                    Text("Cancel run (false start), nothing saved")
                }
            }
        }

        if (uiState.isSessionComplete) {
            CompletionSummaryCard(
                completedCount = allAthletes.count { it.status == AthleteStatus.COMPLETED },
                absentCount = uiState.absentCount,
                pendingCount = uiState.pendingReviewCount,
                totalCount = allAthletes.size,
                onFinish = { onAction(StopwatchAction.OnNavigateBack) }
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(bottom = 80.dp, top = 4.dp)
        ) {
            if (uiState.completedAthletes.isNotEmpty()) {
                item(key = "section_completed") {
                    AthleteSectionHeader(
                        icon = "✅",
                        label = "Completed",
                        count = uiState.completedAthletes.size,
                        tint = if (isSystemInDarkTheme()) PerformanceGreenTextDark else PerformanceGreenText
                    )
                }
                items(uiState.completedAthletes, key = { it.athleteId }) { athlete ->
                    IndividualAthleteRow(
                        athlete = athlete,
                        isSelected = athlete.athleteId == uiState.selectedAthleteId,
                        onClick = { onAction(StopwatchAction.OnSelectAthlete(athlete.athleteId)) },
                        onEditTrial = { resultId -> onAction(StopwatchAction.OnOpenEditDialog(athlete.athleteId, resultId)) },
                        onToggleAbsent = { onAction(StopwatchAction.OnToggleAbsent(athlete.athleteId)) }
                    )
                }
            }
            if (uiState.upcomingAthletes.isNotEmpty()) {
                item(key = "section_upcoming") {
                    AthleteSectionHeader(
                        icon = "⏳",
                        label = "Upcoming",
                        count = uiState.upcomingAthletes.size,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                items(uiState.upcomingAthletes, key = { it.athleteId }) { athlete ->
                    IndividualAthleteRow(
                        athlete = athlete,
                        isSelected = athlete.athleteId == uiState.selectedAthleteId,
                        onClick = { onAction(StopwatchAction.OnSelectAthlete(athlete.athleteId)) },
                        onEditTrial = { resultId -> onAction(StopwatchAction.OnOpenEditDialog(athlete.athleteId, resultId)) },
                        onToggleAbsent = { onAction(StopwatchAction.OnToggleAbsent(athlete.athleteId)) }
                    )
                }
            }
        }
    }
}

/** The run just stopped: whose it was, the time, and whether it was saved. Undo deletes it and re-selects them. */
@Composable
private fun LastTrialCard(trial: LastTrial, onUndo: () -> Unit) {
    val isDark = isSystemInDarkTheme()
    val saved = trial.outOfRangeMessage == null
    val bg = when {
        !saved -> MaterialTheme.colorScheme.errorContainer
        isDark -> PerformanceGreenDark
        else -> PerformanceGreen.copy(alpha = 0.15f)
    }
    val fg = when {
        !saved -> MaterialTheme.colorScheme.onErrorContainer
        isDark -> PerformanceGreenTextDark
        else -> PerformanceGreenText
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = bg,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, fg.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${trial.athleteName} · Trial ${trial.trialNumber} of ${trial.totalTrials}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = fg,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${formatStopwatchTime(trial.timeMs)} s",
                        style = MaterialTheme.typography.headlineSmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = fg
                    )
                    if (saved) {
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Default.Check, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
                        Text("Saved", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = fg)
                    }
                }
                trial.outOfRangeMessage?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = fg)
                }
            }
            TextButton(onClick = onUndo) {
                Text(if (saved) "Undo" else "Retry", color = fg, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun AthleteSectionHeader(icon: String, label: String, count: Int, tint: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(icon, style = MaterialTheme.typography.labelLarge)
        Text(
            "$label ($count)",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = tint
        )
    }
}

@Composable
private fun CompletionSummaryCard(
    completedCount: Int,
    absentCount: Int,
    pendingCount: Int,
    totalCount: Int,
    onFinish: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val completedColor = if (isDark) PerformanceGreenTextDark else PerformanceGreenText
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "$completedCount of ${countOf(totalCount, "athlete")} completed",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SummaryStat(label = "Completed", value = completedCount, color = completedColor)
                SummaryStat(label = "Absent", value = absentCount, color = MaterialTheme.colorScheme.error)
                SummaryStat(label = "Pending", value = pendingCount, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = onFinish, modifier = Modifier.fillMaxWidth()) {
                Text("Finish Session", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SummaryStat(label: String, value: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun GroupStartModeContent(uiState: StopwatchUiState, onAction: (StopwatchAction) -> Unit, padding: PaddingValues) {
    val haptic = LocalHapticFeedback.current
    Column(modifier = Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Heat ${uiState.currentHeatNumber} of ${uiState.totalHeats}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(16.dp))
        StopwatchDisplay(elapsedMs = uiState.elapsedMs)
        Spacer(Modifier.height(16.dp))

        if (uiState.stopwatchPhase == StopwatchPhase.READY) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .height(80.dp)
                    .acceleratorClick(
                        onClick = { onAction(StopwatchAction.OnStartStop) }
                    ),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = if (isSystemInDarkTheme()) PerformanceGreenDark else PerformanceGreenText)
            ) { Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) { Text("START SESSION", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.White) } }
        }

        Spacer(Modifier.height(16.dp))
        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 24.dp)) {
            items(uiState.heatAthletes, key = { it.athleteId }) { athlete ->
                GroupAthleteRow(
                    athlete = athlete,
                    isRunning = uiState.stopwatchPhase == StopwatchPhase.RUNNING,
                    onToggleAbsent = { onAction(StopwatchAction.OnToggleAbsent(athlete.athleteId)) },
                    onCapture = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onAction(StopwatchAction.OnCaptureTime(athlete.athleteId))
                    },
                    onEdit = { onAction(StopwatchAction.OnOpenEditDialog(athlete.athleteId)) }
                )
            }
        }

    }

    if (uiState.showMissingEntriesDialog) {
        AlertDialog(
            onDismissRequest = { onAction(StopwatchAction.OnDismissMissingEntriesDialog) },
            title = { Text("Missing Entries") },
            text = { Text("Some athletes do not have recorded times. What would you like to do?") },
            confirmButton = {
                TextButton(onClick = {
                    onAction(StopwatchAction.OnDismissMissingEntriesDialog)
                    onAction(StopwatchAction.OnSubmitPending)
                }) {
                    Text("Leave as Pending")
                }
            },
            dismissButton = {
                TextButton(onClick = { onAction(StopwatchAction.OnDismissMissingEntriesDialog) }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun StopwatchDisplay(elapsedMs: Long) {
    val minutes = (elapsedMs / 60000).toInt()
    val seconds = ((elapsedMs % 60000) / 1000).toInt()
    val centis = ((elapsedMs % 1000) / 10).toInt()

    Text(
        text = "%02d:%02d.%02d".format(minutes, seconds, centis),
        style = MaterialTheme.typography.displayLarge,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}

// Shared minimal row: name (+ optional subtitle) on the left, mode-specific trailing content on the
// right — the same pattern Group Testing already used (HeatAthleteRow) and Individual mode now reuses,
// instead of each mode carrying its own card paradigm.
@Composable
private fun AthleteTimingRow(
    name: String,
    highlighted: Boolean,
    muted: Boolean,
    subtitle: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    trailing: @Composable () -> Unit
) {
    val borderColor = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    val bgColor = if (muted) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        color = bgColor,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(if (highlighted) 2.dp else 1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    color = if (muted) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (muted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

@Composable
private fun IndividualAthleteRow(
    athlete: AthleteQueueItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    onEditTrial: (String?) -> Unit,
    onToggleAbsent: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val completedColor = if (isDark) PerformanceGreenTextDark else PerformanceGreenText
    val isAbsent = athlete.status == AthleteStatus.ABSENT
    val isCompleted = athlete.status == AthleteStatus.COMPLETED
    val trials = athlete.displayTrials
    val subtitle = when {
        isAbsent -> "Absent"
        !isCompleted && athlete.totalTrials > 1 -> "Trial ${athlete.currentTrial} of ${athlete.totalTrials}"
        else -> null
    }

    AthleteTimingRow(
        name = athlete.name,
        highlighted = isSelected,
        muted = isAbsent,
        subtitle = subtitle,
        onClick = { if (!isAbsent) onClick() },
        onLongClick = {},
        trailing = {
            // Times show from the first trial on. They used to appear only once every trial was done,
            // so on a 3-trial test the coach saw no time for an athlete until their third run.
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                trials.forEach { trial ->
                    TrialChip(
                        timeMs = trial.timeMs,
                        color = if (isCompleted) completedColor else MaterialTheme.colorScheme.onSurface,
                        onClick = { trial.resultId?.let(onEditTrial) }
                    )
                }
                when {
                    athlete.status == AthleteStatus.CAPTURED && athlete.capturedTimeMs != null -> TrialChip(
                        timeMs = athlete.capturedTimeMs,
                        color = MaterialTheme.colorScheme.primary,
                        onClick = { onEditTrial(null) }
                    )
                    trials.isNotEmpty() -> Unit
                    isSelected -> Surface(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(6.dp)) {
                        Text(
                            "CURRENT",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    else -> {
                        Text("Absent?", style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.width(4.dp))
                        Switch(
                            checked = isAbsent,
                            onCheckedChange = { onToggleAbsent() },
                            modifier = Modifier.height(20.dp).scale(0.65f)
                        )
                    }
                }
            }
        }
    )
}

/** One trial's time. Tapping opens the editor for that trial. */
@Composable
private fun TrialChip(timeMs: Long, color: Color, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f))
    ) {
        Text(
            formatStopwatchTime(timeMs),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.titleSmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
private fun GroupAthleteRow(
    athlete: AthleteQueueItem,
    isRunning: Boolean,
    onToggleAbsent: () -> Unit,
    onCapture: () -> Unit,
    onEdit: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val completedColor = if (isDark) PerformanceGreenTextDark else PerformanceGreenText
    val isAbsent = athlete.status == AthleteStatus.ABSENT
    val isDone = athlete.status == AthleteStatus.CAPTURED || athlete.status == AthleteStatus.COMPLETED

    AthleteTimingRow(
        name = athlete.name,
        highlighted = false,
        muted = isAbsent,
        subtitle = if (isAbsent) "Absent" else null,
        onClick = {
            when {
                isDone -> onEdit()
                isRunning && !isAbsent -> onCapture()
            }
        },
        onLongClick = {},
        trailing = {
            when {
                isDone -> Text(
                    formatStopwatchTime(athlete.capturedTimeMs ?: 0L),
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = completedColor
                )
                !isRunning -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Absent?", style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.width(4.dp))
                    Switch(
                        checked = isAbsent,
                        onCheckedChange = { onToggleAbsent() },
                        modifier = Modifier.height(20.dp).scale(0.65f)
                    )
                }
                !isAbsent -> Text("TAP TO FINISH", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                else -> Spacer(Modifier.width(1.dp))
            }
        }
    )
}

@Composable
private fun EditTimeDialog(
    athleteName: String,
    initialTimeMs: Long,
    onDismiss: () -> Unit,
    onSave: (Long) -> Unit,
    onClear: () -> Unit
) {
    var inputString by remember { mutableStateOf(formatStopwatchTime(initialTimeMs).replace(Regex("[^0-9]"), "")) }
    val displayMs = inputString.toLongOrNull()?.let { it * 10 } ?: 0L

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Time for $athleteName") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = formatStopwatchTime(displayMs),
                    style = MaterialTheme.typography.displayMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(24.dp))
                val keys = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("00", "0", "DEL")
                )
                keys.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                        row.forEach { key ->
                            Button(
                                onClick = {
                                    if (key == "DEL") {
                                        if (inputString.isNotEmpty()) inputString = inputString.dropLast(1)
                                    } else {
                                        if (inputString.length < 6) inputString += key
                                    }
                                },
                                modifier = Modifier.size(64.dp),
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurface)
                            ) {
                                Text(key, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(displayMs) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(
                onClick = { onClear(); onDismiss() },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Text("Clear Entry") }
        }
    )
}

