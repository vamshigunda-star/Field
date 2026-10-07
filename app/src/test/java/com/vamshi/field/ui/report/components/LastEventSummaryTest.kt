package com.vamshi.field.ui.report.components

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The header zone describes the athlete's most recent testing event only. Without the
 * "Last event" prefix, an athlete averaging the 64th percentile whose last event was one weak
 * test read as an overall "Needs Improvement".
 */
class LastEventSummaryTest {

    @Test
    fun `labels the zone as belonging to the last event`() {
        assertEquals("Last event: Needs Improvement • 1 test", lastEventSummary(20, 1))
        assertEquals("Last event: Superior • 6 tests", lastEventSummary(85, 6))
    }

    @Test
    fun `uses the shared 80-40 thresholds`() {
        assertEquals("Last event: Healthy • 2 tests", lastEventSummary(79, 2))
        assertEquals("Last event: Superior • 2 tests", lastEventSummary(80, 2))
    }

    @Test
    fun `athlete with no events gets an explicit message`() {
        assertEquals("No events yet", lastEventSummary(null, 0))
    }
}
