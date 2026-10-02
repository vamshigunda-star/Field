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
 * most-recently-created-wins account resolution — without touching Room or
 * SharedPreferences. Used to unit-test the use cases in this package in isolation from
 * the data layer, using a hand-written fake instead of a mocking framework (none is on
 * the test classpath).
 */
class FakeAuthRepository : AuthRepository {

    private val users = mutableListOf<User>()
    private val currentUserId = MutableStateFlow<String?>(null)
    private var nextId = 0
    private var nextCreatedAt = 1_000L

    override suspend fun signUp(
        firstName: String,
        lastName: String,
        username: String,
        email: String?
    ): AuthResult {
        val normalized = username.trim().lowercase()
        if (users.any { it.username == normalized }) {
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
        users += user
        currentUserId.value = user.id
        return AuthResult.Success(user)
    }

    override fun observeCurrentUser(): Flow<User?> =
        currentUserId.map { id -> users.firstOrNull { it.id == id } }

    override suspend fun isUsernameTaken(username: String): Boolean =
        users.any { it.username == username.trim().lowercase() }

    override suspend fun userCount(): Int = users.size

    override suspend fun establishPrimarySession(): AuthResult {
        val primary = users.maxByOrNull { it.createdAt }
            ?: return AuthResult.Failure(AuthError.Unknown)
        currentUserId.value = primary.id
        return AuthResult.Success(primary)
    }

    /** Test-only helper — reads the fake's session state without going through a use case. */
    fun currentSessionUserId(): String? = currentUserId.value

    /**
     * Test-only helper — drops the session the way the removed sign-out used to, to model a
     * device left signed out by the old password flow.
     */
    fun clearSession() {
        currentUserId.value = null
    }
}
