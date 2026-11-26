package com.vamshi.field.ui.session

import androidx.lifecycle.SavedStateHandle
import com.vamshi.field.domain.model.people.Group
import com.vamshi.field.domain.model.people.GroupCategory
import com.vamshi.field.domain.model.reports.AthleteDashboardData
import com.vamshi.field.domain.model.reports.AthleteTestDetailData
import com.vamshi.field.domain.model.reports.GroupOverviewData
import com.vamshi.field.domain.model.reports.ReportsHomeData
import com.vamshi.field.domain.model.reports.SessionReportData
import com.vamshi.field.domain.model.standards.FitnessTest
import com.vamshi.field.domain.model.testing.TestingEvent
import com.vamshi.field.domain.repository.ReportsRepository
import com.vamshi.field.domain.usecase.reports.ObserveSessionReportUseCase
import com.vamshi.field.domain.usecase.standards.FakeStandardsRepository
import com.vamshi.field.domain.usecase.testing.FakePeopleRepository
import com.vamshi.field.domain.usecase.testing.FakeTestingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Switching session on the standalone Session Report. Spec: 2026-11-26-gemini-app-audit-triage (N1).
 *
 * The previous session's collector used to keep running, so its next emission (any result write)
 * put the old session back on screen; and the old session's selected test carried over.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionReportSwitchTest {

    private val dispatcher = StandardTestDispatcher()

    private val group = Group(id = "grp", name = "Squad", location = null, cycle = null, category = GroupCategory.TEAM)
    private fun test(id: String) = FitnessTest(id = id, categoryId = "cat", name = id, unit = "cm", isHigherBetter = true)
    private fun report(sessionId: String, testId: String, tested: Int = 0) = SessionReportData(
        event = TestingEvent(id = sessionId, groupId = "grp", name = sessionId, date = 0L),
        group = group,
        groupSessions = emptyList(),
        tests = listOf(test(testId)),
        leaderboardByTest = emptyMap(),
        absentByTest = emptyMap(),
        missingByTest = emptyMap(),
        groupTrendByTest = emptyMap(),
        athletesTested = tested,
        totalAthletes = 0
    )

    private val sessions = mapOf(
        "A" to MutableStateFlow<SessionReportData?>(report("A", "test_jump")),
        "B" to MutableStateFlow<SessionReportData?>(report("B", "test_sprint"))
    )

    private val reports = object : ReportsRepository {
        override fun observeSessionReport(groupId: String, sessionId: String): Flow<SessionReportData?> =
            sessions.getValue(sessionId)
        override fun observeHome(): Flow<ReportsHomeData> = emptyFlow()
        override fun observeGroupOverview(groupId: String): Flow<GroupOverviewData?> = emptyFlow()
        override fun observeAthleteDashboard(athleteId: String, contextSessionId: String?): Flow<AthleteDashboardData?> = emptyFlow()
        override fun observeAthleteTestDetail(athleteId: String, testId: String, contextSessionId: String?): Flow<AthleteTestDetailData?> = emptyFlow()
    }

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun switchingSessionDropsOldCollectorAndStaleTest() = runTest(dispatcher) {
        val vm = SessionReportViewModel(
            savedStateHandle = SavedStateHandle(mapOf("groupId" to "grp", "sessionId" to "A")),
            observeSessionReport = ObserveSessionReportUseCase(reports),
            testingRepository = FakeTestingRepository(),
            peopleRepository = FakePeopleRepository(),
            standardsRepository = FakeStandardsRepository()
        )
        testScheduler.runCurrent()
        assertEquals("test_jump", vm.uiState.value.selectedTestId)

        vm.onAction(SessionReportAction.OnSwitchSession("B"))
        testScheduler.runCurrent()
        assertEquals("B", vm.uiState.value.data?.event?.id)
        assertEquals("test_sprint", vm.uiState.value.selectedTestId)

        // A result lands in session A (Room re-emits every flow that reads test_results).
        sessions.getValue("A").value = report("A", "test_jump", tested = 1)
        testScheduler.runCurrent()
        assertEquals("B", vm.uiState.value.data?.event?.id)
    }
}
