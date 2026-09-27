package com.vamshi.field.ui.auth

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vamshi.field.domain.repository.AuthRepository
import com.vamshi.field.domain.repository.SessionManager
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
 *  - [AuthGateState.Loading]              — async check in progress; show a spinner.
 *  - [AuthGateState.Authenticated]        — session exists → navigate to Dashboard.
 *  - [AuthGateState.UnauthenticatedHasUsers] — accounts exist, no session → go to SignIn.
 *  - [AuthGateState.UnauthenticatedNoUsers]  — no accounts at all → go to SignUp.
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
    data object UnauthenticatedHasUsers : AuthGateState
    data object UnauthenticatedNoUsers : AuthGateState
}

@HiltViewModel
class AuthGateViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _state = MutableStateFlow<AuthGateState>(AuthGateState.Loading)
    val state: StateFlow<AuthGateState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val result = withTimeoutOrNull(RESOLUTION_TIMEOUT_MS) {
                    val currentId = sessionManager.currentUserIdOnce()
                    if (currentId != null) {
                        AuthGateState.Authenticated
                    } else if (authRepository.userCount() == 0) {
                        AuthGateState.UnauthenticatedNoUsers
                    } else {
                        AuthGateState.UnauthenticatedHasUsers
                    }
                }

                if (result == null) {
                    Log.w(TAG, "Auth resolution timed out after ${RESOLUTION_TIMEOUT_MS}ms; falling back to onboarding.")
                    _state.value = AuthGateState.UnauthenticatedNoUsers
                } else {
                    _state.value = result
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error resolving auth state", e)
                _state.value = AuthGateState.UnauthenticatedNoUsers
            }
        }
    }

    private companion object {
        const val TAG = "AuthGateViewModel"
        const val RESOLUTION_TIMEOUT_MS = 5_000L
    }
}
