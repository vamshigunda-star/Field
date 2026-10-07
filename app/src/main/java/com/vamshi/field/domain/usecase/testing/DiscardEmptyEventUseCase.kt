package com.vamshi.field.domain.usecase.testing

import com.vamshi.field.domain.repository.TestingRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Deletes a testing event only if no score has been recorded against it.
 *
 * The event row is written the moment the coach taps Start, so backing straight out of the grid
 * would otherwise leave an empty event in Reports. The emptiness check is re-read here at click
 * time rather than trusted from the screen: a score saved from the stopwatch a moment earlier
 * must never be discarded by a stale dialog.
 *
 * @return `true` if the event was deleted; `false` if it has results and was kept.
 */
class DiscardEmptyEventUseCase @Inject constructor(
    private val repository: TestingRepository
) {
    suspend operator fun invoke(eventId: String): Boolean {
        if (repository.getEventResults(eventId).first().isNotEmpty()) return false
        repository.deleteEventById(eventId)
        return true
    }
}
