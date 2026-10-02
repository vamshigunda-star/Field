package com.vamshi.field.ui.testing

import androidx.lifecycle.SavedStateHandle
import com.vamshi.field.domain.model.people.BiologicalSex
import com.vamshi.field.domain.model.people.Group
import com.vamshi.field.domain.model.people.GroupCategory
import com.vamshi.field.domain.model.people.Individual
import com.vamshi.field.domain.model.standards.FitnessTest
import com.vamshi.field.domain.model.standards.TestCatalogCsv
import com.vamshi.field.domain.model.standards.TimingMode
import com.vamshi.field.domain.model.testing.TestingEvent
import com.vamshi.field.domain.usecase.standards.CalculatePercentileUseCase
import com.vamshi.field.domain.usecase.standards.FakeStandardsRepository
import com.vamshi.field.domain.usecase.testing.FakePeopleRepository
import com.vamshi.field.domain.usecase.testing.FakeTestingRepository
import com.vamshi.field.domain.usecase.testing.GetTestingGridDataUseCase
import com.vamshi.field.domain.usecase.testing.RecordTestResultUseCase
import com.vamshi.field.domain.usecase.testing.StopwatchSessionUseCase
import com.vamshi.field.ui.testing.stopwatch.StopwatchViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Drives the whole shipped catalog through the group testing screen.
 *
 * This is the test whose absence made "sometimes when I select a specific test the app switches
 * off" impossible to chase: the code path is identical for all 83 tests and only the catalog row
 * differs, so the defect is data-driven and a human tapping through cannot cover it. Here the
 * loop does it.
 *
 * Every check accumulates failures and reports them together, because the useful output is
 * *which* tests are broken, not the first one that is.
 *
 * Runs under Robolectric because [TestingGridViewModel]'s failure paths log through
 * `android.util.Log`, which is not mocked on a plain JVM test.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class GroupTestingSelectAllTestsTest {

    private val dispatcher = StandardTestDispatcher()

    private lateinit var testingRepository: FakeTestingRepository
    private lateinit var peopleRepository: FakePeopleRepository
    private lateinit var standardsRepository: FakeStandardsRepository
    private lateinit var catalog: List<FitnessTest>

    private val group = Group(
        id = GROUP_ID,
        name = "Morning Squad",
        location = "Main Field",
        cycle = "2026",
        category = GroupCategory.TEAM
    )

    /**
     * One plain athlete, one carrying a medical alert and one restricted. A harness built only
     * from blank athletes cannot notice a path that drops the safety fields.
     */
    private val athletes = listOf(
        athlete("ath_plain", "Asha", "Rao", BiologicalSex.FEMALE),
        athlete("ath_alert", "Bilal", "Khan", BiologicalSex.MALE, medicalAlert = "Asthma — inhaler in bag"),
        athlete("ath_restricted", "Chen", "Wei", BiologicalSex.UNSPECIFIED, isRestricted = true)
    )

    private val event = TestingEvent(
        id = EVENT_ID,
        groupId = GROUP_ID,
        name = "Term 1 Baseline",
        date = 1_700_000_000_000L
    )

    @Before
    fun setUp() {
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
        catalog = TestCatalogCsv.fitnessTests()

        testingRepository = FakeTestingRepository()
        peopleRepository = FakePeopleRepository()
        standardsRepository = FakeStandardsRepository()

        peopleRepository.givenGroupWithAthletes(group, athletes)
        testingRepository.givenEvent(event, catalog)
        testingRepository.givenRoster(GROUP_ID, athletes)
        standardsRepository.givenTests(*catalog.toTypedArray())
    }

    @After
    fun tearDown() {
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    // --- The grid ---

    @Test
    fun `grid loads the whole catalog and every athlete`() = runTest(dispatcher) {
        val viewModel = gridViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("grid reported an error", null, state.errorMessage)
        assertTrue("grid never finished loading", !state.isLoading)

        val gridData = state.gridData
        assertNotNull("grid data never arrived", gridData)
        assertEquals("not every catalog test reached the grid", catalog.size, gridData!!.tests.size)
        assertEquals("not every athlete reached the grid", athletes.size, gridData.students.size)
    }

    /**
     * The select path itself: tab through every test and open the editor on each. A test whose
     * row data breaks this never gets as far as a score.
     */
    @Test
    fun `every test in the catalog can be selected and opened for editing`() = runTest(dispatcher) {
        val viewModel = gridViewModel()
        advanceUntilIdle()
        val athlete = athletes.first()

        val failures = mutableListOf<String>()
        catalog.forEachIndexed { index, test ->
            try {
                viewModel.onAction(TestingGridAction.OnSelectTestTab(index))
                viewModel.onAction(TestingGridAction.OnStartEditing(athlete, test))
                advanceUntilIdle()

                val state = viewModel.uiState.value
                when {
                    state.selectedTestIndex != index ->
                        failures += "${test.id}: tab did not select (got ${state.selectedTestIndex})"
                    state.editingCell == null ->
                        failures += "${test.id}: selecting the cell did not open an editor"
                    state.editingCell?.test?.id != test.id ->
                        failures += "${test.id}: editor opened on ${state.editingCell?.test?.id}"
                }
                viewModel.onAction(TestingGridAction.OnDismissEditing)
            } catch (e: Exception) {
                failures += "${test.id}: threw ${e::class.simpleName}: ${e.message}"
            }
        }

        assertTrue(report("tests that could not be selected", failures), failures.isEmpty())
    }

    /**
     * A score at the midpoint of the declared valid range must reach storage. Asserting on the
     * stored row rather than on the absence of an exception is the point: `saveScore` catches
     * everything, so a broken save is silent.
     */
    @Test
    fun `every test in the catalog accepts and persists a midpoint score`() = runTest(dispatcher) {
        val viewModel = gridViewModel()
        advanceUntilIdle()
        val athlete = athletes.first()

        val failures = mutableListOf<String>()
        catalog.forEachIndexed { index, test ->
            val score = midpointOf(test)
            if (score == null) {
                failures += "${test.id}: no valid range to pick a score from"
                return@forEachIndexed
            }

            viewModel.onAction(TestingGridAction.OnSelectTestTab(index))
            viewModel.onAction(TestingGridAction.OnStartEditing(athlete, test))
            viewModel.onAction(TestingGridAction.OnSaveScore(score))
            advanceUntilIdle()

            val state = viewModel.uiState.value
            val stored = testingRepository.resultFor(athlete.id, test.id)
            when {
                state.errorMessage != null -> failures += "${test.id}: ${state.errorMessage}"
                stored == null -> failures += "${test.id}: save reported success but stored nothing"
                stored.rawScore != score -> failures += "${test.id}: stored ${stored.rawScore}, sent $score"
            }
            viewModel.onAction(TestingGridAction.OnDismissError)
        }

        assertTrue(report("tests that could not record a score", failures), failures.isEmpty())

        // Guards against the loop passing vacuously: one stored row per catalog test.
        assertEquals(
            "expected one stored result per catalog test",
            catalog.size,
            testingRepository.storedResults().size
        )
    }

    /**
     * `RecordTestResultUseCase` compares with `<` and `>`, so both bounds are legal scores. A
     * coach who records exactly the minimum must not be told their score is invalid.
     */
    @Test
    fun `every test accepts scores exactly on its valid bounds`() = runTest(dispatcher) {
        val viewModel = gridViewModel()
        advanceUntilIdle()

        val failures = mutableListOf<String>()
        catalog.forEachIndexed { index, test ->
            val min = test.validMin ?: return@forEachIndexed
            val max = test.validMax ?: return@forEachIndexed

            listOf("min" to min, "max" to max).forEachIndexed { boundIndex, (label, bound) ->
                // A different athlete per bound so the second save is not an edit of the first.
                val athlete = athletes[boundIndex % athletes.size]
                viewModel.onAction(TestingGridAction.OnSelectTestTab(index))
                viewModel.onAction(TestingGridAction.OnStartEditing(athlete, test))
                viewModel.onAction(TestingGridAction.OnSaveScore(bound))
                advanceUntilIdle()

                val state = viewModel.uiState.value
                if (state.errorMessage != null) {
                    failures += "${test.id} at $label bound $bound: ${state.errorMessage}"
                } else if (testingRepository.resultFor(athlete.id, test.id) == null) {
                    failures += "${test.id} at $label bound $bound: stored nothing"
                }
                viewModel.onAction(TestingGridAction.OnDismissError)
            }
        }

        assertTrue(report("tests rejecting a score on their own bound", failures), failures.isEmpty())
    }

    /**
     * The other half of the contract: an out-of-range score is rejected *as a message*, never as
     * a crash, and leaves a retryable [FailedGridAction] behind.
     */
    @Test
    fun `an out-of-range score surfaces an error instead of throwing`() = runTest(dispatcher) {
        val viewModel = gridViewModel()
        advanceUntilIdle()
        val athlete = athletes.first()

        val failures = mutableListOf<String>()
        catalog.forEachIndexed { index, test ->
            val min = test.validMin ?: return@forEachIndexed
            val belowMin = min - 1.0

            viewModel.onAction(TestingGridAction.OnSelectTestTab(index))
            viewModel.onAction(TestingGridAction.OnStartEditing(athlete, test))
            viewModel.onAction(TestingGridAction.OnSaveScore(belowMin))
            advanceUntilIdle()

            val state = viewModel.uiState.value
            when {
                state.errorMessage == null ->
                    failures += "${test.id}: accepted $belowMin, below its minimum of $min"
                state.failedAction !is FailedGridAction.Save ->
                    failures += "${test.id}: rejected the score but left nothing to retry"
            }
            viewModel.onAction(TestingGridAction.OnDismissError)
        }

        assertTrue(report("tests mishandling an out-of-range score", failures), failures.isEmpty())
    }

    // --- The stopwatch ---

    /**
     * Tapping a cell for a time-based test navigates to the stopwatch instead of the editor, so
     * for those tests the stopwatch reaching a loaded session *is* the select path. A test that
     * parks on `isLoading` is a screen the coach sees spin forever.
     */
    @Test
    fun `every stopwatch-routed test loads a session in both timing modes`() = runTest(dispatcher) {
        val timed = catalog.filter { it.canUseStopwatch }
        assertTrue("no stopwatch-routed tests in the catalog — check canUseStopwatch", timed.isNotEmpty())

        val failures = mutableListOf<String>()
        timed.forEach { test ->
            listOf(TimingMode.INDIVIDUAL, TimingMode.GROUP_START).forEach { mode ->
                try {
                    val viewModel = stopwatchViewModel(test.id, mode)
                    advanceUntilIdle()

                    val state = viewModel.uiState.value
                    when {
                        !state.sessionLoaded -> failures += "${test.id}/$mode: session never loaded"
                        state.isLoading -> failures += "${test.id}/$mode: still loading"
                        state.testName != test.name ->
                            failures += "${test.id}/$mode: loaded '${state.testName}'"
                    }
                } catch (e: Exception) {
                    failures += "${test.id}/$mode: threw ${e::class.simpleName}: ${e.message}"
                }
            }
        }

        assertTrue(report("stopwatch sessions that failed to load", failures), failures.isEmpty())
    }

    // --- Helpers ---

    private fun gridViewModel() = TestingGridViewModel(
        savedStateHandle = SavedStateHandle(mapOf("eventId" to EVENT_ID, "groupId" to GROUP_ID)),
        testingRepository = testingRepository,
        peopleRepository = peopleRepository,
        getGridData = GetTestingGridDataUseCase(peopleRepository, testingRepository),
        recordTestResult = recordTestResult()
    )

    private fun stopwatchViewModel(testId: String, mode: TimingMode) = StopwatchViewModel(
        savedStateHandle = SavedStateHandle(
            mapOf(
                "eventId" to EVENT_ID,
                "fitnessTestId" to testId,
                "groupId" to GROUP_ID,
                "athleteId" to athletes.first().id,
                "timingMode" to mode.name
            )
        ),
        stopwatchSessionUseCase = StopwatchSessionUseCase(testingRepository, standardsRepository),
        recordResult = recordTestResult(),
        testingRepository = testingRepository,
        peopleRepository = peopleRepository
    )

    private fun recordTestResult() = RecordTestResultUseCase(
        repository = testingRepository,
        calculatePercentile = CalculatePercentileUseCase(standardsRepository),
        standardsRepository = standardsRepository
    )

    private fun midpointOf(test: FitnessTest): Double? {
        val min = test.validMin ?: return null
        val max = test.validMax ?: return null
        return min + (max - min) / 2.0
    }

    private fun report(headline: String, failures: List<String>) =
        "${failures.size} of ${catalog.size} $headline:\n" + failures.joinToString("\n")

    private fun athlete(
        id: String,
        firstName: String,
        lastName: String,
        sex: BiologicalSex,
        medicalAlert: String? = null,
        isRestricted: Boolean = false
    ) = Individual(
        id = id,
        firstName = firstName,
        lastName = lastName,
        // Roughly 15 years old, so norm lookups land in the adolescence bracket.
        dateOfBirth = System.currentTimeMillis() - 15L * 31_557_600_000L,
        sex = sex,
        medicalAlert = medicalAlert,
        isRestricted = isRestricted
    )

    private companion object {
        const val EVENT_ID = "evt_baseline"
        const val GROUP_ID = "grp_morning"
    }
}
