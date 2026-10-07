package com.vamshi.field.ui.help

import com.vamshi.field.domain.model.reports.PerformanceThresholds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HowToUseGuidesTest {

    private val guides = HowToUseGuides.all

    @Test
    fun `page has the three core guides in order`() {
        assertEquals(listOf("add_athletes", "record_test", "understand_results"), guides.map { it.id })
    }

    @Test
    fun `ids are unique so lazy list keys and saved expand state never collide`() {
        assertEquals(guides.size, guides.map { it.id }.toSet().size)
    }

    @Test
    fun `every guide has steps, a caption and an accessibility description`() {
        guides.forEach { guide ->
            assertTrue("${guide.id} has no steps", guide.steps.isNotEmpty())
            guide.steps.forEach { step ->
                assertTrue("${guide.id}: blank step", step.title.isNotBlank() && step.body.isNotBlank())
            }
            assertTrue("${guide.id}: blank caption", guide.screenshot.caption.isNotBlank())
            assertTrue("${guide.id}: blank description", guide.screenshot.contentDescription.isNotBlank())
        }
    }

    @Test
    fun `screenshot file names are unique valid resource names`() {
        val names = guides.map { it.screenshot.fileName }
        assertEquals(names.size, names.toSet().size)
        names.forEach { assertTrue("$it is not a valid drawable name", Regex("[a-z0-9_]+\\.(webp|png)").matches(it)) }
    }

    @Test
    fun `zone step quotes the shared thresholds, not retyped numbers`() {
        val zoneStep = guides.flatMap { it.steps }.single { it.showZoneLegend }
        assertTrue(zoneStep.body.contains("${PerformanceThresholds.SUPERIOR_MIN}th percentile"))
        assertTrue(zoneStep.body.contains("below ${PerformanceThresholds.HEALTHY_MIN}th"))
    }
}
