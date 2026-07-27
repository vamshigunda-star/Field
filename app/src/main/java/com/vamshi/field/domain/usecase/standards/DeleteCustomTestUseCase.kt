package com.vamshi.field.domain.usecase.standards

import com.vamshi.field.domain.model.standards.TestSource
import com.vamshi.field.domain.repository.StandardsRepository
import javax.inject.Inject

/** What confirming a delete would do — drives the confirmation dialog's copy. */
sealed interface DeleteCustomTestPreview {
    data object WillDelete : DeleteCustomTestPreview
    data class WillArchive(val resultCount: Int) : DeleteCustomTestPreview
    data object NotAllowed : DeleteCustomTestPreview
}

sealed interface DeleteCustomTestResult {
    /** No results referenced the test, so the row (and its norms, via CASCADE) is gone. */
    data object Deleted : DeleteCustomTestResult

    /** Results exist, so the test was hidden instead of removed. */
    data class Archived(val resultCount: Int) : DeleteCustomTestResult

    /** Seeded tests and unknown ids are not removable. */
    data object NotAllowed : DeleteCustomTestResult
}

/**
 * Removes a coach-authored test, choosing between a real delete and an archive.
 *
 * `test_results.testId` is a RESTRICT foreign key, so hard-deleting a test that has results
 * would throw at the database. Rather than surface that to the coach as an error, a test
 * with history is archived: hidden from browsing but still resolvable by
 * [StandardsRepository.getTestById] so old session reports keep showing its name.
 */
class DeleteCustomTestUseCase @Inject constructor(
    private val repository: StandardsRepository
) {
    /**
     * Same decision logic as [invoke], without executing it. The confirmation dialog uses
     * this so its copy can say what will actually happen ("removes it permanently" vs.
     * "3 athletes have results — archiving keeps their history") instead of hedging.
     */
    suspend fun preview(testId: String): DeleteCustomTestPreview {
        val test = repository.getTestById(testId)
        if (test == null || test.source != TestSource.USER) {
            return DeleteCustomTestPreview.NotAllowed
        }
        val resultCount = repository.countResultsForTest(testId)
        return if (resultCount > 0) {
            DeleteCustomTestPreview.WillArchive(resultCount)
        } else {
            DeleteCustomTestPreview.WillDelete
        }
    }

    suspend operator fun invoke(testId: String): DeleteCustomTestResult {
        val test = repository.getTestById(testId)
        if (test == null || test.source != TestSource.USER) {
            return DeleteCustomTestResult.NotAllowed
        }

        val resultCount = repository.countResultsForTest(testId)
        return if (resultCount > 0) {
            repository.archiveCustomTest(testId)
            DeleteCustomTestResult.Archived(resultCount)
        } else {
            repository.deleteCustomTest(testId)
            DeleteCustomTestResult.Deleted
        }
    }
}
