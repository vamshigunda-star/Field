package com.vamshi.field.ui.auth.unlock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vamshi.field.ui.auth.components.AuthErrorBanner
import com.vamshi.field.ui.components.AppTopBar

/**
 * Quiet, white "Welcome back" unlock screen shown to a returning coach on every launch
 * after the first. Deliberately plain — matches [AppTopBar]'s white/compact contract,
 * unlike the one-time gradient hero on [com.vamshi.field.ui.auth.onboarding.OnboardingScreen].
 */
@Composable
fun UnlockScreen(
    onUnlockSuccess: () -> Unit,
    onNavigateToRestore: () -> Unit,
    viewModel: UnlockViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.unlockSuccess) {
        if (uiState.unlockSuccess) {
            viewModel.onAction(UnlockAction.NavigationConsumed)
            onUnlockSuccess()
        }
    }

    UnlockContent(
        uiState = uiState,
        onAction = viewModel::onAction,
        onNavigateToRestore = onNavigateToRestore
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnlockContent(
    uiState: UnlockUiState,
    onAction: (UnlockAction) -> Unit,
    onNavigateToRestore: () -> Unit
) {
    val focusManager = LocalFocusManager.current

    Scaffold(
        topBar = { AppTopBar(title = "Field") }
    ) { paddingValues ->
        if (uiState.isLoading && uiState.accountId == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (uiState.errorMessage != null) {
                AuthErrorBanner(
                    message = uiState.errorMessage,
                    onDismiss = { onAction(UnlockAction.DismissError) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Text(
                text = "Welcome back, ${uiState.coachDisplayName}",
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Enter your password to continue",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (uiState.showAccountSwitcher) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    uiState.accounts.forEach { account ->
                        val label = listOf(account.firstName, account.lastName)
                            .filter { it.isNotBlank() }
                            .joinToString(" ")
                            .ifBlank { "Coach" }
                        FilterChip(
                            selected = account.id == uiState.accountId,
                            onClick = { onAction(UnlockAction.AccountSelected(account.id)) },
                            label = { Text(label) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            OutlinedTextField(
                value = uiState.password,
                onValueChange = { onAction(UnlockAction.PasswordChanged(it)) },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = if (uiState.passwordVisible)
                    VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { onAction(UnlockAction.TogglePasswordVisibility) }) {
                        Icon(
                            imageVector = if (uiState.passwordVisible)
                                Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (uiState.passwordVisible)
                                "Hide password" else "Show password"
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = {
                    focusManager.clearFocus()
                    onAction(UnlockAction.Submit)
                }),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { onAction(UnlockAction.Submit) },
                enabled = !uiState.isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = MaterialTheme.shapes.large
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Continue")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(
                onClick = onNavigateToRestore,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("Trouble signing in?")
            }
        }
    }
}
