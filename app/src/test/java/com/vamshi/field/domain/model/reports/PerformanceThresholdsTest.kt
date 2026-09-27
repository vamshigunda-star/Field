package com.vamshi.field.domain.model.reports

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Boundary coverage for the one rule the whole app classifies against.
 *
 * The boundaries are the whole point: the app previously had five different thresholds in UI
 * code, so the same athlete could read Yellow in the testing grid and Red in the report.
 */
class PerformanceThresholdsTest {

    @Test
    fun `null percentile is no data, not a zone`() {
        assertEquals(Classification.NO_DATA, PerformanceThresholds.classify(null as Int?))
    }

    @Test
    fun `boundaries classify on the documented side`() {
        assertEquals(Classification.NEEDS_IMPROVEMENT, PerformanceThresholds.classify(0))
        assertEquals(Classification.NEEDS_IMPROVEMENT, PerformanceThresholds.classify(39))
        assertEquals(Classification.HEALTHY, PerformanceThresholds.classify(40))
        assertEquals(Classification.HEALTHY, PerformanceThresholds.classify(79))
        assertEquals(Classification.SUPERIOR, PerformanceThresholds.classify(80))
        assertEquals(Classification.SUPERIOR, PerformanceThresholds.classify(100))
    }

    @Test
    fun `the encode constants land inside the bands they name`() {
        assertEquals(Classification.NEEDS_IMPROVEMENT, PerformanceThresholds.classify(PerformanceThresholds.ENCODE_NEEDS_IMPROVEMENT))
        assertEquals(Classification.HEALTHY, PerformanceThresholds.classify(PerformanceThresholds.ENCODE_HEALTHY))
        assertEquals(Classification.SUPERIOR, PerformanceThresholds.classify(PerformanceThresholds.ENCODE_SUPERIOR))
    }

    @Test
    fun `float overload rounds to the same verdict`() {
        assertEquals(Classification.NEEDS_IMPROVEMENT, PerformanceThresholds.classify(39.4f))
        assertEquals(Classification.HEALTHY, PerformanceThresholds.classify(39.5f))
        assertEquals(Classification.SUPERIOR, PerformanceThresholds.classify(79.5f))
        assertEquals(Classification.NO_DATA, PerformanceThresholds.classify(null as Float?))
    }
}
