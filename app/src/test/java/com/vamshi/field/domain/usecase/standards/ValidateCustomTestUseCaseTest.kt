package com.vamshi.field.domain.usecase.standards

import com.vamshi.field.domain.model.standards.CustomTestDraft
import com.vamshi.field.domain.model.standards.CustomTestField
import com.vamshi.field.domain.model.standards.ScoringBands
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidateCustomTestUseCaseTest {

    private val validate = ValidateCustomTestUseCase()

    private fun validDraft() = CustomTestDraft(
        name = "Sled Push 20m",
        categoryId = "cat_agility",
        unit = "sec",
        isHigherBetter = false
    )

    private fun fieldsInError(draft: CustomTestDraft) = validate(draft).map { it.field }.toSet()

    @Test
    fun `a complete draft has no errors`() {
        assertEquals(emptyList<Any>(), validate(validDraft()))
    }

    @Test
    fun `name is required and must not be only whitespace`() {
        assertTrue(CustomTestField.NAME in fieldsInError(validDraft().copy(name = "")))
        assertTrue(CustomTestField.NAME in fieldsInError(validDraft().copy(name = "   ")))
    }

    @Test
    fun `name over 60 characters is rejected`() {
        assertTrue(CustomTestField.NAME in fieldsInError(validDraft().copy(name = "x".repeat(61))))
        assertTrue(CustomTestField.NAME !in fieldsInError(validDraft().copy(name = "x".repeat(60))))
    }

    @Test
    fun `category and unit are required`() {
        assertTrue(CustomTestField.CATEGORY in fieldsInError(validDraft().copy(categoryId = "")))
        assertTrue(CustomTestField.UNIT in fieldsInError(validDraft().copy(unit = " ")))
    }

    @Test
    fun `unit over 12 characters is rejected`() {
        assertTrue(CustomTestField.UNIT in fieldsInError(validDraft().copy(unit = "x".repeat(13))))
    }

    @Test
    fun `trials outside 1 to 5 are rejected`() {
        assertTrue(CustomTestField.TRIALS in fieldsInError(validDraft().copy(trialsPerAthlete = 0)))
        assertTrue(CustomTestField.TRIALS in fieldsInError(validDraft().copy(trialsPerAthlete = 6)))
        assertTrue(CustomTestField.TRIALS !in fieldsInError(validDraft().copy(trialsPerAthlete = 1)))
        assertTrue(CustomTestField.TRIALS !in fieldsInError(validDraft().copy(trialsPerAthlete = 5)))
    }

    @Test
    fun `valid range must be ascending when both ends are given`() {
        assertTrue(
            CustomTestField.VALID_RANGE in
                fieldsInError(validDraft().copy(validMin = 10.0, validMax = 5.0))
        )
        assertTrue(
            "equal bounds admit no valid score",
            CustomTestField.VALID_RANGE in
                fieldsInError(validDraft().copy(validMin = 5.0, validMax = 5.0))
        )
        assertTrue(
            CustomTestField.VALID_RANGE !in
                fieldsInError(validDraft().copy(validMin = 5.0, validMax = 10.0))
        )
    }

    @Test
    fun `a single open bound is allowed`() {
        // "at least 0, no ceiling" is a normal way to describe a distance.
        assertEquals(emptyList<Any>(), validate(validDraft().copy(validMin = 0.0, validMax = null)))
        assertEquals(emptyList<Any>(), validate(validDraft().copy(validMin = null, validMax = 120.0)))
    }

    // --- Scoring bands ---

    private fun shared(vararg cuts: Double?) =
        ScoringBands(sameForAllSexes = true, shared = cuts.toList())

    @Test
    fun `raw-scores-only needs no bands`() {
        assertEquals(emptyList<Any>(), validate(validDraft().copy(scoring = null)))
    }

    @Test
    fun `complete ascending cut points are valid for a higher-is-better test`() {
        val draft = validDraft().copy(isHigherBetter = true, scoring = shared(20.0, 30.0, 40.0))
        assertEquals(emptyList<Any>(), validate(draft))
    }

    @Test
    fun `complete descending cut points are valid for a lower-is-better test`() {
        val draft = validDraft().copy(isHigherBetter = false, scoring = shared(8.0, 6.5, 5.5))
        assertEquals(emptyList<Any>(), validate(draft))
    }

    @Test
    fun `ascending cut points on a lower-is-better test are rejected`() {
        // The easiest mistake to make: entering a sprint ladder as if bigger were better.
        val draft = validDraft().copy(isHigherBetter = false, scoring = shared(5.5, 6.5, 8.0))
        assertTrue(CustomTestField.BANDS in fieldsInError(draft))
    }

    @Test
    fun `descending cut points on a higher-is-better test are rejected`() {
        val draft = validDraft().copy(isHigherBetter = true, scoring = shared(40.0, 30.0, 20.0))
        assertTrue(CustomTestField.BANDS in fieldsInError(draft))
    }

    @Test
    fun `equal cut points are rejected because they make a zero-width band`() {
        val draft = validDraft().copy(isHigherBetter = true, scoring = shared(20.0, 20.0, 40.0))
        assertTrue(CustomTestField.BANDS in fieldsInError(draft))
    }

    @Test
    fun `a partially filled ladder is rejected`() {
        val draft = validDraft().copy(isHigherBetter = true, scoring = shared(20.0, null, 40.0))
        assertTrue(CustomTestField.BANDS in fieldsInError(draft))
    }

    @Test
    fun `cut points outside the valid score range are rejected`() {
        val draft = validDraft().copy(
            isHigherBetter = true,
            validMin = 0.0,
            validMax = 35.0,
            scoring = shared(20.0, 30.0, 40.0)
        )
        assertTrue(CustomTestField.BANDS in fieldsInError(draft))
    }

    @Test
    fun `per-sex ladders are validated independently`() {
        val draft = validDraft().copy(
            isHigherBetter = true,
            scoring = ScoringBands(
                sameForAllSexes = false,
                male = listOf(20.0, 30.0, 40.0),   // fine
                female = listOf(40.0, 30.0, 20.0)  // reversed
            )
        )
        val errors = validate(draft).filter { it.field == CustomTestField.BANDS }
        assertEquals(1, errors.size)
        assertTrue("the message should say which ladder is wrong", errors.single().message.startsWith("Female"))
    }

    @Test
    fun `the shared ladder is ignored when per-sex standards are in use`() {
        val draft = validDraft().copy(
            isHigherBetter = true,
            scoring = ScoringBands(
                sameForAllSexes = false,
                shared = listOf(null, null, null), // untouched, must not trip validation
                male = listOf(20.0, 30.0, 40.0),
                female = listOf(18.0, 28.0, 38.0)
            )
        )
        assertEquals(emptyList<Any>(), validate(draft))
    }

    @Test
    fun `every broken field is reported at once rather than just the first`() {
        val errors = fieldsInError(
            validDraft().copy(name = "", unit = "", categoryId = "", trialsPerAthlete = 99)
        )
        assertEquals(
            setOf(
                CustomTestField.NAME,
                CustomTestField.UNIT,
                CustomTestField.CATEGORY,
                CustomTestField.TRIALS
            ),
            errors
        )
    }
}
