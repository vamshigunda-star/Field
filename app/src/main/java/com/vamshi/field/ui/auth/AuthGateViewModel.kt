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
        Log.e("AuthGateViewModel", "Init started")
        viewModelScope.launch {
            try {
                Log.e("AuthGateViewModel", "Resolving auth state...")
                val result = withTimeoutOrNull(5000) {
                    val currentId = sessionManager.currentUserIdOnce()
                    Log.e("AuthGateViewModel", "currentUserIdOnce: $currentId")
                    if (currentId != null) {
                        AuthGateState.Authenticated
                    } else {
                        val count = authRepository.userCount()
                        Log.e("AuthGateViewModel", "userCount: $count")
                        if (count == 0) AuthGateState.UnauthenticatedNoUsers
                        else AuthGateState.UnauthenticatedHasUsers
                    }
                }
                
                if (result == null) {
                    Log.w("AuthGateViewModel", "Auth resolution timed out. Falling back to UnauthenticatedNoUsers.")
                    _state.value = AuthGateState.UnauthenticatedNoUsers
                } else {
                    Log.e("AuthGateViewModel", "Resolved to: $result")
                    _state.value = result
                }
            } catch (e: Exception) {
                Log.e("AuthGateViewModel", "Error resolving auth state", e)
                _state.value = AuthGateState.UnauthenticatedNoUsers
            }
        }
    }
}
