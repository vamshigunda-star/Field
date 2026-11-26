package com.vamshi.field.domain.usecase.testing

import com.vamshi.field.domain.model.people.Individual
import com.vamshi.field.domain.model.standards.FitnessTest
import com.vamshi.field.domain.repository.StandardsRepository
import com.vamshi.field.domain.repository.TestingRepository
import javax.inject.Inject

data class StopwatchSession(
    val fitnessTest: FitnessTest,
    val athletes: List<Individual>,
    val heats: List<List<Individual>>,
    val trialsPerAthlete: Int,
    val trialCounts: Map<String, Int>
)

class StopwatchSessionUseCase @Inject constructor(
    private val testingRepository: TestingRepository,
    private val standardsRepository: StandardsRepository
) {
    suspend operator fun invoke(
        eventId: String,
        fitnessTestId: String,
        groupId: String
    ): StopwatchSession {
        val test = standardsRepository.getTestById(fitnessTestId)
            ?: throw IllegalArgumentException("Fitness test not found: $fitnessTestId")

        val athletes = testingRepository.getAthletesInGroupOrdered(groupId)

        val trialCounts = athletes.associate { athlete ->
            athlete.id to testingRepository.getTrialCountForAthlete(eventId, athlete.id, fitnessTestId)
        }

        // athletesPerHeat = 1 in the catalog means "timed one at a time" (5-10-5, sprints). As a Group
        // Heats size it split a 2-athlete group into two heats of one, so the second athlete looked
        // gone. Only a real multi-athlete heat size is honoured; anything else uses the default.
        val perHeat = test.athletesPerHeat?.takeIf { it > 1 } ?: DEFAULT_HEAT_SIZE
        val heats = if (athletes.isNotEmpty()) athletes.chunked(perHeat) else emptyList()

        return StopwatchSession(
            fitnessTest = test,
            athletes = athletes,
            heats = heats,
            trialsPerAthlete = test.trialsPerAthlete,
            trialCounts = trialCounts
        )
    }

    private companion object {
        const val DEFAULT_HEAT_SIZE = 6
    }
}
