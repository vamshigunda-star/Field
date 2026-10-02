package com.vamshi.field.domain.usecase.auth

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerateUsernameUseCaseTest {

    private val repository = FakeAuthRepository()
    private val generateUsername = GenerateUsernameUseCase(repository)

    @Test
    fun `slugifies a normal two-word name`() = runTest {
        val username = generateUsername("Jordan Reyes")

        assertEquals("jordan.reyes", username)
    }

    @Test
    fun `short names are padded to satisfy the 3-character minimum`() = runTest {
        val username = generateUsername("Jo")

        assertTrue("expected length >= 3, was '$username'", username.length >= 3)
        assertTrue(username.matches(ValidateUsernameUseCase.USERNAME_REGEX))
    }

    @Test
    fun `blank name falls back to coach`() = runTest {
        val username = generateUsername("   ")

        assertEquals("coach", username)
    }

    @Test
    fun `non-alphanumeric characters collapse to single dots`() = runTest {
        val username = generateUsername("O'Brien-Smith!!")

        assertEquals("o.brien.smith", username)
    }

    @Test
    fun `collision appends an incrementing numeric suffix`() = runTest {
        repository.signUp(
            firstName = "Jordan",
            lastName = "Reyes",
            username = "jordan.reyes",
            email = null
        )

        val username = generateUsername("Jordan Reyes")

        assertEquals("jordan.reyes2", username)
    }

    @Test
    fun `a second collision keeps incrementing past the first taken suffix`() = runTest {
        repository.signUp(
            firstName = "Jordan", lastName = "", username = "jordan.reyes", email = null
        )
        repository.signUp(
            firstName = "Jordan", lastName = "", username = "jordan.reyes2", email = null
        )

        val username = generateUsername("Jordan Reyes")

        assertEquals("jordan.reyes3", username)
    }

    @Test
    fun `every generated username satisfies the shared username regex`() = runTest {
        val names = listOf("Jo", "A", "  ", "Jordan Reyes", "O'Brien-Smith", "X".repeat(60), "123 456")

        names.forEach { name ->
            val username = generateUsername(name)
            assertTrue(
                "'$username' (generated from '$name') violates USERNAME_REGEX",
                username.matches(ValidateUsernameUseCase.USERNAME_REGEX)
            )
        }
    }
}
