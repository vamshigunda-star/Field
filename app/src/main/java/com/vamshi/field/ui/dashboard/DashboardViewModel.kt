package com.vamshi.field.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vamshi.field.data.storage.OnboardingPreferencesStore
import com.vamshi.field.domain.model.people.Group
import com.vamshi.field.domain.model.testing.TestingEvent
import com.vamshi.field.domain.repository.PeopleRepository
import com.vamshi.field.domain.repository.TestingRepository
import com.vamshi.field.domain.usecase.auth.ObserveCurrentUserUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val availableEvents: List<TestingEvent> = emptyList(),
    val groups: List<Group> = emptyList(),
    val activeAthletes: Int = 0,
    val scheduledTestCount: Int = 0,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    /** Coach's first name for the greeting; defaults to empty (shows "Coach"). */
    val coachFirstName: String = "",
    /** Coach's last name; included so the greeting can use a full name if desired. */
    val coachLastName: String = "",
    /** True while the leaderboard event picker is open. */
    val showLeaderboardPicker: Boolean = false,
    /** Whether the user has dismissed the Getting Started checklist. */
    val isGettingStartedDismissed: Boolean = false
)

sealed interface DashboardAction {
    data object OnCreateEventClick : DashboardAction
    data object OnQuickTestClick : DashboardAction
    data object OnIndividualTestClick : DashboardAction
    data object OnRosterClick : DashboardAction
    data object OnTestLibraryClick : DashboardAction
    data object OnRecommendationsClick : DashboardAction
    data object OnSettingsClick : DashboardAction
    data object OnDismissError : DashboardAction
    data object OnLeaderboardClick : DashboardAction
    data object OnDismissLeaderboardPicker : DashboardAction
    data class OnPickLeaderboardEvent(val eventId: String, val groupId: String) : DashboardAction
    data object OnAnalyticsClick : DashboardAction
    data object OnDismissGettingStarted : DashboardAction
    /** Nav-only: opens the "How to use Field" page. */
    data object OnHowToUseClick : DashboardAction
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val peopleRepository: PeopleRepository,
    private val testingRepository: TestingRepository,
    private val observeCurrentUser: ObserveCurrentUserUseCase,
    private val onboardingPreferencesStore: OnboardingPreferencesStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        loadDashboardData()
        observeCoachName()
        observeOnboardingPreferences()
    }

    fun onAction(action: DashboardAction) {
        when (action) {
            is DashboardAction.OnDismissError -> {
                _uiState.update { it.copy(errorMessage = null) }
            }
            DashboardAction.OnLeaderboardClick -> {
                _uiState.update { it.copy(showLeaderboardPicker = true) }
            }
            DashboardAction.OnDismissLeaderboardPicker -> {
                _uiState.update { it.copy(showLeaderboardPicker = false) }
            }
            DashboardAction.OnDismissGettingStarted -> {
                viewModelScope.launch {
                    onboardingPreferencesStore.setGettingStartedDismissed(true)
                }
                _uiState.update { it.copy(isGettingStartedDismissed = true) }
            }
            is DashboardAction.OnPickLeaderboardEvent -> Unit // navigation only, handled by the Screen
            else -> Unit
        }
    }

    private fun observeOnboardingPreferences() {
        viewModelScope.launch {
            onboardingPreferencesStore.observeGettingStartedDismissed()
                .collect { dismissed ->
                    _uiState.update { it.copy(isGettingStartedDismissed = dismissed) }
                }
        }
    }

    private fun observeCoachName() {
        viewModelScope.launch {
            observeCurrentUser()
                .catch { /* non-critical — greeting degrades to "Coach" */ }
                .collect { user ->
                    _uiState.update {
                        it.copy(
                            coachFirstName = user?.firstName ?: "",
                            coachLastName = user?.lastName ?: ""
                        )
                    }
                }
        }
    }

    private fun loadDashboardData() {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            peopleRepository.getAllIndividuals()
                .catch { e -> _uiState.update { it.copy(errorMessage = e.message) } }
                .collect { athletes ->
                    _uiState.update { it.copy(activeAthletes = athletes.size) }
                }
        }
        viewModelScope.launch {
            peopleRepository.getAllGroups()
                .catch { e -> _uiState.update { it.copy(errorMessage = e.message) } }
                .collect { groups ->
                    _uiState.update { it.copy(groups = groups) }
                }
        }
        viewModelScope.launch {
            testingRepository.getAllEvents()
                .catch { e ->
                    _uiState.update { it.copy(errorMessage = e.message, isLoading = false) }
                }
                .collect { events ->
                    _uiState.update {
                        it.copy(
                            availableEvents = events.sortedByDescending { it.date },
                            scheduledTestCount = events.size,
                            isLoading = false
                        )
                    }
                }
        }
    }
}
