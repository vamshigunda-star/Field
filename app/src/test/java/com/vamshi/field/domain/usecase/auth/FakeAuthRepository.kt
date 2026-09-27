package com.vamshi.field.domain.usecase.auth

import com.vamshi.field.domain.model.auth.AuthError
import com.vamshi.field.domain.model.auth.AuthResult
import com.vamshi.field.domain.model.auth.User
import com.vamshi.field.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory [AuthRepository] test double.
 *
 * Mirrors the real implementation's observable behavior — auto-sign-in on [signUp],
 * a generic [AuthError.InvalidCredentials] on failed [signIn]/[unlock] (no username-
 * enumeration leak), most-recently-created-wins account resolution — without touching
 * Room, SharedPreferences, or PBKDF2. Used to unit-test the use cases in this package
 * in isolation from the data layer, using a hand-written fake instead of a
 * mocking framework (none is on the test classpath).
 */
class FakeAuthRepository : AuthRepository {

    private data class StoredUser(val user: User, val password: String)

    private val users = mutableListOf<StoredUser>()
    private val currentUserId = MutableStateFlow<String?>(null)
    private var nextId = 0
    private var nextCreatedAt = 1_000L

    override suspend fun signUp(
        firstName: String,
        lastName: String,
        username: String,
        password: String,
        email: String?,
        securityQuestion: String?,
        securityAnswer: String?
    ): AuthResult {
        val normalized = username.trim().lowercase()
        if (users.any { it.user.username == normalized }) {
            return AuthResult.Failure(AuthError.UsernameTaken)
        }
        val user = User(
            id = "user-${nextId++}",
            firstName = firstName,
            lastName = lastName,
            username = normalized,
            email = email,
            createdAt = nextCreatedAt++
        )
        users += StoredUser(user, password)
        currentUserId.value = user.id
        return AuthResult.Success(user)
    }

    override suspend fun signIn(username: String, password: String): AuthResult {
        val normalized = username.trim().lowercase()
        val stored = users.firstOrNull { it.user.username == normalized && it.password == password }
            ?: return AuthResult.Failure(AuthError.InvalidCredentials)
        currentUserId.value = stored.user.id
        return AuthResult.Success(stored.user)
    }

    override suspend fun signOut() {
        currentUserId.value = null
    }

    override fun observeCurrentUser(): Flow<User?> =
        currentUserId.map { id -> users.firstOrNull { it.user.id == id }?.user }

    override suspend fun isUsernameTaken(username: String): Boolean =
        users.any { it.user.username == username.trim().lowercase() }

    override suspend fun userCount(): Int = users.size

    override suspend fun resetPassword(
        username: String,
        securityAnswer: String,
        newPassword: String
    ): AuthResult = AuthResult.Failure(AuthError.NoSecurityQuestion)

    override suspend fun getSecurityQuestion(username: String): String? = null

    override suspend fun getPrimaryAccount(): User? =
        users.maxByOrNull { it.user.createdAt }?.user

    override suspend fun listAccounts(): List<User> =
        users.sortedByDescending { it.user.createdAt }.map { it.user }

    override suspend fun unlock(userId: String, password: String): AuthResult {
        val stored = users.firstOrNull { it.user.id == userId && it.password == password }
            ?: return AuthResult.Failure(AuthError.InvalidCredentials)
        currentUserId.value = stored.user.id
        return AuthResult.Success(stored.user)
    }

    override suspend fun establishSessionAfterRestore(): AuthResult {
        val primary = users.maxByOrNull { it.user.createdAt }
            ?: return AuthResult.Failure(AuthError.Unknown)
        currentUserId.value = primary.user.id
        return AuthResult.Success(primary.user)
    }

    /** Test-only helper — reads the fake's session state without going through a use case. */
    fun currentSessionUserId(): String? = currentUserId.value
}
