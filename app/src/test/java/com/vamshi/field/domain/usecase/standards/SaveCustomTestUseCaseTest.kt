package com.vamshi.field.domain.usecase.standards

import com.vamshi.field.domain.model.standards.CustomTestDraft
import com.vamshi.field.domain.model.standards.CustomTestField
import com.vamshi.field.domain.model.standards.FitnessTest
import com.vamshi.field.domain.model.standards.InputParadigm
import com.vamshi.field.domain.model.standards.InterpretationStrategy
import com.vamshi.field.domain.model.standards.MeasurementMethod
import com.vamshi.field.domain.model.standards.ScoringBands
import com.vamshi.field.domain.model.standards.TestSource
import com.vamshi.field.domain.model.standards.TimingMode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SaveCustomTestUseCaseTest {

    private lateinit var repository: FakeStandardsRepository
    private lateinit var save: SaveCustomTestUseCase

    @Before
    fun setUp() {
        repository = FakeStandardsRepository()
        save = SaveCustomTestUseCase(repository, ValidateCustomTestUseCase(), GenerateNormsUseCase())
    }

    private fun draft() = CustomTestDraft(
        name = "Sled Push 20m",
        categoryId = "cat_agility",
        unit = "sec",
        isHigherBetter = false
    )

    private fun seededTest(id: String, name: String) = FitnessTest(
        id = id,
        categoryId = "cat_cardio",
        name = name,
        unit = "laps",
        isHigherBetter = true,
        source = TestSource.SEED
    )

    @Test
    fun `saving a valid draft persists it as a USER test and returns the new id`() = runTest {
        val result = save(draft())

        val id = (result as SaveCustomTestResult.Success).testId
        assertTrue("custom ids are prefixed to stay distinct from seeded test_* ids", id.startsWith("custom_"))

        val stored = repository.storedTest(id)!!
        assertEquals(TestSource.USER, stored.source)
        assertEquals("Sled Push 20m", stored.name)
        assertEquals("sec", stored.unit)
        assertEquals(false, stored.isHigherBetter)
    }

    @Test
    fun `a test with no bands is stored as NONE so no misleading zone is shown`() = runTest {
        val id = (save(draft()) as SaveCustomTestResult.Success).testId
        assertEquals(InterpretationStrategy.NONE, repository.storedTest(id)!!.interpretationStrategy)
    }

    @Test
    fun `measurement method expands into the paradigm and timing mode the app renders`() = runTest {
        val id = (save(draft().copy(measurementMethod = MeasurementMethod.STOPWATCH))
            as SaveCustomTestResult.Success).testId

        val stored = repository.storedTest(id)!!
        assertEquals(InputParadigm.CHRONO, stored.inputParadigm)
        assertEquals(
            "a non-MANUAL_ENTRY timing mode is what routes the grid to the stopwatch",
            TimingMode.INDIVIDUAL,
            stored.timingMode
        )
    }

    @Test
    fun `name and unit are trimmed and a blank description becomes null`() = runTest {
        val id = (save(draft().copy(name = "  Sled Push  ", unit = " sec ", description = "   "))
            as SaveCustomTestResult.Success).testId

        val stored = repository.storedTest(id)!!
        assertEquals("Sled Push", stored.name)
        assertEquals("sec", stored.unit)
        assertEquals(null, stored.description)
    }

    @Test
    fun `a test with bands is stored as NORM_LOOKUP with its norms`() = runTest {
        val result = save(
            draft().copy(scoring = ScoringBands(sameForAllSexes = true, shared = listOf(8.0, 6.5, 5.5)))
        )

        val id = (result as SaveCustomTestResult.Success).testId
        assertEquals(
            InterpretationStrategy.NORM_LOOKUP,
            repository.storedTest(id)!!.interpretationStrategy
        )
        assertEquals("4 bands x 3 sexes", 12, repository.normsByTest[id]!!.size)
    }

    @Test
    fun `switching an existing test back to raw scores clears its norms`() = runTest {
        val id = (save(draft().copy(scoring = ScoringBands(shared = listOf(8.0, 6.5, 5.5))))
            as SaveCustomTestResult.Success).testId
        assertEquals(12, repository.normsByTest[id]!!.size)

        save(draft().copy(id = id, scoring = null))

        assertEquals(emptyList<Any>(), repository.normsByTest[id])
        assertEquals(
            InterpretationStrategy.NONE,
            repository.storedTest(id)!!.interpretationStrategy
        )
    }

    @Test
    fun `an invalid band ladder blocks the save entirely`() = runTest {
        // Lower-is-better with ascending cut points.
        val result = save(
            draft().copy(isHigherBetter = false, scoring = ScoringBands(shared = listOf(5.5, 6.5, 8.0)))
        )

        assertTrue(result is SaveCustomTestResult.Invalid)
        assertEquals(emptyMap<String, Any>(), repository.normsByTest)
    }

    @Test
    fun `a saved band ladder round-trips back into the edit form`() = runTest {
        val hydrate = GetCustomTestDraftUseCase(repository)
        val cuts = listOf(8.0, 6.5, 5.5)
        val id = (save(draft().copy(isHigherBetter = false, scoring = ScoringBands(shared = cuts)))
            as SaveCustomTestResult.Success).testId

        val reloaded = hydrate(id)!!

        // Cut points are read back off the stored band edges, not from a second copy that
        // could drift from the rows actually used for lookups.
        assertEquals(true, reloaded.scoring!!.sameForAllSexes)
        assertEquals(cuts, reloaded.scoring!!.shared)
    }

    @Test
    fun `per-sex ladders round-trip without collapsing to shared`() = runTest {
        val hydrate = GetCustomTestDraftUseCase(repository)
        val id = (save(
            draft().copy(
                isHigherBetter = true,
                scoring = ScoringBands(
                    sameForAllSexes = false,
                    male = listOf(25.0, 35.0, 45.0),
                    female = listOf(20.0, 30.0, 40.0)
                )
            )
        ) as SaveCustomTestResult.Success).testId

        val reloaded = hydrate(id)!!.scoring!!

        assertEquals(false, reloaded.sameForAllSexes)
        assertEquals(listOf(25.0, 35.0, 45.0), reloaded.male)
        assertEquals(listOf(20.0, 30.0, 40.0), reloaded.female)
    }

    @Test
    fun `a duplicate name is rejected regardless of case or surrounding whitespace`() = runTest {
        repository.givenTests(seededTest("test_pacer", "PACER (20m Shuttle Run)"))

        val result = save(draft().copy(name = "  pacer (20m shuttle run) "))

        val errors = (result as SaveCustomTestResult.Invalid).errors
        assertEquals(listOf(CustomTestField.NAME), errors.map { it.field })
    }

    @Test
    fun `editing a test does not collide with its own name`() = runTest {
        val existing = FitnessTest(
            id = "custom_1",
            categoryId = "cat_agility",
            name = "Sled Push 20m",
            unit = "sec",
            isHigherBetter = false,
            source = TestSource.USER
        )
        repository.givenTests(existing)

        val result = save(draft().copy(id = "custom_1", description = "Updated instructions"))

        assertTrue(result is SaveCustomTestResult.Success)
        assertEquals("custom_1", (result as SaveCustomTestResult.Success).testId)
        assertEquals("Updated instructions", repository.storedTest("custom_1")!!.description)
    }

    @Test
    fun `an invalid draft is not persisted`() = runTest {
        val result = save(draft().copy(name = ""))

        assertTrue(result is SaveCustomTestResult.Invalid)
        assertEquals(emptyList<Any>(), repository.storedTest("custom_1")?.let { listOf(it) } ?: emptyList<Any>())
    }

    @Test
    fun `a blank name reports only the blank-name error, not a duplicate as well`() = runTest {
        // Two tests already have blank-ish names; the duplicate check must not pile on.
        val result = save(draft().copy(name = "   "))

        val errors = (result as SaveCustomTestResult.Invalid).errors
        assertEquals(1, errors.size)
        assertEquals(CustomTestField.NAME, errors.single().field)
    }
}
