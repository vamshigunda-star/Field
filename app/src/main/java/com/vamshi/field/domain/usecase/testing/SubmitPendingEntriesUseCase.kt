package com.vamshi.field.domain.usecase.testing

import com.vamshi.field.domain.logging.AppLogger
import com.vamshi.field.domain.logging.NoOpAppLogger
import com.vamshi.field.domain.repository.PendingTestEntryRepository
import com.vamshi.field.domain.repository.PeopleRepository
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Reads all pending entries for an event, persists each as a real TestResult via
 * RecordTestResultUseCase (which computes percentile snapshots), then clears the pending set.
 *
 * If recording any single entry fails the whole submit is aborted — already-persisted entries
 * are intentionally NOT rolled back (they are valid test results), and the corresponding pending
 * entries are cleared as they succeed so a retry only re-attempts the unprocessed remainder.
 *
 * Wrapped in NonCancellable so an in-flight flush survives the ViewModel being cleared
 * (e.g. coach navigates away while isSubmitting=true). Without this, a partial flush
 * could leave some rows in test_results and others stranded in pending_test_entries.
 */
class SubmitPendingEntriesUseCase @Inject constructor(
    private val pendingRepository: PendingTestEntryRepository,
    private val peopleRepository: PeopleRepository,
    private val recordTestResult: RecordTestResultUseCase,
    private val logger: AppLogger = NoOpAppLogger
) {
    suspend operator fun invoke(eventId: String): Result<Int> = withContext(NonCancellable) {
        try {
            val pending = pendingRepository.getPendingForEvent(eventId)
            var written = 0
            for (entry in pending) {
                val athlete = peopleRepository.getIndividualById(entry.individualId)
                if (athlete == null) {
                    // Pending row references an athlete that no longer exists. The
                    // pending entity has no FK to individuals (only to events), so this
                    // is reachable. Drop the orphan and warn — silently leaving it
                    // means the grid permanently shows a stale pending cell.
                    logger.warn(
                        "SubmitPending",
                        "Dropping orphan pending entry: individualId=${entry.individualId} testId=${entry.testId} (athlete not found)"
                    )
                    pendingRepository.delete(eventId, entry.individualId, entry.testId)
                    continue
                }
                val ageMillis = System.currentTimeMillis() - athlete.dateOfBirth
                val ageYears = (ageMillis / (365.25 * 24 * 60 * 60 * 1000)).toFloat()
                recordTestResult(
                    eventId = eventId,
                    individualId = entry.individualId,
                    testId = entry.testId,
                    rawScore = entry.rawScore,
                    ageAtTime = ageYears,
                    sex = athlete.sex
                )
                pendingRepository.delete(eventId, entry.individualId, entry.testId)
                written++
            }
            Result.success(written)
        } catch (e: Exception) {
            logger.error("SubmitPending", "flush FAILED eventId=$eventId", e)
            Result.failure(e)
        }
    }
}
