package com.vamshi.field.ui.recommendations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vamshi.field.domain.model.standards.FitnessTest
import com.vamshi.field.domain.model.standards.RecommendationCategory
import com.vamshi.field.domain.usecase.standards.GetRecommendationsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RecommendationsUiState(
    val categories: List<RecommendationCategory> = emptyList(),
    val selectedCategory: RecommendationCategory? = null,
    val recommendedTests: List<FitnessTest> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

sealed interface RecommendationsAction {
    data class OnToggleCategory(val category: RecommendationCategory) : RecommendationsAction
    data object OnApplyAndContinue : RecommendationsAction
    data object OnNavigateBack : RecommendationsAction
    data object OnDismissError : RecommendationsAction
}

@HiltViewModel
class RecommendationsViewModel @Inject constructor(
    private val getRecommendations: GetRecommendationsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecommendationsUiState())
    val uiState: StateFlow<RecommendationsUiState> = _uiState.asStateFlow()

    private var testsJob: Job? = null

    init {
        viewModelScope.launch {
            getRecommendations.getCategories()
                .catch { e -> _uiState.update { it.copy(errorMessage = e.message, isLoading = false) } }
                .collect { categories ->
                    // Re-resolve by id so a reseed that renames/removes a category can't
                    // leave a stale selection (or stale description text) on screen. Otherwise
                    // seed only on first arrival — don't reopen a section the coach collapsed.
                    val previousId = _uiState.value.selectedCategory?.id
                    val hasSeeded = testsJob != null
                    val selected = when {
                        previousId != null -> categories.firstOrNull { it.id == previousId }
                        !hasSeeded -> categories.firstOrNull()
                        else -> null
                    }
                    _uiState.update {
                        it.copy(categories = categories, selectedCategory = selected, isLoading = false)
                    }
                    if (selected != null && selected.id != previousId) {
                        loadRecommendedTests(selected.id)
                    }
                }
        }
    }

    fun onAction(action: RecommendationsAction) {
        when (action) {
            is RecommendationsAction.OnToggleCategory -> {
                val collapsing = _uiState.value.selectedCategory?.id == action.category.id
                testsJob?.cancel()
                if (collapsing) {
                    _uiState.update { it.copy(selectedCategory = null, recommendedTests = emptyList()) }
                } else {
                    _uiState.update { it.copy(selectedCategory = action.category) }
                    loadRecommendedTests(action.category.id)
                }
            }
            is RecommendationsAction.OnDismissError -> _uiState.update { it.copy(errorMessage = null) }
            is RecommendationsAction.OnApplyAndContinue -> Unit // navigation-only, handled by the Screen
            is RecommendationsAction.OnNavigateBack -> Unit // navigation-only, handled by the Screen
        }
    }

    private fun loadRecommendedTests(categoryId: String) {
        testsJob?.cancel()
        testsJob = viewModelScope.launch {
            getRecommendations.getRecommendedTests(categoryId)
                .catch { e -> _uiState.update { it.copy(errorMessage = e.message) } }
                .collect { tests ->
                    _uiState.update { it.copy(recommendedTests = tests) }
                }
        }
    }
}
