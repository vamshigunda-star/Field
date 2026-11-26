package com.vamshi.field.domain.usecase.testing

import com.vamshi.field.domain.model.people.BiologicalSex
import com.vamshi.field.domain.model.people.Group
import com.vamshi.field.domain.model.people.GroupCategory
import com.vamshi.field.domain.model.people.Individual
import com.vamshi.field.domain.model.standards.FitnessTest
import com.vamshi.field.domain.model.standards.InputParadigm
import com.vamshi.field.domain.model.standards.TimingMode
import com.vamshi.field.domain.model.testing.CaptureMethod
import com.vamshi.field.domain.model.testing.TestResult
import com.vamshi.field.domain.usecase.standards.FakeStandardsRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The event leaderboard after a multi-trial test. Every trial used to become an entry, and the
 * screen keys entries by athlete id, so the second trial crashed the list.
 */
class GetGroupLeaderboardUseCaseTest {

    private val testing = FakeTestingRepository()
    private val people = FakePeopleRepository()
    private val standards = FakeStandardsRepository()

    private val asha = athlete("a", "Asha")
    private val bilal = athlete("b", "Bilal")

    private fun sprint(higherIsBetter: Boolean = false) = FitnessTest(
        id = "test_sprint", categoryId = "cat_speed", name = "40m Sprint", unit = "seconds",
        isHigherBetter = higherIsBetter, timingMode = TimingMode.INDIVIDUAL,
        inputParadigm = InputParadigm.CHRONO, trialsPerAthlete = 3
    )

    @Test
    fun `event mode keeps one entry per athlete - their best trial`() = runBlocking {
        standards.givenTests(sprint())
        people.givenGroupWithAthletes(group(), listOf(asha, bilal))
        listOf(6.10, 5.80, 6.40).forEachIndexed { i, t -> save("a$i", asha.id, t, i.toLong()) }
        listOf(5.90, 6.00, 5.70).forEachIndexed { i, t -> save("b$i", bilal.id, t, i.toLong()) }

        val board = GetGroupLeaderboardUseCase(testing, people, standards)("evt", "test_sprint", "grp")!!

        assertEquals(listOf(bilal.id, asha.id), board.entries.map { it.individualId })
        assertEquals(listOf(5.70, 5.80), board.entries.map { it.rawScore })
    }

    @Test
    fun `higher-is-better takes the highest trial`() = runBlocking {
        standards.givenTests(sprint(higherIsBetter = true))
        people.givenGroupWithAthletes(group(), listOf(asha))
        listOf(40.0, 52.0, 47.0).forEachIndexed { i, s -> save("a$i", asha.id, s, i.toLong()) }

        val board = GetGroupLeaderboardUseCase(testing, people, standards)("evt", "test_sprint", "grp")!!

        assertEquals(listOf(52.0), board.entries.map { it.rawScore })
    }

    private suspend fun save(id: String, athleteId: String, score: Double, at: Long) =
        testing.saveResult(
            TestResult(
                id = id, eventId = "evt", individualId = athleteId, testId = "test_sprint",
                rawScore = score, ageAtTime = 15f, captureMethod = CaptureMethod.STOPWATCH, createdAt = at
            )
        )

    private fun group() = Group(id = "grp", name = "Squad", location = "Field", cycle = "2026", category = GroupCategory.TEAM)

    private fun athlete(id: String, name: String) = Individual(
        id = id, firstName = name, lastName = "Test",
        dateOfBirth = System.currentTimeMillis() - 15L * 31_557_600_000L, sex = BiologicalSex.FEMALE
    )
}
