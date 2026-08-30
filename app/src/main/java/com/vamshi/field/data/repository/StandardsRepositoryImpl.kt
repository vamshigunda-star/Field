package com.vamshi.field.data.repository

import com.vamshi.field.data.local.daos.standards.StandardsDao
import com.vamshi.field.data.mapper.standards.toDomain
import com.vamshi.field.data.mapper.standards.toEntity
import com.vamshi.field.domain.model.people.BiologicalSex
import com.vamshi.field.domain.model.standards.FitnessTest
import com.vamshi.field.domain.model.standards.NormReference
import com.vamshi.field.domain.model.standards.TestCategory
import com.vamshi.field.domain.model.standards.TestSource
import com.vamshi.field.domain.repository.StandardsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class StandardsRepositoryImpl @Inject constructor(
    private val dao: StandardsDao
) : StandardsRepository {

    // --- BROWSING ---
    override fun getAllCategories(): Flow<List<TestCategory>> {
        return dao.getAllCategories().map { list -> list.map { it.toDomain() } }
    }

    override fun getTestsByCategory(categoryId: String): Flow<List<FitnessTest>> {
        return dao.getTestsByCategory(categoryId).map { list -> list.map { it.toDomain() } }
    }

    override fun getAllTests(): Flow<List<FitnessTest>> {
        return dao.getAllTests().map { list -> list.map { it.toDomain() } }
    }

    override suspend fun getTestById(testId: String): FitnessTest? {
        return dao.getTestById(testId)?.toDomain()
    }

    private val normBandsCache = java.util.concurrent.ConcurrentHashMap<Triple<String, BiologicalSex, Int>, List<NormReference>>()

    // --- INTERPRETATION ---
    override suspend fun getNormResult(
        testId: String,
        sex: BiologicalSex,
        age: Double,
        score: Double
    ): NormReference? {
        return dao.findNormResult(testId, sex.name, age, score)?.toDomain()
    }

    override suspend fun getNormBandsForAthleteTest(
        testId: String,
        sex: BiologicalSex,
        age: Double
    ): List<NormReference> {
        val ageInt = age.toInt()
        val key = Triple(testId, sex, ageInt)
        return normBandsCache.getOrPut(key) {
            val direct = dao.getNormBandsForAthleteTest(testId, sex.name, age).map { it.toDomain() }
            if (direct.isNotEmpty()) {
                direct
            } else {
                dao.getNormBandsClosestToAge(testId, sex.name, age).map { it.toDomain() }
            }
        }
    }


    // --- SETUP / IMPORT ---
    override suspend fun importStandards(
        categories: List<TestCategory>,
        tests: List<FitnessTest>
    ) {
        normBandsCache.clear()
        categories.forEach { dao.insertCategory(it.toEntity()) }
        tests.forEach { dao.insertTest(it.toEntity()) }
    }

    override suspend fun replaceSeedNorms(norms: List<NormReference>) {
        normBandsCache.clear()
        dao.replaceSeedNorms(norms.map { it.toEntity() })
    }

    // --- CUSTOM TESTS ---
    override fun getCustomTests(): Flow<List<FitnessTest>> {
        // getAllTests() already excludes archived rows, so this is just the source filter.
        return dao.getAllTests().map { list ->
            list.map { it.toDomain() }.filter { it.source == TestSource.USER }
        }
    }

    override suspend fun isTestNameTaken(name: String, excludeTestId: String?): Boolean {
        // The DAO compares against a non-null column, so "" is a safe stand-in for "exclude
        // nothing" — no row can have an empty primary key.
        return dao.countTestsNamed(name, excludeTestId.orEmpty()) > 0
    }

    override suspend fun saveCustomTest(test: FitnessTest, norms: List<NormReference>) {
        normBandsCache.clear()
        dao.saveUserTestWithNorms(
            test = test.toEntity(),
            norms = norms.map { it.toEntity() }
        )
    }

    override suspend fun getNormsForTest(testId: String): List<NormReference> {
        return dao.getNormsForTest(testId).map { it.toDomain() }
    }

    override suspend fun countResultsForTest(testId: String): Int {
        return dao.countResultsForTest(testId)
    }

    override suspend fun archiveCustomTest(testId: String) {
        normBandsCache.clear()
        dao.archiveUserTest(testId, System.currentTimeMillis())
    }

    override suspend fun deleteCustomTest(testId: String) {
        normBandsCache.clear()
        dao.deleteUserTest(testId)
    }
}
