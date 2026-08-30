package com.vamshi.field.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vamshi.field.data.storage.TourPreferencesStore
import com.vamshi.field.domain.model.people.Group
import com.vamshi.field.domain.model.testing.TestingEvent
import com.vamshi.field.domain.repository.PeopleRepository
import com.vamshi.field.domain.repository.TestingRepository
import com.vamshi.field.domain.usecase.auth.ObserveCurrentUserUseCase
import com.vamshi.field.domain.usecase.auth.SignOutUseCase
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
    /** True after sign-out; screen navigates to SignIn via LaunchedEffect. */
    val navigateToSignIn: Boolean = false,
    /** True while the leaderboard event picker is open. */
    val showLeaderboardPicker: Boolean = false,
    /** True while the tour selection bottom sheet is open. */
    val showTourSelectionSheet: Boolean = false,
    /** True while the 4-slide Welcome tour dialog is open. */
    val showWelcomeTour: Boolean = false,
    /** True while the 5-step Testing workflow tour dialog is open. */
    val showTestingTour: Boolean = false,
    /** Whether the user has dismissed the Getting Started checklist. */
    val isGettingStartedDismissed: Boolean = false,
    /** Whether the initial welcome tour has been seen. */
    val hasSeenWelcomeTour: Boolean = true,
    /** Whether the interactive spotlight walkthrough on the Dashboard is active. */
    val showDashboardSpotlight: Boolean = false,
    /** Whether the user has completed or dismissed the dashboard spotlight tour. */
    val hasSeenDashboardSpotlight: Boolean = true,
    /** True while the interactive pipeline workflow simulator is open. */
    val showPipelineSimulator: Boolean = false
)

sealed interface DashboardAction {
    data object OnCreateEventClick : DashboardAction
    data object OnQuickTestClick : DashboardAction
    data object OnIndividualTestClick : DashboardAction
    data object OnRosterClick : DashboardAction
    data object OnTestLibraryClick : DashboardAction
    data object OnRecommendationsClick : DashboardAction
    data object OnNewTestClick : DashboardAction
    data object OnSettingsClick : DashboardAction
    data object OnDismissError : DashboardAction
    data object OnLeaderboardClick : DashboardAction
    data object OnDismissLeaderboardPicker : DashboardAction
    data class OnPickLeaderboardEvent(val eventId: String, val groupId: String) : DashboardAction
    data object OnAnalyticsClick : DashboardAction
    data object OnSignOutClick : DashboardAction
    data object NavigationConsumed : DashboardAction
    // Tours and Onboarding Actions
    data object OnOpenTourMenuClick : DashboardAction
    data object OnDismissTourMenu : DashboardAction
    data object OnOpenWelcomeTour : DashboardAction
    data object OnDismissWelcomeTour : DashboardAction
    data object OnOpenTestingTour : DashboardAction
    data object OnDismissTestingTour : DashboardAction
    data object OnDismissGettingStarted : DashboardAction
    data object OnResetGettingStarted : DashboardAction
    data object OnStartDashboardSpotlight : DashboardAction
    data object OnDismissDashboardSpotlight : DashboardAction
    data object OnOpenPipelineSimulator : DashboardAction
    data object OnDismissPipelineSimulator : DashboardAction
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val peopleRepository: PeopleRepository,
    private val testingRepository: TestingRepository,
    private val observeCurrentUser: ObserveCurrentUserUseCase,
    private val signOutUseCase: SignOutUseCase,
    private val tourPreferencesStore: TourPreferencesStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        loadDashboardData()
        observeCoachName()
        observeTourPreferences()
    }

    fun onAction(action: DashboardAction) {
        when (action) {
            is DashboardAction.OnDismissError -> {
                _uiState.update { it.copy(errorMessage = null) }
            }
            DashboardAction.OnSignOutClick -> {
                viewModelScope.launch {
                    signOutUseCase()
                    _uiState.update { it.copy(navigateToSignIn = true) }
                }
            }
            DashboardAction.NavigationConsumed -> {
                _uiState.update { it.copy(navigateToSignIn = false) }
            }
            DashboardAction.OnLeaderboardClick -> {
                _uiState.update { it.copy(showLeaderboardPicker = true) }
            }
            DashboardAction.OnDismissLeaderboardPicker -> {
                _uiState.update { it.copy(showLeaderboardPicker = false) }
            }
            DashboardAction.OnOpenTourMenuClick -> {
                _uiState.update { it.copy(showTourSelectionSheet = true) }
            }
            DashboardAction.OnDismissTourMenu -> {
                _uiState.update { it.copy(showTourSelectionSheet = false) }
            }
            DashboardAction.OnOpenWelcomeTour -> {
                _uiState.update { it.copy(showWelcomeTour = true, showTourSelectionSheet = false) }
            }
            DashboardAction.OnDismissWelcomeTour -> {
                viewModelScope.launch {
                    tourPreferencesStore.setHasSeenWelcomeTour(true)
                }
                _uiState.update { it.copy(showWelcomeTour = false) }
            }
            DashboardAction.OnOpenTestingTour -> {
                _uiState.update { it.copy(showTestingTour = true, showTourSelectionSheet = false) }
            }
            DashboardAction.OnDismissTestingTour -> {
                viewModelScope.launch {
                    tourPreferencesStore.setHasSeenTestingTour(true)
                }
                _uiState.update { it.copy(showTestingTour = false) }
            }
            DashboardAction.OnDismissGettingStarted -> {
                viewModelScope.launch {
                    tourPreferencesStore.setGettingStartedDismissed(true)
                }
                _uiState.update { it.copy(isGettingStartedDismissed = true) }
            }
            DashboardAction.OnResetGettingStarted -> {
                viewModelScope.launch {
                    tourPreferencesStore.setGettingStartedDismissed(false)
                }
                _uiState.update { it.copy(isGettingStartedDismissed = false) }
            }
            DashboardAction.OnStartDashboardSpotlight -> {
                _uiState.update { it.copy(showDashboardSpotlight = true, showTourSelectionSheet = false) }
            }
            DashboardAction.OnDismissDashboardSpotlight -> {
                viewModelScope.launch {
                    tourPreferencesStore.setHasSeenDashboardSpotlight(true)
                }
                _uiState.update { it.copy(showDashboardSpotlight = false) }
            }
            DashboardAction.OnOpenPipelineSimulator -> {
                _uiState.update { it.copy(showPipelineSimulator = true, showTourSelectionSheet = false) }
            }
            DashboardAction.OnDismissPipelineSimulator -> {
                _uiState.update { it.copy(showPipelineSimulator = false) }
            }
            is DashboardAction.OnPickLeaderboardEvent -> Unit // navigation only, handled by the Screen
            else -> Unit
        }
    }

    private fun observeTourPreferences() {
        viewModelScope.launch {
            tourPreferencesStore.observeHasSeenWelcomeTour()
                .collect { seen ->
                    _uiState.update {
                        it.copy(
                            hasSeenWelcomeTour = seen,
                            showWelcomeTour = !seen && it.showWelcomeTour
                        )
                    }
                }
        }
        viewModelScope.launch {
            tourPreferencesStore.observeGettingStartedDismissed()
                .collect { dismissed ->
                    _uiState.update { it.copy(isGettingStartedDismissed = dismissed) }
                }
        }
        viewModelScope.launch {
            tourPreferencesStore.observeHasSeenDashboardSpotlight()
                .collect { seen ->
                    _uiState.update { it.copy(hasSeenDashboardSpotlight = seen) }
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
