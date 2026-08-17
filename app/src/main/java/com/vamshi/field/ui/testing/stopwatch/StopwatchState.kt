package com.vamshi.field.ui.testing.stopwatch

import com.vamshi.field.domain.model.standards.TimingMode

data class StopwatchUiState(
    val mode: TimingMode = TimingMode.INDIVIDUAL,
    val eventName: String = "",
    val testName: String = "",
    val testUnit: String = "",
    val groupName: String? = null, // Session header context; null if event has no group or lookup failed
    val stopwatchPhase: StopwatchPhase = StopwatchPhase.READY,
    val elapsedMs: Long = 0L,
    val currentAthlete: AthleteQueueItem? = null,
    val allAthletes: List<AthleteQueueItem> = emptyList(), // INDIVIDUAL mode roster; single source of truth, kept in sync by the ViewModel on every mutation
    val completedAthletes: List<AthleteQueueItem> = emptyList(), // allAthletes partitioned by status; hoisted here so the list Composable never re-filters during recomposition
    val upcomingAthletes: List<AthleteQueueItem> = emptyList(),
    val heatAthletes: List<AthleteQueueItem> = emptyList(),
    val currentHeatNumber: Int = 1,
    val totalHeats: Int = 1,
    val completedCount: Int = 0,
    val totalCount: Int = 0,
    val absentCount: Int = 0,
    val pendingReviewCount: Int = 0,
    val trialsPerAthlete: Int = 0,
    val confirmationData: ConfirmationData? = null,
    val canUndo: Boolean = false,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val sessionLoaded: Boolean = false,
    val isSessionComplete: Boolean = false,
    val selectedAthleteId: String? = null,
    val pendingResults: Map<String, Double> = emptyMap(), // athleteId -> rawScore
    val isSubmitting: Boolean = false,
    val showDiscardDialog: Boolean = false,
    val showMissingEntriesDialog: Boolean = false,
    val editingAthleteId: String? = null,
    val editingResultId: String? = null,
    val historicalResults: Map<String, List<com.vamshi.field.domain.model.testing.TestResult>> = emptyMap()
) {
    val hasPendingChanges: Boolean get() = pendingResults.isNotEmpty()
}

enum class StopwatchPhase { READY, RUNNING, CONFIRMING }

enum class AthleteStatus { WAITING, ACTIVE, CAPTURED, COMPLETED, ABSENT }

data class AthleteQueueItem(
    val athleteId: String,
    val name: String,
    val currentTrial: Int,
    val totalTrials: Int,
    val status: AthleteStatus = AthleteStatus.WAITING,
    val capturedTimeMs: Long? = null,
    // Union of DB-confirmed trials (historicalResults) and trials recorded this session, deduped by
    // resultId and sorted by capture order. Session-recorded entries always carry a real resultId
    // (RecordTestResultUseCase returns the persisted TestResult directly), so display never depends on
    // Room's Flow re-emission timing — see StopwatchViewModel.sessionResults.
    val displayTrials: List<TrialDisplay> = emptyList()
)

data class TrialDisplay(val timeMs: Long, val resultId: String?)

data class ConfirmationData(
    val athleteName: String,
    val timeMs: Long,
    val trialNumber: Int
)

sealed interface StopwatchAction {
    data object OnStartStop : StopwatchAction
    data object OnUndo : StopwatchAction
    data object OnDismissError : StopwatchAction
    data object OnNavigateBack : StopwatchAction
    data object OnRequestBack : StopwatchAction
    data object OnConfirmDiscard : StopwatchAction
    data object OnDismissDiscard : StopwatchAction
    data object OnNext : StopwatchAction
    data class OnSelectAthlete(val athleteId: String) : StopwatchAction
    data object OnResetAthlete : StopwatchAction
    
    // New Actions for Continuous Mass Timing
    data class OnToggleAbsent(val athleteId: String) : StopwatchAction
    data class OnCaptureTime(val athleteId: String) : StopwatchAction
    data class OnOpenEditDialog(val athleteId: String, val resultId: String? = null) : StopwatchAction
    data class OnSaveEditedTime(val athleteId: String, val newTimeMs: Long, val resultId: String? = null) : StopwatchAction
    data class OnClearEntry(val athleteId: String, val resultId: String? = null) : StopwatchAction
    data object OnCloseEditDialog : StopwatchAction
    
    data object OnRequestSubmit : StopwatchAction
    data object OnDismissMissingEntriesDialog : StopwatchAction
    data object OnSubmitPending : StopwatchAction
    data class OnToggleTimingMode(val mode: TimingMode) : StopwatchAction
}

sealed interface StopwatchUiEvent {
    data class ScrollToAthlete(val athleteId: String) : StopwatchUiEvent
    data class ShowSnackbar(val message: String) : StopwatchUiEvent
}


