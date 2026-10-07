package com.vamshi.field.domain.usecase.testing

import com.vamshi.field.domain.model.testing.CaptureMethod
import com.vamshi.field.domain.model.testing.TestResult
import com.vamshi.field.domain.model.testing.TestingEvent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscardEmptyEventUseCaseTest {

    private val repository = FakeTestingRepository()
    private val discard = DiscardEmptyEventUseCase(repository)
    private val event = TestingEvent(id = "evt_1", groupId = "grp_1", name = "Baseline", date = 0L)

    @Test
    fun `an event with no scores is deleted`() = runTest {
        repository.givenEvent(event, emptyList())

        assertTrue(discard(event.id))
        assertNull(repository.getEventById(event.id))
    }

    @Test
    fun `an event with a score is kept`() = runTest {
        repository.givenEvent(event, emptyList())
        repository.saveResult(
            TestResult(
                id = "res_1",
                eventId = event.id,
                individualId = "ath_1",
                testId = "test_1",
                rawScore = 10.0,
                ageAtTime = 15f,
                percentile = null,
                classification = null,
                normVariantUsed = null,
                captureMethod = CaptureMethod.MANUAL_ENTRY,
                createdAt = 0L
            )
        )

        assertFalse(discard(event.id))
        assertEquals(event, repository.getEventById(event.id))
        assertEquals(1, repository.getEventResults(event.id).first().size)
    }
}
