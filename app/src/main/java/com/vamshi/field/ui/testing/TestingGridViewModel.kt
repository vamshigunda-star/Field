package com.vamshi.field.ui.testing

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vamshi.field.domain.model.people.Individual
import com.vamshi.field.domain.model.standards.FitnessTest
import com.vamshi.field.domain.model.testing.TestResult
import com.vamshi.field.domain.model.testing.TestingEvent
import com.vamshi.field.domain.repository.PeopleRepository
import com.vamshi.field.domain.repository.TestingRepository
import com.vamshi.field.domain.usecase.testing.DiscardEmptyEventUseCase
import com.vamshi.field.domain.usecase.testing.GetTestingGridDataUseCase
import com.vamshi.field.domain.usecase.testing.RecordTestResultUseCase
import com.vamshi.field.domain.usecase.testing.TestingGridData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class CaptureMethodPreference { INDIVIDUAL_STOPWATCH, GROUP_STOPWATCH, MANUAL }

data class TestingGridUiState(
    val event: TestingEvent? = null,
    val gridData: TestingGridData? = null,
    val selectedTestIndex: Int = 0,
    val editingCell: EditingCell? = null,
    val timingChoiceCell: TimingChoiceCell? = null,
    val testCapturePreferences: Map<String, CaptureMethodPreference> = emptyMap(),
    val deleteCandidate: DeleteCandidate? = null,
    val showCompletionDialog: Boolean = false,
    val showLeaveDialog: Boolean = false,
    val leaveConfirmed: Boolean = false, // consumed by the screen, which navigates back
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val failedAction: FailedGridAction? = null
)

sealed interface FailedGridAction {
    data class Save(
        val athlete: Individual,
        val test: FitnessTest,
        val rawScore: Double,
        /** The score this save was replacing, so a retry still removes it. */
        val replacing: TestResult? = null
    ) : FailedGridAction

    data class Delete(
        val resultId: String,
        val athlete: Individual,
        val test: FitnessTest
    ) : FailedGridAction
}

data class EditingCell(
    val athlete: Individual,
    val test: FitnessTest,
    val currentResult: TestResult?
)

data class TimingChoiceCell(
    val athlete: Individual,
    val test: FitnessTest
)

data class DeleteCandidate(
    val athlete: Individual,
    val test: FitnessTest,
    val resultId: String
)

sealed interface TestingGridAction {
    data class OnSelectTestTab(val index: Int) : TestingGridAction
    data class OnStartEditing(val athlete: Individual, val test: FitnessTest) : TestingGridAction
    data object OnDismissEditing : TestingGridAction
    data class OnSaveScore(val rawScore: Double) : TestingGridAction
    data class OnRequestTimingChoice(val athlete: Individual, val test: FitnessTest) : TestingGridAction
    data class OnSelectTimingMethod(val testId: String, val method: CaptureMethodPreference) : TestingGridAction
    data object OnDismissTimingChoice : TestingGridAction
    data class OnRequestDelete(val athlete: Individual, val test: FitnessTest, val resultId: String) : TestingGridAction
    data object OnConfirmDelete : TestingGridAction
    data object OnDismissDelete : TestingGridAction
    data object OnDismissError : TestingGridAction
    data object OnRetryFailedAction : TestingGridAction
    data object OnRequestSaveSession : TestingGridAction
    data object OnDismissCompletionDialog : TestingGridAction
    data object OnRequestLeave : TestingGridAction
    data object OnDismissLeave : TestingGridAction
    data object OnConfirmLeave : TestingGridAction
    data object OnDiscardAndLeave : TestingGridAction
    data object OnLeaveConsumed : TestingGridAction
    
    // Navigation actions — handled by the screen composable
    data object OnNavigateBack : TestingGridAction
    data class OnNavigateToAthleteReport(val individualId: String) : TestingGridAction
    data class OnNavigateToLeaderboard(val eventId: String, val groupId: String, val mode: String) : TestingGridAction
    data class OnNavigateToGroupReport(val eventId: String, val groupId: String) : TestingGridAction
    data class OnNavigateToStopwatch(
        val eventId: String,
        val fitnessTestId: String,
        val groupId: String,
        val individualId: String?,
        val timingMode: String? = null
    ) : TestingGridAction
}

@HiltViewModel
class TestingGridViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val testingRepository: TestingRepository,
    private val peopleRepository: PeopleRepository,
    private val getGridData: GetTestingGridDataUseCase,
    private val recordTestResult: RecordTestResultUseCase,
    private val discardEmptyEvent: DiscardEmptyEventUseCase
) : ViewModel() {

    val eventId: String = savedStateHandle["eventId"] ?: ""
    val groupId: String = savedStateHandle["groupId"] ?: ""

    private val _uiState = MutableStateFlow(TestingGridUiState())
    val uiState: StateFlow<TestingGridUiState> = _uiState.asStateFlow()

    init {
        if (eventId.isNotEmpty() && groupId.isNotEmpty()) {
            viewModelScope.launch {
                val event = testingRepository.getEventById(eventId)
                _uiState.update { it.copy(event = event) }
            }
            viewModelScope.launch {
                getGridData.invoke(eventId, groupId)
                    .catch { e -> _uiState.update { it.copy(errorMessage = e.message, isLoading = false) } }
                    .collect { data ->
                        _uiState.update { it.copy(gridData = data, isLoading = false) }
                    }
            }
        }
    }

    fun onAction(action: TestingGridAction) {
        when (action) {
            is TestingGridAction.OnSelectTestTab -> {
                _uiState.update { it.copy(selectedTestIndex = action.index) }
            }
            is TestingGridAction.OnStartEditing -> {
                val currentResult = _uiState.value.gridData?.results?.find {
                    it.individualId == action.athlete.id && it.testId == action.test.id
                }
                _uiState.update {
                    it.copy(
                        editingCell = EditingCell(action.athlete, action.test, currentResult),
                        timingChoiceCell = null
                    )
                }
            }
            TestingGridAction.OnDismissEditing -> {
                _uiState.update { it.copy(editingCell = null) }
            }
            is TestingGridAction.OnRequestTimingChoice -> {
                _uiState.update {
                    it.copy(
                        timingChoiceCell = TimingChoiceCell(action.athlete, action.test),
                        editingCell = null
                    )
                }
            }
            TestingGridAction.OnDismissTimingChoice -> {
                _uiState.update { it.copy(timingChoiceCell = null) }
            }
            is TestingGridAction.OnSelectTimingMethod -> {
                _uiState.update {
                    it.copy(
                        testCapturePreferences = it.testCapturePreferences + (action.testId to action.method)
                    )
                }
            }
            is TestingGridAction.OnSaveScore -> {
                val cell = _uiState.value.editingCell
                if (cell != null) {
                    saveScore(cell.athlete, cell.test, action.rawScore, cell.currentResult)
                }
            }
            is TestingGridAction.OnRequestDelete -> {
                _uiState.update {
                    it.copy(
                        deleteCandidate = DeleteCandidate(action.athlete, action.test, action.resultId),
                        editingCell = null
                    )
                }
            }
            TestingGridAction.OnConfirmDelete -> {
                deleteResult()
            }
            TestingGridAction.OnDismissDelete -> {
                _uiState.update { it.copy(deleteCandidate = null) }
            }
            TestingGridAction.OnDismissError -> {
                _uiState.update { it.copy(errorMessage = null) }
            }
            TestingGridAction.OnRetryFailedAction -> {
                val failed = _uiState.value.failedAction
                when (failed) {
                    is FailedGridAction.Save -> saveScore(failed.athlete, failed.test, failed.rawScore, failed.replacing)
                    is FailedGridAction.Delete -> retryDelete(failed)
                    null -> Unit
                }
            }
            TestingGridAction.OnRequestSaveSession -> {
                _uiState.update { it.copy(showCompletionDialog = true) }
            }
            TestingGridAction.OnDismissCompletionDialog -> {
                _uiState.update { it.copy(showCompletionDialog = false) }
            }
            TestingGridAction.OnRequestLeave -> {
                _uiState.update { it.copy(showLeaveDialog = true) }
            }
            TestingGridAction.OnDismissLeave -> {
                _uiState.update { it.copy(showLeaveDialog = false) }
            }
            TestingGridAction.OnConfirmLeave -> {
                _uiState.update { it.copy(showLeaveDialog = false, leaveConfirmed = true) }
            }
            TestingGridAction.OnDiscardAndLeave -> discardAndLeave()
            TestingGridAction.OnLeaveConsumed -> {
                _uiState.update { it.copy(leaveConfirmed = false) }
            }
            else -> Unit
        }
    }

    private fun saveScore(athlete: Individual, test: FitnessTest, rawScore: Double, currentResult: TestResult?) {
        if (_uiState.value.gridData == null) return

        // A fresh attempt supersedes any prior failure; clearing the editing cell
        // dismisses the keyboard while the save is in flight.
        _uiState.update { it.copy(failedAction = null, editingCell = null) }

        viewModelScope.launch {
            try {
                // Get full athlete data to calculate age
                val fullAthlete = peopleRepository.getIndividualById(athlete.id)
                    ?: throw IllegalStateException("Athlete not found — score not saved")
                val ageMillis = System.currentTimeMillis() - fullAthlete.dateOfBirth
                val ageYears = (ageMillis / (365.25 * 24 * 60 * 60 * 1000)).toFloat()

                recordTestResult(
                    eventId = eventId,
                    individualId = fullAthlete.id,
                    testId = test.id,
                    rawScore = rawScore,
                    ageAtTime = ageYears,
                    sex = fullAthlete.sex
                )

                // Only once the new score is stored does the one it replaces go. Deleting first
                // meant a rejected edit (e.g. a typo outside the valid range) lost the original.
                if (currentResult != null) {
                    testingRepository.deleteResultById(currentResult.id)
                }
            } catch (e: Exception) {
                Log.e(
                    "TestingGridViewModel",
                    "saveScore FAILED event=$eventId athlete=${athlete.id} test=${test.id} score=$rawScore",
                    e
                )
                _uiState.update {
                    it.copy(
                        errorMessage = e.message,
                        editingCell = null,
                        failedAction = FailedGridAction.Save(athlete, test, rawScore, currentResult)
                    )
                }
            }
        }
    }

    private fun discardAndLeave() {
        viewModelScope.launch {
            val discarded = try {
                discardEmptyEvent(eventId)
            } catch (e: Exception) {
                Log.e("TestingGridViewModel", "discardEmptyEvent FAILED event=$eventId", e)
                false
            }
            _uiState.update {
                it.copy(
                    showLeaveDialog = false,
                    leaveConfirmed = true,
                    errorMessage = if (discarded) it.errorMessage else "Scores were recorded, so the event was kept."
                )
            }
        }
    }

    private fun deleteResult() {
        val candidate = _uiState.value.deleteCandidate ?: return
        viewModelScope.launch {
            try {
                testingRepository.deleteResultById(candidate.resultId)
                _uiState.update { it.copy(deleteCandidate = null) }
            } catch (e: Exception) {
                Log.e(
                    "TestingGridViewModel",
                    "deleteResultById FAILED resultId=${candidate.resultId}",
                    e
                )
                _uiState.update {
                    it.copy(
                        errorMessage = "Delete failed: ${e.message}",
                        deleteCandidate = null,
                        failedAction = FailedGridAction.Delete(candidate.resultId, candidate.athlete, candidate.test)
                    )
                }
            }
        }
    }

    private fun retryDelete(failed: FailedGridAction.Delete) {
        _uiState.update { it.copy(failedAction = null) }
        viewModelScope.launch {
            try {
                testingRepository.deleteResultById(failed.resultId)
            } catch (e: Exception) {
                Log.e(
                    "TestingGridViewModel",
                    "deleteResultById RETRY FAILED resultId=${failed.resultId}",
                    e
                )
                _uiState.update {
                    it.copy(
                        errorMessage = "Delete failed: ${e.message}",
                        failedAction = FailedGridAction.Delete(failed.resultId, failed.athlete, failed.test)
                    )
                }
            }
        }
    }
}
