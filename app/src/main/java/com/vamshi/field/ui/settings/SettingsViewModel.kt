package com.vamshi.field.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vamshi.field.data.storage.TourPreferencesStore
import com.vamshi.field.domain.repository.BackupRepository
import com.vamshi.field.domain.usecase.backup.BackupDataUseCase
import com.vamshi.field.domain.usecase.backup.ListAvailableBackupsUseCase
import com.vamshi.field.domain.usecase.backup.RestoreDataUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.vamshi.field.data.backup.DriveBackupHelper
import androidx.core.content.edit
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backupDataUseCase: BackupDataUseCase,
    private val listAvailableBackupsUseCase: ListAvailableBackupsUseCase,
    private val restoreDataUseCase: RestoreDataUseCase,
    private val backupRepository: BackupRepository,
    private val driveBackupHelper: DriveBackupHelper,
    private val tourPreferencesStore: TourPreferencesStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        // Determine initial connection state from saved prefs
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val savedEmail = prefs.getString("google_account_name", null)
        val isConnected = savedEmail != null
        _uiState.update { it.copy(isDriveConnected = isConnected, connectedEmail = savedEmail) }

        viewModelScope.launch {
            val timestamp = backupRepository.getLastBackupTimestamp()
            _uiState.update { it.copy(lastBackupTimestamp = timestamp) }
        }

        viewModelScope.launch {
            backupRepository.isSyncing().collect { syncing ->
                _uiState.update { it.copy(isSyncing = syncing) }
            }
        }
    }

    fun onAction(action: SettingsAction) {
        when (action) {
            is SettingsAction.ConnectDrive -> { /* handled in UI */ }
            is SettingsAction.ConnectDriveSuccess -> handleConnectDriveSuccess(action.accountName)
            is SettingsAction.ConnectDriveError -> _uiState.update { it.copy(errorMessage = action.error) }
            is SettingsAction.DisconnectDrive -> handleDisconnectDrive()
            is SettingsAction.BackupNow -> handleBackupNow()
            is SettingsAction.RequestRestoreData -> handleRequestRestoreData()
            is SettingsAction.DismissRestoreConfirmation -> dismissRestoreConfirmation()
            is SettingsAction.SelectBackup -> _uiState.update { it.copy(selectedBackupId = action.backupId) }
            is SettingsAction.RestoreData -> handleRestoreData()
            is SettingsAction.OnOpenWelcomeTour -> _uiState.update { it.copy(showWelcomeTour = true) }
            is SettingsAction.OnDismissWelcomeTour -> _uiState.update { it.copy(showWelcomeTour = false) }
            is SettingsAction.OnOpenTestingTour -> _uiState.update { it.copy(showTestingTour = true) }
            is SettingsAction.OnDismissTestingTour -> _uiState.update { it.copy(showTestingTour = false) }
            is SettingsAction.OnResetAllTours -> handleResetAllTours()
            is SettingsAction.NavigateBack -> Unit
        }
    }

    private fun handleResetAllTours() {
        viewModelScope.launch {
            tourPreferencesStore.resetAllToursAndChecklists()
            _uiState.update { it.copy(tourResetMessage = "All walkthroughs and onboarding tips have been reset.") }
        }
    }

    private fun handleDisconnectDrive() {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit { remove("google_account_name") }
        _uiState.update { it.copy(isDriveConnected = false, connectedEmail = null, errorMessage = null) }
        viewModelScope.launch {
            try {
                driveBackupHelper.signOut(context)
            } catch (_: Exception) {
                // Ignore sign out errors
            }
        }
    }

    private fun handleConnectDriveSuccess(accountName: String) {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit { putString("google_account_name", accountName) }
        _uiState.update { it.copy(isDriveConnected = true, connectedEmail = accountName, errorMessage = null) }
    }

    private fun handleBackupNow() {
        if (!_uiState.value.isDriveConnected) {
            _uiState.update { it.copy(errorMessage = "Please connect to Google Drive first.") }
            return
        }

        viewModelScope.launch {
            try {
                backupDataUseCase()
                val timestamp = backupRepository.getLastBackupTimestamp()
                _uiState.update { it.copy(lastBackupTimestamp = timestamp, errorMessage = "Backup successful") }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message ?: "Failed to backup") }
            }
        }
    }

    private fun handleRequestRestoreData() {
        if (!_uiState.value.isDriveConnected) {
            _uiState.update { it.copy(errorMessage = "Please connect to Google Drive first.") }
            return
        }

        _uiState.update { it.copy(showRestoreConfirmation = true, isLoadingBackups = true) }
        viewModelScope.launch {
            try {
                val backups = listAvailableBackupsUseCase()
                _uiState.update {
                    it.copy(
                        isLoadingBackups = false,
                        availableBackups = backups,
                        selectedBackupId = backups.firstOrNull()?.id
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        showRestoreConfirmation = false,
                        isLoadingBackups = false,
                        errorMessage = e.message ?: "Couldn't load backups. Please try again."
                    )
                }
            }
        }
    }

    private fun dismissRestoreConfirmation() {
        _uiState.update {
            it.copy(
                showRestoreConfirmation = false,
                isLoadingBackups = false,
                availableBackups = emptyList(),
                selectedBackupId = null
            )
        }
    }

    private fun handleRestoreData() {
        val backupId = _uiState.value.selectedBackupId
        dismissRestoreConfirmation()
        if (backupId == null) return

        viewModelScope.launch {
            try {
                restoreDataUseCase(backupId)
                val timestamp = backupRepository.getLastBackupTimestamp()
                _uiState.update { it.copy(lastBackupTimestamp = timestamp, errorMessage = "Restore successful") }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message ?: "Failed to restore") }
            }
        }
    }
}
