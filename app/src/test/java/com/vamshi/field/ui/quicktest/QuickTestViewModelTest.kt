package com.vamshi.field.ui.quicktest

import androidx.lifecycle.SavedStateHandle
import com.vamshi.field.domain.model.people.BiologicalSex
import com.vamshi.field.domain.model.people.Group
import com.vamshi.field.domain.model.people.GroupCategory
import com.vamshi.field.domain.model.people.Individual
import com.vamshi.field.domain.model.standards.FitnessTest
import com.vamshi.field.domain.model.standards.InputParadigm
import com.vamshi.field.domain.model.standards.InterpretationStrategy
import com.vamshi.field.domain.model.testing.TestResult
import com.vamshi.field.domain.model.testing.TestingEvent
import com.vamshi.field.domain.usecase.standards.CalculatePercentileUseCase
import com.vamshi.field.domain.usecase.standards.FakeStandardsRepository
import com.vamshi.field.domain.usecase.standards.GetTestLibraryUseCase
import com.vamshi.field.domain.usecase.testing.CreateEventUseCase
import com.vamshi.field.domain.usecase.testing.DiscardEmptyEventUseCase
import com.vamshi.field.domain.usecase.testing.FakePeopleRepository
import com.vamshi.field.domain.usecase.testing.FakeTestingRepository
import com.vamshi.field.domain.usecase.testing.RecordTestResultUseCase
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Individual / Quick Test persistence. Specs: 2026-11-26-testing-workflow-audit-triage (D5, D5b). */
@OptIn(ExperimentalCoroutinesApi::class)
class QuickTestViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var testing: FakeTestingRepository
    private lateinit var people: FakePeopleRepository
    private lateinit var standards: FakeStandardsRepository

    private val jump = FitnessTest(
        id = "test_jump", categoryId = "cat_power", name = "Vertical Jump", unit = "cm",
        isHigherBetter = true, inputParadigm = InputParadigm.NUMERIC,
        validMin = 10.0, validMax = 125.0, interpretationStrategy = InterpretationStrategy.NONE
    )
    private val asha = Individual(
        id = "ath_a", firstName = "Asha", lastName = "Rao",
        dateOfBirth = System.currentTimeMillis() - 15L * 31_557_600_000L, sex = BiologicalSex.FEMALE
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        testing = FakeTestingRepository()
        people = FakePeopleRepository()
        standards = FakeStandardsRepository()
        standards.givenTests(jump)
        people.givenGroupWithAthletes(
            Group(id = "grp", name = "Squad", location = "Field", cycle = "2026", category = GroupCategory.TEAM),
            listOf(asha)
        )
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `re-entering a score replaces the saved row`() = runTest(dispatcher) {
        val vm = started()

        vm.onAction(QuickTestAction.OnSaveScore(jump.id, 40.0))
        runCurrent()
        vm.onAction(QuickTestAction.OnSaveScore(jump.id, 45.0))
        runCurrent()

        assertEquals(listOf(45.0), testing.storedResults().map { it.rawScore })
    }

    @Test
    fun `deleting results of a new quick test removes the empty event too`() = runTest(dispatcher) {
        val vm = started()
        vm.onAction(QuickTestAction.OnSaveScore(jump.id, 40.0))
        runCurrent()
        val eventId = testing.storedResults().single().eventId

        vm.onAction(QuickTestAction.OnConfirmDelete)
        runCurrent()

        assertTrue(testing.storedResults().isEmpty())
        assertEquals(null, testing.getEventById(eventId))
    }

    @Test
    fun `deleting on a resumed event keeps scores from earlier sessions`() = runTest(dispatcher) {
        testing.givenEvent(TestingEvent(id = "evt_old", name = "Earlier", date = 1L), listOf(jump))
        testing.saveResult(
            TestResult(id = "earlier", eventId = "evt_old", individualId = asha.id, testId = jump.id,
                rawScore = 38.0, ageAtTime = 15f, createdAt = 1L)
        )
        val vm = started(eventId = "evt_old")
        vm.onAction(QuickTestAction.OnSaveScore(jump.id, 41.0))
        runCurrent()

        vm.onAction(QuickTestAction.OnConfirmDelete)
        runCurrent()

        assertEquals(listOf("earlier"), testing.storedResults().map { it.id })
        assertTrue("a resumed event is never deleted", testing.getEventById("evt_old") != null)
    }

    private fun TestScope.started(eventId: String? = null): QuickTestViewModel {
        val args = buildMap<String, Any> {
            put("athleteId", asha.id)
            put("testIds", jump.id)
            if (eventId != null) put("eventId", eventId)
        }
        val vm = QuickTestViewModel(
            savedStateHandle = SavedStateHandle(args),
            peopleRepository = people,
            getTestLibrary = GetTestLibraryUseCase(standards),
            createEventUseCase = CreateEventUseCase(testing),
            recordResult = RecordTestResultUseCase(testing, CalculatePercentileUseCase(standards), standards),
            calculatePercentile = CalculatePercentileUseCase(standards),
            testingRepository = testing,
            discardEmptyEvent = DiscardEmptyEventUseCase(testing)
        )
        runCurrent()
        vm.onAction(QuickTestAction.OnConfirmSetup)
        runCurrent()
        return vm
    }
}
