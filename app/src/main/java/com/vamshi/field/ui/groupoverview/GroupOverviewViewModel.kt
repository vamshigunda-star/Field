package com.vamshi.field.ui.groupoverview

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vamshi.field.domain.model.reports.GroupOverviewData
import com.vamshi.field.domain.repository.ReportsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GroupOverviewUiState(
    val data: GroupOverviewData? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val selectedTrendTestId: String? = null
)

sealed interface GroupOverviewAction {
    data class OnNavigateToSession(val sessionId: String) : GroupOverviewAction
    data object OnNavigateToCreateSession : GroupOverviewAction
    data object OnNavigateBack : GroupOverviewAction
    data class OnSelectTestTrend(val testId: String) : GroupOverviewAction
    data object OnDismissTestTrend : GroupOverviewAction
}

@HiltViewModel
class GroupOverviewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val reports: ReportsRepository
) : ViewModel() {

    val groupId: String = savedStateHandle["groupId"] ?: ""

    private val _uiState = MutableStateFlow(GroupOverviewUiState())
    val uiState: StateFlow<GroupOverviewUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            reports.observeGroupOverview(groupId)
                .catch { e -> _uiState.update { it.copy(errorMessage = e.message, isLoading = false) } }
                .collect { data -> _uiState.update { it.copy(data = data, isLoading = false) } }
        }
    }

    fun onAction(action: GroupOverviewAction) {
        when (action) {
            is GroupOverviewAction.OnSelectTestTrend -> _uiState.update { it.copy(selectedTrendTestId = action.testId) }
            GroupOverviewAction.OnDismissTestTrend -> _uiState.update { it.copy(selectedTrendTestId = null) }
            else -> Unit
        }
    }
}
