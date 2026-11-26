package com.vamshi.field.data.repository

import com.vamshi.field.domain.model.people.BiologicalSex
import com.vamshi.field.domain.model.reports.Classification
import com.vamshi.field.domain.model.standards.NormReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The benchmark lines on the Athlete × Test chart. Spec: 2026-11-26-gemini-app-audit-triage (#1).
 *
 * Norm rows carry a representative percentile per band (20 / 60 / 90). The chart used to ask for
 * the band nearest p35 as "Healthy", which is the 20 row — so the Healthy line sat at the Needs
 * Improvement floor.
 */
class NormBandThresholdTest {

    private fun band(min: Double, max: Double, pct: Int) = NormReference(
        id = "n$pct", testId = "test_push_up", sex = BiologicalSex.MALE,
        ageMin = 20f, ageMax = 40f, minScore = min, maxScore = max, percentile = pct
    )

    // Push-ups, male 20–29, straight from norms.csv.
    private val pushUps = listOf(band(0.0, 21.0, 20), band(22.0, 35.0, 60), band(36.0, 100.0, 90))

    // A lower-is-better test (seconds): faster is better.
    private val sprint = listOf(band(5.6, 99.0, 20), band(4.8, 5.5, 60), band(0.0, 4.7, 90))

    @Test
    fun healthyLineUsesHealthyBand() {
        assertEquals(22.0, bandEntryScore(pushUps, Classification.HEALTHY, isHigherBetter = true)!!, 0.0)
    }

    @Test
    fun superiorLineUsesSuperiorBand() {
        assertEquals(36.0, bandEntryScore(pushUps, Classification.SUPERIOR, isHigherBetter = true)!!, 0.0)
    }

    @Test
    fun needsMaxIsTopOfNeedsImprovementBand() {
        assertEquals(21.0, bandExitScore(pushUps, Classification.NEEDS_IMPROVEMENT, isHigherBetter = true)!!, 0.0)
    }

    @Test
    fun lowerIsBetterUsesTheSlowEdgeOfEachBand() {
        assertEquals(5.5, bandEntryScore(sprint, Classification.HEALTHY, isHigherBetter = false)!!, 0.0)
        assertEquals(4.7, bandEntryScore(sprint, Classification.SUPERIOR, isHigherBetter = false)!!, 0.0)
        assertEquals(5.6, bandExitScore(sprint, Classification.NEEDS_IMPROVEMENT, isHigherBetter = false)!!, 0.0)
    }

    @Test
    fun reversedMinMaxRowsStillWork() {
        val reversed = listOf(band(35.0, 22.0, 60))
        assertEquals(22.0, bandEntryScore(reversed, Classification.HEALTHY, isHigherBetter = true)!!, 0.0)
    }

    @Test
    fun missingZoneIsNull() {
        assertNull(bandEntryScore(listOf(band(0.0, 21.0, 20)), Classification.HEALTHY, isHigherBetter = true))
        assertNull(bandEntryScore(emptyList(), Classification.SUPERIOR, isHigherBetter = true))
    }
}
