package com.vamshi.field.ui.settings

import com.vamshi.field.domain.model.backup.DriveBackupSummary

sealed interface SettingsAction {
    object ConnectDrive : SettingsAction
    data class ConnectDriveSuccess(val accountName: String) : SettingsAction
    data class ConnectDriveError(val error: String) : SettingsAction
    object DisconnectDrive : SettingsAction
    object BackupNow : SettingsAction
    object RequestRestoreData : SettingsAction   // button tap — loads backups, opens confirmation
    object DismissRestoreConfirmation : SettingsAction
    data class SelectBackup(val backupId: String) : SettingsAction
    object RestoreData : SettingsAction           // now only fired by dialog confirm
    object NavigateBack : SettingsAction
    // Tours & Tutorials Actions
    object OnOpenWelcomeTour : SettingsAction
    object OnDismissWelcomeTour : SettingsAction
    object OnOpenTestingTour : SettingsAction
    object OnDismissTestingTour : SettingsAction
    object OnResetAllTours : SettingsAction
}

data class SettingsUiState(
    val isSyncing: Boolean = false,
    val isDriveConnected: Boolean = false,
    val connectedEmail: String? = null,
    val lastBackupTimestamp: Long? = null,
    val errorMessage: String? = null,
    val showRestoreConfirmation: Boolean = false,
    val isLoadingBackups: Boolean = false,
    val availableBackups: List<DriveBackupSummary> = emptyList(),
    val selectedBackupId: String? = null,
    val showWelcomeTour: Boolean = false,
    val showTestingTour: Boolean = false,
    val tourResetMessage: String? = null
)
