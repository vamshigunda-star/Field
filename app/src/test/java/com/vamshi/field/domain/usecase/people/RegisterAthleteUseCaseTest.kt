package com.vamshi.field.domain.usecase.people

import com.vamshi.field.domain.model.people.BiologicalSex
import com.vamshi.field.domain.usecase.testing.FakePeopleRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class RegisterAthleteUseCaseTest {

    private val useCase = RegisterAthleteUseCase(FakePeopleRepository())

    private fun utcMillis(year: Int, month: Int, day: Int): Long =
        Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(year, month - 1, day)
        }.timeInMillis

    @Test
    fun `birth date before 1970 is accepted`() = runTest {
        // Negative epoch millis. A `<= 0` guard used to reject the whole Older Adults bracket.
        val result = useCase("Ada", "Older", utcMillis(1955, 3, 10), BiologicalSex.FEMALE)
        assertTrue(result.isSuccess)
    }

    @Test
    fun `birth date in the future is rejected`() = runTest {
        val result = useCase("Tom", "Future", System.currentTimeMillis() + 86_400_000L, BiologicalSex.MALE)
        assertTrue(result.isFailure)
    }
}
