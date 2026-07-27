package com.vamshi.field.ui.auth.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vamshi.field.ui.auth.components.AuthErrorBanner
import com.vamshi.field.ui.theme.AquaCyan
import com.vamshi.field.ui.theme.NavyVariant
import com.vamshi.field.ui.theme.FieldTheme
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun OnboardingScreen(
    onOnboardingSuccess: () -> Unit,
    onNavigateToRestore: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.onboardingSuccess) {
        if (uiState.onboardingSuccess) {
            viewModel.onAction(OnboardingAction.NavigationConsumed)
            onOnboardingSuccess()
        }
    }

    OnboardingContent(
        uiState = uiState,
        onAction = viewModel::onAction,
        onNavigateToRestore = onNavigateToRestore
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingContent(
    uiState: OnboardingUiState,
    onAction: (OnboardingAction) -> Unit,
    onNavigateToRestore: () -> Unit
) {
    val focusManager = LocalFocusManager.current

    Column(modifier = Modifier.fillMaxSize()) {
        OnboardingHero(modifier = Modifier.weight(0.38f))

        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.62f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (uiState.errorMessage != null) {
                    AuthErrorBanner(
                        message = uiState.errorMessage,
                        onDismiss = { onAction(OnboardingAction.DismissError) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Text(
                    text = "Let's get set up",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Coach name
                OutlinedTextField(
                    value = uiState.coachName,
                    onValueChange = { onAction(OnboardingAction.CoachNameChanged(it)) },
                    label = { Text("Coach name") },
                    isError = uiState.coachNameError != null,
                    supportingText = uiState.coachNameError?.let { { Text(it) } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                    modifier = Modifier.fillMaxWidth()
                )

                // Password
                OutlinedTextField(
                    value = uiState.password,
                    onValueChange = { onAction(OnboardingAction.PasswordChanged(it)) },
                    label = { Text("Password") },
                    isError = uiState.passwordError != null,
                    supportingText = uiState.passwordError?.let { { Text(it) } }
                        ?: { Text("At least 8 characters, one letter, one digit") },
                    singleLine = true,
                    visualTransformation = if (uiState.passwordVisible)
                        VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { onAction(OnboardingAction.TogglePasswordVisibility) }) {
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
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                    modifier = Modifier.fillMaxWidth()
                )

                // Email (optional)
                OutlinedTextField(
                    value = uiState.email,
                    onValueChange = { onAction(OnboardingAction.EmailChanged(it)) },
                    label = { Text("Email (optional)") },
                    supportingText = {
                        Text("Optional — lets you back up and recover your data via Google Drive. Skip if you don't need it.")
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = {
                        focusManager.clearFocus()
                        onAction(OnboardingAction.Submit)
                    }),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { onAction(OnboardingAction.Submit) },
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
                        Text("Get Started")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = onNavigateToRestore,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text("Restore existing data")
                }
            }
        }
    }
}

@Composable
private fun OnboardingHero(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        NavyVariant,
                        MaterialTheme.colorScheme.primary,
                        AquaCyan
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Text(
                text = "Track. Test. Improve.",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Fitness testing built for coaches and PE teachers.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.9f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Preview
@Composable
private fun OnboardingContentPreview() {
    FieldTheme {
        OnboardingContent(
            uiState = OnboardingUiState(),
            onAction = {},
            onNavigateToRestore = {}
        )
    }
}
