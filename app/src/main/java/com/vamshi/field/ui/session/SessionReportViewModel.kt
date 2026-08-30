package com.vamshi.field.ui.session

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vamshi.field.data.storage.TourPreferencesStore
import com.vamshi.field.domain.model.reports.SessionReportData
import com.vamshi.field.domain.repository.PeopleRepository
import com.vamshi.field.domain.repository.StandardsRepository
import com.vamshi.field.domain.repository.TestingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SessionReportUiState(
    val data: SessionReportData? = null,
    val selectedTestId: String? = null,
    val isSwitcherOpen: Boolean = false,
    val isLoading: Boolean = true,
    val isExporting: Boolean = false,
    val errorMessage: String? = null,
    val showDeleteDialog: Boolean = false,
    val isDeleted: Boolean = false,
    val isInsightSheetOpen: Boolean = false,
    val showTestingTour: Boolean = false,
    val hasSeenCoachMark: Boolean = true
)

sealed interface SessionReportAction {
    data class OnSelectTest(val testId: String) : SessionReportAction
    data class OnSwitchSession(val sessionId: String) : SessionReportAction
    data object OnOpenSwitcher : SessionReportAction
    data object OnDismissSwitcher : SessionReportAction
    data object OnOpenInsight : SessionReportAction
    data object OnDismissInsight : SessionReportAction
    data class OnNavigateToAthlete(val individualId: String) : SessionReportAction
    data object OnResumeTesting : SessionReportAction
    data object OnNavigateBack : SessionReportAction
    data object OnExportCsv : SessionReportAction
    data object OnDismissError : SessionReportAction
    data object OnRequestDelete : SessionReportAction
    data object OnConfirmDelete : SessionReportAction
    data object OnDismissDelete : SessionReportAction
    data object OnOpenTestingTour : SessionReportAction
    data object OnDismissTestingTour : SessionReportAction
    data object OnDismissCoachMark : SessionReportAction
}

@HiltViewModel
class SessionReportViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val observeSessionReport: com.vamshi.field.domain.usecase.reports.ObserveSessionReportUseCase,
    private val testingRepository: TestingRepository,
    private val peopleRepository: PeopleRepository,
    private val standardsRepository: StandardsRepository,
    private val tourPreferencesStore: TourPreferencesStore
) : ViewModel() {

    val groupId: String = savedStateHandle["groupId"] ?: ""
    private val initialSessionId: String = savedStateHandle["sessionId"] ?: ""

    private val _uiState = MutableStateFlow(SessionReportUiState())
    val uiState: StateFlow<SessionReportUiState> = _uiState.asStateFlow()

    private val _exportEvent = MutableSharedFlow<ExportRequest>()
    val exportEvent = _exportEvent.asSharedFlow()

    sealed interface ExportRequest {
        data class Event(
            val eventName: String,
            val results: List<Pair<com.vamshi.field.domain.model.people.Individual, com.vamshi.field.domain.model.testing.TestResult>>,
            val tests: Map<String, com.vamshi.field.domain.model.standards.FitnessTest>
        ) : ExportRequest
    }

    init {
        load(initialSessionId)
        viewModelScope.launch {
            tourPreferencesStore.observeHasSeenSessionReportCoachMark()
                .collect { seen ->
                    _uiState.update { it.copy(hasSeenCoachMark = seen) }
                }
        }
    }

    fun onAction(action: SessionReportAction) {
        when (action) {
            is SessionReportAction.OnSelectTest -> _uiState.update { it.copy(selectedTestId = action.testId) }
            SessionReportAction.OnOpenSwitcher -> _uiState.update { it.copy(isSwitcherOpen = true) }
            SessionReportAction.OnDismissSwitcher -> _uiState.update { it.copy(isSwitcherOpen = false) }
            SessionReportAction.OnOpenInsight -> _uiState.update { it.copy(isInsightSheetOpen = true) }
            SessionReportAction.OnDismissInsight -> _uiState.update { it.copy(isInsightSheetOpen = false) }
            is SessionReportAction.OnSwitchSession -> {
                _uiState.update { it.copy(isSwitcherOpen = false, isLoading = true, data = null) }
                load(action.sessionId)
            }
            SessionReportAction.OnDismissError -> _uiState.update { it.copy(errorMessage = null) }
            SessionReportAction.OnExportCsv -> exportResults()
            SessionReportAction.OnRequestDelete -> _uiState.update { it.copy(showDeleteDialog = true) }
            SessionReportAction.OnDismissDelete -> _uiState.update { it.copy(showDeleteDialog = false) }
            SessionReportAction.OnConfirmDelete -> deleteEvent()
            SessionReportAction.OnOpenTestingTour -> _uiState.update { it.copy(showTestingTour = true) }
            SessionReportAction.OnDismissTestingTour -> _uiState.update { it.copy(showTestingTour = false) }
            SessionReportAction.OnDismissCoachMark -> {
                viewModelScope.launch {
                    tourPreferencesStore.setHasSeenSessionReportCoachMark(true)
                }
                _uiState.update { it.copy(hasSeenCoachMark = true) }
            }
            is SessionReportAction.OnNavigateToAthlete,
            SessionReportAction.OnResumeTesting,
            SessionReportAction.OnNavigateBack -> Unit
        }
    }

    private fun deleteEvent() {
        val eventId = _uiState.value.data?.event?.id ?: return
        viewModelScope.launch {
            try {
                testingRepository.deleteEventById(eventId)
                _uiState.update { it.copy(showDeleteDialog = false, isDeleted = true) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(showDeleteDialog = false, errorMessage = "Failed to delete: ${e.message}")
                }
            }
        }
    }

    private fun load(sessionId: String) {
        viewModelScope.launch {
            observeSessionReport(groupId, sessionId)
                .catch { e -> _uiState.update { it.copy(errorMessage = e.message, isLoading = false) } }
                .collect { data ->
                    _uiState.update {
                        it.copy(
                            data = data,
                            selectedTestId = it.selectedTestId ?: data?.tests?.firstOrNull()?.id,
                            isLoading = false
                        )
                    }
                }
        }
    }

    private fun exportResults() {
        val data = _uiState.value.data ?: return
        _uiState.update { it.copy(isExporting = true) }
        viewModelScope.launch {
            try {
                val results = testingRepository.getEventResults(data.event.id).first()
                val athleteIds = results.map { it.individualId }.distinct()
                val athletesMap = peopleRepository.getIndividualsByIds(athleteIds).first().associateBy { it.id }
                val tests = standardsRepository.getAllTests().first().associateBy { it.id }
                
                val pairedResults = results.mapNotNull { result ->
                    athletesMap[result.individualId]?.let { it to result }
                }
                
                _uiState.update { it.copy(isExporting = false) }
                _exportEvent.emit(ExportRequest.Event(data.event.name, pairedResults, tests))
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Export failed: ${e.message}", isExporting = false) }
            }
        }
    }
}
