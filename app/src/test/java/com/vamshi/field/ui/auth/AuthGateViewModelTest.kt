package com.vamshi.field.ui.auth

import com.vamshi.field.domain.repository.AuthRepository
import com.vamshi.field.domain.repository.SessionManager
import com.vamshi.field.domain.usecase.auth.FakeAuthRepository
import com.vamshi.field.domain.usecase.auth.ResumeSessionUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The auth gate decides the app's start destination, and while it is undecided the nav graph
 * shows a full-screen spinner. That makes termination a user-visible property: any path that
 * leaves the gate on [AuthGateState.Loading] presents to a coach as a permanently frozen app.
 *
 * These tests pin all three exits — resolved, timed out, and thrown. Runs under Robolectric
 * because the fallback paths log through `android.util.Log`, which is not mocked on a plain
 * JVM test.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AuthGateViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        // viewModelScope dispatches on Main; route it to the test scheduler so the gate's
        // 5s timeout elapses in virtual time rather than wall clock.
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeSessionManager(private var userId: String? = null) : SessionManager {
        private val flow = MutableStateFlow(userId)
        override fun observeCurrentUserId(): Flow<String?> = flow.asStateFlow()
        override suspend fun setCurrentUserId(id: String?) {
            userId = id
            flow.value = id
        }
        override suspend fun currentUserIdOnce(): String? = userId
    }

    /** Stands in for a database read that has stalled and will never return. */
    private class HangingAuthRepository(
        delegate: AuthRepository = FakeAuthRepository()
    ) : AuthRepository by delegate {
        override suspend fun userCount(): Int = awaitCancellation()
    }

    private class ThrowingAuthRepository(
        delegate: AuthRepository = FakeAuthRepository()
    ) : AuthRepository by delegate {
        override suspend fun userCount(): Int = throw IllegalStateException("database is corrupt")
    }

    private fun gate(sessionManager: SessionManager, repository: AuthRepository) =
        AuthGateViewModel(ResumeSessionUseCase(sessionManager, repository))

    @Test
    fun authGate_whenRepositoryHangs_fallsBackToOnboardingWithinTimeout() = runTest(dispatcher) {
        val viewModel = gate(FakeSessionManager(null), HangingAuthRepository())

        assertEquals(AuthGateState.Loading, viewModel.state.value)

        // Just shy of the 5s budget the gate is still — correctly — waiting.
        advanceTimeBy(4_999)
        runCurrent()
        assertEquals(AuthGateState.Loading, viewModel.state.value)

        // Once the budget elapses it must abandon the read rather than wait forever.
        advanceTimeBy(2)
        runCurrent()
        assertEquals(AuthGateState.NoAccount, viewModel.state.value)
    }

    @Test
    fun authGate_whenRepositoryThrows_fallsBackToOnboarding() = runTest(dispatcher) {
        val viewModel = gate(FakeSessionManager(null), ThrowingAuthRepository())

        advanceUntilIdle()

        assertEquals(AuthGateState.NoAccount, viewModel.state.value)
    }

    @Test
    fun authGate_whenSessionExists_resolvesToAuthenticated() = runTest(dispatcher) {
        val viewModel = gate(FakeSessionManager("user-0"), FakeAuthRepository())

        advanceUntilIdle()

        assertEquals(AuthGateState.Authenticated, viewModel.state.value)
    }

    /** A device signed out under the old password flow must open, not strand the coach. */
    @Test
    fun authGate_whenNoSessionButAccountsExist_resolvesToAuthenticated() = runTest(dispatcher) {
        val repository = FakeAuthRepository()
        repository.signUp("Ada", "Lovelace", "ada", null)

        val viewModel = gate(FakeSessionManager(null), repository)

        advanceUntilIdle()

        assertEquals(AuthGateState.Authenticated, viewModel.state.value)
    }

    @Test
    fun authGate_whenNoSessionAndNoAccounts_resolvesToOnboarding() = runTest(dispatcher) {
        val viewModel = gate(FakeSessionManager(null), FakeAuthRepository())

        advanceUntilIdle()

        assertEquals(AuthGateState.NoAccount, viewModel.state.value)
    }
}
