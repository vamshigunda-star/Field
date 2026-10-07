package com.vamshi.field.data.mapper.testing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Demo results seeded before 1.0 stored enum names ("HEALTHY") while real results store the
 * norm row's label ("Healthy Fitness Zone"), so the CSV export and result chips mixed both.
 */
class ClassificationLabelTest {

    @Test
    fun `legacy enum names map to the norms_csv labels`() {
        assertEquals("Superior", normalizeClassificationLabel("SUPERIOR"))
        assertEquals("Healthy Fitness Zone", normalizeClassificationLabel("HEALTHY"))
        assertEquals("Needs Improvement", normalizeClassificationLabel("NEEDS_IMPROVEMENT"))
    }

    @Test
    fun `real labels and custom norm labels pass through untouched`() {
        assertEquals("Healthy Fitness Zone", normalizeClassificationLabel("Healthy Fitness Zone"))
        assertEquals("Excellent", normalizeClassificationLabel("Excellent"))
        assertNull(normalizeClassificationLabel(null))
    }
}
