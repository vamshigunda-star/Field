package com.vamshi.field.data.repository

import com.vamshi.field.data.local.daos.testing.TestingDao
import com.vamshi.field.data.local.entities.testing.EventTestCrossRef
import com.vamshi.field.data.mapper.people.toDomain
import com.vamshi.field.data.mapper.standards.toDomain
import com.vamshi.field.data.mapper.testing.toDomain
import com.vamshi.field.data.mapper.testing.toEntity
import com.vamshi.field.domain.model.people.Individual
import com.vamshi.field.domain.model.standards.FitnessTest
import com.vamshi.field.domain.model.testing.TestResult
import com.vamshi.field.domain.model.testing.TestingEvent
import com.vamshi.field.domain.repository.TestingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class TestingRepositoryImpl @Inject constructor(
    private val dao: TestingDao
) : TestingRepository {

    // --- EVENTS ---
    override fun getAllEvents(): Flow<List<TestingEvent>> {
        return dao.getAllEvents().map { list -> list.map { it.toDomain() } }
    }

    override fun getEventsForGroup(groupId: String): Flow<List<TestingEvent>> {
        return dao.getEventsForGroup(groupId).map { list -> list.map { it.toDomain() } }
    }

    override fun getEventFlow(id: String): Flow<TestingEvent?> {
        return dao.getEventByIdFlow(id).map { it?.toDomain() }
    }

    override suspend fun getEventById(eventId: String): TestingEvent? {
        return dao.getEventById(eventId)?.toDomain()
    }

    override suspend fun deleteEventById(eventId: String) {
        dao.deleteEventById(eventId)
    }

    override suspend fun createEvent(event: TestingEvent, testIds: List<String>) {
        // 1. Save the event details
        dao.insertEvent(event.toEntity())

        // 2. Loop through the test IDs and link them to this event
        testIds.forEachIndexed { index, testId ->
            val crossRef = EventTestCrossRef(
                eventId = event.id,
                testId = testId,
                sortOrder = index
            )
            dao.addTestToEvent(crossRef)
        }
    }

    // --- MENU ---
    override fun getTestsForEvent(eventId: String): Flow<List<FitnessTest>> {
        return dao.getTestsForEvent(eventId).map { list -> list.map { it.toDomain() } }
    }

    // --- RESULTS ---
    override suspend fun saveResult(result: TestResult) {
        dao.insertResult(result.toEntity())
    }

    override fun getHistoryForTest(individualId: String, testId: String): Flow<List<TestResult>> {
        return dao.getHistoryForTest(individualId, testId).map { list -> list.map { it.toDomain() } }
    }

    override fun getEventResults(eventId: String): Flow<List<TestResult>> {
        return dao.getEventResults(eventId).map { list -> list.map { it.toDomain() } }
    }

    override fun getAllResults(): Flow<List<TestResult>> {
        return dao.getAllResults().map { list -> list.map { it.toDomain() } }
    }

    override fun getAllResultsForIndividual(individualId: String): Flow<List<TestResult>> {
        return dao.getAllResultsForIndividual(individualId).map { list -> list.map { it.toDomain() } }
    }

    override suspend fun getLatestResultPerTestForIndividual(individualId: String): List<TestResult> {
        return dao.getLatestResultPerTestForIndividual(individualId).map { it.toDomain() }
    }

    override fun getAllLatestResults(): Flow<List<TestResult>> {
        return dao.getAllLatestResults().map { list -> list.map { it.toDomain() } }
    }

    override suspend fun getAllLatestResultsOnce(): List<TestResult> {
        return dao.getAllLatestResultsOnce().map { it.toDomain() }
    }

    // --- STOPWATCH SUPPORT ---
    override suspend fun getAthletesInGroupOrdered(groupId: String): List<Individual> {
        return dao.getAthletesInGroupOrdered(groupId).map { it.toDomain() }
    }

    override suspend fun getTrialCountForAthlete(eventId: String, individualId: String, testId: String): Int {
        return dao.getTrialCountForAthlete(eventId, individualId, testId)
    }

    override suspend fun deleteResultById(resultId: String) {
        dao.deleteResultById(resultId)
    }
}
