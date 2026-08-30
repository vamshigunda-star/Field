package com.vamshi.field.ui.auth.restore

/**
 * All user-initiated events from the restore-backup screen.
 *
 * The Auth launcher itself lives in the Screen composable (it needs an
 * Activity result contract, same pattern as [com.vamshi.field.ui.settings.SettingsScreen]) —
 * [AuthSucceeded]/[AuthFailed] are how its result is reported back in.
 * A successful sign-in loads the available backups for that account rather than
 * restoring immediately; [RestoreSelectedBackup] is fired once the coach picks one.
 */
sealed interface RestoreBackupAction {
    data object AuthSucceeded : RestoreBackupAction
    data class AuthFailed(val message: String) : RestoreBackupAction
    data class RestoreSelectedBackup(val backupId: String) : RestoreBackupAction
    data object DismissError : RestoreBackupAction
    /** Called by the screen after consuming the [RestoreBackupUiState.restoreSuccess] flag. */
    data object NavigationConsumed : RestoreBackupAction
}
