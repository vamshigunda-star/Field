package com.vamshi.field.ui.settings

import androidx.activity.result.IntentSenderRequest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.vamshi.field.ui.components.AppTopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    SettingsContent(
        uiState = uiState,
        onAction = { action ->
            if (action == SettingsAction.NavigateBack) {
                onNavigateBack()
            } else {
                viewModel.onAction(action)
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(
    uiState: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val authorizationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode != android.app.Activity.RESULT_OK) {
            onAction(SettingsAction.ConnectDriveError("Authorization cancelled or failed."))
            return@rememberLauncherForActivityResult
        }

        scope.launch {
            try {
                val authResult = Identity.getAuthorizationClient(context)
                    .getAuthorizationResultFromIntent(result.data)

                val accessToken = authResult.accessToken
                if (accessToken != null) {
                    val email = fetchEmailFromDrive(accessToken)
                    onAction(SettingsAction.ConnectDriveSuccess(email ?: "Connected"))
                } else {
                    onAction(SettingsAction.ConnectDriveError("Failed to get access token."))
                }
            } catch (e: Exception) {
                onAction(SettingsAction.ConnectDriveError("Authorization failed: ${e.message}"))
            }
        }
    }

    val onConnectClick = {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(
                Scope(DriveScopes.DRIVE_APPDATA),
                Scope("https://www.googleapis.com/auth/userinfo.email")
            ))
            .build()

        Identity.getAuthorizationClient(context)
            .authorize(request)
            .addOnSuccessListener { result ->
                if (result.hasResolution()) {
                    authorizationLauncher.launch(
                        IntentSenderRequest.Builder(result.pendingIntent!!.intentSender).build()
                    )
                } else {
                    scope.launch {
                        val email = result.accessToken?.let { fetchEmailFromDrive(it) }
                        onAction(SettingsAction.ConnectDriveSuccess(email ?: "Connected"))
                    }
                }
            }
            .addOnFailureListener { e ->
                onAction(SettingsAction.ConnectDriveError("Authorization failed: ${e.message}"))
            }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Settings",
                navigationIcon = {
                    IconButton(onClick = { onAction(SettingsAction.NavigateBack) }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Data Backup & Restore",
                style = MaterialTheme.typography.headlineMedium,
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (uiState.errorMessage != null) {
                Text(
                    text = uiState.errorMessage,
                    color = if (uiState.errorMessage.contains("successful", ignoreCase = true)) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }

            if (uiState.isSyncing) {
                CircularProgressIndicator()
                Text("Syncing in progress...")
            } else {
                if (!uiState.isDriveConnected) {
                    Button(onClick = { onConnectClick() }) {
                        Text("Connect Google Drive")
                    }
                } else {
                    if (uiState.connectedEmail != null) {
                        Text(
                            text = "Connected as: ${uiState.connectedEmail}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Button(
                        onClick = { onAction(SettingsAction.DisconnectDrive) },
                    ) {
                        Text("Sign Out")
                    }
                }

                Button(
                    onClick = { onAction(SettingsAction.BackupNow) },
                    enabled = uiState.isDriveConnected,
                ) {
                    Text("Backup Now")
                }

                Button(
                    onClick = { onAction(SettingsAction.RequestRestoreData) },
                    enabled = uiState.isDriveConnected,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Restore Data (Overwrites Local)")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            HorizontalDivider()

            Text(
                text = "Guided Tours & Tutorials",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            if (uiState.tourResetMessage != null) {
                Text(
                    text = uiState.tourResetMessage,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            OutlinedButton(
                onClick = { onAction(SettingsAction.OnOpenWelcomeTour) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Replay App Overview Tour")
            }

            OutlinedButton(
                onClick = { onAction(SettingsAction.OnOpenTestingTour) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Replay Testing Workflow Guide")
            }

            TextButton(
                onClick = { onAction(SettingsAction.OnResetAllTours) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Reset All Onboarding Hints & Checklists")
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (uiState.lastBackupTimestamp != null) {
                val dateString = SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault())
                    .format(Date(uiState.lastBackupTimestamp))
                Text(
                    text = "Last Backup: $dateString",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Text(
                    text = "No previous backup found locally.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        if (uiState.showWelcomeTour) {
            com.vamshi.field.ui.components.tour.WelcomeTourDialog(
                onDismiss = { onAction(SettingsAction.OnDismissWelcomeTour) },
                onStartTestingTour = { onAction(SettingsAction.OnOpenTestingTour) }
            )
        }

        if (uiState.showTestingTour) {
            com.vamshi.field.ui.components.tour.TestingTourDialog(
                onDismiss = { onAction(SettingsAction.OnDismissTestingTour) }
            )
        }

        if (uiState.showRestoreConfirmation) {
            AlertDialog(
                onDismissRequest = { onAction(SettingsAction.DismissRestoreConfirmation) },
                title = { Text("Restore from backup?") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("This replaces every athlete, group, and result on this device with the selected backup. It can't be undone.")
                        when {
                            uiState.isLoadingBackups -> Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                            uiState.availableBackups.isEmpty() -> Text(
                                "No backups were found for this Google account.",
                                color = MaterialTheme.colorScheme.error
                            )
                            else -> Column {
                                uiState.availableBackups.forEach { backup ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onAction(SettingsAction.SelectBackup(backup.id)) },
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = uiState.selectedBackupId == backup.id,
                                            onClick = { onAction(SettingsAction.SelectBackup(backup.id)) }
                                        )
                                        Column {
                                            Text(backup.deviceLabel, style = MaterialTheme.typography.bodyLarge)
                                            val dateText = if (backup.lastModified == 0L) {
                                                "Backed up date unknown"
                                            } else {
                                                "Backed up " + SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
                                                    .format(Date(backup.lastModified))
                                            }
                                            Text(
                                                dateText,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = { onAction(SettingsAction.RestoreData) },
                        enabled = uiState.selectedBackupId != null,
                    ) {
                        Text("Restore", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { onAction(SettingsAction.DismissRestoreConfirmation) }) {
                        Text("Cancel")
                    }
                },
            )
        }
    }
}

private suspend fun fetchEmailFromDrive(accessToken: String): String? = withContext(Dispatchers.IO) {
    try {
        val drive = Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance()
        ) { httpRequest ->
            httpRequest.headers.authorization = "Bearer $accessToken"
        }
        .setApplicationName("Field Backup")
        .build()

        drive.about().get().setFields("user(emailAddress)").execute().user.emailAddress
    } catch (_: Exception) {
        null
    }
}
