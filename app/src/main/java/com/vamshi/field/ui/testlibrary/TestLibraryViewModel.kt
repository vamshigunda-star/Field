package com.vamshi.field.ui.testlibrary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vamshi.field.domain.model.standards.FitnessTest
import com.vamshi.field.domain.model.standards.TestCategory
import com.vamshi.field.domain.usecase.standards.DeleteCustomTestPreview
import com.vamshi.field.domain.usecase.standards.DeleteCustomTestResult
import com.vamshi.field.domain.usecase.standards.DeleteCustomTestUseCase
import com.vamshi.field.domain.usecase.standards.GetTestLibraryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Confirmation dialog for removing a custom test. [resultCount] > 0 means confirming
 * archives instead of deleting, and the dialog copy says so.
 */
data class DeleteTestDialogState(
    val testId: String,
    val testName: String,
    val resultCount: Int
)

data class TestLibraryUiState(
    val categories: List<TestCategory> = emptyList(),
    val allTests: List<FitnessTest> = emptyList(),
    val expandedCategoryId: String? = null, // accordion: id of the single open category section, null = all collapsed
    val searchQuery: String = "",
    val deleteDialog: DeleteTestDialogState? = null,
    /** One-shot toast-style confirmation after a delete/archive; screen shows and dismisses. */
    val snackbarMessage: String? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

sealed interface TestLibraryAction {
    data class OnToggleCategoryExpanded(val categoryId: String) : TestLibraryAction
    data class OnSearchQueryChanged(val query: String) : TestLibraryAction
    /** Nav-only: handled by the screen, ignored by the ViewModel. */
    data class OnEditTest(val testId: String) : TestLibraryAction
    data class OnRequestDeleteTest(val testId: String) : TestLibraryAction
    data object OnConfirmDeleteTest : TestLibraryAction
    data object OnDismissDeleteDialog : TestLibraryAction
    data object OnDismissSnackbar : TestLibraryAction
    data object OnNavigateBack : TestLibraryAction
    data object OnDismissError : TestLibraryAction
}

@HiltViewModel
class TestLibraryViewModel @Inject constructor(
    private val getTestLibrary: GetTestLibraryUseCase,
    private val deleteCustomTest: DeleteCustomTestUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(TestLibraryUiState())
    val uiState: StateFlow<TestLibraryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(getTestLibrary.getCategories(), getTestLibrary.getAllTests()) { categories, tests ->
                categories to tests
            }
                .catch { e -> _uiState.update { it.copy(errorMessage = e.message, isLoading = false) } }
                .collect { (categories, tests) ->
                    _uiState.update { state ->
                        state.copy(
                            categories = categories,
                            allTests = tests,
                            // seed only on first arrival; don't collapse a category the coach already opened
                            expandedCategoryId = state.expandedCategoryId ?: categories.firstOrNull()?.id,
                            isLoading = false
                        )
                    }
                }
        }
    }

    fun onAction(action: TestLibraryAction) {
        when (action) {
            is TestLibraryAction.OnToggleCategoryExpanded -> {
                _uiState.update {
                    it.copy(expandedCategoryId = if (it.expandedCategoryId == action.categoryId) null else action.categoryId)
                }
            }
            is TestLibraryAction.OnSearchQueryChanged -> _uiState.update { it.copy(searchQuery = action.query) }
            is TestLibraryAction.OnRequestDeleteTest -> requestDelete(action.testId)
            TestLibraryAction.OnConfirmDeleteTest -> confirmDelete()
            TestLibraryAction.OnDismissDeleteDialog -> _uiState.update { it.copy(deleteDialog = null) }
            TestLibraryAction.OnDismissSnackbar -> _uiState.update { it.copy(snackbarMessage = null) }
            is TestLibraryAction.OnDismissError -> _uiState.update { it.copy(errorMessage = null) }
            is TestLibraryAction.OnEditTest -> Unit // navigation — the screen's job
            is TestLibraryAction.OnNavigateBack -> Unit
        }
    }

    private fun requestDelete(testId: String) {
        viewModelScope.launch {
            val name = _uiState.value.allTests.find { it.id == testId }?.name ?: return@launch
            when (val preview = deleteCustomTest.preview(testId)) {
                is DeleteCustomTestPreview.WillDelete ->
                    _uiState.update { it.copy(deleteDialog = DeleteTestDialogState(testId, name, 0)) }
                is DeleteCustomTestPreview.WillArchive ->
                    _uiState.update {
                        it.copy(deleteDialog = DeleteTestDialogState(testId, name, preview.resultCount))
                    }
                DeleteCustomTestPreview.NotAllowed -> Unit // seeded test — the button isn't shown anyway
            }
        }
    }

    private fun confirmDelete() {
        val dialog = _uiState.value.deleteDialog ?: return
        viewModelScope.launch {
            val message = when (val result = deleteCustomTest(dialog.testId)) {
                DeleteCustomTestResult.Deleted -> "\"${dialog.testName}\" deleted"
                is DeleteCustomTestResult.Archived ->
                    "\"${dialog.testName}\" archived — ${result.resultCount} " +
                        "result${if (result.resultCount == 1) "" else "s"} kept"
                DeleteCustomTestResult.NotAllowed -> null
            }
            // The Room Flow re-emits and the test drops out of the list on its own.
            _uiState.update { it.copy(deleteDialog = null, snackbarMessage = message) }
        }
    }
}
