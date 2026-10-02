package com.vamshi.field.domain.repository

import com.vamshi.field.domain.model.auth.AuthResult
import com.vamshi.field.domain.model.auth.User
import kotlinx.coroutines.flow.Flow

/**
 * Domain contract for the coach account on this device.
 *
 * Field has no password, lock screen or sign-out by design: a forgotten password must never
 * lock a coach out of their athletes' data, so the device's own screen lock is the security
 * boundary. An "account" is just the coach's display name plus an optional email for Drive backup.
 *
 * Implementations live in the data layer. The domain layer only depends on this
 * interface — never on concrete Room/SharedPreferences types.
 */
interface AuthRepository {

    /**
     * Creates a new coach account on this device and makes it the active session.
     *
     * @param firstName The coach's given name (trimmed, non-blank, ≤50 chars). Onboarding
     *                   stores the full "Coach Name" string here and leaves [lastName] blank.
     * @param lastName  The coach's family name. May be blank.
     * @param username  Internal handle — normalized to lowercase+trim. Onboarding generates
     *                   this automatically via `GenerateUsernameUseCase`; the coach never sees it.
     * @param email     Optional — only used to enable Google Drive backup/restore later.
     * @return [AuthResult.Success] with the new [User], or a typed [AuthResult.Failure].
     */
    suspend fun signUp(
        firstName: String,
        lastName: String,
        username: String,
        email: String? = null
    ): AuthResult

    /**
     * Reactively observes the coach whose session is active.
     *
     * Emits `null` when no session is active.
     */
    fun observeCurrentUser(): Flow<User?>

    /**
     * Returns `true` if the given username is already registered on this device.
     *
     * Username is normalized (trim + lowercase) before the lookup.
     */
    suspend fun isUsernameTaken(username: String): Boolean

    /**
     * Returns the total number of coach accounts stored on this device.
     *
     * Used at startup to decide between Dashboard and onboarding.
     */
    suspend fun userCount(): Int

    /**
     * Sets the session to the most recently created account on this device.
     *
     * Used after a Google Drive restore and at app startup when an account exists but no
     * session does. More than one account is rare (an old backup, or an install from before
     * passwordless onboarding); no data is scoped per account, so which one wins only changes
     * the Dashboard greeting.
     *
     * @return [AuthResult.Success] with that [User], or [AuthResult.Failure] with
     *         [com.vamshi.field.domain.model.auth.AuthError.Unknown] if there are no accounts.
     */
    suspend fun establishPrimarySession(): AuthResult
}
