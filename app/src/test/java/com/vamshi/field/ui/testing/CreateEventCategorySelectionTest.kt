package com.vamshi.field.ui.testing

import org.junit.Assert.assertEquals
import org.junit.Test

class CreateEventCategorySelectionTest {

    private val flexibility = setOf("sit_reach", "back_scratch", "trunk_flex")

    @Test
    fun `nothing selected selects the whole category`() {
        assertEquals(flexibility, toggleCategory(emptySet(), flexibility))
    }

    @Test
    fun `partly selected completes the category`() {
        assertEquals(flexibility, toggleCategory(setOf("sit_reach"), flexibility))
    }

    @Test
    fun `fully selected clears only that category`() {
        val selected = flexibility + "pacer"
        assertEquals(setOf("pacer"), toggleCategory(selected, flexibility))
    }

    @Test
    fun `selecting a category keeps other categories' tests`() {
        assertEquals(flexibility + "pacer", toggleCategory(setOf("pacer"), flexibility))
    }

    @Test
    fun `an empty category changes nothing`() {
        assertEquals(setOf("pacer"), toggleCategory(setOf("pacer"), emptySet()))
    }
}
