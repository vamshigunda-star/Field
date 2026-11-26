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
    val athleteCount: Int = 0, // whole group, both modes — the header counted only the current heat
    val absentCount: Int = 0,
    val pendingReviewCount: Int = 0,
    val trialsPerAthlete: Int = 0,
    // The trial just captured. Stays on the timer panel until the next START, so the coach
    // actually sees the time they stopped — it used to be reset to 0 in the same frame as STOP.
    val lastTrial: LastTrial? = null,
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

enum class StopwatchPhase { READY, RUNNING }

enum class AthleteStatus { WAITING, ACTIVE, CAPTURED, COMPLETED, ABSENT }

data class AthleteQueueItem(
    val athleteId: String,
    val name: String,
    val currentTrial: Int,
    val totalTrials: Int,
    val status: AthleteStatus = AthleteStatus.WAITING,
    val capturedTimeMs: Long? = null,
    // Safety: shown on the timer panel and heat rows before the athlete runs.
    val medicalAlert: String? = null,
    val isRestricted: Boolean = false,
    // Union of DB-confirmed trials (historicalResults) and trials recorded this session, deduped by
    // resultId and sorted by capture order. Session-recorded entries always carry a real resultId
    // (RecordTestResultUseCase returns the persisted TestResult directly), so display never depends on
    // Room's Flow re-emission timing — see StopwatchViewModel.sessionResults.
    val displayTrials: List<TrialDisplay> = emptyList()
)

data class TrialDisplay(val timeMs: Long, val resultId: String?)

data class LastTrial(
    val athleteId: String,
    val athleteName: String,
    val trialNumber: Int,
    val totalTrials: Int,
    val timeMs: Long,
    val resultId: String?,          // null when the time was rejected and nothing was saved
    val outOfRangeMessage: String?  // non-null → not saved; START re-runs the same athlete
)

/** "4.87" or "1:02.35". Truncates to hundredths like a hand stopwatch. */
internal fun formatStopwatchTime(ms: Long): String {
    val minutes = (ms / 60000).toInt()
    val seconds = ((ms % 60000) / 1000).toInt()
    val centis = ((ms % 1000) / 10).toInt()
    return if (minutes > 0) "%d:%02d.%02d".format(minutes, seconds, centis) else "%d.%02d".format(seconds, centis)
}

/**
 * The edit keypad types digits right-to-left as centiseconds (like a cash register), so its text is
 * the time in centiseconds. It used to be seeded from the *formatted* time with punctuation
 * stripped — "1:02.35" became "10235", read back as 102.35 s — so every edit of a time over a
 * minute added 40 s per minute, even when the coach changed nothing.
 */
internal fun editSeed(ms: Long): String = if (ms <= 0L) "" else (ms / 10).toString()

internal fun parseEditCentis(text: String): Long = (text.toLongOrNull() ?: 0L) * 10

/** Seven digits: up to 9999.99 s, which covers the 1.5-mile run's 2400 s ceiling. */
internal const val EDIT_MAX_DIGITS = 7

/** Seconds → ms, rounded. Truncating showed 4.02 s as 4.01 (4.02 * 1000 = 4019.999…). */
internal fun Double.secondsToMs(): Long = kotlin.math.round(this * 1000).toLong()

sealed interface StopwatchAction {
    data object OnStartStop : StopwatchAction
    /** False start: stop the clock without saving anything. */
    data object OnCancelRun : StopwatchAction
    /** Delete the trial on the result card and select that athlete again. */
    data object OnUndoLastTrial : StopwatchAction
    data object OnDismissError : StopwatchAction
    data object OnNavigateBack : StopwatchAction
    data object OnRequestBack : StopwatchAction
    data object OnConfirmDiscard : StopwatchAction
    data object OnDismissDiscard : StopwatchAction
    data class OnSelectAthlete(val athleteId: String) : StopwatchAction
    
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


