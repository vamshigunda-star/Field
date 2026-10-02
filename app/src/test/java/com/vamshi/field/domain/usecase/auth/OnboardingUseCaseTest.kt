package com.vamshi.field.domain.usecase.auth

import com.vamshi.field.domain.model.auth.AuthError
import com.vamshi.field.domain.model.auth.AuthResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingUseCaseTest {

    private val repository = FakeAuthRepository()
    private val onboarding = OnboardingUseCase(
        repository = repository,
        validateName = ValidateNameUseCase(),
        generateUsername = GenerateUsernameUseCase(repository)
    )

    @Test
    fun `blank coach name is rejected before touching the repository`() = runTest {
        val result = onboarding(coachName = "   ", email = null)

        assertEquals(AuthResult.Failure(AuthError.InvalidName), result)
        assertEquals(0, repository.userCount())
    }

    @Test
    fun `a name alone is enough to create an account`() = runTest {
        val result = onboarding(coachName = "Jordan Reyes", email = null)

        assertTrue(result is AuthResult.Success)
        val user = (result as AuthResult.Success).user
        assertEquals("Jordan Reyes", user.firstName)
        assertEquals("", user.lastName)
        assertEquals("jordan.reyes", user.username)
        assertNull(user.email)
    }

    @Test
    fun `blank email is normalized to null`() = runTest {
        val result = onboarding(coachName = "Jordan Reyes", email = "   ")

        assertNull((result as AuthResult.Success).user.email)
    }

    @Test
    fun `non-blank email is trimmed and kept`() = runTest {
        val result = onboarding(coachName = "Alex Kim", email = "  alex@school.edu  ")

        assertEquals("alex@school.edu", (result as AuthResult.Success).user.email)
    }

    @Test
    fun `onboarding auto-signs-in the new account`() = runTest {
        val result = onboarding(coachName = "Jordan Reyes", email = null)

        val user = (result as AuthResult.Success).user
        assertEquals(user.id, repository.currentSessionUserId())
    }

    @Test
    fun `two coaches with the same name on one device get distinct usernames`() = runTest {
        val first = onboarding(coachName = "Alex Kim", email = null)
        val second = onboarding(coachName = "Alex Kim", email = null)

        val firstUsername = (first as AuthResult.Success).user.username
        val secondUsername = (second as AuthResult.Success).user.username
        assertTrue(firstUsername != secondUsername)
    }
}
