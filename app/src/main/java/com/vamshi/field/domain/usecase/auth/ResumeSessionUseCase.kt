package com.vamshi.field.domain.usecase.auth

import com.vamshi.field.domain.model.auth.AuthResult
import com.vamshi.field.domain.repository.AuthRepository
import com.vamshi.field.domain.repository.SessionManager
import javax.inject.Inject

/**
 * Decides at app startup whether a coach is already set up on this device.
 *
 * Field has no password and no sign-out, so "an account exists" means "open the app": an
 * existing session is kept, and a missing one is re-established on the primary account.
 * The missing-session case is a device that was signed out under the old password flow —
 * without this it would have no way back in short of a Drive restore.
 *
 * @return `true` if a coach session is active after the call, `false` if the device has no account.
 */
class ResumeSessionUseCase @Inject constructor(
    private val sessionManager: SessionManager,
    private val repository: AuthRepository
) {
    suspend operator fun invoke(): Boolean {
        if (sessionManager.currentUserIdOnce() != null) return true
        if (repository.userCount() == 0) return false
        return repository.establishPrimarySession() is AuthResult.Success
    }
}
