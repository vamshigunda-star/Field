package com.vamshi.field.ui.testing.stopwatch

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The edit-time keypad's text is centiseconds. It was seeded from the formatted time with the
 * punctuation stripped, so "1:02.35" became "10235" and saved as 1:42.35.
 */
class EditTimeEncodingTest {

    @Test
    fun `a time over a minute survives an unchanged edit`() {
        assertEquals(62_350L, parseEditCentis(editSeed(62_350L)))
    }

    @Test
    fun `sub-minute and long-run times round-trip`() {
        listOf(4_870L, 59_990L, 2_399_990L).forEach { ms ->
            assertEquals(ms, parseEditCentis(editSeed(ms)))
        }
    }

    @Test
    fun `an empty time starts blank and reads as zero`() {
        assertEquals("", editSeed(0L))
        assertEquals(0L, parseEditCentis(""))
    }
}
