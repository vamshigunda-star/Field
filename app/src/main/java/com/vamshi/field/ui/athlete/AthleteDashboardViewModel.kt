package com.vamshi.field.ui.athlete

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vamshi.field.domain.model.people.Individual
import com.vamshi.field.domain.model.reports.AthleteDashboardData
import com.vamshi.field.domain.model.standards.FitnessTest
import com.vamshi.field.domain.model.testing.TestResult
import com.vamshi.field.domain.repository.StandardsRepository
import com.vamshi.field.domain.repository.TestingRepository
import com.vamshi.field.domain.usecase.reports.ObserveAthleteDashboardUseCase
import com.vamshi.field.domain.usecase.testing.AthleteRadarData
import com.vamshi.field.domain.usecase.testing.GetAthleteRadarDataUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
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

data class AthleteDashboardUiState(
    val data: AthleteDashboardData? = null,
    val radarData: AthleteRadarData? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val isExporting: Boolean = false
)

sealed interface AthleteDashboardAction {
    data class OnNavigateToTest(val testId: String) : AthleteDashboardAction
    data class OnStartQuickTest(val testIds: List<String>) : AthleteDashboardAction
    data object OnNavigateBack : AthleteDashboardAction
    data object OnExportCsv : AthleteDashboardAction
    data object OnDismissError : AthleteDashboardAction
}

@HiltViewModel
class AthleteDashboardViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val observeAthleteDashboard: ObserveAthleteDashboardUseCase,
    private val testingRepository: TestingRepository,
    private val standardsRepository: StandardsRepository,
    private val getAthleteRadarData: GetAthleteRadarDataUseCase
) : ViewModel() {

    var athleteId: String = ""
        private set
    var contextSessionId: String? = null
        private set

    private val _uiState = MutableStateFlow(AthleteDashboardUiState())
    val uiState: StateFlow<AthleteDashboardUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    fun loadDashboard(athleteId: String, contextSessionId: String? = null) {
        this.athleteId = athleteId
        this.contextSessionId = contextSessionId
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            observeAthleteDashboard(athleteId, contextSessionId)
                .catch { e -> _uiState.update { it.copy(errorMessage = e.message, isLoading = false) } }
                .collect { d ->
                    if (d != null) {
                        val radarDeferred = async {
                            try {
                                getAthleteRadarData(athleteId)
                            } catch (e: Exception) {
                                Log.e("AthleteDashboardVM", "Failed to fetch radar data", e)
                                null
                            }
                        }
                        val radar = radarDeferred.await()
                        _uiState.update { it.copy(data = d, radarData = radar, isLoading = false) }
                    } else {
                        _uiState.update { it.copy(data = null, radarData = null, isLoading = false) }
                    }
                }
        }
    }

    fun onAction(action: AthleteDashboardAction) {
        when (action) {
            AthleteDashboardAction.OnDismissError -> _uiState.update { it.copy(errorMessage = null) }
            AthleteDashboardAction.OnExportCsv -> exportResults()
            else -> Unit
        }
    }

    private fun exportResults() {
        val athlete = _uiState.value.data?.athlete ?: return
        _uiState.update { it.copy(isExporting = true) }
        viewModelScope.launch {
            try {
                val results = testingRepository.getAllResultsForIndividual(athleteId).first()
                val tests = standardsRepository.getAllTests().first().associateBy { it.id }
                _uiState.update { it.copy(isExporting = false) }
                // We'll trigger the actual export from the UI to get the context
                _exportEvent.emit(ExportRequest.Athlete(athlete, results, tests))
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Export failed: ${e.message}", isExporting = false) }
            }
        }
    }

    private val _exportEvent = MutableSharedFlow<ExportRequest>()
    val exportEvent = _exportEvent.asSharedFlow()

    sealed interface ExportRequest {
        data class Athlete(
            val athlete: Individual,
            val results: List<TestResult>,
            val tests: Map<String, FitnessTest>
        ) : ExportRequest
    }
}

