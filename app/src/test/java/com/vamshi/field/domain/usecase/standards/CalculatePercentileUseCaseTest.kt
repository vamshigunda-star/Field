package com.vamshi.field.domain.usecase.standards

import com.vamshi.field.domain.model.people.BiologicalSex
import com.vamshi.field.domain.model.standards.FitnessTest
import com.vamshi.field.domain.model.standards.NormReference
import com.vamshi.field.domain.model.standards.TestSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class CalculatePercentileUseCaseTest {

    private lateinit var repository: FakeStandardsRepository
    private lateinit var useCase: CalculatePercentileUseCase

    @Before
    fun setUp() {
        repository = FakeStandardsRepository()
        useCase = CalculatePercentileUseCase(repository)
    }

    @Test
    fun `invoke with score inside standard band returns matching percentile and classification`() = runBlocking {
        val test = FitnessTest(
            id = "test_push_up",
            categoryId = "cat_strength",
            name = "Push-Up Test",
            unit = "reps",
            isHigherBetter = true
        )
        val bands = listOf(
            NormReference(id = "1", testId = "test_push_up", sex = BiologicalSex.MALE, ageMin = 13f, ageMax = 19f, minScore = 0.0, maxScore = 20.0, percentile = 30, classification = "Needs Improvement", source = TestSource.SEED),
            NormReference(id = "2", testId = "test_push_up", sex = BiologicalSex.MALE, ageMin = 13f, ageMax = 19f, minScore = 21.0, maxScore = 35.0, percentile = 60, classification = "Healthy Fitness Zone", source = TestSource.SEED),
            NormReference(id = "3", testId = "test_push_up", sex = BiologicalSex.MALE, ageMin = 13f, ageMax = 19f, minScore = 36.0, maxScore = 100.0, percentile = 90, classification = "Superior", source = TestSource.SEED)
        )
        repository.givenTests(test)
        repository.normsByTest["test_push_up"] = bands

        val result = useCase("test_push_up", 28.0, 16.0, BiologicalSex.MALE)

        assertNotNull(result)
        assertEquals(60, result?.percentile)
        assertEquals("Healthy Fitness Zone", result?.classification)
    }

    @Test
    fun `invoke with out of bounds score clamps to Superior when higher is better`() = runBlocking {
        val test = FitnessTest(
            id = "test_push_up",
            categoryId = "cat_strength",
            name = "Push-Up Test",
            unit = "reps",
            isHigherBetter = true
        )
        val bands = listOf(
            NormReference(id = "1", testId = "test_push_up", sex = BiologicalSex.MALE, ageMin = 13f, ageMax = 19f, minScore = 0.0, maxScore = 20.0, percentile = 30, classification = "Needs Improvement", source = TestSource.SEED),
            NormReference(id = "2", testId = "test_push_up", sex = BiologicalSex.MALE, ageMin = 13f, ageMax = 19f, minScore = 21.0, maxScore = 35.0, percentile = 60, classification = "Healthy Fitness Zone", source = TestSource.SEED),
            NormReference(id = "3", testId = "test_push_up", sex = BiologicalSex.MALE, ageMin = 13f, ageMax = 19f, minScore = 36.0, maxScore = 100.0, percentile = 90, classification = "Superior", source = TestSource.SEED)
        )
        repository.givenTests(test)
        repository.normsByTest["test_push_up"] = bands

        // 120 push-ups is above maxScore of 100
        val result = useCase("test_push_up", 120.0, 16.0, BiologicalSex.MALE)

        assertNotNull(result)
        assertEquals(90, result?.percentile)
        assertEquals("Superior", result?.classification)
    }

    @Test
    fun `invoke with out of bounds score clamps to Superior when lower is better for timed test`() = runBlocking {
        val test = FitnessTest(
            id = "test_sprint",
            categoryId = "cat_speed",
            name = "40m Sprint",
            unit = "seconds",
            isHigherBetter = false
        )
        val bands = listOf(
            NormReference(id = "1", testId = "test_sprint", sex = BiologicalSex.MALE, ageMin = 13f, ageMax = 19f, minScore = 4.2, maxScore = 5.0, percentile = 90, classification = "Superior", source = TestSource.SEED),
            NormReference(id = "2", testId = "test_sprint", sex = BiologicalSex.MALE, ageMin = 13f, ageMax = 19f, minScore = 5.1, maxScore = 6.2, percentile = 60, classification = "Healthy Fitness Zone", source = TestSource.SEED),
            NormReference(id = "3", testId = "test_sprint", sex = BiologicalSex.MALE, ageMin = 13f, ageMax = 19f, minScore = 6.3, maxScore = 9.0, percentile = 30, classification = "Needs Improvement", source = TestSource.SEED)
        )
        repository.givenTests(test)
        repository.normsByTest["test_sprint"] = bands

        // 3.8s is faster than minScore of 4.2s -> should clamp to Superior
        val result = useCase("test_sprint", 3.8, 16.0, BiologicalSex.MALE)

        assertNotNull(result)
        assertEquals(90, result?.percentile)
        assertEquals("Superior", result?.classification)
    }

    @Test
    fun `invoke returns null when no norms exist for test`() = runBlocking {
        val result = useCase("unknown_test", 10.0, 16.0, BiologicalSex.MALE)
        assertNull(result)
    }
}
