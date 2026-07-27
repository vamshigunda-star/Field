package com.vamshi.field.domain.repository

import com.vamshi.field.domain.model.people.BiologicalSex
import com.vamshi.field.domain.model.standards.FitnessTest
import com.vamshi.field.domain.model.standards.NormReference
import com.vamshi.field.domain.model.standards.TestCategory
import kotlinx.coroutines.flow.Flow

interface StandardsRepository {

    // --- Browsing ---
    fun getAllCategories(): Flow<List<TestCategory>>

    fun getTestsByCategory(categoryId: String): Flow<List<FitnessTest>>

    fun getAllTests(): Flow<List<FitnessTest>>

    suspend fun getTestById(testId: String): FitnessTest?

    // --- The "Magic" Lookup ---
    suspend fun getNormResult(
        testId: String,
        sex: BiologicalSex,
        age: Double,
        score: Double
    ): NormReference?

    // --- Admin / Setup ---

    /** Upserts catalog rows in place; never deletes tests or categories. */
    suspend fun importStandards(
        categories: List<TestCategory>,
        tests: List<FitnessTest>
    )

    /**
     * Atomically swaps the *seeded* norm set for the given one.
     *
     * Scoped to [com.vamshi.field.domain.model.standards.TestSource.SEED]: coach-authored
     * norms are never touched by a catalog re-import.
     */
    suspend fun replaceSeedNorms(norms: List<NormReference>)

    // --- Coach-authored tests (Custom Test Builder) ---
    //
    // Every write below applies only to rows with source = USER; the seeded catalog is
    // not reachable through this interface.

    /** Non-archived tests the coach authored, newest catalog state, name-ordered. */
    fun getCustomTests(): Flow<List<FitnessTest>>

    /**
     * Case-insensitive duplicate check across non-archived tests.
     * [excludeTestId] is the test being edited, so it doesn't collide with itself.
     */
    suspend fun isTestNameTaken(name: String, excludeTestId: String?): Boolean

    /**
     * Inserts or updates a coach-authored test together with its scoring bands.
     *
     * The test row and its norms are written as one unit: norm rows have UUID keys and
     * cannot be upserted, so an edit replaces the test's existing USER norms wholesale.
     * Passing an empty [norms] clears them, which is how switching a test back to
     * raw-scores-only takes effect. Callers must set `source = USER`.
     */
    suspend fun saveCustomTest(test: FitnessTest, norms: List<NormReference>)

    /** Every norm row for a test, in (sex, age, score) order. Used to rebuild the edit form. */
    suspend fun getNormsForTest(testId: String): List<NormReference>

    /** How many results reference this test — decides hard delete vs archive. */
    suspend fun countResultsForTest(testId: String): Int

    /** Hides the test from browsing while keeping the row for existing results. */
    suspend fun archiveCustomTest(testId: String)

    /** Removes the test outright. Only valid when no results reference it. */
    suspend fun deleteCustomTest(testId: String)
}
