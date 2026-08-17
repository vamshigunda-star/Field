package com.vamshi.field.ui.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vamshi.field.domain.model.reports.AthleteDashboardData
import com.vamshi.field.domain.model.reports.ReportsHomeData
import com.vamshi.field.domain.model.reports.SessionReportData
import com.vamshi.field.domain.repository.ReportsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import java.util.Collections
import java.util.LinkedHashMap

import com.vamshi.field.domain.repository.PeopleRepository
import com.vamshi.field.domain.repository.StandardsRepository
import com.vamshi.field.domain.repository.TestingRepository
import com.vamshi.field.domain.usecase.testing.AthleteRadarData
import com.vamshi.field.domain.usecase.testing.GetAthleteRadarDataUseCase
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import com.vamshi.field.domain.model.people.Individual
import com.vamshi.field.domain.model.testing.TestResult
import com.vamshi.field.domain.model.standards.FitnessTest

// ── UI State ──────────────────────────────────────────────────────────────────

data class ReportsHubUiState(
    val homeData: ReportsHomeData? = null,
    val isLoadingHome: Boolean = true,

    // Athlete Profile tab
    val selectedAthleteId: String? = null,
    val athleteData: AthleteDashboardData? = null,
    val athleteRadarData: AthleteRadarData? = null,
    val isLoadingAthlete: Boolean = false,
    val selectedAthleteTestId: String? = null,

    // Event Report tab
    val selectedEventId: String? = null,
    val selectedEventGroupId: String? = null,
    val eventData: SessionReportData? = null,
    val isLoadingEvent: Boolean = false,
    val selectedEventTestId: String? = null,
    val isSwitcherOpen: Boolean = false,
    val isInsightSheetOpen: Boolean = false,

    val errorMessage: String? = null,
    val isExporting: Boolean = false,
    val showDeleteDialog: Boolean = false,
    val isEventDeleted: Boolean = false
)

// ── Actions ───────────────────────────────────────────────────────────────────

sealed interface ReportsHubAction {
    data class SelectAthlete(val id: String) : ReportsHubAction
    data class SelectAthleteTest(val testId: String) : ReportsHubAction
    data class SelectEvent(val eventId: String, val groupId: String) : ReportsHubAction
    data class SelectEventTest(val testId: String) : ReportsHubAction
    data object OnOpenSwitcher : ReportsHubAction
    data object OnDismissSwitcher : ReportsHubAction
    data object OnOpenInsight : ReportsHubAction
    data object OnDismissInsight : ReportsHubAction
    data class OnSwitchSession(val sessionId: String) : ReportsHubAction
    data object DismissError : ReportsHubAction
    
    data class OnNavigateToAthleteDashboard(val athleteId: String) : ReportsHubAction
    data class OnStartQuickTest(val athleteId: String, val testIds: List<String>) : ReportsHubAction

    data object ExportAthleteCsv : ReportsHubAction
    data object ExportEventCsv : ReportsHubAction
    data object RequestDeleteEvent : ReportsHubAction
    data object ConfirmDeleteEvent : ReportsHubAction
    data object DismissDeleteEvent : ReportsHubAction
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class ReportsHubViewModel @Inject constructor(
    private val reports: ReportsRepository,
    private val testingRepository: TestingRepository,
    private val peopleRepository: PeopleRepository,
    private val standardsRepository: StandardsRepository,
    private val getAthleteRadarData: GetAthleteRadarDataUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReportsHubUiState())
    val uiState: StateFlow<ReportsHubUiState> = _uiState.asStateFlow()

    private val _exportEvent = kotlinx.coroutines.flow.MutableSharedFlow<ExportRequest>()
    val exportEvent = _exportEvent.asSharedFlow()

    sealed interface ExportRequest {
        data class Athlete(
            val athlete: Individual,
            val results: List<TestResult>,
            val tests: Map<String, FitnessTest>
        ) : ExportRequest

        data class Event(
            val eventName: String,
            val results: List<Pair<Individual, TestResult>>,
            val tests: Map<String, FitnessTest>
        ) : ExportRequest
    }

    private var athleteJob: Job? = null
    private var eventJob: Job? = null

    init {
        viewModelScope.launch {
            reports.observeHome()
                .catch { e -> _uiState.update { it.copy(errorMessage = e.message, isLoadingHome = false) } }
                .collect { data ->
                    _uiState.update { it.copy(homeData = data, isLoadingHome = false) }

                    // Auto-load first athlete from flags list if nothing selected yet
                    if (_uiState.value.selectedAthleteId == null) {
                        val firstId = data.flags.firstOrNull()?.individualId
                        if (firstId != null) loadAthlete(firstId)
                    }

                    // Auto-load first event session if nothing selected yet
                    if (_uiState.value.selectedEventId == null) {
                        val firstValid = data.recentSessions.firstOrNull { it.groupId != null }
                        if (firstValid != null) {
                            loadEvent(firstValid.event.id, firstValid.groupId!!)
                        }
                    }
                }
        }
    }

    fun onAction(action: ReportsHubAction) {
        when (action) {
            is ReportsHubAction.SelectAthlete -> loadAthlete(action.id)
            is ReportsHubAction.SelectAthleteTest -> _uiState.update { it.copy(selectedAthleteTestId = action.testId) }
            is ReportsHubAction.SelectEvent -> loadEvent(action.eventId, action.groupId)
            is ReportsHubAction.SelectEventTest -> _uiState.update { it.copy(selectedEventTestId = action.testId) }
            ReportsHubAction.OnOpenSwitcher -> _uiState.update { it.copy(isSwitcherOpen = true) }
            ReportsHubAction.OnDismissSwitcher -> _uiState.update { it.copy(isSwitcherOpen = false) }
            ReportsHubAction.OnOpenInsight -> _uiState.update { it.copy(isInsightSheetOpen = true) }
            ReportsHubAction.OnDismissInsight -> _uiState.update { it.copy(isInsightSheetOpen = false) }
            is ReportsHubAction.OnSwitchSession -> {
                _uiState.update { it.copy(isSwitcherOpen = false, isLoadingEvent = true, eventData = null) }
                val groupId = _uiState.value.selectedEventGroupId ?: return
                loadEvent(action.sessionId, groupId)
            }
            ReportsHubAction.DismissError -> _uiState.update { it.copy(errorMessage = null) }
            ReportsHubAction.ExportAthleteCsv -> exportAthleteResults()
            ReportsHubAction.ExportEventCsv -> exportEventResults()
            ReportsHubAction.RequestDeleteEvent -> _uiState.update { it.copy(showDeleteDialog = true) }
            ReportsHubAction.DismissDeleteEvent -> _uiState.update { it.copy(showDeleteDialog = false) }
            ReportsHubAction.ConfirmDeleteEvent -> deleteEvent()
            is ReportsHubAction.OnNavigateToAthleteDashboard,
            is ReportsHubAction.OnStartQuickTest -> { /* Handled in UI navigation layer */ }
        }
    }

    private data class CachedAthleteReport(
        val athleteData: AthleteDashboardData,
        val radarData: AthleteRadarData?,
        val testId: String?
    )

    private val athleteCache = Collections.synchronizedMap(
        object : LinkedHashMap<String, CachedAthleteReport>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedAthleteReport>?): Boolean {
                return size > 25
            }
        }
    )

    private fun loadAthlete(id: String) {
        athleteJob?.cancel()
        val cached = athleteCache[id]
        if (cached != null) {
            _uiState.update {
                it.copy(
                    selectedAthleteId = id,
                    athleteData = cached.athleteData,
                    athleteRadarData = cached.radarData,
                    selectedAthleteTestId = cached.testId,
                    isLoadingAthlete = false
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    selectedAthleteId = id,
                    isLoadingAthlete = true,
                    athleteData = null,
                    athleteRadarData = null,
                    selectedAthleteTestId = null
                )
            }
        }

        athleteJob = viewModelScope.launch {
            reports.observeAthleteDashboard(id, null)
                .catch { e -> _uiState.update { it.copy(errorMessage = e.message, isLoadingAthlete = false) } }
                .collect { data ->
                    if (data != null) {
                        val radarDeferred = async {
                            try { getAthleteRadarData(id) } catch (_: Exception) { null }
                        }
                        val radar = radarDeferred.await()
                        val initialTestId = data.tiles.firstOrNull()?.test?.id
                        athleteCache[id] = CachedAthleteReport(data, radar, initialTestId)
                        _uiState.update { state ->
                            state.copy(
                                athleteData = data,
                                athleteRadarData = radar,
                                isLoadingAthlete = false,
                                selectedAthleteTestId = state.selectedAthleteTestId ?: initialTestId
                            )
                        }
                    } else {
                        _uiState.update { it.copy(athleteData = null, athleteRadarData = null, isLoadingAthlete = false) }
                    }
                }
        }
    }

    private fun loadEvent(eventId: String, groupId: String) {
        eventJob?.cancel()
        _uiState.update {
            it.copy(
                selectedEventId = eventId,
                selectedEventGroupId = groupId,
                isLoadingEvent = true,
                eventData = null,
                selectedEventTestId = null
            )
        }
        eventJob = viewModelScope.launch {
            reports.observeSessionReport(groupId, eventId)
                .catch { e -> _uiState.update { it.copy(errorMessage = e.message, isLoadingEvent = false) } }
                .collect { data ->
                    _uiState.update { state ->
                        state.copy(
                            eventData = data,
                            isLoadingEvent = false,
                            selectedEventTestId = state.selectedEventTestId ?: data?.tests?.firstOrNull()?.id
                        )
                    }
                }
        }
    }
    
    private fun exportAthleteResults() {
        val athlete = _uiState.value.athleteData?.athlete ?: return
        val athleteId = _uiState.value.selectedAthleteId ?: return
        _uiState.update { it.copy(isExporting = true) }
        viewModelScope.launch {
            try {
                val results = testingRepository.getAllResultsForIndividual(athleteId).first()
                val tests = standardsRepository.getAllTests().first().associateBy { it.id }
                _uiState.update { it.copy(isExporting = false) }
                _exportEvent.emit(ExportRequest.Athlete(athlete, results, tests))
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Export failed: ${e.message}", isExporting = false) }
            }
        }
    }

    private fun exportEventResults() {
        val data = _uiState.value.eventData ?: return
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

    private fun deleteEvent() {
        val eventId = _uiState.value.eventData?.event?.id ?: return
        viewModelScope.launch {
            try {
                testingRepository.deleteEventById(eventId)
                _uiState.update { it.copy(showDeleteDialog = false, isEventDeleted = true) }
                // Need to clear the event or reload the home data? 
                // Home data is observed so it will auto-update!
                _uiState.update { it.copy(selectedEventId = null, eventData = null) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(showDeleteDialog = false, errorMessage = "Failed to delete: ${e.message}")
                }
            }
        }
    }
}
