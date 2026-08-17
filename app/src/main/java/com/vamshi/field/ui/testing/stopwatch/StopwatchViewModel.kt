package com.vamshi.field.ui.testing.stopwatch

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vamshi.field.domain.model.people.Individual
import com.vamshi.field.domain.model.standards.TimingMode
import com.vamshi.field.domain.model.testing.CaptureMethod
import com.vamshi.field.domain.model.testing.TestResult
import com.vamshi.field.domain.repository.PeopleRepository
import com.vamshi.field.domain.repository.TestingRepository
import com.vamshi.field.domain.usecase.testing.RecordTestResultUseCase
import com.vamshi.field.domain.usecase.testing.StopwatchSessionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
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
    private val initialAthleteId: String? = savedStateHandle["athleteId"] // New: handle passed athleteId
    private val passedTimingMode: String? = savedStateHandle["timingMode"]

    private val _uiState = MutableStateFlow(StopwatchUiState())
    val uiState: StateFlow<StopwatchUiState> = _uiState.asStateFlow()

    private val _uiEvents = Channel<StopwatchUiEvent>()
    val uiEvents = _uiEvents.receiveAsFlow()

    private var tickerJob: Job? = null
    private var confirmationJob: Job? = null
    private var startNanos: Long = 0L

    // Full list of unique athletes for this test session
    private var athletes: List<Individual> = emptyList()
    
    // Tracks completed trial counts per athlete for this specific session
    private var completionState = mutableMapOf<String, Int>()

    // TestResults recorded via this ViewModel this session, captured directly from
    // RecordTestResultUseCase's return value (not from re-querying Room). This is the source of truth
    // for "what did I just save" — historicalResults (sourced from Room's Flow, see loadSession) can lag
    // or even miss an invalidation notification entirely, so display must never depend on it alone.
    // Never cleared; buildDisplayTrials dedupes against historicalResults by id once the Flow catches up.
    private val sessionResults = mutableMapOf<String, MutableList<TestResult>>()

    private val absentAthletes = mutableSetOf<String>()

    // For GROUP_START mode
    private var heats: List<List<Individual>> = emptyList()
    private var currentHeatIndex: Int = 0
    private var totalTrialsPerAthlete: Int = 0

    init {
        loadSession()
    }

    private fun loadSession() {
        viewModelScope.launch {
            try {
                val session = stopwatchSessionUseCase(eventId, fitnessTestId, groupId)
                val test = session.fitnessTest

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
                                selectedAthleteId = initialAthleteId // Use passed ID instead of first entry
                            ).withRefreshedAllAthletes()
                        }
                        recomputeSummaryCounts()
                    }
                    TimingMode.GROUP_START -> {
                        currentHeatIndex = findFirstIncompleteHeat(session)
                        _uiState.update {
                            it.copy(
                                mode = TimingMode.GROUP_START,
                                eventName = event?.name ?: "",
                                testName = test.name,
                                testUnit = test.unit,
                                groupName = groupName,
                                trialsPerAthlete = totalTrialsPerAthlete,
                                heatAthletes = buildHeatAthletesList(session.trialsPerAthlete),
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

                // Collect actual database results to ensure 100% accurate trail counts and enable past trial editing
                viewModelScope.launch {
                    testingRepository.getEventResults(eventId).collect { results ->
                        val testResults = results.filter { it.testId == fitnessTestId }.sortedBy { it.createdAt }
                        val historyMap = testResults.groupBy { it.individualId }

                        // completionState per athlete is the max of what the DB Flow currently shows and
                        // what we know we've recorded this session (sessionResults) — the Flow can lag or
                        // even miss a notification, so it must never be allowed to regress a count we
                        // already confirmed synchronously via a successful recordResult() call.
                        val newCompletionState = mutableMapOf<String, Int>()
                        (historyMap.keys + sessionResults.keys).forEach { id ->
                            newCompletionState[id] = maxOf(historyMap[id]?.size ?: 0, sessionResults[id]?.size ?: 0)
                        }

                        completionState = newCompletionState
                        _uiState.update {
                            val withHistory = it.copy(historicalResults = historyMap)
                            if (withHistory.mode == TimingMode.INDIVIDUAL) withHistory.withRefreshedAllAthletes() else withHistory
                        }
                        recomputeSummaryCounts()
                        refreshHeatAthletes()
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = e.message, isLoading = false)
                }
            }
        }
    }

    private fun findFirstIncompleteHeat(session: com.vamshi.field.domain.usecase.testing.StopwatchSession): Int {
        for (i in heats.indices) {
            val hasIncomplete = heats[i].any { (completionState[it.id] ?: 0) < session.trialsPerAthlete }
            if (hasIncomplete) return i
        }
        return 0
    }

    private fun buildHeatAthletesList(totalTrials: Int): List<AthleteQueueItem> {
        if (currentHeatIndex >= heats.size) return emptyList()
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
            AthleteQueueItem(
                athleteId = athlete.id,
                name = athlete.fullName,
                currentTrial = minOf(totalTrials, done + (if (isPending) 1 else 0) + 1),
                totalTrials = totalTrials,
                status = status,
                capturedTimeMs = if (isPending) (pendingScore * 1000).toLong() else null,
                displayTrials = buildDisplayTrials(athlete.id)
            )
        }
    }

    private fun buildDisplayTrials(athleteId: String): List<TrialDisplay> {
        val historical = _uiState.value.historicalResults[athleteId].orEmpty()
        val session = sessionResults[athleteId].orEmpty()
        return (historical + session)
            .distinctBy { it.id }
            .sortedBy { it.createdAt }
            .map { TrialDisplay(timeMs = (it.rawScore * 1000).toLong(), resultId = it.id) }
    }

    fun onAction(action: StopwatchAction) {
        when (action) {
            is StopwatchAction.OnStartStop -> handleStartStop()
            is StopwatchAction.OnUndo -> handleUndo()
            is StopwatchAction.OnNext -> handleNext()
            is StopwatchAction.OnSelectAthlete -> {
                val currentPhase = _uiState.value.stopwatchPhase
                val currentMode = _uiState.value.mode
                
                if (currentMode == TimingMode.INDIVIDUAL) {
                    if (currentPhase != StopwatchPhase.RUNNING) {
                        _uiState.update { it.copy(
                            selectedAthleteId = action.athleteId,
                            stopwatchPhase = StopwatchPhase.READY,
                            elapsedMs = 0L,
                            confirmationData = null,
                            canUndo = false
                        ) }
                    }
                }
            }
            is StopwatchAction.OnResetAthlete -> handleResetAthlete()
            
            // New Mass Timing Actions
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
                        confirmationData = null,
                        canUndo = false,
                        stopwatchPhase = StopwatchPhase.READY,
                        elapsedMs = 0L,
                        heatAthletes = it.heatAthletes.map { a ->
                            if (a.status == AthleteStatus.CAPTURED) a.copy(status = AthleteStatus.WAITING, capturedTimeMs = null) else a
                        }
                    )
                }
                refreshAllAthletesIfIndividual()
            }
            is StopwatchAction.OnDismissDiscard -> {
                _uiState.update { it.copy(showDiscardDialog = false) }
            }
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
                    elapsedMs = 0L
                ).withRefreshedAllAthletes()
            }
            recomputeSummaryCounts()
        } else if (newMode == TimingMode.GROUP_START) {
            currentHeatIndex = findFirstIncompleteHeatIndex()
            _uiState.update {
                it.copy(
                    mode = TimingMode.GROUP_START,
                    stopwatchPhase = StopwatchPhase.READY,
                    elapsedMs = 0L,
                    heatAthletes = buildHeatAthletesList(totalTrialsPerAthlete),
                    currentHeatNumber = currentHeatIndex + 1,
                    totalHeats = heats.size
                )
            }
            recomputeSummaryCounts()
        }
    }

    private fun findFirstIncompleteHeatIndex(): Int {
        for (i in heats.indices) {
            val hasIncomplete = heats[i].any { (completionState[it.id] ?: 0) < totalTrialsPerAthlete }
            if (hasIncomplete) return i
        }
        return 0
    }

    private fun handleStartStop() {
        val phase = _uiState.value.stopwatchPhase
        if (phase == StopwatchPhase.READY) {
            // In INDIVIDUAL mode, prevent starting without an athlete selected.
            if (_uiState.value.mode == TimingMode.INDIVIDUAL && _uiState.value.selectedAthleteId == null) {
                return
            }
            startTimer()
        } else if (phase == StopwatchPhase.RUNNING && _uiState.value.mode == TimingMode.INDIVIDUAL) {
            stopAndStageIndividual()
        }
    }

    private fun startTimer() {
        tickerJob?.cancel() // Ensure no overlapping timers
        startNanos = System.nanoTime()
        _uiState.update { it.copy(stopwatchPhase = StopwatchPhase.RUNNING, elapsedMs = 0L) }

        if (_uiState.value.mode == TimingMode.GROUP_START) {
            refreshHeatAthletes()
        }

        tickerJob = viewModelScope.launch {
            while (true) {
                val elapsed = (System.nanoTime() - startNanos) / 1_000_000
                _uiState.update { it.copy(elapsedMs = elapsed) }
                delay(10)
            }
        }
    }

    private fun refreshHeatAthletes() {
        if (_uiState.value.mode == TimingMode.GROUP_START) {
            val trialsPerAthlete = if (athletes.isNotEmpty()) _uiState.value.totalCount / athletes.size else 0
            _uiState.update { it.copy(heatAthletes = buildHeatAthletesList(trialsPerAthlete)) }
        }
    }

    private fun stopAndStageIndividual() {
        tickerJob?.cancel()
        _uiState.update { it.copy(stopwatchPhase = StopwatchPhase.READY) }

        val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000
        val selectedId = _uiState.value.selectedAthleteId ?: return

        _uiState.update {
            it.copy(
                elapsedMs = elapsedMs,
                pendingResults = it.pendingResults + (selectedId to elapsedMs / 1000.0)
            )
        }
        refreshAllAthletesIfIndividual()
        submitPending() // Auto-save automatically
    }

    private fun handleToggleAbsent(athleteId: String) {
        if (absentAthletes.contains(athleteId)) absentAthletes.remove(athleteId) else absentAthletes.add(athleteId)
        
        // If the currently selected athlete was just marked absent, move to the next.
        if (_uiState.value.selectedAthleteId == athleteId && absentAthletes.contains(athleteId)) {
            handleNext()
        }
        
        refreshAllAthletesIfIndividual()
        recomputeSummaryCounts()
        refreshHeatAthletes()
    }

    /** Single source of truth for completedCount/totalCount/absentCount/pendingReviewCount/isSessionComplete.
     *  GROUP_START's isSessionComplete is owned by advanceToNextHeat() (heat-exhaustion based) and is left
     *  untouched here — this function only decides isSessionComplete for INDIVIDUAL mode. */
    private fun recomputeSummaryCounts() {
        val presentAthletes = athletes.filterNot { absentAthletes.contains(it.id) }
        val activeAthletes = presentAthletes.size
        val newTotalCount = activeAthletes * totalTrialsPerAthlete
        val completedTrials = completionState.values.sum()
        val fullyCompletedAthletes = presentAthletes.count { (completionState[it.id] ?: 0) >= totalTrialsPerAthlete }
        val pendingReview = activeAthletes - fullyCompletedAthletes

        _uiState.update { state ->
            val isComplete = if (state.mode == TimingMode.INDIVIDUAL) {
                (activeAthletes > 0 && fullyCompletedAthletes == activeAthletes && state.pendingResults.isEmpty() && state.selectedAthleteId == null) ||
                (athletes.isNotEmpty() && absentAthletes.size == athletes.size)
            } else {
                // For GROUP_START, session is complete if all athletes are either absent or have all trials recorded
                val allAccountedFor = athletes.all { 
                    absentAthletes.contains(it.id) || (completionState[it.id] ?: 0) >= totalTrialsPerAthlete 
                }
                allAccountedFor && state.pendingResults.isEmpty()
            }
            state.copy(
                totalCount = newTotalCount,
                completedCount = completedTrials,
                absentCount = absentAthletes.size,
                pendingReviewCount = pendingReview,
                isSessionComplete = isComplete
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
        val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000

        _uiState.update { s ->
            s.copy(pendingResults = s.pendingResults + (athleteId to elapsedMs / 1000.0))
        }
        refreshAllAthletesIfIndividual()
        refreshHeatAthletes()

        // Auto-submit in GROUP_START if all athletes in current heat are captured or already completed/absent
        if (_uiState.value.mode == TimingMode.GROUP_START) {
            val allCaptured = _uiState.value.heatAthletes.all { 
                it.status == AthleteStatus.CAPTURED || it.status == AthleteStatus.COMPLETED || it.status == AthleteStatus.ABSENT 
            }
            if (allCaptured) {
                submitPending()
            }
        }
    }

    private fun handleSaveEditedTime(athleteId: String, newTimeMs: Long, resultId: String?) {
        if (resultId != null) {
            // Edit historical result
            viewModelScope.launch {
                try {
                    val athlete = athletes.find { it.id == athleteId } ?: return@launch
                    val age = ((System.currentTimeMillis() - athlete.dateOfBirth) / 31_557_600_000L).toFloat()
                    testingRepository.deleteResultById(resultId)
                    recordResult(eventId, athleteId, fitnessTestId, newTimeMs / 1000.0, age, athlete.sex, CaptureMethod.STOPWATCH)
                } catch (e: Exception) {
                    _uiEvents.send(StopwatchUiEvent.ShowSnackbar("Failed to edit time: ${e.message}"))
                }
            }
            _uiState.update { s -> s.copy(editingAthleteId = null, editingResultId = null) }
        } else {
            // Edit pending result
            _uiState.update { s ->
                s.copy(
                    pendingResults = s.pendingResults + (athleteId to newTimeMs / 1000.0),
                    editingAthleteId = null,
                    editingResultId = null
                )
            }
            refreshAllAthletesIfIndividual()
            refreshHeatAthletes()
        }
    }

    private fun handleClearEntry(athleteId: String, resultId: String?) {
        if (resultId != null) {
            // Delete historical result
            viewModelScope.launch {
                try {
                    testingRepository.deleteResultById(resultId)
                } catch (e: Exception) {
                    _uiEvents.send(StopwatchUiEvent.ShowSnackbar("Failed to clear time: ${e.message}"))
                }
            }
            _uiState.update { s -> s.copy(editingAthleteId = null, editingResultId = null) }
        } else {
            // Clear pending result
            _uiState.update { s ->
                s.copy(
                    pendingResults = s.pendingResults - athleteId,
                    editingAthleteId = null,
                    editingResultId = null
                )
            }
            refreshAllAthletesIfIndividual()
            refreshHeatAthletes()
        }
    }
    
    private fun handleRequestSubmit() {
        val hasMissing = _uiState.value.heatAthletes.any { it.status == AthleteStatus.ACTIVE || it.status == AthleteStatus.WAITING }
        if (hasMissing) {
            _uiState.update { it.copy(showMissingEntriesDialog = true) }
        } else {
            submitPending()
        }
    }

    private fun handleResetAthlete() {
        tickerJob?.cancel()
        val currentAthleteId = _uiState.value.selectedAthleteId
        _uiState.update {
            it.copy(
                stopwatchPhase = StopwatchPhase.READY,
                elapsedMs = 0L,
                confirmationData = null,
                canUndo = false,
                pendingResults = if (currentAthleteId != null) it.pendingResults - currentAthleteId else it.pendingResults
            )
        }
        refreshAllAthletesIfIndividual()
    }

    private fun handleNext() {
        if (_uiState.value.mode == TimingMode.INDIVIDUAL) {
            val all = _uiState.value.allAthletes
            val currentId = _uiState.value.selectedAthleteId
            val currentIndex = all.indexOfFirst { it.athleteId == currentId }

            // Sequential search for the next waiting athlete
            val nextAthlete = if (currentIndex != -1) {
                all.drop(currentIndex + 1).find { it.status == AthleteStatus.WAITING }
                    ?: all.take(currentIndex).find { it.status == AthleteStatus.WAITING }
            } else {
                all.find { it.status == AthleteStatus.WAITING }
            }

            _uiState.update { state ->
                state.copy(
                    stopwatchPhase = StopwatchPhase.READY,
                    selectedAthleteId = nextAthlete?.athleteId,
                    confirmationData = null,
                    canUndo = false,
                    elapsedMs = 0L
                )
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
        currentHeatIndex++
        if (currentHeatIndex >= heats.size) {
            _uiState.update { state -> state.copy(isSessionComplete = true) }
        } else {
            _uiState.update { state ->
                val trialsPerAthlete = if (athletes.isNotEmpty()) state.totalCount / athletes.size else 0
                state.copy(
                    stopwatchPhase = StopwatchPhase.READY,
                    elapsedMs = 0L,
                    heatAthletes = buildHeatAthletesList(trialsPerAthlete),
                    currentHeatNumber = currentHeatIndex + 1,
                    confirmationData = null,
                    completedCount = completionState.values.sum()
                )
            }
        }
    }

    private fun handleUndo() {
        val currentAthleteId = _uiState.value.selectedAthleteId ?: return
        _uiState.update {
            it.copy(
                stopwatchPhase = StopwatchPhase.READY,
                confirmationData = null,
                canUndo = false,
                pendingResults = it.pendingResults - currentAthleteId
            )
        }
        refreshAllAthletesIfIndividual()
    }

    private fun submitPending() {
        val state = _uiState.value
        if (state.pendingResults.isEmpty() || state.isSubmitting) return

        _uiState.update { it.copy(isSubmitting = true) }
        viewModelScope.launch {
            try {
                for ((athleteId, rawScore) in state.pendingResults) {
                    val athlete = athletes.find { it.id == athleteId } ?: continue
                    val age = ((System.currentTimeMillis() - athlete.dateOfBirth) / 31_557_600_000L).toFloat()
                    val saved = recordResult(eventId, athleteId, fitnessTestId, rawScore, age, athlete.sex, CaptureMethod.STOPWATCH)
                    sessionResults.getOrPut(athleteId) { mutableListOf() }.add(saved)
                    completionState[athleteId] = (completionState[athleteId] ?: 0) + 1
                }

                if (state.mode == TimingMode.GROUP_START) {
                    tickerJob?.cancel()
                    _uiState.update {
                        it.copy(
                            pendingResults = emptyMap(),
                            isSubmitting = false,
                            showMissingEntriesDialog = false
                        )
                    }
                    recomputeSummaryCounts()
                    advanceToNextHeat()
                } else {
                    val savedName = state.pendingResults.keys.firstOrNull()
                        ?.let { id -> athletes.find { it.id == id }?.fullName }
                    _uiState.update {
                        it.copy(
                            pendingResults = emptyMap(),
                            isSubmitting = false,
                            showMissingEntriesDialog = false,
                            elapsedMs = 0L,
                            stopwatchPhase = StopwatchPhase.READY
                        ).withRefreshedAllAthletes()
                    }
                    recomputeSummaryCounts()
                    refreshHeatAthletes()
                    if (savedName != null) {
                        _uiEvents.send(StopwatchUiEvent.ShowSnackbar("$savedName's time saved"))
                    }
                    // No artificial delay — advance to the next waiting athlete immediately.
                    handleNext()
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSubmitting = false) }
                _uiEvents.send(StopwatchUiEvent.ShowSnackbar("Save failed: ${e.message}"))
            }
        }
    }

    private fun buildAllAthletesList(): List<AthleteQueueItem> {
        val count = athletes.size
        if (count == 0) return emptyList()
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
                capturedTimeMs = if (isPending) (pendingScore * 1000).toLong() else null,
                displayTrials = buildDisplayTrials(athlete.id)
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        tickerJob?.cancel()
        confirmationJob?.cancel()
    }
}
