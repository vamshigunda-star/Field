package com.vamshi.field.domain.model.reports

import com.vamshi.field.domain.model.people.AgeBracket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AgeBracketTest {

    @Test
    fun `brackets are contiguous and cover 5 to the cap with no gaps or overlaps`() {
        val ordered = AgeBracket.entries.sortedBy { it.minAge }
        assertEquals(5, ordered.first().minAge)
        assertEquals(AgeBracket.UPPER_AGE_CAP, ordered.last().maxAge)
        ordered.zipWithNext { a, b ->
            assertEquals("gap or overlap between ${a.name} and ${b.name}", a.maxAge + 1, b.minAge)
        }
    }

    @Test
    fun `of maps ages to the expected bracket`() {
        assertNull("below the youngest bracket has no norms", AgeBracket.of(4))
        assertEquals(AgeBracket.CHILDHOOD, AgeBracket.of(5))
        assertEquals(AgeBracket.CHILDHOOD, AgeBracket.of(12))
        assertEquals(AgeBracket.ADOLESCENCE, AgeBracket.of(13))
        assertEquals(AgeBracket.ADOLESCENCE, AgeBracket.of(19))
        assertEquals(AgeBracket.YOUNG_ADULT, AgeBracket.of(20))
        assertEquals(AgeBracket.YOUNG_ADULT, AgeBracket.of(40))
        assertEquals(AgeBracket.MIDDLE_ADULT, AgeBracket.of(41))
        assertEquals(AgeBracket.MIDDLE_ADULT, AgeBracket.of(62))
        assertEquals(AgeBracket.OLDER_ADULT, AgeBracket.of(63))
        assertEquals(AgeBracket.OLDER_ADULT, AgeBracket.of(115))
        assertNull("above the cap has no norms", AgeBracket.of(116))
    }
}
