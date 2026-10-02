package com.vamshi.field.ui.auth

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vamshi.field.domain.usecase.auth.ResumeSessionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * Determines the app's initial destination on launch.
 *
 * Decision logic:
 *  - [AuthGateState.Loading]       — async check in progress; show a spinner.
 *  - [AuthGateState.Authenticated] — a coach account exists on this device → Dashboard.
 *  - [AuthGateState.NoAccount]     — no account at all → Onboarding.
 *
 * There is no lock screen: Field has no password, so an existing account always opens the
 * app ([ResumeSessionUseCase]).
 *
 * This ViewModel is consumed by [com.vamshi.field.ui.navigation.ALearningNavGraph]
 * to determine the [NavHost] start destination. Once the destination is resolved the
 * [NavHost] takes over and this ViewModel is not observed further.
 *
 * Resolution is bounded twice over — by [RESOLUTION_TIMEOUT_MS] and by a catch-all — and both
 * failure paths fall back to onboarding. The gate can therefore never strand the app on
 * [AuthGateState.Loading], which is what keeps a slow or corrupt database from looking like a
 * permanent hang. `AuthGateViewModelTest` asserts all three exits.
 */
sealed interface AuthGateState {
    data object Loading : AuthGateState
    data object Authenticated : AuthGateState
    data object NoAccount : AuthGateState
}

@HiltViewModel
class AuthGateViewModel @Inject constructor(
    private val resumeSession: ResumeSessionUseCase
) : ViewModel() {

    private val _state = MutableStateFlow<AuthGateState>(AuthGateState.Loading)
    val state: StateFlow<AuthGateState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val result = withTimeoutOrNull(RESOLUTION_TIMEOUT_MS) {
                    if (resumeSession()) AuthGateState.Authenticated else AuthGateState.NoAccount
                }

                if (result == null) {
                    Log.w(TAG, "Auth resolution timed out after ${RESOLUTION_TIMEOUT_MS}ms; falling back to onboarding.")
                    _state.value = AuthGateState.NoAccount
                } else {
                    _state.value = result
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error resolving auth state", e)
                _state.value = AuthGateState.NoAccount
            }
        }
    }

    private companion object {
        const val TAG = "AuthGateViewModel"
        const val RESOLUTION_TIMEOUT_MS = 5_000L
    }
}
