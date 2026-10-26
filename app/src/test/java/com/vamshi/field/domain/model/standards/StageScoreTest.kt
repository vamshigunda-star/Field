package com.vamshi.field.domain.model.standards

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StageScoreTest {

    @Test
    fun `shuttle 10 and shuttle 1 are different scores`() {
        // The old encoding parsed "8.10" and "8.1" with toDouble(): both became 8.1.
        val ten = StageScore.parse("8.10")!!
        val one = StageScore.parse("8.1")!!
        assertNotEquals(one, ten, 0.0)
        assertTrue(ten > one)
    }

    @Test
    fun `stage scores sort in running order`() {
        val order = listOf("7.16", "8.1", "8.9", "8.10", "8.11", "9.1").map { StageScore.parse(it)!! }
        assertEquals(order, order.sorted())
    }

    @Test
    fun `format round-trips what the picker emits`() {
        for (level in 1..21) for (shuttle in 0..16) {
            val text = "$level.$shuttle"
            assertEquals(text, StageScore.format(StageScore.parse(text)!!))
        }
    }

    @Test
    fun `malformed stage text is rejected`() {
        assertNull(StageScore.parse(""))
        assertNull(StageScore.parse("8.1.2"))
        assertNull(StageScore.parse("8.100"))
        assertNull(StageScore.parse("-1.2"))
        assertNull(StageScore.parse("a.b"))
    }

    @Test
    fun `formatScore only uses stage notation for stage tests`() {
        val raw = StageScore.encode(8, 10)
        assertEquals("8.10", formatScore(raw, InputParadigm.MULTI_STAGE))
        assertEquals("12", formatScore(12.0, InputParadigm.NUMERIC))
        assertEquals(8.10, parseScore("8.10", InputParadigm.NUMERIC)!!, 1e-9)
        assertEquals(raw, parseScore("8.10", InputParadigm.MULTI_STAGE)!!, 1e-9)
    }

    @Test
    fun `picker caps match each test's validMax in tests csv`() {
        // A Yo-Yo cap above 8 would let 15.20 encode past 16.08 and lift the athlete a band.
        val beep = StageLimits.forTest("test_beep")!!
        val yoYo = StageLimits.forTest("test_yo_yo")!!
        assertEquals(21.16, StageScore.encode(beep.maxLevel, beep.maxShuttle), 1e-9)
        assertEquals(24.08, StageScore.encode(yoYo.maxLevel, yoYo.maxShuttle), 1e-9)
        assertNull(StageLimits.forTest("custom-levels-test"))
    }
}
