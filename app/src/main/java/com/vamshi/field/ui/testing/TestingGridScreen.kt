package com.vamshi.field.ui.testing

import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vamshi.field.ui.components.AppTopBar
import com.vamshi.field.ui.components.AppTopBarSubtitleColor
import com.vamshi.field.ui.components.InlineErrorBanner
import kotlinx.coroutines.delay

@Composable
fun TestingGridScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAthleteReport: (String) -> Unit,
    onNavigateToLeaderboard: (String, String, String) -> Unit,
    onNavigateToGroupReport: (String, String) -> Unit,
    onNavigateToStopwatch: (String, String, String, String?, String?) -> Unit = { _, _, _, _, _ -> },
    viewModel: TestingGridViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

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
    var sessionSeconds by remember { mutableIntStateOf(0) }

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
                    IconButton(onClick = { onAction(TestingGridAction.OnNavigateBack) }) {
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
                uiState.isLoading -> LoadingState()
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
