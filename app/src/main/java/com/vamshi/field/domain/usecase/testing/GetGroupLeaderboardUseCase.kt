package com.vamshi.field.domain.usecase.testing

import com.vamshi.field.domain.model.standards.InputParadigm
import com.vamshi.field.domain.model.testing.TestResult
import com.vamshi.field.domain.repository.PeopleRepository
import com.vamshi.field.domain.repository.StandardsRepository
import com.vamshi.field.domain.repository.TestingRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

// --- Output Models ---

data class LeaderboardEntry(
    val rank: Int,
    val individualId: String,
    val athleteName: String,
    val rawScore: Double,
    val unit: String,
    val percentile: Int?,
    val classification: String?,
    val isImproved: Boolean?,        // null if no previous result exists
    val inputParadigm: InputParadigm = InputParadigm.NUMERIC
)

data class GroupLeaderboard(
    val eventId: String?, // Optional
    val testId: String,
    val testName: String,
    val isHigherBetter: Boolean,
    val entries: List<LeaderboardEntry>  // ordered rank 1 → last
)

// --- Use Case ---

class GetGroupLeaderboardUseCase @Inject constructor(
    private val testingRepository: TestingRepository,
    private val peopleRepository: PeopleRepository,
    private val standardsRepository: StandardsRepository
) {
    suspend operator fun invoke(
        eventId: String?,
        testId: String,
        groupId: String
    ): GroupLeaderboard? {

        // Step 1: Fetch test metadata
        val test = standardsRepository.getTestById(testId) ?: return null

        // Step 2: Fetch all individuals in this group
        val individuals = peopleRepository
            .getIndividualsInGroup(groupId)
            .first()

        val groupAthleteIds = individuals.map { it.id }.toSet()
        val nameMap = individuals.associate { it.id to it.fullName }

        // Step 3: Fetch results
        val results = if (eventId != null) {
            testingRepository
                .getEventResults(eventId)
                .first()
                .filter { it.testId == testId && it.individualId in groupAthleteIds }
        } else {
            // "All Time" mode: Get latest result per athlete for this test
            val allLatestResults = mutableListOf<TestResult>()
            for (individual in individuals) {
                val latest = testingRepository.getHistoryForTest(individual.id, testId).first()
                    .maxByOrNull { it.createdAt }
                if (latest != null) allLatestResults.add(latest)
            }
            allLatestResults
        }

        // Step 4: For each result, compute isImproved by comparing to previous attempt
        val entries = results.map { result ->
            // Fetch this athlete's full history on this test
            val history = testingRepository
                .getHistoryForTest(result.individualId, testId)
                .first()

            // Find the result immediately before this event's result
            val previousResult = history
                .filter { it.createdAt < result.createdAt }
                .maxByOrNull { it.createdAt }

            val isImproved = previousResult?.let {
                val delta = result.rawScore - it.rawScore
                if (test.isHigherBetter) delta > 0 else delta < 0
            }

            LeaderboardEntry(
                rank = 0,            // placeholder
                individualId = result.individualId,
                athleteName = nameMap[result.individualId] ?: "Unknown",
                rawScore = result.rawScore,
                unit = test.unit,
                percentile = result.percentile,
                classification = result.classification,
                isImproved = isImproved,
                inputParadigm = test.inputParadigm
            )
        }

        // Step 6: Sort by rawScore — direction depends on test type
        val sorted = if (test.isHigherBetter) {
            entries.sortedByDescending { it.rawScore }  // PACER: more laps = rank 1
        } else {
            entries.sortedBy { it.rawScore }             // mile run: less time = rank 1
        }

        // Step 7: Assign final rank by position (1-indexed)
        val ranked = sorted.mapIndexed { index, entry ->
            entry.copy(rank = index + 1)
        }

        return GroupLeaderboard(
            eventId = eventId,
            testId = testId,
            testName = test.name,
            isHigherBetter = test.isHigherBetter,
            entries = ranked
        )
    }
}

