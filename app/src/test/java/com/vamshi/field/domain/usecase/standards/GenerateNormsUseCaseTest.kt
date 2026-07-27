package com.vamshi.field.domain.usecase.standards

import com.vamshi.field.domain.model.people.BiologicalSex
import com.vamshi.field.domain.model.standards.BandLevel
import com.vamshi.field.domain.model.standards.CustomTestDraft
import com.vamshi.field.domain.model.standards.NormReference
import com.vamshi.field.domain.model.standards.ScoringBands
import com.vamshi.field.domain.model.standards.TestSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerateNormsUseCaseTest {

    private val generate = GenerateNormsUseCase()

    private fun draft(
        isHigherBetter: Boolean,
        scoring: ScoringBands?,
        validMin: Double? = null,
        validMax: Double? = null
    ) = CustomTestDraft(
        name = "T",
        categoryId = "c",
        unit = "u",
        isHigherBetter = isHigherBetter,
        validMin = validMin,
        validMax = validMax,
        scoring = scoring
    )

    private fun shared(vararg cuts: Double) = ScoringBands(sameForAllSexes = true, shared = cuts.toList())

    /** Bands for one sex, in ascending score order, as (min, max, percentile). */
    private fun List<NormReference>.forSex(sex: BiologicalSex) =
        filter { it.sex == sex }.sortedBy { it.minScore }.map { Triple(it.minScore, it.maxScore, it.percentile) }

    @Test
    fun `no scoring produces no norms`() {
        assertEquals(emptyList<NormReference>(), generate("t1", draft(true, null)))
    }

    @Test
    fun `higher is better maps ascending cut points to ascending percentiles`() {
        val norms = generate("t1", draft(true, shared(20.0, 30.0, 40.0), validMin = 0.0, validMax = 100.0))

        assertEquals(
            listOf(
                Triple(0.0, 20.0, 15),    // Needs work
                Triple(20.0, 30.0, 45),   // Fair
                Triple(30.0, 40.0, 70),   // Good
                Triple(40.0, 100.0, 90)   // Excellent
            ),
            norms.forSex(BiologicalSex.MALE)
        )
    }

    @Test
    fun `lower is better inverts so the smallest scores get the best percentile`() {
        // A sprint time: 5.5s is excellent, over 8.0s needs work.
        val norms = generate("t1", draft(false, shared(8.0, 6.5, 5.5), validMin = 0.0, validMax = 60.0))

        assertEquals(
            listOf(
                Triple(0.0, 5.5, 90),     // Excellent — fastest
                Triple(5.5, 6.5, 70),     // Good
                Triple(6.5, 8.0, 45),     // Fair
                Triple(8.0, 60.0, 15)     // Needs work — slowest
            ),
            norms.forSex(BiologicalSex.MALE)
        )
    }

    @Test
    fun `bands are contiguous so no score falls into a hole`() {
        val norms = generate("t1", draft(true, shared(20.0, 30.0, 40.0))).forSex(BiologicalSex.MALE)

        norms.zipWithNext().forEach { (lower, upper) ->
            assertEquals(
                "band N's max must equal band N+1's min",
                lower.second,
                upper.first,
                0.0
            )
        }
    }

    @Test
    fun `same-for-all writes male female and unspecified`() {
        val norms = generate("t1", draft(true, shared(20.0, 30.0, 40.0)))

        assertEquals(12, norms.size)
        assertEquals(
            setOf(BiologicalSex.MALE, BiologicalSex.FEMALE, BiologicalSex.UNSPECIFIED),
            norms.map { it.sex }.toSet()
        )
        // The lookup matches sex exactly, so without the UNSPECIFIED rows those athletes
        // would silently never get a zone.
        assertEquals(4, norms.count { it.sex == BiologicalSex.UNSPECIFIED })
    }

    @Test
    fun `per-sex standards write only male and female`() {
        val norms = generate(
            "t1",
            draft(
                true,
                ScoringBands(
                    sameForAllSexes = false,
                    male = listOf(25.0, 35.0, 45.0),
                    female = listOf(20.0, 30.0, 40.0)
                )
            )
        )

        assertEquals(8, norms.size)
        assertEquals(0, norms.count { it.sex == BiologicalSex.UNSPECIFIED })
        assertEquals(25.0, norms.forSex(BiologicalSex.MALE)[1].first, 0.0)
        assertEquals(20.0, norms.forSex(BiologicalSex.FEMALE)[1].first, 0.0)
    }

    @Test
    fun `incomplete cut points produce nothing rather than a partial ladder`() {
        val norms = generate("t1", draft(true, ScoringBands(shared = listOf(20.0, null, 40.0))))
        assertEquals(emptyList<NormReference>(), norms)
    }

    @Test
    fun `every row is tagged USER so a reseed cannot delete it`() {
        val norms = generate("t1", draft(true, shared(20.0, 30.0, 40.0)))
        assertTrue(norms.all { it.source == TestSource.USER })
        assertTrue(norms.all { it.testId == "t1" })
    }

    @Test
    fun `percentiles land inside the zones their labels imply`() {
        // 60/30 thresholds from ClassifyPercentileUseCase.
        assertTrue("Needs work must be red", BandLevel.NEEDS_WORK.percentile < 30)
        assertTrue("Fair must be yellow", BandLevel.FAIR.percentile in 30..59)
        assertTrue("Good must be green", BandLevel.GOOD.percentile >= 60)
        assertTrue("Excellent must be green", BandLevel.EXCELLENT.percentile >= 60)
    }

    @Test
    fun `outer bounds fall back to a wide range that allows negative scores`() {
        // Sit-and-reach is measured past the toes, so it goes below zero.
        val norms = generate("t1", draft(true, shared(-5.0, 0.0, 5.0))).forSex(BiologicalSex.MALE)
        assertTrue("floor must admit negative scores", norms.first().first < -5.0)
        assertTrue(norms.last().second > 5.0)
    }
}
