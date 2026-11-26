package com.vamshi.field.domain.usecase.people

import com.vamshi.field.domain.repository.TestingRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * How many recorded results an athlete has. The delete-athlete dialog states this number,
 * because deleting an athlete cascades to every one of their results — they disappear from
 * past event reports and leaderboards too, not just from the roster.
 */
class CountAthleteResultsUseCase @Inject constructor(
    private val testingRepository: TestingRepository
) {
    suspend operator fun invoke(individualId: String): Int =
        testingRepository.getAllResultsForIndividual(individualId).first().size
}
