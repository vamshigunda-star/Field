package com.vamshi.field.ui.auth.onboarding

/** All user-initiated events from the onboarding screen. */
sealed interface OnboardingAction {
    data class CoachNameChanged(val value: String) : OnboardingAction
    data class EmailChanged(val value: String) : OnboardingAction
    data object Submit : OnboardingAction
    data object DismissError : OnboardingAction
    /** Called by the screen after consuming the [OnboardingUiState.onboardingSuccess] flag. */
    data object NavigationConsumed : OnboardingAction
}
