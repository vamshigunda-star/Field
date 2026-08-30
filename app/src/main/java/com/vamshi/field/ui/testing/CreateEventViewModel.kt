package com.vamshi.field.ui.testing

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vamshi.field.data.storage.CustomPresetsStore
import com.vamshi.field.domain.model.people.Group
import com.vamshi.field.domain.model.standards.FitnessTest
import com.vamshi.field.domain.model.standards.TestCategory
import com.vamshi.field.domain.model.standards.TestPreset
import com.vamshi.field.domain.repository.PeopleRepository
import com.vamshi.field.domain.usecase.standards.GetRecommendationsUseCase
import com.vamshi.field.domain.usecase.standards.GetTestLibraryUseCase
import com.vamshi.field.domain.usecase.testing.CreateEventUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class CreateEventUiState(
    val groups: List<Group> = emptyList(),
    val categories: List<TestCategory> = emptyList(),
    val allTests: List<FitnessTest> = emptyList(),
    val presets: List<TestPreset> = emptyList(),
    val groupAthleteCounts: Map<String, Int> = emptyMap(),
    val selectedGroupId: String? = null,
    val expandedCategoryId: String? = null, // accordion: id of the single open category section, null = all collapsed
    val selectedTestIds: Set<String> = emptySet(),
    val eventName: String = "",
    val isSavePresetDialogOpen: Boolean = false,
    val pendingPresetName: String = "",
    val isLoading: Boolean = true,
    val isCreating: Boolean = false,
    val errorMessage: String? = null,
    val eventCreated: Pair<String, String>? = null  // eventId to groupId, consumed by screen for nav
)

sealed interface CreateEventAction {
    data class SetEventName(val name: String) : CreateEventAction
    data class SelectGroup(val groupId: String) : CreateEventAction
    data class ToggleCategoryExpanded(val categoryId: String) : CreateEventAction
    data class ToggleTest(val testId: String) : CreateEventAction
    data class ApplyPreset(val presetId: String) : CreateEventAction
    data class DeletePreset(val presetId: String) : CreateEventAction
    data class SetPendingPresetName(val name: String) : CreateEventAction
    data object OpenSavePresetDialog : CreateEventAction
    data object DismissSavePresetDialog : CreateEventAction
    data object ConfirmSavePreset : CreateEventAction
    data object CreateEvent : CreateEventAction
    data object ClearError : CreateEventAction
    data object NavigationConsumed : CreateEventAction
    data object NavigateBack : CreateEventAction
}

@HiltViewModel
class CreateEventViewModel @Inject constructor(
    private val peopleRepository: PeopleRepository,
    private val getTestLibrary: GetTestLibraryUseCase,
    private val getRecommendations: GetRecommendationsUseCase,
    private val createEventUseCase: CreateEventUseCase,
    private val customPresetsStore: CustomPresetsStore,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateEventUiState())
    val uiState: StateFlow<CreateEventUiState> = _uiState.asStateFlow()

    private val recommendationId: String? = savedStateHandle["recommendationId"]
    private var recommendationApplied = false

    init {
        viewModelScope.launch {
            combine(peopleRepository.getAllGroups(), peopleRepository.getGroupAthleteCounts()) { groups, counts ->
                groups to counts
            }.collect { (groups, counts) ->
                _uiState.update { it.copy(groups = groups, groupAthleteCounts = counts, isLoading = false) }
            }
        }
        viewModelScope.launch {
            combine(getTestLibrary.getCategories(), getTestLibrary.getAllTests()) { categories, tests ->
                categories to tests
            }.collect { (categories, tests) ->
                _uiState.update { state ->
                    state.copy(
                        categories = categories,
                        allTests = tests,
                        expandedCategoryId = state.expandedCategoryId,
                        presets = buildPresets(categories, tests)
                    )
                }
            }
        }

        if (recommendationId != null) {
            viewModelScope.launch {
                combine(
                    getRecommendations.getRecommendedTests(recommendationId),
                    getTestLibrary.getAllTests()
                ) { recommendedTests, allTests ->
                    recommendedTests to allTests
                }.collect { (recommendedTests, allTests) ->
                    if (!recommendationApplied && recommendedTests.isNotEmpty()) {
                        val knownIds = allTests.map { it.id }.toSet()
                        val toSelect = if (knownIds.isNotEmpty()) {
                            recommendedTests.map { it.id }.filter { it in knownIds }.toSet()
                        } else {
                            recommendedTests.map { it.id }.toSet()
                        }
                        if (toSelect.isNotEmpty()) {
                            recommendationApplied = true
                            android.util.Log.d("CreateEventViewModel", "Successfully auto-selected ${toSelect.size} recommended tests for category $recommendationId")
                            _uiState.update { it.copy(selectedTestIds = toSelect) }
                        }
                    }
                }
            }
        }
    }

    fun onAction(action: CreateEventAction) {
        when (action) {
            is CreateEventAction.SetEventName -> _uiState.update { it.copy(eventName = action.name) }
            is CreateEventAction.SelectGroup -> _uiState.update { it.copy(selectedGroupId = action.groupId) }
            is CreateEventAction.ToggleCategoryExpanded -> {
                _uiState.update {
                    it.copy(expandedCategoryId = if (it.expandedCategoryId == action.categoryId) null else action.categoryId)
                }
            }
            is CreateEventAction.ToggleTest -> {
                val current = _uiState.value.selectedTestIds
                _uiState.update {
                    it.copy(selectedTestIds = if (action.testId in current) current - action.testId else current + action.testId)
                }
            }
            is CreateEventAction.ApplyPreset -> applyPreset(action.presetId)
            is CreateEventAction.DeletePreset -> deletePreset(action.presetId)
            is CreateEventAction.SetPendingPresetName -> _uiState.update { it.copy(pendingPresetName = action.name) }
            CreateEventAction.OpenSavePresetDialog -> openSaveDialog()
            CreateEventAction.DismissSavePresetDialog -> _uiState.update {
                it.copy(isSavePresetDialogOpen = false, pendingPresetName = "")
            }
            CreateEventAction.ConfirmSavePreset -> confirmSavePreset()
            CreateEventAction.CreateEvent -> createEvent()
            CreateEventAction.ClearError -> _uiState.update { it.copy(errorMessage = null) }
            CreateEventAction.NavigationConsumed -> _uiState.update { it.copy(eventCreated = null) }
            CreateEventAction.NavigateBack -> Unit
        }
    }

    private fun buildPresets(categories: List<TestCategory>, tests: List<FitnessTest>): List<TestPreset> {
        if (tests.isEmpty()) return emptyList()
        val knownTestIds = tests.map { it.id }.toSet()
        return customPresetsStore.load().map { preset ->
            preset.copy(testIds = preset.testIds.filter { it in knownTestIds })
        }
    }

    private fun applyPreset(presetId: String) {
        val preset = _uiState.value.presets.find { it.id == presetId } ?: return
        val currentlyApplied = _uiState.value.selectedTestIds == preset.testIds.toSet()
        _uiState.update {
            it.copy(selectedTestIds = if (currentlyApplied) emptySet() else preset.testIds.toSet())
        }
    }

    private fun openSaveDialog() {
        if (_uiState.value.selectedTestIds.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Select at least one test before saving a custom list") }
            return
        }
        _uiState.update { it.copy(isSavePresetDialogOpen = true, pendingPresetName = "") }
    }

    private fun confirmSavePreset() {
        val state = _uiState.value
        val name = state.pendingPresetName.trim()
        if (name.isBlank()) {
            _uiState.update { it.copy(errorMessage = "List name cannot be empty") }
            return
        }
        val preset = TestPreset(
            id = "custom_${UUID.randomUUID()}",
            name = name,
            description = null,
            testIds = state.selectedTestIds.toList(),
            isBuiltIn = false
        )
        customPresetsStore.add(preset)
        _uiState.update {
            it.copy(
                presets = it.presets + preset,
                isSavePresetDialogOpen = false,
                pendingPresetName = ""
            )
        }
    }

    private fun deletePreset(presetId: String) {
        customPresetsStore.delete(presetId)
        _uiState.update { it.copy(presets = it.presets.filterNot { preset -> preset.id == presetId }) }
    }

    private fun createEvent() {
        val state = _uiState.value
        val groupId = state.selectedGroupId ?: run {
            _uiState.update { it.copy(errorMessage = "Please select a group") }
            return
        }
        if (state.eventName.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please enter an event name") }
            return
        }
        if (state.selectedTestIds.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Please select at least one test") }
            return
        }

        _uiState.update { it.copy(isCreating = true) }
        viewModelScope.launch {
            val result = createEventUseCase(
                name = state.eventName,
                date = System.currentTimeMillis(),
                groupId = groupId,
                testIds = state.selectedTestIds.toList()
            )
            result.onSuccess { event ->
                _uiState.update { it.copy(isCreating = false, eventCreated = event.id to groupId) }
            }
            result.onFailure { e ->
                _uiState.update { it.copy(isCreating = false, errorMessage = e.message ?: "Failed to create event") }
            }
        }
    }
}
