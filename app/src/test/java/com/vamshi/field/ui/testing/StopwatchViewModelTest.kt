package com.vamshi.field.ui.testing

import androidx.lifecycle.SavedStateHandle
import com.vamshi.field.domain.model.people.BiologicalSex
import com.vamshi.field.domain.model.people.Group
import com.vamshi.field.domain.model.people.GroupCategory
import com.vamshi.field.domain.model.people.Individual
import com.vamshi.field.domain.model.standards.FitnessTest
import com.vamshi.field.domain.model.standards.InputParadigm
import com.vamshi.field.domain.model.standards.InterpretationStrategy
import com.vamshi.field.domain.model.standards.TimingMode
import com.vamshi.field.domain.model.testing.TestingEvent
import com.vamshi.field.domain.usecase.standards.CalculatePercentileUseCase
import com.vamshi.field.domain.usecase.standards.FakeStandardsRepository
import com.vamshi.field.domain.usecase.testing.FakePeopleRepository
import com.vamshi.field.domain.usecase.testing.FakeTestingRepository
import com.vamshi.field.domain.usecase.testing.RecordTestResultUseCase
import com.vamshi.field.domain.usecase.testing.StopwatchSessionUseCase
import com.vamshi.field.ui.testing.stopwatch.StopwatchAction
import com.vamshi.field.ui.testing.stopwatch.StopwatchPhase
import com.vamshi.field.ui.testing.stopwatch.StopwatchViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Individual-mode stopwatch, as used by the 5-10-5 and every other one-at-a-time timed test.
 *
 * Each test pins a defect from specs/2026-11-26-stopwatch-time-disappears-after-stop.md. The
 * ticker loops forever while running, so these drive the dispatcher with runCurrent(), never
 * advanceUntilIdle(), and time comes from a fake clock so every elapsed value is exact.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StopwatchViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var testingRepository: FakeTestingRepository
    private lateinit var peopleRepository: FakePeopleRepository
    private lateinit var standardsRepository: FakeStandardsRepository
    private var nowNanos = 0L

    private val shuttle = FitnessTest(
        id = TEST_ID,
        categoryId = "cat_agility",
        name = "5-10-5 Shuttle Run",
        unit = "seconds",
        isHigherBetter = false,
        timingMode = TimingMode.INDIVIDUAL,
        inputParadigm = InputParadigm.CHRONO,
        athletesPerHeat = 1,
        trialsPerAthlete = 3,
        validMin = 3.7,
        validMax = 9.0,
        interpretationStrategy = InterpretationStrategy.NONE
    )

    private val asha = athlete("ath_a", "Asha")
    private val bilal = athlete("ath_b", "Bilal")

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        testingRepository = FakeTestingRepository()
        peopleRepository = FakePeopleRepository()
        standardsRepository = FakeStandardsRepository()
        standardsRepository.givenTests(shuttle)
        testingRepository.givenEvent(
            TestingEvent(id = EVENT_ID, groupId = GROUP_ID, name = "Baseline", date = 1_700_000_000_000L),
            listOf(shuttle)
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `stop keeps the time and the athlete it belongs to on screen`() = runTest(dispatcher) {
        val vm = viewModel(asha, bilal)

        run(vm, 4870)

        val state = vm.uiState.value
        assertEquals("clock was reset after STOP", 4870L, state.elapsedMs)
        val last = state.lastTrial
        assertNotNull("no result card after STOP", last)
        last!!
        assertEquals(asha.id, last.athleteId)
        assertEquals(1, last.trialNumber)
        assertEquals(4870L, last.timeMs)
        assertNotNull("trial was not saved", last.resultId)
        assertEquals("selection should rotate to the next athlete", bilal.id, state.selectedAthleteId)
        assertEquals(
            "a partial athlete's time must be on their row",
            listOf(4870L),
            state.allAthletes.first { it.athleteId == asha.id }.displayTrials.map { it.timeMs }
        )
    }

    @Test
    fun `clearing a just-saved trial removes it from the row and the count`() = runTest(dispatcher) {
        val vm = viewModel(asha, bilal)
        run(vm, 4500)
        val id = vm.uiState.value.lastTrial!!.resultId!!

        vm.onAction(StopwatchAction.OnClearEntry(asha.id, id))
        runCurrent()

        val state = vm.uiState.value
        assertTrue(state.allAthletes.first { it.athleteId == asha.id }.displayTrials.isEmpty())
        assertEquals(0, state.completedCount)
        assertTrue(testingRepository.storedResults().isEmpty())
        assertNull("the result card should go with the cleared trial", state.lastTrial)
    }

    @Test
    fun `editing to an out-of-range time keeps the original trial`() = runTest(dispatcher) {
        val vm = viewModel(asha, bilal)
        run(vm, 4500)
        val id = vm.uiState.value.lastTrial!!.resultId!!

        vm.onAction(StopwatchAction.OnSaveEditedTime(asha.id, newTimeMs = 2000, resultId = id))
        runCurrent()

        assertTrue("nothing may be deleted before validation", testingRepository.deletedIds.isEmpty())
        assertEquals(listOf(4.5), testingRepository.storedResults().map { it.rawScore })
    }

    @Test
    fun `editing a trial replaces it in place and keeps its position`() = runTest(dispatcher) {
        val vm = viewModel(asha)
        run(vm, 4100)
        val firstId = vm.uiState.value.lastTrial!!.resultId!!
        run(vm, 4200)
        run(vm, 4300)

        vm.onAction(StopwatchAction.OnSaveEditedTime(asha.id, newTimeMs = 5000, resultId = firstId))
        runCurrent()

        val trials = vm.uiState.value.allAthletes.single().displayTrials
        assertEquals(3, trials.size)
        assertEquals(firstId, trials.first().resultId)
        assertEquals(5000L, trials.first().timeMs)
        assertEquals(3, testingRepository.storedResults().size)
        assertTrue(testingRepository.deletedIds.isEmpty())
    }

    @Test
    fun `the last athlete with trials left stays selected`() = runTest(dispatcher) {
        val vm = viewModel(asha)

        run(vm, 4500)

        assertEquals(asha.id, vm.uiState.value.selectedAthleteId)
    }

    @Test
    fun `a finished athlete cannot be timed a fourth time`() = runTest(dispatcher) {
        val vm = viewModel(asha)
        repeat(3) { run(vm, 4500) }

        vm.onAction(StopwatchAction.OnSelectAthlete(asha.id))
        vm.onAction(StopwatchAction.OnStartStop)
        runCurrent()

        assertEquals(StopwatchPhase.READY, vm.uiState.value.stopwatchPhase)
        assertEquals(3, testingRepository.storedResults().size)
    }

    @Test
    fun `final trial completes the session without hiding its time`() = runTest(dispatcher) {
        val vm = viewModel(asha)
        repeat(2) { run(vm, 4500) }

        run(vm, 4650)

        val state = vm.uiState.value
        assertTrue(state.isSessionComplete)
        assertEquals(4650L, state.elapsedMs)
        assertEquals(3, state.lastTrial!!.trialNumber)
    }

    @Test
    fun `cancel run saves nothing and keeps the athlete`() = runTest(dispatcher) {
        val vm = viewModel(asha, bilal)
        vm.onAction(StopwatchAction.OnStartStop)
        runCurrent()
        nowNanos += 1_200L * 1_000_000

        vm.onAction(StopwatchAction.OnCancelRun)
        runCurrent()

        val state = vm.uiState.value
        assertEquals(StopwatchPhase.READY, state.stopwatchPhase)
        assertEquals(asha.id, state.selectedAthleteId)
        assertTrue(testingRepository.storedResults().isEmpty())
    }

    @Test
    fun `an out-of-range run is shown, not saved, and the athlete stays up`() = runTest(dispatcher) {
        val vm = viewModel(asha, bilal)

        run(vm, 2000)

        val state = vm.uiState.value
        val last = state.lastTrial!!
        assertNull(last.resultId)
        assertNotNull(last.outOfRangeMessage)
        assertEquals(2000L, state.elapsedMs)
        assertEquals(asha.id, state.selectedAthleteId)
        assertTrue(testingRepository.storedResults().isEmpty())
    }

    @Test
    fun `4_02 seconds displays as 4020 ms, not 4019`() = runTest(dispatcher) {
        val vm = viewModel(asha)

        run(vm, 4020)

        assertEquals(4020L, vm.uiState.value.allAthletes.single().displayTrials.single().timeMs)
    }

    // --- Helpers ---

    private fun TestScope.viewModel(vararg roster: Individual): StopwatchViewModel {
        val athletes = roster.toList()
        peopleRepository.givenGroupWithAthletes(
            Group(id = GROUP_ID, name = "Squad", location = "Field", cycle = "2026", category = GroupCategory.TEAM),
            athletes
        )
        testingRepository.givenRoster(GROUP_ID, athletes)
        val vm = StopwatchViewModel(
            savedStateHandle = SavedStateHandle(
                mapOf(
                    "eventId" to EVENT_ID,
                    "fitnessTestId" to TEST_ID,
                    "groupId" to GROUP_ID,
                    "athleteId" to athletes.first().id
                )
            ),
            stopwatchSessionUseCase = StopwatchSessionUseCase(testingRepository, standardsRepository),
            recordResult = RecordTestResultUseCase(
                repository = testingRepository,
                calculatePercentile = CalculatePercentileUseCase(standardsRepository),
                standardsRepository = standardsRepository
            ),
            testingRepository = testingRepository,
            peopleRepository = peopleRepository
        )
        vm.nanoClock = { nowNanos }
        runCurrent()
        return vm
    }

    /** START, let [ms] pass on the fake clock, STOP. */
    private fun TestScope.run(vm: StopwatchViewModel, ms: Long) {
        vm.onAction(StopwatchAction.OnStartStop)
        runCurrent()
        nowNanos += ms * 1_000_000
        vm.onAction(StopwatchAction.OnStartStop)
        runCurrent()
    }

    private fun athlete(id: String, firstName: String) = Individual(
        id = id,
        firstName = firstName,
        lastName = "Test",
        dateOfBirth = System.currentTimeMillis() - 15L * 31_557_600_000L,
        sex = BiologicalSex.FEMALE
    )

    private companion object {
        const val EVENT_ID = "evt_shuttle"
        const val GROUP_ID = "grp_squad"
        const val TEST_ID = "test_5_10_5_shuttle_run"
    }
}
