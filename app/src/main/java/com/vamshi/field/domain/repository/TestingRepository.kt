package com.vamshi.field.domain.repository

import com.vamshi.field.domain.model.people.Individual
import com.vamshi.field.domain.model.standards.FitnessTest
import com.vamshi.field.domain.model.testing.TestResult
import com.vamshi.field.domain.model.testing.TestingEvent
import kotlinx.coroutines.flow.Flow

interface TestingRepository {

    // --- Events ---
    fun getAllEvents(): Flow<List<TestingEvent>>

    fun getEventsForGroup(groupId: String): Flow<List<TestingEvent>>

    fun getEventFlow(id: String): Flow<TestingEvent?>

    suspend fun getEventById(eventId: String): TestingEvent?

    suspend fun deleteEventById(eventId: String)

    /**
     * Creates an event and automatically sets up the "Menu" of tests for it.
     * @param event The event details (name, date, etc)
     * @param testIds A list of FitnessTest IDs that will be performed at this event.
     */
    suspend fun createEvent(event: TestingEvent, testIds: List<String>)

    // --- Menu Retrieval ---
    fun getTestsForEvent(eventId: String): Flow<List<FitnessTest>>

    // --- Results ---
    suspend fun saveResult(result: TestResult)

    fun getHistoryForTest(individualId: String, testId: String): Flow<List<TestResult>>

    fun getEventResults(eventId: String): Flow<List<TestResult>>

    fun getAllResults(): Flow<List<TestResult>>

    fun getAllResultsForIndividual(individualId: String): Flow<List<TestResult>>

    suspend fun getLatestResultPerTestForIndividual(individualId: String): List<TestResult>

    fun getAllLatestResults(): Flow<List<TestResult>>

    suspend fun getAllLatestResultsOnce(): List<TestResult>

    // --- Stopwatch Support ---
    suspend fun getAthletesInGroupOrdered(groupId: String): List<Individual>
    suspend fun getTrialCountForAthlete(eventId: String, individualId: String, testId: String): Int
    suspend fun deleteResultById(resultId: String)
}
