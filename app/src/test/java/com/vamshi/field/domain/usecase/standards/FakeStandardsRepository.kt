package com.vamshi.field.domain.usecase.standards

import com.vamshi.field.domain.model.people.BiologicalSex
import com.vamshi.field.domain.model.standards.FitnessTest
import com.vamshi.field.domain.model.standards.NormReference
import com.vamshi.field.domain.model.standards.TestCategory
import com.vamshi.field.domain.model.standards.TestSource
import com.vamshi.field.domain.repository.StandardsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory [StandardsRepository] test double.
 *
 * Mirrors the behaviours the custom-test use cases actually depend on — archived tests
 * disappear from browsing but stay resolvable by id, name matching is case- and
 * whitespace-insensitive, and only USER rows are mutable — without Room. Hand-written
 * rather than mocked, matching [com.vamshi.field.domain.usecase.auth.FakeAuthRepository]
 * (no mocking framework is on the test classpath).
 */
class FakeStandardsRepository : StandardsRepository {

    private val tests = MutableStateFlow<List<FitnessTest>>(emptyList())
    private val categories = MutableStateFlow<List<TestCategory>>(emptyList())
    private val archivedIds = mutableSetOf<String>()

    /** testId -> number of test_results rows referencing it. */
    val resultCounts = mutableMapOf<String, Int>()

    /** testId -> its stored norm rows. */
    val normsByTest = mutableMapOf<String, List<NormReference>>()

    fun givenTests(vararg test: FitnessTest) {
        tests.value = test.toList()
    }

    fun givenCategories(vararg category: TestCategory) {
        categories.value = category.toList()
    }

    fun isArchived(testId: String) = testId in archivedIds

    fun storedTest(testId: String): FitnessTest? = tests.value.find { it.id == testId }

    // --- Browsing (archived rows are hidden, matching the DAO's isDeleted = 0 filter) ---

    override fun getAllCategories(): Flow<List<TestCategory>> = categories

    override fun getTestsByCategory(categoryId: String): Flow<List<FitnessTest>> =
        tests.map { list -> list.filter { it.categoryId == categoryId && it.id !in archivedIds } }

    override fun getAllTests(): Flow<List<FitnessTest>> =
        tests.map { list -> list.filter { it.id !in archivedIds } }

    /** Deliberately unfiltered — reports resolve archived tests through this. */
    override suspend fun getTestById(testId: String): FitnessTest? =
        tests.value.find { it.id == testId }

    override suspend fun getNormResult(
        testId: String,
        sex: BiologicalSex,
        age: Double,
        score: Double
    ): NormReference? = null

    override suspend fun getNormBandsForAthleteTest(
        testId: String,
        sex: BiologicalSex,
        age: Double
    ): List<NormReference> = normsByTest[testId].orEmpty()

    override suspend fun importStandards(
        categories: List<TestCategory>,
        tests: List<FitnessTest>
    ) = Unit

    override suspend fun replaceSeedNorms(norms: List<NormReference>) = Unit

    // --- Custom tests ---

    override fun getCustomTests(): Flow<List<FitnessTest>> =
        tests.map { list -> list.filter { it.source == TestSource.USER && it.id !in archivedIds } }

    override suspend fun isTestNameTaken(name: String, excludeTestId: String?): Boolean =
        tests.value.any {
            it.id !in archivedIds &&
                it.id != excludeTestId &&
                it.name.trim().equals(name.trim(), ignoreCase = true)
        }

    override suspend fun saveCustomTest(test: FitnessTest, norms: List<NormReference>) {
        tests.value = tests.value.filterNot { it.id == test.id } + test
        // Mirrors replaceUserNormsForTest: an edit swaps the test's norms wholesale, so an
        // empty list is how "switch back to raw scores only" clears them.
        normsByTest[test.id] = norms
    }

    override suspend fun getNormsForTest(testId: String): List<NormReference> =
        normsByTest[testId].orEmpty()

    override suspend fun countResultsForTest(testId: String): Int = resultCounts[testId] ?: 0

    override suspend fun archiveCustomTest(testId: String) {
        archivedIds += testId
    }

    override suspend fun deleteCustomTest(testId: String) {
        tests.value = tests.value.filterNot { it.id == testId && it.source == TestSource.USER }
    }
}
