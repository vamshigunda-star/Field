package com.vamshi.field.data.local.daos.standards

import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vamshi.field.data.AppDatabase
import com.vamshi.field.data.local.entities.standards.FitnessTestEntity
import com.vamshi.field.data.local.entities.standards.NormReferenceEntity
import com.vamshi.field.data.local.entities.standards.TestCategoryEntity
import com.vamshi.field.domain.model.people.BiologicalSex
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression cover for the bug that made the custom-test feature unsafe to build:
 * re-importing the CSV catalog used to run an unscoped `DELETE FROM norm_references`,
 * so bumping `KEY_DATA_SEEDED` silently destroyed every norm a coach had authored.
 *
 * If someone reintroduces an unscoped delete, [replaceSeedNorms_deletesOnlySeededRows]
 * is the test that catches it.
 */
@RunWith(AndroidJUnit4::class)
class StandardsDaoSourceScopingTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: StandardsDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java
        ).setDriver(AndroidSQLiteDriver()).build()
        dao = db.standardsDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun replaceSeedNorms_deletesOnlySeededRows() = runBlocking {
        seedCatalogRow()

        dao.insertNorms(
            listOf(
                norm(id = "seeded_norm", percentile = 10, source = "SEED"),
                norm(id = "coach_norm", percentile = 90, source = "USER")
            )
        )

        // Simulate a seed-key bump that ships a different seeded norm set.
        dao.replaceSeedNorms(listOf(norm(id = "seeded_norm_v2", percentile = 20, source = "SEED")))

        val remaining = dao.getNormsForTest(TEST_ID).associateBy { it.id }
        assertEquals(
            "coach-authored norm must survive a catalog re-import",
            setOf("coach_norm", "seeded_norm_v2"),
            remaining.keys
        )
        assertEquals("USER", remaining.getValue("coach_norm").source)
        assertEquals(90, remaining.getValue("coach_norm").percentile)
    }

    @Test
    fun replaceSeedNorms_withEmptyList_stillKeepsUserNorms() = runBlocking {
        seedCatalogRow()
        dao.insertNorms(listOf(norm(id = "coach_norm", percentile = 75, source = "USER")))

        dao.replaceSeedNorms(emptyList())

        val remaining = dao.getNormsForTest(TEST_ID)
        assertEquals(1, remaining.size)
        assertEquals("coach_norm", remaining.single().id)
    }

    // --- Archive / delete. These assert the real SQL (the `source = 'USER'` guards and the
    // isDeleted filters), which a repository fake cannot cover. ---

    @Test
    fun archiveUserTest_hidesFromBrowsingButKeepsItResolvableById() = runBlocking {
        seedCatalogRow()
        dao.insertTest(customTest())

        dao.archiveUserTest("custom_1", now = 123L)

        assertEquals(
            "archived tests must not appear in the library or event setup",
            listOf(TEST_ID),
            dao.getAllTestsOnce().map { it.id }
        )
        assertNotNull(
            "reports resolve historical test names through getTestById",
            dao.getTestById("custom_1")
        )
        assertEquals(emptyList<String>(), dao.getTestsByCategory(CATEGORY_ID).first().map { it.id }
            .filter { it == "custom_1" })
    }

    @Test
    fun archiveUserTest_refusesToTouchASeededTest() = runBlocking {
        seedCatalogRow()

        dao.archiveUserTest(TEST_ID, now = 123L)

        assertEquals(listOf(TEST_ID), dao.getAllTestsOnce().map { it.id })
    }

    @Test
    fun deleteUserTest_removesTheTestAndCascadesItsNorms() = runBlocking {
        seedCatalogRow()
        dao.insertTest(customTest())
        dao.insertNorms(listOf(norm(id = "custom_norm", percentile = 80, source = "USER").copy(testId = "custom_1")))

        dao.deleteUserTest("custom_1")

        assertNull(dao.getTestById("custom_1"))
        assertEquals(
            "norm_references has a CASCADE FK to fitness_tests",
            emptyList<String>(),
            dao.getNormsForTest("custom_1").map { it.id }
        )
    }

    @Test
    fun deleteUserTest_refusesToTouchASeededTest() = runBlocking {
        seedCatalogRow()

        dao.deleteUserTest(TEST_ID)

        assertNotNull(dao.getTestById(TEST_ID))
    }

    @Test
    fun countTestsNamed_ignoresCaseAndWhitespaceAndSkipsArchivedRows() = runBlocking {
        seedCatalogRow()
        dao.insertTest(customTest())

        assertEquals(1, dao.countTestsNamed("  sled push 20m ", excludeId = ""))
        assertEquals(
            "a test must not collide with itself when being edited",
            0,
            dao.countTestsNamed("Sled Push 20m", excludeId = "custom_1")
        )

        dao.archiveUserTest("custom_1", now = 123L)
        assertEquals(
            "an archived name is free to reuse",
            0,
            dao.countTestsNamed("Sled Push 20m", excludeId = "")
        )
    }

    private fun customTest() = FitnessTestEntity(
        id = "custom_1",
        categoryId = CATEGORY_ID,
        name = "Sled Push 20m",
        unit = "sec",
        isHigherBetter = false,
        source = "USER"
    )

    private suspend fun seedCatalogRow() {
        dao.insertCategory(TestCategoryEntity(id = CATEGORY_ID, name = "Cardio", source = "SEED"))
        dao.insertTest(
            FitnessTestEntity(
                id = TEST_ID,
                categoryId = CATEGORY_ID,
                name = "PACER",
                unit = "laps",
                isHigherBetter = true,
                source = "SEED"
            )
        )
    }

    private fun norm(id: String, percentile: Int, source: String) = NormReferenceEntity(
        id = id,
        testId = TEST_ID,
        variant = "Default",
        sex = BiologicalSex.MALE,
        ageMin = 0f,
        ageMax = 99f,
        minScore = 0.0,
        maxScore = 100.0,
        percentile = percentile,
        source = source
    )

    private companion object {
        const val CATEGORY_ID = "cat_cardio"
        const val TEST_ID = "test_pacer"
    }
}
