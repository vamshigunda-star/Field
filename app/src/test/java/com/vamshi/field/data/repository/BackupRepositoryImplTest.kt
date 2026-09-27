package com.vamshi.field.data.repository

import androidx.room3.Room
import androidx.test.core.app.ApplicationProvider
import com.google.gson.Gson
import com.vamshi.field.data.AppDatabase
import com.vamshi.field.data.backup.DeviceIdentifier
import com.vamshi.field.data.backup.DriveBackupHelper
import com.vamshi.field.data.local.entities.people.IndividualEntity
import com.vamshi.field.domain.model.backup.BackupEventTestCrossRef
import com.vamshi.field.domain.model.backup.BackupFitnessTest
import com.vamshi.field.domain.model.backup.BackupGroup
import com.vamshi.field.domain.model.backup.BackupIndividual
import com.vamshi.field.domain.model.backup.BackupNormReference
import com.vamshi.field.domain.model.backup.BackupPayload
import com.vamshi.field.domain.model.backup.BackupTestCategory
import com.vamshi.field.domain.model.backup.BackupTestResult
import com.vamshi.field.domain.model.backup.BackupTestingEvent
import com.vamshi.field.domain.model.people.BiologicalSex
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Regression coverage for the restore-from-Drive data-loss bug: a malformed backup payload
 * used to be applied via two separate transactions (a raw-SQL clear that committed on its own,
 * then a second transaction that mapped and inserted). If mapping threw, the clear had already
 * committed and local data was gone with nothing restored. [BackupRepositoryImpl.restoreEntities]
 * now does the clear and the insert in one atomic transaction.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class BackupRepositoryImplTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: BackupRepositoryImpl

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = BackupRepositoryImpl(context, db, Gson(), DriveBackupHelper(DeviceIdentifier(context)))
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun seedOneIndividual(): IndividualEntity {
        val existing = IndividualEntity(
            id = "existing-1",
            firstName = "Original",
            lastName = "Athlete",
            dateOfBirth = 0L,
            sex = BiologicalSex.MALE
        )
        db.backupDao().insertIndividuals(listOf(existing))
        return existing
    }

    @Test
    fun `restoreEntities with an unparseable field leaves existing local data untouched`() = runTest {
        val existing = seedOneIndividual()

        // "not-a-real-sex" doesn't match any BiologicalSex constant, so BiologicalSex.valueOf
        // throws mid-mapping inside the transaction — exactly what a corrupted or
        // schema-mismatched backup file produces.
        val corruptPayload = BackupPayload(
            individuals = listOf(
                BackupIndividual(
                    id = "restored-1",
                    firstName = "Restored",
                    lastName = "Athlete",
                    dateOfBirth = 0L,
                    gender = "not-a-real-sex",
                    notes = null
                )
            ),
            groups = emptyList(),
            groupMembers = emptyList(),
            testingEvents = emptyList(),
            eventTests = emptyList(),
            testResults = emptyList(),
            users = emptyList()
        )

        try {
            repository.restoreEntities(corruptPayload)
            throw AssertionError("Expected restoreEntities to throw on an invalid payload")
        } catch (e: IllegalArgumentException) {
            // expected: BiologicalSex.valueOf("not-a-real-sex") failing
        }

        val remaining = db.backupDao().getAllIndividuals()
        assertEquals(
            "Local data must survive a failed restore instead of being wiped",
            listOf(existing),
            remaining
        )
    }

    @Test
    fun `restoreEntities with a valid payload replaces local data`() = runTest {
        seedOneIndividual()

        val payload = BackupPayload(
            individuals = listOf(
                BackupIndividual(
                    id = "restored-1",
                    firstName = "Restored",
                    lastName = "Athlete",
                    dateOfBirth = 12345L,
                    gender = BiologicalSex.FEMALE.name,
                    notes = "from backup"
                )
            ),
            groups = listOf(BackupGroup(id = "g1", name = "Team A", type = "TEAM", isActive = true)),
            groupMembers = emptyList(),
            testingEvents = emptyList(),
            eventTests = emptyList(),
            testResults = emptyList(),
            users = emptyList()
        )

        repository.restoreEntities(payload)

        val individuals = db.backupDao().getAllIndividuals()
        assertEquals(1, individuals.size)
        assertEquals("restored-1", individuals.first().id)
        assertEquals(1, db.backupDao().getAllGroups().size)
    }

    /**
     * The failure mode that made custom tests unsafe to ship: `test_results.testId` is a
     * RESTRICT foreign key to `fitness_tests`, and the backup payload used to carry no
     * catalog at all. A backup containing results for a coach-authored test would blow the
     * constraint on restore, roll the whole transaction back, and surface as
     * CorruptedBackup — losing the coach's entire restore, not just the custom test.
     */
    @Test
    fun `restoreEntities restores a custom test before the results that reference it`() = runTest {
        val payload = BackupPayload(
            individuals = listOf(
                BackupIndividual("ath-1", "Custom", "Athlete", 0L, BiologicalSex.MALE.name, null)
            ),
            groups = emptyList(),
            groupMembers = emptyList(),
            testingEvents = listOf(BackupTestingEvent("ev-1", "Sled Day", 1_000L, null)),
            eventTests = listOf(BackupEventTestCrossRef("ev-1", "custom-test-1")),
            testResults = listOf(
                BackupTestResult(
                    id = "res-1",
                    eventId = "ev-1",
                    individualId = "ath-1",
                    testId = "custom-test-1",
                    rawScore = 12.5,
                    standardizedScore = null,
                    timestamp = 1_000L,
                    captureMethod = "MANUAL_ENTRY",
                    notes = null
                )
            ),
            users = emptyList(),
            customCategories = listOf(
                BackupTestCategory("custom-cat-1", "Sport Specific", null, 99, null)
            ),
            customTests = listOf(customTest()),
            customNorms = listOf(customNorm())
        )

        repository.restoreEntities(payload)

        val tests = db.standardsDao().getAllTestsOnce()
        assertEquals(listOf("custom-test-1"), tests.map { it.id })
        assertEquals(
            "restored custom rows must be USER so the CSV importer never deletes them",
            "USER",
            tests.single().source
        )
        assertEquals(1, db.standardsDao().getNormsForTest("custom-test-1").size)
        assertEquals(1, db.backupDao().getAllTestResults().size)
    }

    @Test
    fun `backup payload carries only coach-authored catalog rows`() = runTest {
        db.standardsDao().insertCategory(
            com.vamshi.field.data.local.entities.standards.TestCategoryEntity(
                id = "cat_cardio", name = "Cardio", source = "SEED"
            )
        )
        db.standardsDao().insertTest(
            com.vamshi.field.data.local.entities.standards.FitnessTestEntity(
                id = "test_pacer", categoryId = "cat_cardio", name = "PACER",
                unit = "laps", isHigherBetter = true, source = "SEED"
            )
        )
        db.standardsDao().insertTest(
            com.vamshi.field.data.local.entities.standards.FitnessTestEntity(
                id = "custom-test-1", categoryId = "cat_cardio", name = "Sled Push 20m",
                unit = "sec", isHigherBetter = false, source = "USER"
            )
        )

        assertEquals(listOf("custom-test-1"), db.backupDao().getUserTests().map { it.id })
    }

    /**
     * Coaches have backups in Drive written before the custom-catalog fields existed.
     *
     * Gson builds data classes through Unsafe, so Kotlin constructor defaults never run for
     * absent JSON fields — a non-null `List<T> = emptyList()` would come back null and NPE
     * on the first read. These fields are nullable for that reason; this test pins the
     * behaviour so nobody "tidies" them back to non-null defaults.
     */
    @Test
    fun `a payload written without the custom-catalog fields restores without error`() = runTest {
        val legacyJson = """
            {"individuals":[],"groups":[],"groupMembers":[],"testingEvents":[],
             "eventTests":[],"testResults":[],"users":[]}
        """.trimIndent()

        val payload = Gson().fromJson(legacyJson, BackupPayload::class.java)
        assertNull(payload.customTests)

        // The real assertion: restoring a legacy payload must not throw.
        repository.restoreEntities(payload)
        assertEquals(0, db.standardsDao().getAllTestsOnce().size)
    }

    private fun customTest() = BackupFitnessTest(
        id = "custom-test-1",
        categoryId = "custom-cat-1",
        name = "Sled Push 20m",
        unit = "sec",
        isHigherBetter = false,
        description = "Push the sled 20 metres.",
        timingMode = "MANUAL_ENTRY",
        inputParadigm = "NUMERIC",
        athletesPerHeat = null,
        trialsPerAthlete = 1,
        validMin = 0.0,
        validMax = 120.0,
        interpretationStrategy = "NORM_LOOKUP",
        calculationConfig = null,
        youtubeId = null,
        isDeleted = false
    )

    private fun customNorm() = BackupNormReference(
        id = "custom-norm-1",
        testId = "custom-test-1",
        variant = null,
        sex = BiologicalSex.MALE.name,
        ageMin = 0f,
        ageMax = 99f,
        minScore = 0.0,
        maxScore = 8.0,
        percentile = 90,
        classification = "Excellent"
    )

    @Test
    fun `getLastBackupTimestamp is null until a backup timestamp is recorded`() = runTest {
        assertNull(repository.getLastBackupTimestamp())
    }

    @Test
    fun `backup timestamp survives repository recreation, simulating process death`() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        repository.recordBackupTimestamp(42_000L)

        // A fresh repository instance over the same app context reads the same SharedPreferences
        // file — this is what happens after the process is killed and relaunched.
        val recreated = BackupRepositoryImpl(context, db, Gson(), DriveBackupHelper(DeviceIdentifier(context)))

        assertEquals(42_000L, recreated.getLastBackupTimestamp())
    }

    // ---------------------------------------------------------------------------
    // Lossless round trip.
    //
    // The existing tests above cover the *dangerous* version of a broken restore: a
    // failed one must not wipe local data. They all pass while the quiet version --
    // a restore that "succeeds" and silently drops columns -- goes unnoticed, which
    // is exactly how it shipped. These exercise the round trip itself.
    // ---------------------------------------------------------------------------

    /**
     * The headline regression. BackupTestingEvent carried no groupId, so a restored
     * event came back with groupId = null and TestingDao.getEventsForGroup could not
     * find it. The coach restores, opens their group, and sees nothing -- even though
     * the rows are in the database.
     */
    @Test
    fun `restoreEntities keeps a testing event attached to its group`() = runTest {
        val payload = basePayload(
            groups = listOf(BackupGroup(id = "g1", name = "Team A", type = "TEAM", isActive = true)),
            testingEvents = listOf(
                BackupTestingEvent(
                    id = "ev-1", name = "Spring Testing", timestamp = 5_000L,
                    notes = null, groupId = "g1"
                )
            )
        )

        repository.restoreEntities(payload)

        val restored = db.backupDao().getAllTestingEvents().single()
        assertEquals("g1", restored.groupId)
    }

    /**
     * Safety-critical: medicalAlert and isRestricted were not in the payload at all, so
     * every athlete came back from a restore reading as having no medical alert.
     */
    @Test
    fun `restoreEntities preserves medical alert and restriction flag`() = runTest {
        val payload = basePayload(
            individuals = listOf(
                BackupIndividual(
                    id = "ath-1", firstName = "Asha", lastName = "Rao", dateOfBirth = 1_000L,
                    gender = BiologicalSex.FEMALE.name, notes = null,
                    medicalAlert = "Asthma - inhaler on site", isRestricted = true,
                    email = "asha@example.com", isActive = false
                )
            )
        )

        repository.restoreEntities(payload)

        val restored = db.backupDao().getAllIndividuals().single()
        assertEquals("Asthma - inhaler on site", restored.medicalAlert)
        assertEquals(true, restored.isRestricted)
        assertEquals("asha@example.com", restored.email)
        assertEquals(false, restored.isActive)
    }

    /** percentile and ageAtTime were dropped, so restored results rendered grey and aged zero. */
    @Test
    fun `restoreEntities preserves percentile and ageAtTime on a result`() = runTest {
        repository.restoreEntities(resultPayload(percentile = 92, ageAtTime = 14.5f, standardizedScore = 92.0))

        val restored = db.backupDao().getAllTestResults().single()
        assertEquals(92, restored.percentile)
        assertEquals(14.5f, restored.ageAtTime, 0.001f)
        assertEquals("SUPERIOR", restored.classification)
    }

    /**
     * Recovery for backups already in Drive: they have standardizedScore (the write-only
     * copy of the percentile) but no percentile field, so reading it back restores the
     * zone colour retroactively.
     */
    @Test
    fun `restoreEntities recovers percentile from standardizedScore on a legacy payload`() = runTest {
        repository.restoreEntities(
            resultPayload(percentile = null, ageAtTime = null, standardizedScore = 71.0, classification = null)
        )

        val restored = db.backupDao().getAllTestResults().single()
        assertEquals(71, restored.percentile)
    }

    /**
     * The load-bearing compatibility test. Gson instantiates data classes through Unsafe,
     * bypassing the constructor, so a Kotlin default is NOT applied to a key missing from
     * the JSON -- a non-null field added to the payload would arrive as null and throw.
     *
     * This must deserialize real JSON. Building the DTO in Kotlin applies the defaults and
     * would test nothing.
     */
    @Test
    fun `restoreEntities accepts a legacy payload whose JSON omits the new fields`() = runTest {
        val legacyJson = """
            {
              "individuals": [
                {"id":"ath-1","firstName":"Old","lastName":"Backup","dateOfBirth":0,"gender":"MALE","notes":null}
              ],
              "groups": [{"id":"g1","name":"Team A","type":"TEAM","isActive":true}],
              "groupMembers": [],
              "testingEvents": [{"id":"ev-1","name":"Old Event","timestamp":1000,"notes":null}],
              "eventTests": [],
              "testResults": [],
              "users": []
            }
        """.trimIndent()

        val payload = Gson().fromJson(legacyJson, BackupPayload::class.java)
        repository.restoreEntities(payload)

        val individual = db.backupDao().getAllIndividuals().single()
        assertEquals("ath-1", individual.id)
        assertNull("a legacy payload has no medical alert to restore", individual.medicalAlert)
        assertEquals("absent isRestricted must fall back to false, not crash", false, individual.isRestricted)
        assertEquals("absent isActive must fall back to true", true, individual.isActive)
        // Legacy events genuinely have no groupId; they stay detached, which is correct.
        assertNull(db.backupDao().getAllTestingEvents().single().groupId)
    }

    /**
     * testing_events.groupId is ON DELETE SET NULL. An event pointing at a group that
     * isn't in the payload would be silently detached by the foreign key, reproducing the
     * original bug one restore later. Null it deliberately instead.
     */
    @Test
    fun `restoreEntities detaches an event whose group is missing from the payload`() = runTest {
        val payload = basePayload(
            groups = emptyList(),
            testingEvents = listOf(
                BackupTestingEvent(
                    id = "ev-1", name = "Orphan", timestamp = 1_000L,
                    notes = null, groupId = "group-that-is-not-here"
                )
            )
        )

        repository.restoreEntities(payload)

        assertNull(db.backupDao().getAllTestingEvents().single().groupId)
    }

    /** Group location and cycle were dropped, which breaks cycle-based roster filtering. */
    @Test
    fun `restoreEntities preserves group location and cycle`() = runTest {
        val payload = basePayload(
            groups = listOf(
                BackupGroup(
                    id = "g1", name = "Team A", type = "TEAM", isActive = true,
                    location = "Main Field", cycle = "Winter 2026"
                )
            )
        )

        repository.restoreEntities(payload)

        val restored = db.backupDao().getAllGroups().single()
        assertEquals("Main Field", restored.location)
        assertEquals("Winter 2026", restored.cycle)
    }

    /** A blank category (what `category ?: ""` writes) must land as null, not "". */
    @Test
    fun `restoreEntities stores a blank group category as null`() = runTest {
        repository.restoreEntities(
            basePayload(groups = listOf(BackupGroup(id = "g1", name = "Team A", type = "", isActive = true)))
        )

        assertNull(db.backupDao().getAllGroups().single().category)
    }

    // --- helpers -------------------------------------------------------------

    private fun basePayload(
        individuals: List<BackupIndividual> = emptyList(),
        groups: List<BackupGroup> = emptyList(),
        testingEvents: List<BackupTestingEvent> = emptyList(),
        testResults: List<BackupTestResult> = emptyList()
    ) = BackupPayload(
        individuals = individuals,
        groups = groups,
        groupMembers = emptyList(),
        testingEvents = testingEvents,
        eventTests = emptyList(),
        testResults = testResults,
        users = emptyList()
    )

    /** An athlete, a custom test to satisfy the RESTRICT foreign key, an event, and one result. */
    private fun resultPayload(
        percentile: Int?,
        ageAtTime: Float?,
        standardizedScore: Double?,
        classification: String? = "SUPERIOR"
    ) = BackupPayload(
        individuals = listOf(
            BackupIndividual("ath-1", "Res", "Athlete", 0L, BiologicalSex.MALE.name, null)
        ),
        groups = emptyList(),
        groupMembers = emptyList(),
        testingEvents = listOf(BackupTestingEvent("ev-1", "Event", 1_000L, null)),
        eventTests = emptyList(),
        testResults = listOf(
            BackupTestResult(
                id = "res-1",
                eventId = "ev-1",
                individualId = "ath-1",
                testId = "custom-test-1",
                rawScore = 12.5,
                standardizedScore = standardizedScore,
                timestamp = 1_000L,
                captureMethod = "MANUAL_ENTRY",
                notes = null,
                ageAtTime = ageAtTime,
                percentile = percentile,
                classification = classification
            )
        ),
        users = emptyList(),
        customCategories = listOf(BackupTestCategory("custom-cat-1", "Sport Specific", null, 99, null)),
        customTests = listOf(customTest()),
        customNorms = listOf(customNorm())
    )

}
