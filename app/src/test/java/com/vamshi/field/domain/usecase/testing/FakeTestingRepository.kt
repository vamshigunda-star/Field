package com.vamshi.field.domain.usecase.testing

import com.vamshi.field.domain.model.people.Individual
import com.vamshi.field.domain.model.standards.FitnessTest
import com.vamshi.field.domain.model.testing.TestResult
import com.vamshi.field.domain.model.testing.TestingEvent
import com.vamshi.field.domain.repository.TestingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory [TestingRepository] test double.
 *
 * Hand-written rather than mocked, matching [com.vamshi.field.domain.usecase.standards.FakeStandardsRepository]
 * (no mocking framework is on the test classpath). Results are held in a [MutableStateFlow] so
 * the grid's `combine(...)` actually re-emits after a save, which is what lets a test assert
 * that a score reached storage rather than only that no exception was thrown.
 */
class FakeTestingRepository : TestingRepository {

    private val events = MutableStateFlow<List<TestingEvent>>(emptyList())
    private val results = MutableStateFlow<List<TestResult>>(emptyList())

    /** eventId -> the ordered test "menu" for that event. */
    private val eventTests = MutableStateFlow<Map<String, List<FitnessTest>>>(emptyMap())

    /** groupId -> roster, in the order the stopwatch should queue them. */
    private val groupRosters = mutableMapOf<String, List<Individual>>()

    /** Set to make the next [saveResult] throw, to exercise the failure paths. */
    var saveFailure: Exception? = null

    fun givenEvent(event: TestingEvent, tests: List<FitnessTest>) {
        events.value = events.value.filterNot { it.id == event.id } + event
        eventTests.value = eventTests.value + (event.id to tests)
    }

    fun givenRoster(groupId: String, athletes: List<Individual>) {
        groupRosters[groupId] = athletes
    }

    fun storedResults(): List<TestResult> = results.value

    fun resultFor(individualId: String, testId: String): TestResult? =
        results.value.lastOrNull { it.individualId == individualId && it.testId == testId }

    // --- Events ---

    override fun getAllEvents(): Flow<List<TestingEvent>> = events

    override fun getEventsForGroup(groupId: String): Flow<List<TestingEvent>> =
        events.map { list -> list.filter { it.groupId == groupId } }

    override fun getEventFlow(id: String): Flow<TestingEvent?> =
        events.map { list -> list.find { it.id == id } }

    override suspend fun getEventById(eventId: String): TestingEvent? =
        events.value.find { it.id == eventId }

    override suspend fun deleteEventById(eventId: String) {
        events.value = events.value.filterNot { it.id == eventId }
        eventTests.value = eventTests.value - eventId
        results.value = results.value.filterNot { it.eventId == eventId }
    }

    override suspend fun createEvent(event: TestingEvent, testIds: List<String>) {
        events.value = events.value.filterNot { it.id == event.id } + event
        // The menu is resolved from ids by the real implementation's join; callers that need
        // the resolved tests use givenEvent instead.
        eventTests.value = eventTests.value + (event.id to eventTests.value[event.id].orEmpty())
    }

    // --- Menu Retrieval ---

    override fun getTestsForEvent(eventId: String): Flow<List<FitnessTest>> =
        eventTests.map { it[eventId].orEmpty() }

    // --- Results ---

    override suspend fun saveResult(result: TestResult) {
        saveFailure?.let { throw it }
        results.value = results.value + result
    }

    override fun getHistoryForTest(individualId: String, testId: String): Flow<List<TestResult>> =
        results.map { list -> list.filter { it.individualId == individualId && it.testId == testId } }

    override fun getEventResults(eventId: String): Flow<List<TestResult>> =
        results.map { list -> list.filter { it.eventId == eventId } }

    override fun getAllResults(): Flow<List<TestResult>> = results

    override fun getAllResultsForIndividual(individualId: String): Flow<List<TestResult>> =
        results.map { list -> list.filter { it.individualId == individualId } }

    override suspend fun getLatestResultPerTestForIndividual(individualId: String): List<TestResult> =
        results.value.filter { it.individualId == individualId }
            .groupBy { it.testId }
            .mapNotNull { (_, group) -> group.maxByOrNull { it.createdAt } }

    override fun getAllLatestResults(): Flow<List<TestResult>> =
        results.map { list ->
            list.groupBy { it.individualId to it.testId }
                .mapNotNull { (_, group) -> group.maxByOrNull { it.createdAt } }
        }

    override suspend fun getAllLatestResultsOnce(): List<TestResult> =
        results.value.groupBy { it.individualId to it.testId }
            .mapNotNull { (_, group) -> group.maxByOrNull { it.createdAt } }

    // --- Stopwatch Support ---

    override suspend fun getAthletesInGroupOrdered(groupId: String): List<Individual> =
        groupRosters[groupId].orEmpty()

    override suspend fun getTrialCountForAthlete(
        eventId: String,
        individualId: String,
        testId: String
    ): Int = results.value.count {
        it.eventId == eventId && it.individualId == individualId && it.testId == testId
    }

    override suspend fun deleteResultById(resultId: String) {
        results.value = results.value.filterNot { it.id == resultId }
    }
}
