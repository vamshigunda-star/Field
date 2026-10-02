package com.vamshi.field.domain.model.backup

import com.google.gson.Gson
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Drive backup JSON is a persisted, cross-version format: its keys are the Kotlin field names
 * of the Backup* DTOs, read back by plain Gson reflection. Renaming a field — or letting R8 rename
 * it (see proguard-rules.pro) — makes every backup already in a coach's Drive unrestorable.
 *
 * The fixture is hand-written against today's field names on purpose. If this test fails after a
 * rename, the fix is to keep the old name (or add @SerializedName with the old name), never to
 * edit the fixture.
 */
class BackupJsonContractTest {

    private val gson = Gson()

    private fun fixture(): String =
        requireNotNull(javaClass.classLoader?.getResourceAsStream("backup/backup_v1_fixture.json")) {
            "backup/backup_v1_fixture.json missing from test resources"
        }.bufferedReader().use { it.readText() }

    @Test
    fun `v1 fixture restores every user-data field`() {
        val payload = gson.fromJson(fixture(), BackupPayload::class.java)

        val athlete = payload.individuals.single()
        assertEquals("Asha", athlete.firstName)
        assertEquals("Asthma - inhaler in kit bag", athlete.medicalAlert)
        assertEquals(true, athlete.isRestricted)

        assertEquals("grp-1", payload.testingEvents.single().groupId)
        assertEquals(60, payload.testResults.single().percentile)
        assertEquals("custom-sprint", payload.customTests.orEmpty().single().id)
        assertEquals(1, payload.customNorms.orEmpty().size)
        assertEquals(1, payload.schemaVersion)
    }

    @Test
    fun `backup written before the lossless fields still parses`() {
        // Simulate a pre-schemaVersion backup by stripping every later-added key.
        val root = JsonParser.parseString(fixture()).asJsonObject
        listOf("customCategories", "customTests", "customNorms", "schemaVersion").forEach(root::remove)
        root.getAsJsonArray("individuals").forEach { it.asJsonObject.remove("medicalAlert") }

        val payload = gson.fromJson(root, BackupPayload::class.java)

        assertNull(payload.customTests)
        assertNull(payload.schemaVersion)
        assertNull(payload.individuals.single().medicalAlert)
        assertTrue(payload.testResults.isNotEmpty())
    }

    @Test
    fun `serialized keys match the fixture keys`() {
        // Round-trip guards the write side: a field added or renamed on the DTO changes what
        // toJson emits, so the top-level key set must stay a superset of the v1 format.
        val written = JsonParser.parseString(
            gson.toJson(gson.fromJson(fixture(), BackupPayload::class.java))
        ).asJsonObject.keySet()
        val v1Keys = JsonParser.parseString(fixture()).asJsonObject.keySet()

        assertTrue("Backup keys dropped: ${v1Keys - written}", written.containsAll(v1Keys))
    }
}
