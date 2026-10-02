package com.vamshi.field.ui.components.charts

import org.junit.Assert.assertEquals
import org.junit.Test

class RadarAxisPartitionTest {

    private data class Axis(val label: String, val testCount: Int)

    private val axes = listOf(
        Axis("Cardio", 2), Axis("Strength", 1), Axis("Endurance", 0), Axis("Flexibility", 0),
        Axis("Speed", 0), Axis("Agility", 3), Axis("Power", 0), Axis("Balance", 1),
        Axis("Coordination", 0), Axis("Reaction", 0), Axis("Body", 0)
    )

    @Test
    fun `four of eleven tested splits four and seven preserving order`() {
        val p = partitionAxes(axes) { it.testCount }
        assertEquals(listOf("Cardio", "Strength", "Agility", "Balance"), p.tested.map { it.label })
        assertEquals(7, p.untested.size)
        assertEquals("Endurance", p.untested.first().label)
    }

    @Test
    fun `nothing tested leaves every axis untested`() {
        val p = partitionAxes(axes.map { it.copy(testCount = 0) }) { it.testCount }
        assertEquals(0, p.tested.size)
        assertEquals(11, p.untested.size)
    }

    @Test
    fun `everything tested leaves no untested axes`() {
        val p = partitionAxes(axes.map { it.copy(testCount = 1) }) { it.testCount }
        assertEquals(11, p.tested.size)
        assertEquals(0, p.untested.size)
    }
}
