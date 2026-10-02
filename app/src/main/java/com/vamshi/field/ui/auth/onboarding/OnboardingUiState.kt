package com.vamshi.field.ui.auth.onboarding

/**
 * UI state for the onboarding screen (Coach Name + optional Email).
 *
 * [isLoading] defaults to `false` — the form starts idle and becomes `true` only during
 * the async submit operation.
 *
 * [onboardingSuccess] is the flag the screen's `LaunchedEffect` watches to trigger
 * navigation to Dashboard without passing nav callbacks into the ViewModel.
 */
data class OnboardingUiState(
    val coachName: String = "",
    val email: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val onboardingSuccess: Boolean = false,

    // Per-field validation error string
    val coachNameError: String? = null
)
