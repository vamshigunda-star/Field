package com.vamshi.field.domain.repository

import com.vamshi.field.domain.model.auth.AuthResult
import com.vamshi.field.domain.model.auth.User
import kotlinx.coroutines.flow.Flow

/**
 * Domain contract for all authentication operations.
 *
 * Implementations live in the data layer. The domain layer only depends on this
 * interface — never on concrete Room/SharedPreferences types.
 */
interface AuthRepository {

    /**
     * Creates a new coach account on this device.
     *
     * @param firstName The coach's given name (trimmed, non-blank, ≤50 chars). Onboarding
     *                   stores the full "Coach Name" string here and leaves [lastName] blank.
     * @param lastName  The coach's family name (trimmed, non-blank, ≤50 chars). May be blank.
     * @param username  The desired login handle — normalized to lowercase+trim. Onboarding
     *                   generates this automatically via `GenerateUsernameUseCase`; the coach
     *                   never types it.
     * @param password  Plain-text password; hashed inside the data layer via PBKDF2.
     * @param email     Optional — only used to enable Google Drive backup/restore later. Never
     *                   required and never a login field.
     * @return [AuthResult.Success] with the new [User], or a typed [AuthResult.Failure].
     */
    suspend fun signUp(
        firstName: String,
        lastName: String,
        username: String,
        password: String,
        email: String? = null
    ): AuthResult

    /**
     * Clears the active session. Does not delete the account.
     */
    suspend fun signOut()

    /**
     * Reactively observes the currently signed-in user.
     *
     * Emits `null` when no session is active or after [signOut].
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
     * Used by the auth gate to decide the initial destination: zero accounts → SignUp.
     */
    suspend fun userCount(): Int

    /**
     * Resolves the account the Unlock screen should greet by default.
     *
     * Zero accounts → `null`. One account → that account. Multiple accounts (rare —
     * only happens via a Drive restore that merges accounts, or manual multi-coach use)
     * → the most recently created one.
     */
    suspend fun getPrimaryAccount(): User?

    /**
     * Lists every coach account stored on this device, most recently created first.
     *
     * Used only to power the Unlock screen's account-switcher, which is hidden entirely
     * when there's a single account.
     */
    suspend fun listAccounts(): List<User>

    /**
     * Validates [password] for the account identified by [userId] and establishes a
     * session on success, keyed by a known account ID (from
     * [getPrimaryAccount]/[listAccounts]) rather than a typed username.
     *
     * @return [AuthResult.Success] with the [User], or [AuthResult.Failure] with the
     *         generic [com.vamshi.field.domain.model.auth.AuthError.InvalidCredentials]
     *         — deliberately not distinguishing "no such account" from "wrong password".
     */
    suspend fun unlock(userId: String, password: String): AuthResult

    /**
     * Establishes a session immediately after a Google Drive restore completes.
     *
     * The restored payload already contains a full account (with valid credentials) —
     * this does not re-prompt for a password. Picks the single/most-recent restored
     * account (same rule as [getPrimaryAccount]) and sets it as the active session.
     *
     * @return [AuthResult.Success] with the restored [User], or [AuthResult.Failure]
     *         with [com.vamshi.field.domain.model.auth.AuthError.Unknown] if the restore
     *         produced zero accounts.
     */
    suspend fun establishSessionAfterRestore(): AuthResult
}
