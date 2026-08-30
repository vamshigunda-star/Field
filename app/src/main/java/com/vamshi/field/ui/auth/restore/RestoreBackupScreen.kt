package com.vamshi.field.ui.auth.restore

import androidx.activity.result.IntentSenderRequest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes
import com.vamshi.field.domain.model.backup.DriveBackupSummary
import com.vamshi.field.ui.auth.components.AuthErrorBanner
import com.vamshi.field.ui.components.AppTopBar
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

 /**
 * Pre-auth "restore from Google Drive" screen, reachable from both Onboarding and
 * Unlock for a coach reinstalling the app who already has a backup.
 *
 * No password field, no security question: restoring the backup re-establishes
 * the whole account and session by itself.
 */
@Composable
fun RestoreBackupScreen(
    onNavigateBack: () -> Unit,
    onRestoreSuccess: () -> Unit,
    viewModel: RestoreBackupViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.restoreSuccess) {
        if (uiState.restoreSuccess) {
            viewModel.onAction(RestoreBackupAction.NavigationConsumed)
            onRestoreSuccess()
        }
    }

    RestoreBackupContent(
        uiState = uiState,
        onAction = viewModel::onAction,
        onNavigateBack = onNavigateBack
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestoreBackupContent(
    uiState: RestoreBackupUiState,
    onAction: (RestoreBackupAction) -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val authorizationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode != android.app.Activity.RESULT_OK) {
            onAction(RestoreBackupAction.AuthFailed("Sign-in cancelled or failed."))
            return@rememberLauncherForActivityResult
        }

        scope.launch {
            try {
                Identity.getAuthorizationClient(context)
                    .getAuthorizationResultFromIntent(result.data)
                
                onAction(RestoreBackupAction.AuthSucceeded)
            } catch (e: Exception) {
                onAction(RestoreBackupAction.AuthFailed("Authorization failed: ${e.message}"))
            }
        }
    }

    val onSignInClick = {
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
                    onAction(RestoreBackupAction.AuthSucceeded)
                }
            }
            .addOnFailureListener { e ->
                onAction(RestoreBackupAction.AuthFailed("Authorization failed: ${e.message}"))
            }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Restore data",
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (uiState.errorMessage != null) {
                AuthErrorBanner(
                    message = uiState.errorMessage,
                    onDismiss = { onAction(RestoreBackupAction.DismissError) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Text(
                text = "Already coaching with Field?",
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (uiState.availableBackups.isEmpty()) {
                    "If you've backed up your data to Google Drive before, sign in " +
                        "with the same Google account to restore your athletes, groups, and results."
                } else {
                    "Pick the backup to restore. Each one is a device that's backed up to this account."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))

            when {
                uiState.isRestoring -> LoadingIndicator("Downloading and restoring your data...")
                uiState.isLoadingBackups -> LoadingIndicator("Looking for backups on this account...")
                uiState.availableBackups.isNotEmpty() -> BackupPicker(
                    backups = uiState.availableBackups,
                    onSelect = { onAction(RestoreBackupAction.RestoreSelectedBackup(it.id)) }
                )
                else -> Button(
                    onClick = { onSignInClick() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = MaterialTheme.shapes.large
                ) {
                    Text("Sign in with Google")
                }
            }
        }
    }
}

@Composable
private fun LoadingIndicator(message: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CircularProgressIndicator()
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun BackupPicker(
    backups: List<DriveBackupSummary>,
    onSelect: (DriveBackupSummary) -> Unit
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(backups, key = { it.id }) { backup ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(backup) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(backup.deviceLabel, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = formatBackupDate(backup.lastModified),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private fun formatBackupDate(timestamp: Long): String =
    if (timestamp == 0L) "Backed up date unknown"
    else "Backed up ${SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(timestamp))}"

