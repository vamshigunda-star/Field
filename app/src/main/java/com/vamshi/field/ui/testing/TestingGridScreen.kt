package com.vamshi.field.ui.testing

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vamshi.field.ui.components.AppTopBar
import com.vamshi.field.ui.components.AppTopBarSubtitleColor
import com.vamshi.field.ui.components.InlineErrorBanner
import kotlinx.coroutines.delay
import com.vamshi.field.ui.components.FieldLoadingState

@Composable
fun TestingGridScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAthleteReport: (String) -> Unit,
    onNavigateToLeaderboard: (String, String, String) -> Unit,
    onNavigateToGroupReport: (String, String) -> Unit,
    onNavigateToStopwatch: (String, String, String, String?, String?) -> Unit = { _, _, _, _, _ -> },
    // Finish Testing exits: both leave the grid for good, unlike the top-bar report icon.
    onFinishToEventReport: (eventId: String, groupId: String) -> Unit = { _, _ -> },
    onFinishToHome: () -> Unit = onNavigateBack,
    viewModel: TestingGridViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // System back asks first, same as the top-bar arrow. Dialogs shown over the grid consume
    // back themselves, so this only fires from the bare grid.
    BackHandler { viewModel.onAction(TestingGridAction.OnRequestLeave) }

    LaunchedEffect(uiState.leaveConfirmed) {
        if (uiState.leaveConfirmed) {
            viewModel.onAction(TestingGridAction.OnLeaveConsumed)
            onNavigateBack()
        }
    }

    TestingGridContent(
        uiState = uiState,
        eventId = viewModel.eventId,
        groupId = viewModel.groupId,
        onAction = { action ->
            Log.d("TestingGridScreen", "onAction triggered: $action")
            when (action) {
                is TestingGridAction.OnNavigateBack -> onNavigateBack()
                is TestingGridAction.OnNavigateToAthleteReport -> onNavigateToAthleteReport(action.individualId)
                is TestingGridAction.OnNavigateToLeaderboard -> onNavigateToLeaderboard(action.eventId, action.groupId, action.mode)
                is TestingGridAction.OnNavigateToGroupReport -> onNavigateToGroupReport(action.eventId, action.groupId)
                is TestingGridAction.OnNavigateToStopwatch -> onNavigateToStopwatch(action.eventId, action.fitnessTestId, action.groupId, action.individualId, action.timingMode)
                else -> viewModel.onAction(action)
            }
        }
    )

    if (uiState.showCompletionDialog) {
        val uniqueAthletesTested = uiState.gridData?.results?.map { it.individualId }?.distinct()?.size ?: 0
        val totalResults = uiState.gridData?.results?.size ?: 0

        TestingCompleteDialog(
            athleteCount = uniqueAthletesTested,
            testsRecordedCount = totalResults,
            onViewReport = {
                viewModel.onAction(TestingGridAction.OnDismissCompletionDialog)
                onFinishToEventReport(viewModel.eventId, viewModel.groupId)
            },
            onBackToDashboard = {
                viewModel.onAction(TestingGridAction.OnDismissCompletionDialog)
                onFinishToHome()
            },
            onContinueTesting = {
                viewModel.onAction(TestingGridAction.OnDismissCompletionDialog)
            }
        )
    }

    if (uiState.showLeaveDialog) {
        LeaveTestingDialog(
            hasResults = !uiState.gridData?.results.isNullOrEmpty(),
            onKeepTesting = { viewModel.onAction(TestingGridAction.OnDismissLeave) },
            onLeave = { viewModel.onAction(TestingGridAction.OnConfirmLeave) },
            onDiscard = { viewModel.onAction(TestingGridAction.OnDiscardAndLeave) }
        )
    }

    uiState.deleteCandidate?.let { candidate ->
        DeleteResultDialog(
            athleteName = "${candidate.athlete.firstName} ${candidate.athlete.lastName}",
            testName = candidate.test.name,
            onConfirm = { viewModel.onAction(TestingGridAction.OnConfirmDelete) },
            onDismiss = { viewModel.onAction(TestingGridAction.OnDismissDelete) }
        )
    }

    uiState.timingChoiceCell?.let { choice ->
        TimingChoiceDialog(
            athleteName = "${choice.athlete.firstName} ${choice.athlete.lastName}",
            testName = choice.test.name,
            unit = choice.test.unit,
            suggestedMode = choice.test.timingMode,
            onSelectIndividualStopwatch = {
                viewModel.onAction(TestingGridAction.OnSelectTimingMethod(choice.test.id, CaptureMethodPreference.INDIVIDUAL_STOPWATCH))
                viewModel.onAction(TestingGridAction.OnDismissTimingChoice)
                onNavigateToStopwatch(viewModel.eventId, choice.test.id, viewModel.groupId, choice.athlete.id, com.vamshi.field.domain.model.standards.TimingMode.INDIVIDUAL.name)
            },
            onSelectGroupStopwatch = {
                viewModel.onAction(TestingGridAction.OnSelectTimingMethod(choice.test.id, CaptureMethodPreference.GROUP_STOPWATCH))
                viewModel.onAction(TestingGridAction.OnDismissTimingChoice)
                onNavigateToStopwatch(viewModel.eventId, choice.test.id, viewModel.groupId, choice.athlete.id, com.vamshi.field.domain.model.standards.TimingMode.GROUP_START.name)
            },
            onEnterManually = {
                viewModel.onAction(TestingGridAction.OnSelectTimingMethod(choice.test.id, CaptureMethodPreference.MANUAL))
                viewModel.onAction(TestingGridAction.OnDismissTimingChoice)
                viewModel.onAction(TestingGridAction.OnStartEditing(choice.athlete, choice.test))
            },
            onDismiss = { viewModel.onAction(TestingGridAction.OnDismissTimingChoice) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TestingGridContent(
    uiState: TestingGridUiState,
    eventId: String,
    groupId: String,
    onAction: (TestingGridAction) -> Unit
) {
    var sessionSeconds by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L)
            sessionSeconds++
        }
    }

    val sessionTimeStr = remember(sessionSeconds) {
        "%d:%02d".format(sessionSeconds / 60, sessionSeconds % 60)
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = {
                    Column {
                        Text(uiState.event?.name ?: "Live Testing", style = MaterialTheme.typography.titleLarge)
                        Text(sessionTimeStr, style = MaterialTheme.typography.labelSmall, color = AppTopBarSubtitleColor)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { onAction(TestingGridAction.OnRequestLeave) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { onAction(TestingGridAction.OnNavigateToLeaderboard(eventId, groupId, "event")) }) {
                        Icon(Icons.Default.Leaderboard, contentDescription = "Leaderboard")
                    }
                    IconButton(onClick = { onAction(TestingGridAction.OnNavigateToGroupReport(eventId, groupId)) }) {
                        Icon(Icons.Default.Assessment, contentDescription = "Session Report")
                    }
                }
            )
        },
        bottomBar = {
            val totalResults = uiState.gridData?.results?.size ?: 0
            val hasResults = totalResults > 0
            val totalAthletes = uiState.gridData?.students?.size ?: 0
            val uniqueAthletesTested = remember(uiState.gridData?.results) {
                uiState.gridData?.results?.map { it.individualId }?.distinct()?.size ?: 0
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                color = MaterialTheme.colorScheme.background
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (hasResults) "Session in Progress" else "No Results Entered Yet",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (hasResults) "$uniqueAthletesTested/$totalAthletes Athletes • $totalResults Recorded" else "Tap score field to record",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (hasResults) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Button(
                        onClick = { onAction(TestingGridAction.OnRequestSaveSession) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        enabled = hasResults,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Finish Testing",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            if (uiState.errorMessage != null && uiState.gridData != null) {
                InlineErrorBanner(
                    message = uiState.errorMessage,
                    onDismiss = { onAction(TestingGridAction.OnDismissError) },
                    retryLabel = if (uiState.failedAction != null) "Retry" else null,
                    onRetry = if (uiState.failedAction != null) {
                        { onAction(TestingGridAction.OnRetryFailedAction) }
                    } else null
                )
            }

            when {
                uiState.isLoading -> FieldLoadingState()
                uiState.errorMessage != null && uiState.gridData == null -> ErrorState(
                    message = uiState.errorMessage,
                    onDismiss = { onAction(TestingGridAction.OnDismissError) }
                )
                else -> LiveEntryPhase(uiState, eventId, groupId, onAction, PaddingValues(0.dp))
            }
        }
    }

    uiState.editingCell?.let { cell ->
        ScoreEntryDialog(
            athleteName = "${cell.athlete.firstName} ${cell.athlete.lastName}",
            testName = cell.test.name,
            unit = cell.test.unit,
            testDescription = cell.test.description,
            inputParadigm = cell.test.inputParadigm,
            validMin = cell.test.validMin,
            validMax = cell.test.validMax,
            currentResult = cell.currentResult,
            onDismiss = { onAction(TestingGridAction.OnDismissEditing) },
            onSave = { score -> onAction(TestingGridAction.OnSaveScore(score)) },
            onDeleteSaved = {
                cell.currentResult?.let { result ->
                    onAction(TestingGridAction.OnRequestDelete(cell.athlete, cell.test, result.id))
                }
            }
        )
    }
}
