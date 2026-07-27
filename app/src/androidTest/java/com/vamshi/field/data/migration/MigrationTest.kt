package com.vamshi.field.data.migration

import androidx.room3.testing.MigrationTestHelper
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vamshi.field.data.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/**
 * Schema migration assertions, backed by the exported JSON in `app/schemas`.
 *
 * [MigrationTestHelper.runMigrationsAndValidate] compares the schema the migration
 * actually produced against the one Room generates for that version, so a drift between
 * a hand-written ALTER and the entity definition fails here rather than as an
 * IllegalStateException on a coach's device.
 *
 * `runBlocking` rather than `runTest`: these do real file I/O against a real SQLite
 * database, so virtual time buys nothing and would only add a dependency.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    // Unique per test method. JUnit builds a fresh instance of this class for each @Test,
    // so this gives every test its own database file. Sharing one file made the tests
    // order-dependent: whichever ran second inherited the other's already-migrated v13
    // database and createDatabase(12) had nothing to do.
    private val testDbName = "migration-test-${UUID.randomUUID()}.db"

    @get:Rule
    val helper = MigrationTestHelper(
        instrumentation = InstrumentationRegistry.getInstrumentation(),
        file = File(
            InstrumentationRegistry.getInstrumentation().targetContext.noBackupFilesDir,
            testDbName
        ),
        driver = AndroidSQLiteDriver(),
        databaseClass = AppDatabase::class
    )

    /**
     * 12 → 13 adds `source` to the three catalog tables.
     *
     * The important assertion is not that the column exists — it's that every pre-existing
     * row reads 'SEED'. Those rows were all written by the CSV seeder, and if they came out
     * as anything else the importer would stop deleting them and stale norms would pile up
     * on every reseed.
     */
    @Test
    fun migrate12To13_addsSourceColumn_andBackfillsExistingRowsAsSeed() = runBlocking {
        helper.createDatabase(12).use { db ->
            db.execSQL(
                """
                INSERT INTO test_categories (id, name, description, sortOrder, radarAxis, createdAt, updatedAt, isDeleted)
                VALUES ('cat_cardio', 'Cardiorespiratory Endurance', NULL, 1, 'ENDURANCE', 0, 0, 0)
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO fitness_tests (
                    id, categoryId, name, unit, isHigherBetter, description, timingMode,
                    inputParadigm, athletesPerHeat, trialsPerAthlete, validMin, validMax,
                    interpretationStrategy, calculationConfig, createdAt, updatedAt, isDeleted, youtubeId
                ) VALUES (
                    'test_pacer', 'cat_cardio', 'PACER (20m Shuttle Run)', 'laps', 1, NULL, 'MANUAL_ENTRY',
                    'NUMERIC', NULL, 1, 0.0, 250.0,
                    'NORM_LOOKUP', NULL, 0, 0, 0, NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO norm_references (
                    id, testId, variant, sex, ageMin, ageMax, minScore, maxScore,
                    percentile, classification, createdAt, updatedAt
                ) VALUES (
                    'norm_1', 'test_pacer', 'Default', 'MALE', 10.0, 10.99, 1.0, 14.0,
                    10, 'Needs Improvement', 0, 0
                )
                """.trimIndent()
            )
        }

        // Validates the post-migration schema against 13.json, then we check the backfill.
        helper.runMigrationsAndValidate(13, listOf(AppDatabase.MIGRATION_12_13)).use { db ->
            assertEquals("SEED", db.selectSingleText("SELECT source FROM test_categories WHERE id = 'cat_cardio'"))
            assertEquals("SEED", db.selectSingleText("SELECT source FROM fitness_tests WHERE id = 'test_pacer'"))
            assertEquals("SEED", db.selectSingleText("SELECT source FROM norm_references WHERE id = 'norm_1'"))
        }
    }

    /** An empty v12 database must still migrate cleanly — this is the fresh-install-then-update path. */
    @Test
    fun migrate12To13_onEmptyDatabase_validatesSchema() = runBlocking {
        helper.createDatabase(12).use { /* no rows */ }
        helper.runMigrationsAndValidate(13, listOf(AppDatabase.MIGRATION_12_13)).use { db ->
            assertEquals(0, db.selectSingleLong("SELECT COUNT(*) FROM fitness_tests").toInt())
        }
    }

    private fun SQLiteConnection.selectSingleText(sql: String): String? =
        prepare(sql).use { stmt -> if (stmt.step()) stmt.getText(0) else null }

    private fun SQLiteConnection.selectSingleLong(sql: String): Long =
        prepare(sql).use { stmt -> if (stmt.step()) stmt.getLong(0) else 0L }
}
