package com.vamshi.field.data.repository

import com.vamshi.field.domain.model.people.BiologicalSex
import com.vamshi.field.domain.model.standards.NormReference
import com.vamshi.field.domain.model.standards.TestSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fallback when no norm bracket contains the athlete's age. It used to return every bracket, so a
 * 10-year-old on an adult-only test (push-up: 20–29 upwards) was graded against adult bands.
 */
class NearestNormBracketTest {

    private val pushUp = listOf(20f to 29f, 30f to 39f, 60f to 69f).flatMap { (min, max) ->
        listOf(0.0 to 20.0, 21.0 to 35.0, 36.0 to 100.0).mapIndexed { i, (lo, hi) ->
            NormReference(
                id = "$min-$i", testId = "test_push_up", variant = "Default", sex = BiologicalSex.MALE,
                ageMin = min, ageMax = max, minScore = lo, maxScore = hi,
                percentile = listOf(20, 60, 90)[i], classification = "c$i", source = TestSource.SEED
            )
        }
    }

    @Test
    fun `younger than every bracket gets no norm`() {
        assertTrue(nearestNormBracket(pushUp, 10.0).isEmpty())
    }

    @Test
    fun `older than every bracket uses the oldest bracket only`() {
        val bands = nearestNormBracket(pushUp, 75.0)
        assertEquals(3, bands.size)
        assertTrue(bands.all { it.ageMin == 60f })
    }

    @Test
    fun `a gap in the data uses the nearest bracket only`() {
        val bands = nearestNormBracket(pushUp, 45.0)
        assertEquals(3, bands.size)
        assertTrue(bands.all { it.ageMin == 30f })
    }
}
