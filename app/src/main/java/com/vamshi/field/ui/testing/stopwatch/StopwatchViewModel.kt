package com.vamshi.field.ui.testing.stopwatch

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vamshi.field.domain.model.people.Individual
import com.vamshi.field.domain.model.standards.FitnessTest
import com.vamshi.field.domain.model.standards.InputParadigm
import com.vamshi.field.domain.model.standards.TimingMode
import com.vamshi.field.domain.model.standards.formatScore
import com.vamshi.field.domain.model.testing.CaptureMethod
import com.vamshi.field.domain.model.testing.TestResult
import com.vamshi.field.domain.repository.PeopleRepository
import com.vamshi.field.domain.repository.TestingRepository
import com.vamshi.field.domain.usecase.testing.RecordTestResultUseCase
import com.vamshi.field.domain.usecase.testing.StopwatchSessionUseCase
import com.vamshi.field.ui.util.countOf
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StopwatchViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val stopwatchSessionUseCase: StopwatchSessionUseCase,
    private val recordResult: RecordTestResultUseCase,
    private val testingRepository: TestingRepository,
    private val peopleRepository: PeopleRepository
) : ViewModel() {

    private val eventId: String = savedStateHandle["eventId"] ?: ""
    private val fitnessTestId: String = savedStateHandle["fitnessTestId"] ?: ""
    private val groupId: String = savedStateHandle["groupId"] ?: ""
    private val initialAthleteId: String? = savedStateHandle["athleteId"]
    private val passedTimingMode: String? = savedStateHandle["timingMode"]

    private val _uiState = MutableStateFlow(StopwatchUiState())
    val uiState: StateFlow<StopwatchUiState> = _uiState.asStateFlow()

    private val _uiEvents = Channel<StopwatchUiEvent>(Channel.BUFFERED)
    val uiEvents = _uiEvents.receiveAsFlow()

    /** Monotonic clock in nanoseconds. Replaced in unit tests so elapsed times are exact. */
    internal var nanoClock: () -> Long = System::nanoTime

    private var tickerJob: Job? = null
    private var startNanos: Long = 0L

    private var athletes: List<Individual> = emptyList()
    private var fitnessTest: FitnessTest? = null

    // Trials recorded per athlete. Rebuilt from [trialsFor] once the DB history has arrived.
    private var completionState = mutableMapOf<String, Int>()
    private var historyLoaded = false

    // Results written by this screen, keyed by athlete — taken from RecordTestResultUseCase's return
    // value rather than re-read from Room, whose Flow can lag a save. An edited result is upserted
    // here so the corrected score wins over the stale DB copy until the Flow catches up.
    private val sessionResults = mutableMapOf<String, MutableList<TestResult>>()

    // Deleted this session. Filters the DB copy too, for the same lag reason: a cleared trial used to
    // stay on screen (and in the trial count) because the session copy above was "never cleared".
    private val deletedResultIds = mutableSetOf<String>()

    private val absentAthletes = mutableSetOf<String>()

    // For GROUP_START mode
    private var heats: List<List<Individual>> = emptyList()
    private var currentHeatIndex: Int = 0
    // Set once no heat has a present athlete with trials left (or the coach left the rest as pending).
    private var heatsDone = false
    private var totalTrialsPerAthlete: Int = 0

    init {
        loadSession()
    }

    private fun loadSession() {
        viewModelScope.launch {
            try {
                val session = stopwatchSessionUseCase(eventId, fitnessTestId, groupId)
                val test = session.fitnessTest
                fitnessTest = test

                athletes = session.athletes
                completionState = session.trialCounts.toMutableMap()
                heats = session.heats
                totalTrialsPerAthlete = session.trialsPerAthlete

                val event = testingRepository.getEventById(eventId)
                val groupName = if (groupId.isNotEmpty()) peopleRepository.getGroupById(groupId)?.name else null

                val resolvedMode = passedTimingMode?.let {
                    try { TimingMode.valueOf(it) } catch (_: Exception) { null }
                } ?: if (test.timingMode == TimingMode.GROUP_START) TimingMode.GROUP_START else TimingMode.INDIVIDUAL

                when (resolvedMode) {
                    TimingMode.INDIVIDUAL -> {
                        _uiState.update {
                            it.copy(
                                mode = TimingMode.INDIVIDUAL,
                                eventName = event?.name ?: "",
                                testName = test.name,
                                testUnit = test.unit,
                                groupName = groupName,
                                trialsPerAthlete = totalTrialsPerAthlete,
                                totalHeats = heats.size,
                                isLoading = false,
                                sessionLoaded = true,
                                selectedAthleteId = initialAthleteId
                            ).withRefreshedAllAthletes()
                        }
                        recomputeSummaryCounts()
                    }
                    TimingMode.GROUP_START -> {
                        currentHeatIndex = findFirstIncompleteHeatIndex()
                        _uiState.update {
                            it.copy(
                                mode = TimingMode.GROUP_START,
                                eventName = event?.name ?: "",
                                testName = test.name,
                                testUnit = test.unit,
                                groupName = groupName,
                                trialsPerAthlete = totalTrialsPerAthlete,
                                heatAthletes = buildHeatAthletesList(),
                                currentHeatNumber = currentHeatIndex + 1,
                                totalHeats = heats.size,
                                isLoading = false,
                                sessionLoaded = true
                            )
                        }
                        recomputeSummaryCounts()
                    }
                    else -> _uiState.update { it.copy(isLoading = false) }
                }

                // DB results: accurate trial counts across sessions, and the ids that make editing possible.
                viewModelScope.launch {
                    testingRepository.getEventResults(eventId).collect { results ->
                        val historyMap = results
                            .filter { it.testId == fitnessTestId }
                            .sortedBy { it.createdAt }
                            .groupBy { it.individualId }
                        historyLoaded = true
                        _uiState.update { it.copy(historicalResults = historyMap) }
                        refreshAfterResultsChanged()
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = e.message, isLoading = false)
                }
            }
        }
    }

    /** Every trial for [athleteId] this screen knows of: session copies win over DB copies, deletions removed. */
    private fun trialsFor(athleteId: String): List<TestResult> {
        val historical = _uiState.value.historicalResults[athleteId].orEmpty()
        val session = sessionResults[athleteId].orEmpty()
        return (session + historical)
            .distinctBy { it.id }
            .filterNot { it.id in deletedResultIds }
            .sortedBy { it.createdAt }
    }

    private fun recomputeCompletionState() {
        // Before the first DB emission we only have the loader's counts; rebuilding from an empty
        // history would wrongly drop them to the session-only number.
        if (!historyLoaded) return
        val ids = athletes.map { it.id }.toSet() + sessionResults.keys + _uiState.value.historicalResults.keys
        completionState = ids.associateWith { trialsFor(it).size }.toMutableMap()
    }

    /** One place to re-derive everything after a save, edit, clear or DB emission. */
    private fun refreshAfterResultsChanged() {
        recomputeCompletionState()
        refreshAllAthletesIfIndividual()
        refreshHeatAthletes()
        recomputeSummaryCounts()
    }

    private fun findFirstIncompleteHeatIndex(): Int = nextIncompleteHeatIndex(from = 0) ?: 0

    /** First heat at or after [from], wrapping round, with a present athlete who still has trials left. */
    private fun nextIncompleteHeatIndex(from: Int): Int? {
        if (heats.isEmpty()) return null
        for (offset in heats.indices) {
            val i = (from + offset) % heats.size
            val hasIncomplete = heats[i].any {
                !absentAthletes.contains(it.id) && (completionState[it.id] ?: 0) < totalTrialsPerAthlete
            }
            if (hasIncomplete) return i
        }
        return null
    }

    private fun buildHeatAthletesList(): List<AthleteQueueItem> {
        if (currentHeatIndex >= heats.size) return emptyList()
        val totalTrials = totalTrialsPerAthlete
        val pendingResults = _uiState.value.pendingResults
        val isRunning = _uiState.value.stopwatchPhase == StopwatchPhase.RUNNING
        return heats[currentHeatIndex].map { athlete ->
            val done = completionState[athlete.id] ?: 0
            val isAbsent = absentAthletes.contains(athlete.id)
            val pendingScore = pendingResults[athlete.id]
            val isPending = pendingScore != null
            val status = when {
                isAbsent -> AthleteStatus.ABSENT
                isPending -> AthleteStatus.CAPTURED
                done >= totalTrials -> AthleteStatus.COMPLETED
                else -> if (isRunning) AthleteStatus.ACTIVE else AthleteStatus.WAITING
            }
            val trials = buildDisplayTrials(athlete.id)
            AthleteQueueItem(
                athleteId = athlete.id,
                name = athlete.fullName,
                currentTrial = minOf(totalTrials, done + (if (isPending) 1 else 0) + 1),
                totalTrials = totalTrials,
                status = status,
                // A completed athlete shows their latest trial; it was always 0:00.00 before.
                capturedTimeMs = pendingScore?.secondsToMs() ?: trials.lastOrNull()?.timeMs,
                displayTrials = trials,
                medicalAlert = athlete.medicalAlert,
                isRestricted = athlete.isRestricted
            )
        }
    }

    private fun buildDisplayTrials(athleteId: String): List<TrialDisplay> =
        trialsFor(athleteId).map { TrialDisplay(timeMs = it.rawScore.secondsToMs(), resultId = it.id) }

    fun onAction(action: StopwatchAction) {
        when (action) {
            is StopwatchAction.OnStartStop -> handleStartStop()
            is StopwatchAction.OnCancelRun -> handleCancelRun()
            is StopwatchAction.OnUndoLastTrial -> handleUndoLastTrial()
            is StopwatchAction.OnSelectAthlete -> {
                val state = _uiState.value
                if (state.mode == TimingMode.INDIVIDUAL && state.stopwatchPhase != StopwatchPhase.RUNNING) {
                    _uiState.update {
                        it.copy(
                            selectedAthleteId = action.athleteId,
                            stopwatchPhase = StopwatchPhase.READY,
                            elapsedMs = 0L,
                            lastTrial = null
                        )
                    }
                }
            }
            is StopwatchAction.OnToggleAbsent -> handleToggleAbsent(action.athleteId)
            is StopwatchAction.OnCaptureTime -> handleCaptureTime(action.athleteId)
            is StopwatchAction.OnOpenEditDialog -> _uiState.update { it.copy(editingAthleteId = action.athleteId, editingResultId = action.resultId) }
            is StopwatchAction.OnSaveEditedTime -> handleSaveEditedTime(action.athleteId, action.newTimeMs, action.resultId)
            is StopwatchAction.OnClearEntry -> handleClearEntry(action.athleteId, action.resultId)
            is StopwatchAction.OnCloseEditDialog -> _uiState.update { it.copy(editingAthleteId = null, editingResultId = null) }
            is StopwatchAction.OnRequestSubmit -> handleRequestSubmit()
            is StopwatchAction.OnDismissMissingEntriesDialog -> _uiState.update { it.copy(showMissingEntriesDialog = false) }
            is StopwatchAction.OnSubmitPending -> submitPending()
            is StopwatchAction.OnDismissError -> _uiState.update { it.copy(errorMessage = null) }
            is StopwatchAction.OnRequestBack -> {
                if (_uiState.value.hasPendingChanges) {
                    _uiState.update { it.copy(showDiscardDialog = true) }
                }
            }
            is StopwatchAction.OnConfirmDiscard -> {
                tickerJob?.cancel()
                _uiState.update {
                    it.copy(
                        showDiscardDialog = false,
                        pendingResults = emptyMap(),
                        lastTrial = null,
                        stopwatchPhase = StopwatchPhase.READY,
                        elapsedMs = 0L
                    )
                }
                refreshAllAthletesIfIndividual()
                refreshHeatAthletes()
            }
            is StopwatchAction.OnDismissDiscard -> _uiState.update { it.copy(showDiscardDialog = false) }
            is StopwatchAction.OnToggleTimingMode -> handleToggleTimingMode(action.mode)
            is StopwatchAction.OnNavigateBack -> Unit
        }
    }

    private fun handleToggleTimingMode(newMode: TimingMode) {
        if (_uiState.value.mode == newMode) return
        if (_uiState.value.stopwatchPhase == StopwatchPhase.RUNNING) return

        if (newMode == TimingMode.INDIVIDUAL) {
            _uiState.update {
                it.copy(
                    mode = TimingMode.INDIVIDUAL,
                    stopwatchPhase = StopwatchPhase.READY,
                    elapsedMs = 0L,
                    lastTrial = null,
                    isSessionComplete = false
                ).withRefreshedAllAthletes()
            }
            recomputeSummaryCounts()
        } else if (newMode == TimingMode.GROUP_START) {
            currentHeatIndex = findFirstIncompleteHeatIndex()
            heatsDone = false
            _uiState.update {
                it.copy(
                    mode = TimingMode.GROUP_START,
                    stopwatchPhase = StopwatchPhase.READY,
                    elapsedMs = 0L,
                    lastTrial = null,
                    heatAthletes = buildHeatAthletesList(),
                    currentHeatNumber = currentHeatIndex + 1,
                    totalHeats = heats.size
                )
            }
            recomputeSummaryCounts()
        }
    }

    private fun handleStartStop() {
        val state = _uiState.value
        if (state.stopwatchPhase == StopwatchPhase.READY) {
            if (state.mode == TimingMode.INDIVIDUAL) {
                val selectedId = state.selectedAthleteId ?: return
                // A finished athlete can be selected to review or edit times, but not timed again:
                // a 4th trial was saved, hidden by the row's take(totalTrials), and still reached reports.
                if ((completionState[selectedId] ?: 0) >= totalTrialsPerAthlete) return
                if (state.isSubmitting) return
            }
            startTimer()
        } else if (state.stopwatchPhase == StopwatchPhase.RUNNING && state.mode == TimingMode.INDIVIDUAL) {
            stopAndSaveIndividual()
        }
    }

    private fun startTimer() {
        tickerJob?.cancel()
        startNanos = nanoClock()
        _uiState.update { it.copy(stopwatchPhase = StopwatchPhase.RUNNING, elapsedMs = 0L, lastTrial = null) }

        if (_uiState.value.mode == TimingMode.GROUP_START) {
            refreshHeatAthletes()
        }

        tickerJob = viewModelScope.launch {
            while (true) {
                val elapsed = (nanoClock() - startNanos) / 1_000_000
                _uiState.update { it.copy(elapsedMs = elapsed) }
                delay(10)
            }
        }
    }

    private fun handleCancelRun() {
        if (_uiState.value.stopwatchPhase != StopwatchPhase.RUNNING) return
        tickerJob?.cancel()
        // Group Heats holds this run's finish taps as pending; a cancelled run must not save them.
        _uiState.update { it.copy(stopwatchPhase = StopwatchPhase.READY, elapsedMs = 0L, pendingResults = emptyMap()) }
        refreshAllAthletesIfIndividual()
        refreshHeatAthletes()
        recomputeSummaryCounts()
    }

    private fun refreshHeatAthletes() {
        if (_uiState.value.mode == TimingMode.GROUP_START) {
            _uiState.update { it.copy(heatAthletes = buildHeatAthletesList()) }
        }
    }

    private fun outOfRangeMessage(rawScore: Double, elapsedMs: Long): String? {
        val test = fitnessTest ?: return null
        val time = "${formatStopwatchTime(elapsedMs)} s"
        return when {
            test.validMin != null && rawScore < test.validMin ->
                "$time is below the ${formatScore(test.validMin, InputParadigm.CHRONO)} s minimum — not saved"
            test.validMax != null && rawScore > test.validMax ->
                "$time is above the ${formatScore(test.validMax, InputParadigm.CHRONO)} s maximum — not saved"
            else -> null
        }
    }

    private fun stopAndSaveIndividual() {
        tickerJob?.cancel()
        val elapsedMs = (nanoClock() - startNanos) / 1_000_000
        val rawScore = elapsedMs / 1000.0
        val state = _uiState.value
        val selectedId = state.selectedAthleteId
        // The clock keeps showing the stopped time; nothing in the save path resets it any more.
        _uiState.update { it.copy(stopwatchPhase = StopwatchPhase.READY, elapsedMs = elapsedMs) }
        if (selectedId == null) return
        val athlete = athletes.find { it.id == selectedId } ?: return
        val trialNumber = (completionState[selectedId] ?: 0) + 1

        val rejection = outOfRangeMessage(rawScore, elapsedMs)
        if (rejection != null) {
            // Same athlete stays selected, so START simply re-runs the trial.
            _uiState.update {
                it.copy(
                    lastTrial = LastTrial(
                        athleteId = selectedId,
                        athleteName = athlete.fullName,
                        trialNumber = trialNumber,
                        totalTrials = totalTrialsPerAthlete,
                        timeMs = elapsedMs,
                        resultId = null,
                        outOfRangeMessage = rejection
                    )
                )
            }
            return
        }

        _uiState.update { it.copy(isSubmitting = true) }
        viewModelScope.launch {
            try {
                val saved = recordResult(
                    eventId, selectedId, fitnessTestId, rawScore,
                    athlete.currentAge.toFloat(), athlete.sex, CaptureMethod.STOPWATCH
                )
                sessionResults.getOrPut(selectedId) { mutableListOf() }.add(saved)
                if (!historyLoaded) completionState[selectedId] = (completionState[selectedId] ?: 0) + 1
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        lastTrial = LastTrial(
                            athleteId = selectedId,
                            athleteName = athlete.fullName,
                            trialNumber = trialNumber,
                            totalTrials = totalTrialsPerAthlete,
                            timeMs = elapsedMs,
                            resultId = saved.id,
                            outOfRangeMessage = null
                        )
                    )
                }
                refreshAfterResultsChanged()
                // Select the next athlete; the result card keeps this one's time on screen.
                handleNext()
            } catch (e: Exception) {
                // Keep the run as an unsaved score so the Submit bar can retry it, instead of losing it.
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        pendingResults = it.pendingResults + (selectedId to rawScore)
                    )
                }
                refreshAllAthletesIfIndividual()
                _uiEvents.send(StopwatchUiEvent.ShowSnackbar("Save failed: ${e.message}"))
            }
        }
    }

    private fun handleUndoLastTrial() {
        val last = _uiState.value.lastTrial ?: return
        if (_uiState.value.stopwatchPhase == StopwatchPhase.RUNNING) return
        last.resultId?.let { clearSavedTrial(it) }
        _uiState.update {
            it.copy(
                lastTrial = null,
                elapsedMs = 0L,
                selectedAthleteId = last.athleteId,
                isSessionComplete = false
            )
        }
        recomputeSummaryCounts()
        viewModelScope.launch { _uiEvents.send(StopwatchUiEvent.ScrollToAthlete(last.athleteId)) }
    }

    private fun handleToggleAbsent(athleteId: String) {
        if (absentAthletes.contains(athleteId)) absentAthletes.remove(athleteId) else absentAthletes.add(athleteId)
        refreshAllAthletesIfIndividual()

        // If the currently selected athlete was just marked absent, move to the next.
        if (_uiState.value.selectedAthleteId == athleteId && absentAthletes.contains(athleteId)) {
            handleNext()
        }

        recomputeSummaryCounts()
        refreshHeatAthletes()
    }

    /** Single source of truth for completedCount/totalCount/absentCount/pendingReviewCount/isSessionComplete.
     *  GROUP_START also completes in advanceToNextHeat() when the last heat is done. */
    private fun recomputeSummaryCounts() {
        val presentAthletes = athletes.filterNot { absentAthletes.contains(it.id) }
        val activeAthletes = presentAthletes.size
        val newTotalCount = activeAthletes * totalTrialsPerAthlete
        val completedTrials = presentAthletes.sumOf { minOf(completionState[it.id] ?: 0, totalTrialsPerAthlete) }
        val fullyCompletedAthletes = presentAthletes.count { (completionState[it.id] ?: 0) >= totalTrialsPerAthlete }
        val pendingReview = activeAthletes - fullyCompletedAthletes

        _uiState.update { state ->
            val allAccountedFor = athletes.isNotEmpty() && athletes.all {
                absentAthletes.contains(it.id) || (completionState[it.id] ?: 0) >= totalTrialsPerAthlete
            }
            // Group Heats is also complete once the last heat has been run, even with athletes
            // left as pending through the Missing Entries dialog.
            val heatsExhausted = state.mode == TimingMode.GROUP_START && heatsDone
            state.copy(
                athleteCount = athletes.size,
                totalCount = newTotalCount,
                completedCount = completedTrials,
                absentCount = absentAthletes.size,
                pendingReviewCount = pendingReview,
                isSessionComplete = (allAccountedFor && state.pendingResults.isEmpty()) || heatsExhausted
            )
        }
    }

    private fun refreshAllAthletesIfIndividual() {
        if (_uiState.value.mode == TimingMode.INDIVIDUAL) {
            _uiState.update { it.withRefreshedAllAthletes() }
        }
    }

    private fun StopwatchUiState.withRefreshedAllAthletes(): StopwatchUiState {
        val all = buildAllAthletesList()
        return copy(
            allAthletes = all,
            completedAthletes = all.filter { it.status == AthleteStatus.COMPLETED },
            upcomingAthletes = all.filterNot { it.status == AthleteStatus.COMPLETED }
        )
    }

    private fun handleCaptureTime(athleteId: String) {
        if (_uiState.value.stopwatchPhase != StopwatchPhase.RUNNING) return
        val elapsedMs = (nanoClock() - startNanos) / 1_000_000
        val rawScore = elapsedMs / 1000.0

        outOfRangeMessage(rawScore, elapsedMs)?.let { message ->
            viewModelScope.launch { _uiEvents.send(StopwatchUiEvent.ShowSnackbar(message)) }
            return
        }

        _uiState.update { s -> s.copy(pendingResults = s.pendingResults + (athleteId to rawScore)) }
        refreshAllAthletesIfIndividual()
        refreshHeatAthletes()

        // Auto-submit in GROUP_START once everyone in the heat is captured, completed or absent.
        if (_uiState.value.mode == TimingMode.GROUP_START) {
            val allCaptured = _uiState.value.heatAthletes.all {
                it.status == AthleteStatus.CAPTURED || it.status == AthleteStatus.COMPLETED || it.status == AthleteStatus.ABSENT
            }
            if (allCaptured) submitPending()
        }
    }

    private fun handleSaveEditedTime(athleteId: String, newTimeMs: Long, resultId: String?) {
        _uiState.update { s -> s.copy(editingAthleteId = null, editingResultId = null) }
        val rawScore = newTimeMs / 1000.0

        if (resultId == null) {
            // Edit a score that hasn't been saved yet (Group Heats capture, or a failed save). Checked
            // now: it used to be accepted and fail later in submit with an unclear "Save failed".
            outOfRangeMessage(rawScore, newTimeMs)?.let { message ->
                viewModelScope.launch { _uiEvents.send(StopwatchUiEvent.ShowSnackbar(message)) }
                return
            }
            _uiState.update { s -> s.copy(pendingResults = s.pendingResults + (athleteId to rawScore)) }
            refreshAllAthletesIfIndividual()
            refreshHeatAthletes()
            return
        }

        // Validate before writing anything: the old delete-then-record lost the trial whenever the
        // new time was out of range, because the record step threw after the delete had happened.
        outOfRangeMessage(rawScore, newTimeMs)?.let { message ->
            viewModelScope.launch { _uiEvents.send(StopwatchUiEvent.ShowSnackbar(message)) }
            return
        }
        val existing = trialsFor(athleteId).find { it.id == resultId } ?: return
        val athlete = athletes.find { it.id == athleteId } ?: return

        viewModelScope.launch {
            try {
                // Same id and createdAt → replaces the row in place and keeps the trial's position.
                val updated = recordResult(
                    eventId, athleteId, fitnessTestId, rawScore,
                    existing.ageAtTime, athlete.sex, existing.captureMethod,
                    resultId = existing.id,
                    createdAt = existing.createdAt
                )
                // Replace at the same index: trials saved within one millisecond share a createdAt,
                // and the sort is stable, so list order is the tiebreak.
                val list = sessionResults.getOrPut(athleteId) { mutableListOf() }
                val index = list.indexOfFirst { it.id == updated.id }
                if (index >= 0) list[index] = updated else list.add(updated)
                _uiState.update { s ->
                    val last = s.lastTrial
                    s.copy(lastTrial = if (last?.resultId == updated.id) last.copy(timeMs = newTimeMs) else last)
                }
                refreshAfterResultsChanged()
            } catch (e: Exception) {
                _uiEvents.send(StopwatchUiEvent.ShowSnackbar("Failed to edit time: ${e.message}"))
            }
        }
    }

    private fun handleClearEntry(athleteId: String, resultId: String?) {
        _uiState.update { s -> s.copy(editingAthleteId = null, editingResultId = null) }
        if (resultId != null) {
            clearSavedTrial(resultId)
        } else {
            _uiState.update { s -> s.copy(pendingResults = s.pendingResults - athleteId) }
            refreshAllAthletesIfIndividual()
            refreshHeatAthletes()
            recomputeSummaryCounts()
        }
    }

    private fun clearSavedTrial(resultId: String) {
        // Hide it immediately; the DB delete follows. Restored if the delete fails.
        deletedResultIds += resultId
        _uiState.update { s ->
            s.copy(lastTrial = s.lastTrial?.takeUnless { it.resultId == resultId })
        }
        if (!historyLoaded) {
            sessionResults.forEach { (id, list) ->
                if (list.removeAll { it.id == resultId }) completionState[id] = maxOf(0, (completionState[id] ?: 1) - 1)
            }
        }
        refreshAfterResultsChanged()
        viewModelScope.launch {
            try {
                testingRepository.deleteResultById(resultId)
            } catch (e: Exception) {
                deletedResultIds -= resultId
                refreshAfterResultsChanged()
                _uiEvents.send(StopwatchUiEvent.ShowSnackbar("Failed to clear time: ${e.message}"))
            }
        }
    }

    private fun handleRequestSubmit() {
        val hasMissing = _uiState.value.mode == TimingMode.GROUP_START &&
            _uiState.value.heatAthletes.any { it.status == AthleteStatus.ACTIVE || it.status == AthleteStatus.WAITING }
        if (hasMissing) {
            _uiState.update { it.copy(showMissingEntriesDialog = true) }
        } else {
            submitPending()
        }
    }

    private fun handleNext() {
        if (_uiState.value.mode == TimingMode.INDIVIDUAL) {
            val all = _uiState.value.allAthletes
            val currentId = _uiState.value.selectedAthleteId
            val currentIndex = all.indexOfFirst { it.athleteId == currentId }

            // Rotate: the next athlete after this one with trials left, wrapping round — and finally
            // this athlete again. Leaving them out stranded the last athlete with trials remaining:
            // the selection went to null and the coach had to re-tap them before every run.
            val nextAthlete = if (currentIndex != -1) {
                all.drop(currentIndex + 1).find { it.status == AthleteStatus.WAITING }
                    ?: all.take(currentIndex).find { it.status == AthleteStatus.WAITING }
                    ?: all[currentIndex].takeIf { it.status == AthleteStatus.WAITING }
            } else {
                all.find { it.status == AthleteStatus.WAITING }
            }

            _uiState.update { state ->
                state.copy(stopwatchPhase = StopwatchPhase.READY, selectedAthleteId = nextAthlete?.athleteId)
            }
            recomputeSummaryCounts()

            if (nextAthlete != null) {
                viewModelScope.launch {
                    _uiEvents.send(StopwatchUiEvent.ScrollToAthlete(nextAthlete.athleteId))
                }
            }
        } else {
            advanceToNextHeat()
        }
    }

    private fun advanceToNextHeat() {
        // Next heat with trials left, wrapping back to heat 1 for the next round. It used to stop after
        // the last heat, so a 3-trial test in Group Heats ran one trial per athlete and called it done.
        val next = nextIncompleteHeatIndex(from = currentHeatIndex + 1)
        if (next == null) {
            heatsDone = true
            _uiState.update { state -> state.copy(isSessionComplete = true) }
        } else {
            currentHeatIndex = next
            _uiState.update { state ->
                state.copy(
                    stopwatchPhase = StopwatchPhase.READY,
                    elapsedMs = 0L,
                    heatAthletes = buildHeatAthletesList(),
                    currentHeatNumber = currentHeatIndex + 1
                )
            }
            recomputeSummaryCounts()
        }
    }

    private fun submitPending() {
        val state = _uiState.value
        if (state.pendingResults.isEmpty() || state.isSubmitting) return

        _uiState.update { it.copy(isSubmitting = true) }
        viewModelScope.launch {
            try {
                for ((athleteId, rawScore) in state.pendingResults) {
                    val athlete = athletes.find { it.id == athleteId } ?: continue
                    val saved = recordResult(
                        eventId, athleteId, fitnessTestId, rawScore,
                        athlete.currentAge.toFloat(), athlete.sex, CaptureMethod.STOPWATCH
                    )
                    sessionResults.getOrPut(athleteId) { mutableListOf() }.add(saved)
                    if (!historyLoaded) completionState[athleteId] = (completionState[athleteId] ?: 0) + 1
                }

                tickerJob?.cancel()
                _uiState.update {
                    it.copy(
                        pendingResults = emptyMap(),
                        isSubmitting = false,
                        showMissingEntriesDialog = false
                    )
                }
                refreshAfterResultsChanged()
                if (state.mode == TimingMode.GROUP_START) {
                    advanceToNextHeat()
                } else {
                    _uiEvents.send(StopwatchUiEvent.ShowSnackbar("${countOf(state.pendingResults.size, "time")} saved"))
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSubmitting = false) }
                _uiEvents.send(StopwatchUiEvent.ShowSnackbar("Save failed: ${e.message}"))
            }
        }
    }

    private fun buildAllAthletesList(): List<AthleteQueueItem> {
        if (athletes.isEmpty()) return emptyList()
        val pendingResults = _uiState.value.pendingResults

        return athletes.map { athlete ->
            val done = completionState[athlete.id] ?: 0
            val pendingScore = pendingResults[athlete.id]
            val isPending = pendingScore != null
            val isAbsent = absentAthletes.contains(athlete.id)

            val status = when {
                isAbsent -> AthleteStatus.ABSENT
                done >= totalTrialsPerAthlete -> AthleteStatus.COMPLETED
                isPending -> AthleteStatus.CAPTURED
                else -> AthleteStatus.WAITING
            }

            AthleteQueueItem(
                athleteId = athlete.id,
                name = athlete.fullName,
                currentTrial = minOf(totalTrialsPerAthlete, done + (if (isPending) 1 else 0) + 1),
                totalTrials = totalTrialsPerAthlete,
                status = status,
                capturedTimeMs = pendingScore?.secondsToMs(),
                displayTrials = buildDisplayTrials(athlete.id),
                medicalAlert = athlete.medicalAlert,
                isRestricted = athlete.isRestricted
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        tickerJob?.cancel()
    }
}
