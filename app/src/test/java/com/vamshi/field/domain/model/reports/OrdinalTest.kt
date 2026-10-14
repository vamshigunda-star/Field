package com.vamshi.field.domain.model.reports

import org.junit.Assert.assertEquals
import org.junit.Test

class OrdinalTest {

    @Test
    fun `percentiles get the right English suffix`() {
        val expected = mapOf(
            1 to "1st", 2 to "2nd", 3 to "3rd", 4 to "4th",
            11 to "11th", 12 to "12th", 13 to "13th",
            21 to "21st", 71 to "71st", 81 to "81st", 92 to "92nd", 100 to "100th"
        )
        expected.forEach { (n, label) -> assertEquals(label, ordinal(n)) }
    }
}
