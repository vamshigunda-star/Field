package com.vamshi.field.ui.report

import androidx.lifecycle.SavedStateHandle
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
    val athleteRoster: List<Pair<String, String>> = emptyList(),
    val homeData: ReportsHomeData? = null,
    val selectedTab: ReportsHubTab = ReportsHubTab.ATHLETE_PROFILE,
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

enum class ReportsHubTab { ATHLETE_PROFILE, EVENT_REPORT }

sealed interface ReportsHubAction {
    data class SelectTab(val tab: ReportsHubTab) : ReportsHubAction
    /** Open Event Report on a specific event — sent when a coach finishes testing. */
    data class OpenEvent(val eventId: String, val groupId: String) : ReportsHubAction
    data class SelectAthlete(val id: String) : ReportsHubAction
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
    private val getAthleteRadarData: GetAthleteRadarDataUseCase,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    // The tab lives here, not in the composable: a coach who opens an event's grid from
    // Event Report and comes back expects to land on Event Report, not the first tab.
    private val _uiState = MutableStateFlow(
        ReportsHubUiState(
            selectedTab = savedStateHandle.get<String>(KEY_SELECTED_TAB)
                ?.let { saved -> ReportsHubTab.entries.firstOrNull { it.name == saved } }
                ?: ReportsHubTab.ATHLETE_PROFILE
        )
    )
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
        // 1. Immediately stream the athlete roster for instant search / dropdown opening (< 50ms)
        viewModelScope.launch {
            peopleRepository.getAllIndividuals()
                .catch { /* non-fatal, fallback to empty */ }
                .collect { list ->
                    _uiState.update { it.copy(athleteRoster = list.map { ind -> ind.id to ind.fullName }) }
                }
        }

        // 2. Observe home analytics metrics
        viewModelScope.launch {
            reports.observeHome()
                .catch { e -> _uiState.update { it.copy(errorMessage = e.message, isLoadingHome = false) } }
                .collect { data ->
                    _uiState.update { it.copy(homeData = data, isLoadingHome = false) }

                    // Auto-load first athlete from flags list if nothing selected yet
                    if (_uiState.value.selectedAthleteId == null) {
                        val firstId = data.flags.firstOrNull()?.individualId
                            ?: _uiState.value.athleteRoster.firstOrNull()?.first
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
            is ReportsHubAction.SelectTab -> {
                savedStateHandle[KEY_SELECTED_TAB] = action.tab.name
                _uiState.update { it.copy(selectedTab = action.tab) }
            }
            is ReportsHubAction.OpenEvent -> {
                savedStateHandle[KEY_SELECTED_TAB] = ReportsHubTab.EVENT_REPORT.name
                _uiState.update { it.copy(selectedTab = ReportsHubTab.EVENT_REPORT) }
                loadEvent(action.eventId, action.groupId)
            }
            is ReportsHubAction.SelectAthlete -> loadAthlete(action.id)
            is ReportsHubAction.SelectEvent -> loadEvent(action.eventId, action.groupId)
            is ReportsHubAction.SelectEventTest -> _uiState.update { it.copy(selectedEventTestId = action.testId) }
            ReportsHubAction.OnOpenSwitcher -> _uiState.update { it.copy(isSwitcherOpen = true) }
            ReportsHubAction.OnDismissSwitcher -> _uiState.update { it.copy(isSwitcherOpen = false) }
            ReportsHubAction.OnOpenInsight -> _uiState.update { it.copy(isInsightSheetOpen = true) }
            ReportsHubAction.OnDismissInsight -> _uiState.update { it.copy(isInsightSheetOpen = false) }
            is ReportsHubAction.OnSwitchSession -> {
                val groupId = _uiState.value.selectedEventGroupId
                if (groupId != null) {
                    _uiState.update { it.copy(isSwitcherOpen = false, isLoadingEvent = true, eventData = null) }
                    loadEvent(action.sessionId, groupId)
                } else {
                    _uiState.update { it.copy(isSwitcherOpen = false) }
                }
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

    private data class CachedEventReport(
        val eventData: SessionReportData,
        val testId: String?
    )

    private val athleteCache = Collections.synchronizedMap(
        object : LinkedHashMap<String, CachedAthleteReport>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedAthleteReport>?): Boolean {
                return size > 25
            }
        }
    )

    private val eventCache = Collections.synchronizedMap(
        object : LinkedHashMap<String, CachedEventReport>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedEventReport>?): Boolean {
                return size > 15
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
                    isLoadingAthlete = true
                )
            }
        }

        athleteJob = viewModelScope.launch {
            // Launch radar computation concurrently in parallel with dashboard flow
            val radarDeferred = async {
                try { getAthleteRadarData(id) } catch (_: Exception) { null }
            }

            reports.observeAthleteDashboard(id, null)
                .catch { e -> _uiState.update { it.copy(errorMessage = e.message, isLoadingAthlete = false) } }
                .collect { data ->
                    if (data != null) {
                        val radar = radarDeferred.await()
                        val initialTestId = data.tiles.firstOrNull()?.test?.id
                        _uiState.update { state ->
                            val validTestId = if (state.selectedAthleteTestId != null && data.tiles.any { it.test.id == state.selectedAthleteTestId }) {
                                state.selectedAthleteTestId
                            } else {
                                initialTestId
                            }
                            athleteCache[id] = CachedAthleteReport(data, radar, validTestId)
                            state.copy(
                                athleteData = data,
                                athleteRadarData = radar,
                                isLoadingAthlete = false,
                                selectedAthleteTestId = validTestId
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
        val cached = eventCache[eventId]
        if (cached != null) {
            _uiState.update {
                it.copy(
                    selectedEventId = eventId,
                    selectedEventGroupId = groupId,
                    eventData = cached.eventData,
                    selectedEventTestId = cached.testId,
                    isLoadingEvent = false,
                    isEventDeleted = false
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    selectedEventId = eventId,
                    selectedEventGroupId = groupId,
                    isLoadingEvent = true,
                    eventData = null,
                    selectedEventTestId = null,
                    isEventDeleted = false
                )
            }
        }

        eventJob = viewModelScope.launch {
            reports.observeSessionReport(groupId, eventId)
                .catch { e -> _uiState.update { it.copy(errorMessage = e.message, isLoadingEvent = false) } }
                .collect { data ->
                    if (data != null) {
                        val initialTestId = data.tests.firstOrNull()?.id
                        eventCache[eventId] = CachedEventReport(data, initialTestId)
                        _uiState.update { state ->
                            state.copy(
                                eventData = data,
                                isLoadingEvent = false,
                                selectedEventTestId = state.selectedEventTestId ?: initialTestId
                            )
                        }
                    } else {
                        _uiState.update { it.copy(eventData = null, isLoadingEvent = false) }
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
                eventCache.remove(eventId)
                _uiState.update { 
                    it.copy(
                        showDeleteDialog = false, 
                        isEventDeleted = true, 
                        selectedEventId = null, 
                        eventData = null 
                    ) 
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(showDeleteDialog = false, errorMessage = "Failed to delete: ${e.message}")
                }
            }
        }
    }

    private companion object {
        const val KEY_SELECTED_TAB = "reports_selected_tab"
    }
}
