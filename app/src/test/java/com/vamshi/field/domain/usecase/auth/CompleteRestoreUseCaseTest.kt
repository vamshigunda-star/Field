package com.vamshi.field.domain.usecase.auth

import com.vamshi.field.domain.model.auth.AuthError
import com.vamshi.field.domain.model.auth.AuthResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompleteRestoreUseCaseTest {

    private val repository = FakeAuthRepository()
    private val completeRestore = CompleteRestoreUseCase(repository)

    @Test
    fun `restore that produced zero accounts fails with Unknown`() = runTest {
        val result = completeRestore()

        assertEquals(AuthResult.Failure(AuthError.Unknown), result)
    }

    @Test
    fun `restore that produced one account establishes a session`() = runTest {
        val user = (repository.signUp(
            firstName = "Jordan Reyes", lastName = "", username = "jordan.reyes", email = null
        ) as AuthResult.Success).user
        repository.clearSession()

        val result = completeRestore()

        assertTrue(result is AuthResult.Success)
        assertEquals(user.id, repository.currentSessionUserId())
    }

    @Test
    fun `restore that produced multiple accounts picks the most recently created`() = runTest {
        repository.signUp(
            firstName = "Jordan Reyes", lastName = "", username = "jordan.reyes", email = null
        )
        val second = (repository.signUp(
            firstName = "Alex Kim", lastName = "", username = "alex.kim", email = null
        ) as AuthResult.Success).user
        repository.clearSession()

        val result = completeRestore()

        assertEquals(second.id, (result as AuthResult.Success).user.id)
        assertEquals(second.id, repository.currentSessionUserId())
    }
}
