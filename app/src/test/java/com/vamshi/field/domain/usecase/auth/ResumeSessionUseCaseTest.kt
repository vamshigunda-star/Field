package com.vamshi.field.domain.usecase.auth

import com.vamshi.field.domain.model.auth.AuthResult
import com.vamshi.field.domain.repository.SessionManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Field has no password or lock screen, so an account on the device must always open the app.
 * The case that matters most is an account with no session — a device signed out under the
 * old password flow, which used to land on an Unlock screen the coach could be locked out of.
 */
class ResumeSessionUseCaseTest {

    private val repository = FakeAuthRepository()

    /** Reads the fake repository's session so the use case and repository agree on one state. */
    private val sessionManager = object : SessionManager {
        override fun observeCurrentUserId(): Flow<String?> = repository.observeCurrentUser().map { it?.id }
        override suspend fun setCurrentUserId(id: String?) {
            throw UnsupportedOperationException("the use case sets sessions through the repository")
        }
        override suspend fun currentUserIdOnce(): String? = repository.currentSessionUserId()
    }

    private val resumeSession = ResumeSessionUseCase(sessionManager, repository)

    @Test
    fun `existing session is kept`() = runTest {
        val user = (repository.signUp("Jordan Reyes", "", "jordan.reyes", null) as AuthResult.Success).user

        assertTrue(resumeSession())
        assertEquals(user.id, repository.currentSessionUserId())
    }

    @Test
    fun `account without a session is opened on the most recent account`() = runTest {
        repository.signUp("Jordan Reyes", "", "jordan.reyes", null)
        val latest = (repository.signUp("Alex Kim", "", "alex.kim", null) as AuthResult.Success).user
        repository.clearSession()

        assertTrue(resumeSession())
        assertEquals(latest.id, repository.currentSessionUserId())
    }

    @Test
    fun `no account means onboarding`() = runTest {
        assertFalse(resumeSession())
    }
}
