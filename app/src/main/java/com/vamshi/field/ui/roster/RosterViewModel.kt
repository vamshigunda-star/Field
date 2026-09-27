package com.vamshi.field.ui.roster

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vamshi.field.domain.model.people.BiologicalSex
import com.vamshi.field.domain.model.people.Group
import com.vamshi.field.domain.model.people.Individual
import com.vamshi.field.domain.repository.PeopleRepository
import com.vamshi.field.domain.usecase.people.CreateGroupUseCase
import com.vamshi.field.domain.usecase.people.DeleteGroupUseCase
import com.vamshi.field.domain.usecase.people.ManageRosterUseCase
import com.vamshi.field.domain.usecase.people.RegisterAthleteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.vamshi.field.domain.model.people.AgeBracket

enum class RosterTab {
    ATHLETES, GROUPS
}

/**
 * The roster filter uses the same brackets as the norm tables, so "Adolescence" on a chip
 * and "Adolescence" in a norm lookup mean the same span of ages. Kept as an alias rather
 * than a rename so existing call sites are untouched.
 */
typealias AthleteAgeRange = AgeBracket

private fun ageOf(individual: Individual): Int =
    ((System.currentTimeMillis() - individual.dateOfBirth) / 31_557_600_000L).toInt()

private fun matchesAgeRange(individual: Individual, range: AthleteAgeRange): Boolean =
    ageOf(individual) in range

data class RosterUiState(
    val currentTab: RosterTab = RosterTab.ATHLETES,
    val athleteSearchQuery: String = "",
    val groupSearchQuery: String = "",
    val selectedSexFilters: Set<BiologicalSex> = emptySet(),
    val selectedAgeRange: AthleteAgeRange? = null,
    val selectedAthleteIds: Set<String> = emptySet(),
    val expandedGroupIds: Set<String> = emptySet(),
    val groups: List<Group> = emptyList(),
    val groupMembers: Map<String, List<Individual>> = emptyMap(),
    val athleteGroups: Map<String, List<Group>> = emptyMap(),
    val selectedGroup: Group? = null,
    val allAthletes: List<Individual> = emptyList(),
    val showAddGroupDialog: Boolean = false,
    val showRegisterAthleteDialog: Boolean = false,
    val showAddToGroupDialog: Boolean = false,
    val showManageMembersDialog: String? = null, // Group ID
    val showDeleteAthleteConfirmation: String? = null,
    val showRemoveMemberConfirmation: Pair<String, String>? = null,
    val showDeleteGroupConfirmation: Group? = null,
    val userMessage: String? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null
) {
    val filteredAthletes: List<Individual> = allAthletes
        .filter {
            athleteSearchQuery.isBlank() ||
                it.firstName.contains(athleteSearchQuery, ignoreCase = true) ||
                it.lastName.contains(athleteSearchQuery, ignoreCase = true)
        }
        .filter { selectedSexFilters.isEmpty() || it.sex in selectedSexFilters }
        .filter { selectedAgeRange == null || matchesAgeRange(it, selectedAgeRange) }

    val filteredGroups: List<Group> = if (groupSearchQuery.isBlank()) {
        groups
    } else {
        groups.filter { it.name.contains(groupSearchQuery, ignoreCase = true) }
    }
}

sealed interface RosterAction {
    data class OnTabSelected(val tab: RosterTab) : RosterAction
    data class OnAthleteSearchQueryChanged(val query: String) : RosterAction
    data class OnGroupSearchQueryChanged(val query: String) : RosterAction
    data class OnSexFilterToggled(val sex: BiologicalSex) : RosterAction
    data class OnAgeRangeFilterSelected(val range: AthleteAgeRange?) : RosterAction
    data class OnToggleAthleteSelection(val id: String) : RosterAction
    data class OnToggleGroupExpansion(val id: String) : RosterAction
    data class OnDeleteAthlete(val id: String) : RosterAction
    data class OnConfirmDeleteAthlete(val id: String) : RosterAction
    data object OnDismissDeleteConfirmation : RosterAction
    
    data class OnSelectGroup(val group: Group) : RosterAction
    data object OnShowAddGroupDialog : RosterAction
    data object OnDismissAddGroupDialog : RosterAction
    data class OnCreateGroup(val name: String, val location: String?, val cycle: String?) : RosterAction
    data class OnDeleteGroup(val group: Group) : RosterAction
    data class OnConfirmDeleteGroup(val group: Group) : RosterAction
    data object OnDismissDeleteGroupConfirmation : RosterAction
    data object OnShowRegisterAthleteDialog : RosterAction
    data object OnDismissRegisterAthleteDialog : RosterAction
    data class OnRegisterAthlete(
        val firstName: String,
        val lastName: String,
        val dateOfBirth: Long,
        val sex: BiologicalSex,
        val email: String?,
        val medicalAlert: String?
    ) : RosterAction
    data object OnShowAddToGroupDialog : RosterAction
    data object OnDismissAddToGroupDialog : RosterAction
    data class OnShowManageMembersDialog(val groupId: String) : RosterAction
    data object OnDismissManageMembersDialog : RosterAction
    data class OnAddAthleteToGroup(val groupId: String, val individualId: String) : RosterAction
    data class OnAddSelectedToGroup(val groupId: String) : RosterAction
    data class OnRemoveAthleteFromGroup(val groupId: String, val individualId: String) : RosterAction
    data class OnConfirmRemoveMember(val groupId: String, val individualId: String) : RosterAction
    data object OnDismissRemoveMemberConfirmation : RosterAction

    data class OnNavigateToAthleteReport(val individualId: String) : RosterAction
    data object OnNavigateBack : RosterAction
    data object OnDismissError : RosterAction
    data object OnDismissUserMessage : RosterAction
}

@HiltViewModel
class RosterViewModel @Inject constructor(
    private val peopleRepository: PeopleRepository,
    private val registerAthlete: RegisterAthleteUseCase,
    private val createGroup: CreateGroupUseCase,
    private val deleteGroupUseCase: DeleteGroupUseCase,
    private val manageRoster: ManageRosterUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(RosterUiState())
    val uiState: StateFlow<RosterUiState> = _uiState.asStateFlow()

    private var groupMembersJob: Job? = null

    init {
        viewModelScope.launch {
            peopleRepository.getAllGroups()
                .catch { e -> _uiState.update { it.copy(errorMessage = e.message, isLoading = false) } }
                .collect { groups ->
                    _uiState.update { current ->
                        val selected = current.selectedGroup ?: groups.firstOrNull()
                        current.copy(groups = groups, selectedGroup = selected, isLoading = false)
                    }
                    // Load members for all groups to show stacks and expanded lists
                    groups.forEach { group ->
                        loadGroupMembers(group.id)
                    }
                }
        }
        viewModelScope.launch {
            peopleRepository.getAllIndividuals()
                .catch { e -> _uiState.update { it.copy(errorMessage = e.message) } }
                .collect { athletes ->
                    _uiState.update { it.copy(allAthletes = athletes) }
                    // Load groups for each athlete to show tags
                    athletes.forEach { athlete ->
                        loadAthleteGroups(athlete.id)
                    }
                }
        }
    }

    private fun loadGroupMembers(groupId: String) {
        viewModelScope.launch {
            manageRoster.getStudentsInGroup(groupId)
                .catch { e -> _uiState.update { it.copy(errorMessage = e.message) } }
                .collect { members ->
                    _uiState.update { current ->
                        val newMap = current.groupMembers.toMutableMap()
                        newMap[groupId] = members
                        current.copy(groupMembers = newMap)
                    }
                }
        }
    }

    private fun loadAthleteGroups(athleteId: String) {
        viewModelScope.launch {
            peopleRepository.getGroupsForIndividual(athleteId)
                .catch { e -> _uiState.update { it.copy(errorMessage = e.message) } }
                .collect { groups ->
                    _uiState.update { current ->
                        val newMap = current.athleteGroups.toMutableMap()
                        newMap[athleteId] = groups
                        current.copy(athleteGroups = newMap)
                    }
                }
        }
    }

    fun onAction(action: RosterAction) {
        when (action) {
            is RosterAction.OnTabSelected -> _uiState.update { it.copy(currentTab = action.tab) }
            is RosterAction.OnAthleteSearchQueryChanged -> _uiState.update { it.copy(athleteSearchQuery = action.query) }
            is RosterAction.OnGroupSearchQueryChanged -> _uiState.update { it.copy(groupSearchQuery = action.query) }
            is RosterAction.OnSexFilterToggled -> _uiState.update { current ->
                val newFilters = if (action.sex in current.selectedSexFilters) {
                    current.selectedSexFilters - action.sex
                } else {
                    current.selectedSexFilters + action.sex
                }
                current.copy(selectedSexFilters = newFilters)
            }
            is RosterAction.OnAgeRangeFilterSelected -> _uiState.update { it.copy(selectedAgeRange = action.range) }
            is RosterAction.OnToggleAthleteSelection -> toggleAthleteSelection(action.id)
            is RosterAction.OnToggleGroupExpansion -> toggleGroupExpansion(action.id)
            is RosterAction.OnDeleteAthlete -> _uiState.update { it.copy(showDeleteAthleteConfirmation = action.id) }
            is RosterAction.OnConfirmDeleteAthlete -> deleteAthlete(action.id)
            is RosterAction.OnDismissDeleteConfirmation -> _uiState.update { it.copy(showDeleteAthleteConfirmation = null) }
            
            is RosterAction.OnSelectGroup -> selectGroup(action.group)
            is RosterAction.OnShowAddGroupDialog -> _uiState.update { it.copy(showAddGroupDialog = true) }
            is RosterAction.OnDismissAddGroupDialog -> _uiState.update { it.copy(showAddGroupDialog = false) }
            is RosterAction.OnCreateGroup -> addGroup(action.name, action.location, action.cycle)
            is RosterAction.OnDeleteGroup -> _uiState.update { it.copy(showDeleteGroupConfirmation = action.group) }
            is RosterAction.OnConfirmDeleteGroup -> deleteGroup(action.group)
            is RosterAction.OnDismissDeleteGroupConfirmation -> _uiState.update { it.copy(showDeleteGroupConfirmation = null) }
            is RosterAction.OnShowRegisterAthleteDialog -> _uiState.update { it.copy(showRegisterAthleteDialog = true) }
            is RosterAction.OnDismissRegisterAthleteDialog -> _uiState.update { it.copy(showRegisterAthleteDialog = false) }
            is RosterAction.OnRegisterAthlete -> registerNewAthlete(action)
            is RosterAction.OnShowAddToGroupDialog -> _uiState.update { it.copy(showAddToGroupDialog = true) }
            is RosterAction.OnDismissAddToGroupDialog -> _uiState.update { it.copy(showAddToGroupDialog = false) }
            is RosterAction.OnShowManageMembersDialog -> _uiState.update { it.copy(showManageMembersDialog = action.groupId) }
            is RosterAction.OnDismissManageMembersDialog -> _uiState.update { it.copy(showManageMembersDialog = null) }
            is RosterAction.OnAddAthleteToGroup -> addAthleteToGroup(action.groupId, action.individualId)
            is RosterAction.OnAddSelectedToGroup -> addSelectedToGroup(action.groupId)
            is RosterAction.OnRemoveAthleteFromGroup -> _uiState.update { it.copy(showRemoveMemberConfirmation = action.groupId to action.individualId) }
            is RosterAction.OnConfirmRemoveMember -> removeAthleteFromGroup(action.groupId, action.individualId)
            is RosterAction.OnDismissRemoveMemberConfirmation -> _uiState.update { it.copy(showRemoveMemberConfirmation = null) }
            
            is RosterAction.OnDismissError -> _uiState.update { it.copy(errorMessage = null) }
            is RosterAction.OnDismissUserMessage -> _uiState.update { it.copy(userMessage = null) }
            // Navigation actions handled by the screen composable
            is RosterAction.OnNavigateToAthleteReport -> Unit
            is RosterAction.OnNavigateBack -> Unit
        }
    }

    private fun toggleAthleteSelection(id: String) {
        _uiState.update { current ->
            val newSelected = if (current.selectedAthleteIds.contains(id)) {
                current.selectedAthleteIds - id
            } else {
                current.selectedAthleteIds + id
            }
            current.copy(selectedAthleteIds = newSelected)
        }
    }

    private fun toggleGroupExpansion(id: String) {
        _uiState.update { current ->
            val newExpanded = if (current.expandedGroupIds.contains(id)) {
                current.expandedGroupIds - id
            } else {
                current.expandedGroupIds + id
            }
            current.copy(expandedGroupIds = newExpanded)
        }
    }

    private fun selectGroup(group: Group) {
        _uiState.update { it.copy(selectedGroup = group) }
    }

    private fun addGroup(name: String, location: String?, cycle: String?) {
        viewModelScope.launch {
            createGroup(name, location, cycle)
                .onFailure { e -> _uiState.update { it.copy(errorMessage = e.message) } }
            _uiState.update { it.copy(showAddGroupDialog = false) }
        }
    }

    private fun registerNewAthlete(action: RosterAction.OnRegisterAthlete) {
        viewModelScope.launch {
            registerAthlete(
                firstName = action.firstName,
                lastName = action.lastName,
                dateOfBirth = action.dateOfBirth,
                sex = action.sex,
                email = action.email,
                medicalAlert = action.medicalAlert
            ).onFailure { e -> _uiState.update { it.copy(errorMessage = e.message) } }
            _uiState.update { it.copy(showRegisterAthleteDialog = false) }
        }
    }

    private fun addAthleteToGroup(groupId: String, individualId: String) {
        viewModelScope.launch {
            manageRoster.addStudentToGroup(groupId, individualId)
        }
    }

    private fun addSelectedToGroup(groupId: String) {
        val selectedIds = _uiState.value.selectedAthleteIds
        viewModelScope.launch {
            selectedIds.forEach { athleteId ->
                manageRoster.addStudentToGroup(groupId, athleteId)
            }
            _uiState.update { it.copy(selectedAthleteIds = emptySet(), showAddToGroupDialog = false) }
        }
    }

    private fun removeAthleteFromGroup(groupId: String, individualId: String) {
        viewModelScope.launch {
            manageRoster.removeStudentFromGroup(groupId, individualId)
            _uiState.update { it.copy(showRemoveMemberConfirmation = null) }
        }
    }

    private fun deleteAthlete(id: String) {
        // Note: Repository needs a deleteIndividual method if we want to fully remove them.
        // IndividualEntity has isActive and isDeleted flags.
        viewModelScope.launch {
            val athlete = _uiState.value.allAthletes.find { it.id == id }
            if (athlete != null) {
                peopleRepository.deleteIndividual(athlete)
            }
            _uiState.update { it.copy(showDeleteAthleteConfirmation = null) }
        }
    }

    private fun deleteGroup(group: Group) {
        viewModelScope.launch {
            deleteGroupUseCase(group)
                .onSuccess {
                    _uiState.update { current ->
                        current.copy(
                            showDeleteGroupConfirmation = null,
                            expandedGroupIds = current.expandedGroupIds - group.id,
                            selectedGroup = if (current.selectedGroup?.id == group.id) null else current.selectedGroup,
                            userMessage = "${group.name} deleted"
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { current ->
                        current.copy(
                            showDeleteGroupConfirmation = null,
                            errorMessage = error.message ?: "Failed to delete group"
                        )
                    }
                }
        }
    }
}
