package com.vamshi.field.data

import androidx.room3.ColumnTypeConverters
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.vamshi.field.data.local.Converters
import com.vamshi.field.data.local.daos.auth.UserDao
import com.vamshi.field.data.local.daos.backup.BackupDao
import com.vamshi.field.data.local.daos.people.PeopleDao
import com.vamshi.field.data.local.daos.standards.RecommendationDao
import com.vamshi.field.data.local.daos.standards.StandardsDao
import com.vamshi.field.data.local.daos.testing.PendingTestEntryDao
import com.vamshi.field.data.local.daos.testing.TestingDao
import com.vamshi.field.data.local.entities.auth.UserEntity
import com.vamshi.field.data.local.entities.people.GroupEntity
import com.vamshi.field.data.local.entities.people.GroupMemberCrossRef
import com.vamshi.field.data.local.entities.people.IndividualEntity
import com.vamshi.field.data.local.entities.standards.FitnessTestEntity
import com.vamshi.field.data.local.entities.standards.NormReferenceEntity
import com.vamshi.field.data.local.entities.standards.RecommendationCategoryEntity
import com.vamshi.field.data.local.entities.standards.RecommendationTestCrossRef
import com.vamshi.field.data.local.entities.standards.TestCategoryEntity
import com.vamshi.field.data.local.entities.testing.EventTestCrossRef
import com.vamshi.field.data.local.entities.testing.PendingTestEntryEntity
import com.vamshi.field.data.local.entities.testing.TestResultEntity
import com.vamshi.field.data.local.entities.testing.TestingEventEntity

@Database(
    entities = [
        IndividualEntity::class,
        GroupEntity::class,
        GroupMemberCrossRef::class,
        TestCategoryEntity::class,
        FitnessTestEntity::class,
        NormReferenceEntity::class,
        TestingEventEntity::class,
        TestResultEntity::class,
        EventTestCrossRef::class,
        UserEntity::class,
        PendingTestEntryEntity::class,
        RecommendationCategoryEntity::class,
        RecommendationTestCrossRef::class
    ],
    version = 13,
    exportSchema = true
)
@ColumnTypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun peopleDao(): PeopleDao
    abstract fun standardsDao(): StandardsDao
    abstract fun testingDao(): TestingDao
    abstract fun userDao(): UserDao
    abstract fun pendingTestEntryDao(): PendingTestEntryDao
    abstract fun backupDao(): BackupDao
    abstract fun recommendationDao(): RecommendationDao

    companion object {
        /**
         * Migration 12 → 13: Adds `source` to the three catalog tables.
         *
         * Before this, the CSV re-import ran an unscoped `DELETE FROM norm_references`
         * (see the old `StandardsDao.replaceAllNorms`), so bumping `KEY_DATA_SEEDED` in
         * [com.vamshi.field.data.seed.SeedDataManager] would silently destroy any norms a
         * coach authored in-app. `source` lets the importer delete only rows it owns.
         *
         * Every row that exists at migration time came from the seeder, so `DEFAULT 'SEED'`
         * *is* the backfill — no separate UPDATE pass is needed.
         *
         * The DEFAULT clauses are mandatory (SQLite cannot add a NOT NULL column to a
         * non-empty table without one) and follow [MIGRATION_5_6]. Room records no
         * `defaultValue` for these fields — a Kotlin default is invisible to it — and
         * TableInfo comparison treats a null schema-side default as "don't care", so the
         * clause does not break schema validation. `MigrationTest` asserts this.
         *
         * No index on `source`: fitness_tests has ~40 rows and norm_references ~72, so an
         * index would cost more on write than it saves on read. The absence is deliberate.
         */
        val MIGRATION_12_13 = object : androidx.room3.migration.Migration(12, 13) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE fitness_tests ADD COLUMN source TEXT NOT NULL DEFAULT 'SEED'")
                connection.execSQL("ALTER TABLE test_categories ADD COLUMN source TEXT NOT NULL DEFAULT 'SEED'")
                connection.execSQL("ALTER TABLE norm_references ADD COLUMN source TEXT NOT NULL DEFAULT 'SEED'")
            }
        }

        /**
         * Migration 11 → 12: Adds the nullable `description` column to `test_categories`.
         *
         * Backs category descriptions shown under the Test Library category selector.
         * Sourced from `test_categories.csv` via [com.vamshi.field.data.seed.SeedDataManager]
         * — this migration only adds the column; [MIGRATION_10_11] et al. show the seeder
         * upserts categories in place, so existing rows get their description populated on
         * the next reseed rather than requiring a data migration here.
         */
        val MIGRATION_11_12 = object : androidx.room3.migration.Migration(11, 12) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE test_categories ADD COLUMN description TEXT DEFAULT NULL")
            }
        }

        /**
         * Migration 10 → 11: Adds the nullable `email` column to `users`.
         *
         * Backs the redesigned onboarding flow (Coach Name + Password + optional Email).
         * Email is never a login field — it exists solely so a coach can later connect
         * Google Drive backup/restore without re-entering it. Nullable with no DEFAULT
         * clause, matching how [UserEntity.email] declares no `@ColumnInfo` default —
         * the migration-created schema must match what Room generates on a fresh install.
         *
         * Deliberately does NOT drop `securityQuestion`/`securityAnswerHash`/`securityAnswerSalt`
         * — those columns are already nullable and simply stop being written to by the new
         * onboarding path. Physically dropping them is out of scope here (planned separately).
         */
        val MIGRATION_10_11 = object : androidx.room3.migration.Migration(10, 11) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE users ADD COLUMN email TEXT DEFAULT NULL")
            }
        }

        /**
         * Migration 9 → 10: Adds recommendation_categories and recommendation_test_cross_ref.
         *
         * Backs the "Test Recommendations" feature — coaches pick a population category
         * (Primary School, Elderly, etc.) and get a pre-built test selection. Junction table
         * mirrors event_test_cross_ref's shape (composite PK, CASCADE FKs, index on the
         * non-leading FK column).
         */
        val MIGRATION_9_10 = object : androidx.room3.migration.Migration(9, 10) {
            override suspend fun migrate(connection: SQLiteConnection) {
                // No DEFAULT clauses: the entities declare no @ColumnInfo defaultValue, so the
                // migration-created schema must match what Room generates on a fresh install.
                connection.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `recommendation_categories` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `description` TEXT,
                        `icon` TEXT,
                        `scope` TEXT NOT NULL,
                        `sortOrder` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                connection.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `recommendation_test_cross_ref` (
                        `recommendationCategoryId` TEXT NOT NULL,
                        `testId` TEXT NOT NULL,
                        `sortOrder` INTEGER NOT NULL,
                        `required` INTEGER NOT NULL,
                        PRIMARY KEY(`recommendationCategoryId`, `testId`),
                        FOREIGN KEY(`recommendationCategoryId`) REFERENCES `recommendation_categories`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`testId`) REFERENCES `fitness_tests`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                connection.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_recommendation_test_cross_ref_testId` ON `recommendation_test_cross_ref` (`testId`)"
                )
            }
        }

        val MIGRATION_8_9 = object : androidx.room3.migration.Migration(8, 9) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE fitness_tests ADD COLUMN youtubeId TEXT DEFAULT NULL")
            }
        }

        /**
         * Migration 7 → 8: Adds the `pending_test_entries` table.
         *
         * Holds in-flight scores staged during a TestingGrid session before the coach taps
         * "Submit All". Source of truth for pending state — survives process death so a
         * coach mid-session can resume after a kill/force-quit without losing entries.
         *
         * Composite PK (eventId, individualId, testId) — natural key, enforces one pending
         * score per athlete-test-per-event. FK to testing_events with CASCADE so deleting
         * an event cleans up any orphaned pending state.
         */
        val MIGRATION_7_8 = object : androidx.room3.migration.Migration(7, 8) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `pending_test_entries` (
                        `eventId` TEXT NOT NULL,
                        `individualId` TEXT NOT NULL,
                        `testId` TEXT NOT NULL,
                        `rawScore` REAL NOT NULL,
                        `stagedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`eventId`, `individualId`, `testId`),
                        FOREIGN KEY(`eventId`) REFERENCES `testing_events`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                connection.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_pending_test_entries_eventId` ON `pending_test_entries` (`eventId`)"
                )
            }
        }

        /**
         * Migration 6 → 7: Adds the `users` table for coach authentication.
         *
         * Columns:
         *  - id            TEXT PK
         *  - firstName     TEXT NOT NULL
         *  - lastName      TEXT NOT NULL
         *  - username      TEXT NOT NULL (unique index enforces uniqueness)
         *  - passwordHash  BLOB NOT NULL (PBKDF2 256-bit derived key)
         *  - passwordSalt  BLOB NOT NULL (16-byte SecureRandom salt)
         *  - securityQuestion TEXT (nullable — not all accounts may have one)
         *  - securityAnswerHash BLOB (nullable)
         *  - securityAnswerSalt BLOB (nullable)
         *  - createdAt     INTEGER NOT NULL (epoch millis)
         */
        val MIGRATION_6_7 = object : androidx.room3.migration.Migration(6, 7) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `users` (
                        `id` TEXT NOT NULL,
                        `firstName` TEXT NOT NULL,
                        `lastName` TEXT NOT NULL,
                        `username` TEXT NOT NULL,
                        `passwordHash` BLOB NOT NULL,
                        `passwordSalt` BLOB NOT NULL,
                        `securityQuestion` TEXT,
                        `securityAnswerHash` BLOB,
                        `securityAnswerSalt` BLOB,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                connection.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_users_username` ON `users` (`username`)"
                )
            }
        }

        val MIGRATION_5_6 = object : androidx.room3.migration.Migration(5, 6) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE fitness_tests ADD COLUMN validMin REAL DEFAULT NULL")
                connection.execSQL("ALTER TABLE fitness_tests ADD COLUMN validMax REAL DEFAULT NULL")
                connection.execSQL("ALTER TABLE fitness_tests ADD COLUMN interpretationStrategy TEXT NOT NULL DEFAULT 'NORM_LOOKUP'")
                connection.execSQL("ALTER TABLE fitness_tests ADD COLUMN calculationConfig TEXT DEFAULT NULL")
                connection.execSQL("ALTER TABLE test_categories ADD COLUMN radarAxis TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_4_5 = object : androidx.room3.migration.Migration(4, 5) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE fitness_tests ADD COLUMN inputParadigm TEXT NOT NULL DEFAULT 'NUMERIC'")
            }
        }

        val MIGRATION_3_4 = object : androidx.room3.migration.Migration(3, 4) {
            override suspend fun migrate(connection: SQLiteConnection) {
                // Add stopwatch columns to fitness_tests
                connection.execSQL("ALTER TABLE fitness_tests ADD COLUMN timingMode TEXT NOT NULL DEFAULT 'MANUAL_ENTRY'")
                connection.execSQL("ALTER TABLE fitness_tests ADD COLUMN athletesPerHeat INTEGER DEFAULT NULL")
                connection.execSQL("ALTER TABLE fitness_tests ADD COLUMN trialsPerAthlete INTEGER NOT NULL DEFAULT 1")

                // Add capture method to test_results
                connection.execSQL("ALTER TABLE test_results ADD COLUMN captureMethod TEXT NOT NULL DEFAULT 'MANUAL_ENTRY'")
            }
        }
    }
}
