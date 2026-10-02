package com.vamshi.field.ui.theme

import com.vamshi.field.domain.model.standards.RadarAxis
import com.vamshi.field.domain.model.standards.TestCatalogCsv
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Every seeded category must get its own icon. Resolving by radar axis first used to give
 * Cardiovascular and Muscular Endurance one icon (both ENDURANCE), and Balance and
 * Coordination another, so the Tests Library showed visually identical rows.
 */
class CategoryVisualConfigTest {

    private data class SeedCategory(val name: String, val axis: RadarAxis?)

    private fun seedCategories(): List<SeedCategory> =
        TestCatalogCsv.asset("test_categories.csv").readLines()
            .drop(1)
            .filter { it.isNotBlank() }
            .map { line ->
                val cells = TestCatalogCsv.splitCsvLine(line)
                SeedCategory(cells[1], RadarAxis.entries.find { it.name == cells.last().trim() })
            }

    @Test
    fun `seeded categories resolve to distinct icons when called with their radar axis`() {
        val categories = seedCategories()
        val icons = categories.associate { it.name to getCategoryVisual(it.name, it.axis).icon }
        val duplicates = icons.entries.groupBy({ it.value }, { it.key }).values.filter { it.size > 1 }
        assertEquals("Categories sharing an icon: $duplicates", emptyList<List<String>>(), duplicates)
    }

    @Test
    fun `the axis does not change the icon a category name already resolves to`() {
        seedCategories().forEach { c ->
            assertEquals(c.name, getCategoryVisual(c.name).icon, getCategoryVisual(c.name, c.axis).icon)
        }
    }
}
