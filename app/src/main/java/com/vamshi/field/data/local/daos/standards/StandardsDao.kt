package com.vamshi.field.data.local.daos.standards


import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert
import com.vamshi.field.data.local.entities.standards.FitnessTestEntity
import com.vamshi.field.data.local.entities.standards.NormReferenceEntity
import com.vamshi.field.data.local.entities.standards.TestCategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StandardsDao {

    // --- BROWSING TESTS ---
    // The browsing queries hide archived tests. Archiving is how a coach removes a custom
    // test that already has results attached — the row has to stay for the RESTRICT foreign
    // key from test_results, but it must not appear in the library or in event setup.
    @Query("SELECT * FROM fitness_tests WHERE isDeleted = 0 ORDER BY name ASC")
    fun getAllTests(): Flow<List<FitnessTestEntity>>

    // Also useful as a suspend version for one-time fetches:
    @Query("SELECT * FROM fitness_tests WHERE isDeleted = 0 ORDER BY name ASC")
    suspend fun getAllTestsOnce(): List<FitnessTestEntity>

    @Query("SELECT * FROM test_categories ORDER BY sortOrder ASC")
    fun getAllCategories(): Flow<List<TestCategoryEntity>>

    @Query("SELECT * FROM fitness_tests WHERE categoryId = :categoryId AND isDeleted = 0 ORDER BY name ASC")
    fun getTestsByCategory(categoryId: String): Flow<List<FitnessTestEntity>>

    // Deliberately NOT filtered by isDeleted: reports resolve historical test names through
    // this (see GetAthleteFlagsUseCase), and an archived test must still render as its name
    // rather than "Unknown Test" in a session report from before it was archived.
    @Query("SELECT * FROM fitness_tests WHERE id = :testId LIMIT 1")
    suspend fun getTestById(testId: String): FitnessTestEntity?

    // --- THE MAGIC LOOKUP (Interpretation) ---

    /**
     * Finds the exact norm row matching the student's age, sex, and score.
     * This provides the percentile and classification (e.g., "Excellent").
     *
     * `ORDER BY percentile DESC` makes the `LIMIT 1` deterministic. Both score bounds are
     * inclusive, so two bands sharing a cut point both match a score sitting exactly on it
     * — without an ORDER BY, SQLite picks arbitrarily. Coach-authored bands are contiguous
     * by construction (band N's max is band N+1's min, so no score falls into a hole),
     * which makes that boundary case routine; ties resolve in the athlete's favour.
     *
     * No-op for the seeded catalog: every adjacent pair in norms.csv is separated by a gap
     * (0–9, 10–17, …), so at most one row can match.
     */
    @Query("""
        SELECT * FROM norm_references
        WHERE testId = :testId
        AND sex = :sex
        AND :age BETWEEN ageMin AND ageMax
        AND :score >= MIN(minScore, maxScore)
        AND :score <= MAX(minScore, maxScore)
        ORDER BY percentile DESC
        LIMIT 1
    """)
    suspend fun findNormResult(
        testId: String,
        sex: String,
        age: Double, // Changed to Double to match entity type usually, or Float if you prefer
        score: Double
    ): NormReferenceEntity?

    @Query("""
        SELECT * FROM norm_references
        WHERE testId = :testId
        AND sex = :sex
        AND :age BETWEEN ageMin AND ageMax
        ORDER BY percentile ASC
    """)
    suspend fun getNormBandsForAthleteTest(
        testId: String,
        sex: String,
        age: Double
    ): List<NormReferenceEntity>

    @Query("""
        SELECT * FROM norm_references
        WHERE testId = :testId
        AND sex = :sex
        ORDER BY ABS(ageMin - :age) ASC, percentile ASC
    """)
    suspend fun getNormBandsClosestToAge(
        testId: String,
        sex: String,
        age: Double
    ): List<NormReferenceEntity>


    // --- DATA IMPORT (Admin/Setup) ---

    @Upsert
    suspend fun insertCategory(category: TestCategoryEntity)

    @Upsert
    suspend fun insertTest(test: FitnessTestEntity)

    // Flexible: Insert a whole list of norms at once (for Excel import)
    @Upsert
    suspend fun insertNorms(norms: List<NormReferenceEntity>)

    @Query("SELECT * FROM norm_references WHERE testId = :testId ORDER BY sex, ageMin, minScore")
    suspend fun getNormsForTest(testId: String): List<NormReferenceEntity>

    // --- COACH-AUTHORED TESTS (Custom Test Builder) ---
    //
    // Every mutating query below is guarded with `source = 'USER'`. That guard is the
    // reason this feature structurally cannot damage the seeded catalog, so keep it on
    // any query added here.

    /**
     * Case-insensitive duplicate-name check across non-archived tests.
     * Pass an empty [excludeId] when creating; pass the test's own id when editing.
     */
    @Query(
        """
        SELECT COUNT(*) FROM fitness_tests
        WHERE LOWER(TRIM(name)) = LOWER(TRIM(:name))
        AND id != :excludeId
        AND isDeleted = 0
        """
    )
    suspend fun countTestsNamed(name: String, excludeId: String): Int

    /** Drives the delete-vs-archive decision: a test with results cannot be hard-deleted. */
    @Query("SELECT COUNT(*) FROM test_results WHERE testId = :testId")
    suspend fun countResultsForTest(testId: String): Int

    @Query("UPDATE fitness_tests SET isDeleted = 1, updatedAt = :now WHERE id = :testId AND source = 'USER'")
    suspend fun archiveUserTest(testId: String, now: Long)

    /** Only safe when the test has no results — norm_references cascade, test_results RESTRICT. */
    @Query("DELETE FROM fitness_tests WHERE id = :testId AND source = 'USER'")
    suspend fun deleteUserTest(testId: String)

    @Query("DELETE FROM norm_references WHERE testId = :testId AND source = 'USER'")
    suspend fun deleteUserNormsForTest(testId: String)

    /** Norm rows have UUID keys, so an edit replaces the test's user norms rather than upserting. */
    @Transaction
    suspend fun replaceUserNormsForTest(testId: String, norms: List<NormReferenceEntity>) {
        deleteUserNormsForTest(testId)
        norms.chunked(500).forEach { chunk ->
            insertNorms(chunk)
        }
    }

    /**
     * Writes a coach-authored test and its scoring bands as one unit.
     *
     * Atomic on purpose: the norms have a CASCADE foreign key to the test, so inserting
     * them separately leaves a window where a half-saved test exists with stale bands. The
     * test must be inserted first for the same reason.
     */
    @Transaction
    suspend fun saveUserTestWithNorms(test: FitnessTestEntity, norms: List<NormReferenceEntity>) {
        insertTest(test)
        replaceUserNormsForTest(test.id, norms)
    }

    @Query("DELETE FROM norm_references WHERE source = 'SEED'")
    suspend fun deleteSeedNorms()

    // Norm rows have random UUID primary keys, so upsert can't deduplicate them
    // across reseeds — swap the seeded set atomically instead. Safe: no other
    // table references norm_references. fitness_tests/test_categories must NOT
    // get bulk deletes here; user results reference them.
    //
    // Scoped to source = 'SEED' on purpose. The unscoped version this replaced
    // wiped coach-authored norms on every seed-key bump. Do not reintroduce a
    // `DELETE FROM norm_references` without a source predicate.
    @Transaction
    suspend fun replaceSeedNorms(norms: List<NormReferenceEntity>) {
        deleteSeedNorms()
        norms.chunked(500).forEach { chunk ->
            insertNorms(chunk)
        }
    }
}
