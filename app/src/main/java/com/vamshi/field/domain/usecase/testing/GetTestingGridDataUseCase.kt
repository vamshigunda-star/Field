package com.vamshi.field.domain.usecase.testing

import com.vamshi.field.domain.model.people.Individual
import com.vamshi.field.domain.model.standards.FitnessTest
import com.vamshi.field.domain.model.testing.TestResult
import com.vamshi.field.domain.repository.PeopleRepository
import com.vamshi.field.domain.repository.TestingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

data class TestingGridData(
    val students: List<Individual>,
    val tests: List<FitnessTest>,
    val results: List<TestResult>
) {
    /**
     * First result per (athlete id, test id). The grid reads one per row, and scanning
     * [results] for each row made every recomposition O(rows x results).
     */
    val resultByAthleteAndTest: Map<Pair<String, String>, TestResult> = buildMap {
        for (result in results) {
            val key = result.individualId to result.testId
            if (key !in this) put(key, result)
        }
    }
}

class GetTestingGridDataUseCase @Inject constructor(
    private val peopleRepository: PeopleRepository,
    private val testingRepository: TestingRepository
) {
    fun invoke(eventId: String, groupId: String): Flow<TestingGridData> {
        val studentsFlow = peopleRepository.getIndividualsInGroup(groupId)
        val testsFlow = testingRepository.getTestsForEvent(eventId)
        val resultsFlow = testingRepository.getEventResults(eventId)

        return combine(studentsFlow, testsFlow, resultsFlow) { students, tests, results ->
            TestingGridData(
                students = students,
                tests = tests,
                results = results
            )
        }
    }
}
