package com.vamshi.field.data.repository

import com.vamshi.field.domain.model.people.BiologicalSex
import com.vamshi.field.domain.model.people.Group
import com.vamshi.field.domain.model.people.Individual
import com.vamshi.field.domain.model.standards.FitnessTest
import com.vamshi.field.domain.model.testing.TestResult
import com.vamshi.field.domain.model.testing.TestingEvent
import com.vamshi.field.domain.repository.PeopleRepository
import com.vamshi.field.domain.repository.ReportsRepository
import com.vamshi.field.domain.repository.StandardsRepository
import com.vamshi.field.domain.repository.TestingRepository
import com.vamshi.field.domain.model.reports.*
import com.vamshi.field.domain.usecase.reports.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton
import java.util.Calendar

// ───────────────────────────────────────────────────────────────────
// Repository
// ───────────────────────────────────────────────────────────────────

@Singleton
class ReportsRepositoryImpl @Inject constructor(
    private val people: PeopleRepository,
    private val testing: TestingRepository,
    private val standards: StandardsRepository,
    private val classifyPercentile: ClassifyPercentileUseCase,
    private val calculateAthleteSessionAvg: CalculateAthleteSessionAvgUseCase,
    private val calculateGroupDistribution: CalculateGroupDistributionUseCase,
    private val getAthleteFlags: GetAthleteFlagsUseCase
) : ReportsRepository {

    // ---------- Home ----------

    override fun observeHome(): Flow<ReportsHomeData> = combine(
        people.getAllGroups(),
        people.getAllIndividuals(),
        testing.getAllEvents(),
        testing.getAllResults()
    ) { groups, individuals, events, _ ->
        // Per-emission memoization: athletes sharing (sex, age) share one expectedTests computation.
        // Without this, each athlete triggers ~150 norm probes (50 tests × 3 score points).
        val expectedTestsCache = mutableMapOf<Pair<BiologicalSex, Int>, List<FitnessTest>>()
        suspend fun cachedExpected(athlete: Individual): List<FitnessTest> =
            expectedTestsCache.getOrPut(athlete.sex to athlete.currentAge) {
                expectedTestsForAthlete(athlete)
            }

        val groupCards = groups.map { g -> buildGroupCard(g) }
        val recent = events.take(15).map { e -> buildRecentSessionRow(e, groups) }

        // Engine flags from each group's latest session — keep BELOW_HEALTHY / REGRESSION / ABSENT,
        // but drop MISSING_DATA (session-scoped) and recompute it from full history below so
        // Insights stays in sync with the Athlete Profile.
        val flags = mutableListOf<AthleteFlag>()
        for (g in groups) {
            val members = people.getIndividualsInGroup(g.id).first()
            flags += computeGroupFlags(g, members, ::cachedExpected).filter { it.type != FlagType.MISSING_DATA }
        }

        // Full-history MISSING_DATA per athlete (matches Athlete Profile). Once any session
        // records the missing tests, outstanding becomes empty and the flag clears.
        val groupNameById = groups.associateBy({ it.id }, { it.name })
        val primaryGroupByAthlete = mutableMapOf<String, String>()
        for (g in groups) {
            val members = people.getIndividualsInGroup(g.id).first()
            for (m in members) primaryGroupByAthlete.putIfAbsent(m.id, g.id)
        }
        for (ind in individuals) {
            val outstanding = computeOutstandingTests(ind, ::cachedExpected)
            if (outstanding.isEmpty()) continue
            val gid = primaryGroupByAthlete[ind.id] ?: ""
            flags += AthleteFlag(
                individualId = ind.id,
                athleteName = ind.fullName,
                groupId = gid,
                groupName = groupNameById[gid] ?: "",
                type = FlagType.MISSING_DATA,
                message = "Missing ${outstanding.size} expected test${if (outstanding.size == 1) "" else "s"}: ${outstanding.joinToString { it.name }}",
                testIds = outstanding.map { it.id },
                testNames = outstanding.map { it.name }
            )
        }

        // de-duplicate flagged athletes
        val flaggedIds = flags.map { it.individualId }.toSet()
        val flagged = flaggedIds.size

        // Count "Healthy" — athletes whose latest-session avg pctile is in HEALTHY or SUPERIOR
        var healthy = 0
        for (ind in individuals) {
            val latest = testing.getLatestResultPerTestForIndividual(ind.id)
            val avg = calculateAthleteSessionAvg(latest) ?: continue
            val cls = classifyPercentile(avg)
            if (cls == Classification.HEALTHY || cls == Classification.SUPERIOR) healthy++
        }

        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        val monthStart = cal.timeInMillis
        val sessionsThisMonth = events.count { it.date >= monthStart }

        ReportsHomeData(
            totalAthletes = individuals.size,
            totalHealthy = healthy,
            totalFlagged = flagged,
            sessionsThisMonth = sessionsThisMonth,
            flags = flags.distinctBy { it.individualId to it.type },
            groups = groupCards,
            recentSessions = recent,
            allAthletes = individuals.map { it.id to it.fullName }
        )
    }

    // ---------- Group Overview ----------

    override fun observeGroupOverview(groupId: String): Flow<GroupOverviewData?> = combine(
        people.getGroupFlow(groupId),
        people.getIndividualsInGroup(groupId),
        testing.getEventsForGroup(groupId),
        testing.getAllResults(), // Re-triggers on any result change
        testing.getAllEvents(),
        standards.getAllTests()
    ) { args ->
        val group = args[0] as? Group
        @Suppress("UNCHECKED_CAST")
        val athletes = args[1] as List<Individual>
        @Suppress("UNCHECKED_CAST")
        val sessions = args[2] as List<TestingEvent>
        @Suppress("UNCHECKED_CAST")
        val allResultsInDb = args[3] as List<TestResult>
        @Suppress("UNCHECKED_CAST")
        val allEvents = args[4] as List<TestingEvent>
        @Suppress("UNCHECKED_CAST")
        val allTests = args[5] as List<FitnessTest>

        if (group == null) return@combine null

        val distinctAthletes = athletes.distinctBy { it.id }
        val athleteIds = distinctAthletes.map { it.id }.toSet()
        val distinctSessions = sessions.distinctBy { it.id }
        val sessionIds = distinctSessions.map { it.id }.toSet()
        val testMap = allTests.associateBy { it.id }
        val allEventsMap = allEvents.associateBy { it.id }

        android.util.Log.d("ReportsRepo", "observeGroupOverview: gid=$groupId athletes=${athleteIds.size} sessions=${sessionIds.size} allResults=${allResultsInDb.size}")

        // Distribution from latest results
        val athleteAvgs = distinctAthletes.map { ind ->
            // Use in-memory filter instead of DAO to avoid loop-latency and race conditions
            val latest = allResultsInDb
                .filter { it.individualId == ind.id }
                .groupBy { it.testId }
                .map { (_, list) -> list.maxBy { it.createdAt } }
            calculateAthleteSessionAvg(latest)
        }
        val distribution = calculateGroupDistribution(athleteAvgs)

        // Optimized Session Rows: Pre-filter results for this group's sessions once
        val resultsForTheseSessions = allResultsInDb.filter { it.eventId in sessionIds }
        
        android.util.Log.d("ReportsRepo", "resultsForTheseSessions: ${resultsForTheseSessions.size}")

        val sessionRows = distinctSessions.map { ev ->
            val results = resultsForTheseSessions.filter { it.eventId == ev.id }
            val testsWithResults = results.map { it.testId }.distinct()
            val tested = results.map { it.individualId }.distinct().size
            val flags = computeSessionFlagCount(group, distinctAthletes, ev, results)
            SessionRow(
                event = ev,
                testCount = testsWithResults.size,
                athletesTested = tested,
                totalAthletes = distinctAthletes.size,
                flagCount = flags
            )
        }

        // Per-test trend strips: gather all tests touched by this group's members across ALL sessions
        val relevantResults = allResultsInDb.filter { it.individualId in athleteIds }
        
        android.util.Log.d("ReportsRepo", "relevantResults for trends: ${relevantResults.size}")

        val byTest = relevantResults.groupBy { it.testId }
        val trends = byTest.mapNotNull { (testId, testResults) ->
            val test = testMap[testId] ?: return@mapNotNull null
            
            // Group by date (via session)
            val groupedByDate = testResults.groupBy { r -> 
                allEventsMap[r.eventId]?.date
            }
            
            val pts = groupedByDate.mapNotNull { (date, items) ->
                if (date == null) return@mapNotNull null
                val pctiles = items.mapNotNull { it.percentile }
                if (pctiles.isEmpty()) {
                    android.util.Log.w("ReportsRepo", "No percentiles for test $testId on date $date. resultCount=${items.size}")
                    null
                } else date to pctiles.average().toFloat()
            }.sortedBy { it.first }
            
            android.util.Log.d("ReportsRepo", "Trend for test ${test.name}: pts=${pts.size}")

            if (pts.isEmpty()) null else TestTrendStrip(test, pts)
        }.sortedBy { it.test.name }

        android.util.Log.d("ReportsRepo", "Total trends found: ${trends.size}")

        GroupOverviewData(
            group = group,
            athletes = distinctAthletes,
            distribution = distribution,
            sessions = sessionRows,
            trends = trends
        )
    }

    // ---------- Session Report ----------

    override fun observeSessionReport(groupId: String, sessionId: String): Flow<SessionReportData?> = combine(
        testing.getEventFlow(sessionId),
        people.getGroupFlow(groupId),
        people.getIndividualsInGroup(groupId),
        testing.getEventsForGroup(groupId),
        combine(
            testing.getTestsForEvent(sessionId),
            testing.getEventResults(sessionId),
            testing.getAllResults() // Re-triggers on any result change (useful for deltas)
        ) { tests, results, _ -> tests to results }
    ) { event, group, athletes, sessions, (tests, sessionResults) ->
        if (event == null || group == null) return@combine null

        val distinctAthletes = athletes.distinctBy { it.id }
        val distinctSessions = sessions.distinctBy { it.id }.sortedBy { it.date }
        val distinctTests = tests.distinctBy { it.id }

        // Deduplicate session results: latest per athlete/test
        val deduplicatedSessionResults = sessionResults
            .filter { r -> distinctAthletes.any { a -> a.id == r.individualId } }
            .groupBy { it.individualId to it.testId }
            .map { (_, results) -> results.maxBy { it.createdAt } }

        val groupFlagsByAthlete = computeGroupFlags(group, distinctAthletes).groupBy { it.individualId }

        // Build leaderboard per test with delta vs prior session for that athlete/test
        val leaderboardByTest = mutableMapOf<String, List<LeaderboardRow>>()
        val absentByTest = mutableMapOf<String, List<LeaderboardRow>>()
        val missingByTest = mutableMapOf<String, List<String>>()

        for (test in distinctTests) {
            val testResults = deduplicatedSessionResults.filter { it.testId == test.id }
            val testedIds = testResults.map { it.individualId }.toSet()

            val rows = testResults.map { r ->
                val prevPct = previousResultPercentile(r.individualId, test.id, r.createdAt)
                val curPct = r.percentile
                val delta = if (prevPct != null && curPct != null) curPct - prevPct else null
                LeaderboardRow(
                    rank = 0,
                    individualId = r.individualId,
                    athleteName = distinctAthletes.firstOrNull { it.id == r.individualId }?.fullName ?: "Unknown",
                    rawScore = r.rawScore,
                    unit = test.unit,
                    percentile = curPct,
                    classification = classifyPercentile(curPct),
                    classificationLabel = r.classification,
                    deltaPercentile = delta,
                    flagged = groupFlagsByAthlete.containsKey(r.individualId)
                )
            }
            val sorted = if (test.isHigherBetter)
                rows.sortedByDescending { it.rawScore ?: Double.NEGATIVE_INFINITY }
            else
                rows.sortedBy { it.rawScore ?: Double.POSITIVE_INFINITY }
            val ranked = sorted.mapIndexed { idx, row -> row.copy(rank = idx + 1) }
            leaderboardByTest[test.id] = ranked

            val absent = distinctAthletes.filter { it.id !in testedIds }.map { ind ->
                LeaderboardRow(
                    rank = 0,
                    individualId = ind.id,
                    athleteName = ind.fullName,
                    rawScore = null,
                    unit = test.unit,
                    percentile = null,
                    classification = Classification.NO_DATA,
                    classificationLabel = null,
                    deltaPercentile = null,
                    flagged = groupFlagsByAthlete.containsKey(ind.id),
                    absent = true
                )
            }
            absentByTest[test.id] = absent
            missingByTest[test.id] = absent.map { it.athleteName }
        }

        // Group trend per test: using historical results across all group sessions
        val trendByTest = mutableMapOf<String, List<Pair<Long, Float>>>()
        for (test in distinctTests) {
            val byEvent = mutableListOf<Pair<Long, Float>>()
            for (ev in distinctSessions) {
                // Get all results for this test in THIS specific event for members of THIS group
                // We use first() here because we are already inside a combine block that re-triggers on any result change.
                val eventResults = testing.getEventResults(ev.id).first()
                    .filter { it.testId == test.id && distinctAthletes.any { a -> a.id == it.individualId } }
                
                // Keep only latest attempt per athlete if they did multiple in one session
                val uniqueAthleteResults = eventResults.groupBy { it.individualId }
                    .map { (_, list) -> list.maxBy { it.createdAt } }

                val pctiles = uniqueAthleteResults.mapNotNull { it.percentile }
                if (pctiles.isNotEmpty()) {
                    byEvent += ev.date to pctiles.average().toFloat()
                }
            }
            trendByTest[test.id] = byEvent.sortedBy { it.first }
        }

        SessionReportData(
            event = event,
            group = group,
            groupSessions = distinctSessions.reversed(), // Latest first for switcher
            tests = distinctTests,
            leaderboardByTest = leaderboardByTest,
            absentByTest = absentByTest,
            missingByTest = missingByTest,
            groupTrendByTest = trendByTest,
            athletesTested = deduplicatedSessionResults.map { it.individualId }.distinct().size,
            totalAthletes = distinctAthletes.size
        )
    }

    // ---------- Athlete Dashboard ----------

    override fun observeAthleteDashboard(athleteId: String, contextSessionId: String?): Flow<AthleteDashboardData?> = combine(
        people.getIndividualFlow(athleteId),
        people.getGroupsForIndividual(athleteId),
        testing.getAllResultsForIndividual(athleteId),
        testing.getAllEvents() // To pick/name the context session
    ) { athlete, groups, allResults, allEvents ->
        if (athlete == null) return@combine null

        val ctxEvent = (contextSessionId?.let { cid -> allEvents.find { it.id == cid } })
            ?: allResults.maxByOrNull { it.createdAt }?.let { r -> allEvents.find { it.id == r.eventId } }

        // Deduplicate allResults: latest per (test, event)
        val deduplicatedAllResults = allResults.groupBy { it.testId to it.eventId }
            .map { (_, list) -> list.maxBy { it.createdAt } }

        val sessionResults = if (ctxEvent != null) deduplicatedAllResults.filter { it.eventId == ctxEvent.id } else emptyList()
        val sessionAvg = calculateAthleteSessionAvg(sessionResults)

        // Per-test tiles using LATEST result per test across history
        val resultsByTest = deduplicatedAllResults.groupBy { it.testId }
        val tiles = resultsByTest.mapNotNull { (testId, results) ->
            val test = standards.getTestById(testId) ?: return@mapNotNull null
            val sorted = results.sortedBy { it.createdAt }
            val latest = sorted.lastOrNull()
            val previous = if (sorted.size >= 2) sorted[sorted.size - 2] else null
            val latestPct = latest?.percentile
            val previousPct = previous?.percentile
            val deltaPct = if (latestPct != null && previousPct != null) latestPct - previousPct else null
            AthleteTestTile(
                test = test,
                latestResult = latest,
                classification = classifyPercentile(latest?.percentile),
                sparkline = normalizeForSparkline(sorted.map { it.rawScore }, test.isHigherBetter),
                rawSparkline = sorted.map { it.rawScore },
                deltaPercentile = deltaPct
            )
        }.sortedBy { it.test.name }

        // Outstanding tests = tests for athlete's age/sex with norms but no result yet (full history)
        val outstanding = computeOutstandingTests(athlete, ::cachedExpectedTests)

        // Flags scoped to this athlete's groups (latest session each), excluding MISSING_DATA —
        // we replace it with a history-aware version below so it clears once the athlete
        // completes the outstanding tests in any session.
        val flags = mutableListOf<AthleteFlag>()
        for (g in groups) {
            flags += computeSingleAthleteFlags(g, athlete)
                .filter { it.type != FlagType.MISSING_DATA }
        }

        if (outstanding.isNotEmpty()) {
            val primaryGroup = groups.firstOrNull()
            flags += AthleteFlag(
                individualId = athleteId,
                athleteName = athlete.fullName,
                groupId = primaryGroup?.id ?: "",
                groupName = primaryGroup?.name ?: "",
                type = FlagType.MISSING_DATA,
                message = "Missing ${outstanding.size} expected test${if (outstanding.size == 1) "" else "s"}: ${outstanding.joinToString { it.name }}",
                testIds = outstanding.map { it.id },
                testNames = outstanding.map { it.name }
            )
        }

        AthleteDashboardData(
            athlete = athlete,
            groups = groups,
            contextSession = ctxEvent,
            athleteSessionAvgPctile = sessionAvg,
            sessionTestCount = sessionResults.size,
            tiles = tiles,
            flags = flags.distinctBy { it.type to it.message },
            outstandingTests = outstanding
        )
    }

    // ---------- Athlete × Test detail ----------

    override fun observeAthleteTestDetail(
        athleteId: String,
        testId: String,
        contextSessionId: String?
    ): Flow<AthleteTestDetailData?> = combine(
        people.getIndividualFlow(athleteId),
        testing.getHistoryForTest(athleteId, testId),
        testing.getAllEvents()
    ) { athlete, history, allEvents ->
        val test = standards.getTestById(testId) ?: return@combine null
        if (athlete == null) return@combine null

        val sortedHistory = history.sortedBy { it.createdAt }

        // Build attempts
        val attempts = sortedHistory.mapIndexed { idx, r ->
            val prev = if (idx == 0) null else sortedHistory[idx - 1]
            val deltaRaw = prev?.let { r.rawScore - it.rawScore }
            val curPct = r.percentile
            val prevPct = prev?.percentile
            val deltaPct = if (curPct != null && prevPct != null) curPct - prevPct else null
            val ev = allEvents.find { it.id == r.eventId }
            AttemptRow(
                resultId = r.id,
                sessionId = r.eventId,
                sessionName = ev?.name ?: "Session",
                date = r.createdAt,
                rawScore = r.rawScore,
                percentile = curPct,
                classification = classifyPercentile(curPct),
                classificationLabel = r.classification,
                deltaRaw = deltaRaw,
                deltaPercentile = deltaPct
            )
        }

        // Norm bands: for each attempt compute the raw score that would be p70 (superior) and p35 (healthy)
        val bands = sortedHistory.map { r ->
            val ageYears = r.ageAtTime
            NormBandsForAge(
                date = r.createdAt,
                ageYears = ageYears,
                superiorMin = approxRawAtPercentile(testId, athlete.sex, ageYears.toDouble(), 70, test.isHigherBetter),
                healthyMin = approxRawAtPercentile(testId, athlete.sex, ageYears.toDouble(), 35, test.isHigherBetter),
                needsMax = approxRawAtPercentile(testId, athlete.sex, ageYears.toDouble(), 34, test.isHigherBetter)
            )
        }

        // Peer leaderboard for context session
        val ctxEvent = contextSessionId?.let { cid -> allEvents.find { it.id == cid } }
            ?: sortedHistory.lastOrNull()?.let { r -> allEvents.find { it.id == r.eventId } }
        val peer = ctxEvent?.let { ev ->
            ev.groupId?.let { gid -> buildPeerLeaderboard(ev.id, gid, test) }
        }

        AthleteTestDetailData(
            athlete = athlete,
            test = test,
            attempts = attempts,
            bandsByDate = bands,
            peerLeaderboard = peer,
            contextSession = ctxEvent
        )
    }

    // ───────────────────────── Helpers ─────────────────────────

    private suspend fun buildGroupCard(g: Group): GroupCardData {
        val athletes = people.getIndividualsInGroup(g.id).first()
        val athleteAvgs = athletes.map { ind ->
            val latest = testing.getLatestResultPerTestForIndividual(ind.id)
            calculateAthleteSessionAvg(latest)
        }
        val distribution = calculateGroupDistribution(athleteAvgs)
        val lastSession = testing.getEventsForGroup(g.id).first().firstOrNull()?.date
        return GroupCardData(g, athletes.size, distribution, lastSession)
    }

    private suspend fun buildRecentSessionRow(ev: TestingEvent, allGroups: List<Group>): RecentSessionRow {
        val tests = testing.getTestsForEvent(ev.id).first()
        val results = testing.getEventResults(ev.id).first()
        val groupName = ev.groupId?.let { gid -> allGroups.firstOrNull { it.id == gid }?.name }
        return RecentSessionRow(
            event = ev,
            groupId = ev.groupId,
            groupName = groupName,
            testCount = tests.size,
            athleteTestedCount = results.map { it.individualId }.distinct().size
        )
    }

    private val expectedTestsCache = java.util.concurrent.ConcurrentHashMap<Pair<BiologicalSex, Int>, List<FitnessTest>>()

    private suspend fun cachedExpectedTests(athlete: Individual): List<FitnessTest> =
        expectedTestsCache.getOrPut(athlete.sex to athlete.currentAge) {
            expectedTestsForAthlete(athlete)
        }

    private suspend fun computeSingleAthleteFlags(
        group: Group,
        athlete: Individual
    ): List<AthleteFlag> {
        val sessions = testing.getEventsForGroup(group.id).first()
        val latest = sessions.firstOrNull() ?: return emptyList()
        val latestResults = testing.getEventResults(latest.id).first()
            .filter { it.individualId == athlete.id }
            .groupBy { it.testId }
            .map { (_, list) -> list.maxBy { it.createdAt } }

        if (latestResults.isEmpty()) {
            return listOf(
                AthleteFlag(
                    individualId = athlete.id,
                    athleteName = athlete.fullName,
                    groupId = group.id,
                    groupName = group.name,
                    type = FlagType.ABSENT,
                    message = "Did not test in latest session"
                )
            )
        }

        val prevMap = mutableMapOf<Pair<String, String>, TestResult>()
        for (r in latestResults) {
            val hist = testing.getHistoryForTest(athlete.id, r.testId).first()
            val prev = hist.filter { it.createdAt < latest.date }.maxByOrNull { it.createdAt } ?: continue
            prevMap[athlete.id to r.testId] = prev
        }

        return getAthleteFlags(
            groupId = group.id,
            groupName = group.name,
            latestSessionResults = latestResults,
            previousSessionResultsByAthleteAndTest = prevMap,
            athletesInGroup = listOf(athlete.id to athlete.fullName),
            expectedTestsByAthlete = emptyMap()
        )
    }

    private suspend fun computeGroupFlags(
        group: Group,
        athletes: List<Individual>,
        expectedLookup: (suspend (Individual) -> List<FitnessTest>)? = null
    ): List<AthleteFlag> {
        if (athletes.isEmpty()) return emptyList()
        val sessions = testing.getEventsForGroup(group.id).first()
        val latest = sessions.firstOrNull() ?: return emptyList()
        // Dedupe to latest attempt per (athlete, test). Retakes insert new rows; without this
        // an older failing attempt fires REGRESSION/BELOW_HEALTHY against the previous session
        // even after a successful retake supersedes it.
        val latestResults = testing.getEventResults(latest.id).first()
            .filter { r -> athletes.any { a -> a.id == r.individualId } }
            .groupBy { it.individualId to it.testId }
            .map { (_, list) -> list.maxBy { it.createdAt } }

        // previous result per (athlete, test) before latest event
        val prevMap = mutableMapOf<Pair<String, String>, TestResult>()
        for (a in athletes) {
            for (test in testing.getTestsForEvent(latest.id).first()) {
                val hist = testing.getHistoryForTest(a.id, test.id).first()
                val prev = hist.filter { it.createdAt < latest.date }.maxByOrNull { it.createdAt } ?: continue
                prevMap[a.id to test.id] = prev
            }
        }

        val expected = athletes.associate { a ->
            val tests = expectedLookup?.invoke(a) ?: expectedTestsForAthlete(a)
            a.id to tests.map { it.id }.toSet()
        }

        return getAthleteFlags(
            groupId = group.id,
            groupName = group.name,
            latestSessionResults = latestResults,
            previousSessionResultsByAthleteAndTest = prevMap,
            athletesInGroup = athletes.map { it.id to it.fullName },
            expectedTestsByAthlete = expected
        )
    }

    private suspend fun computeSessionFlagCount(
        group: Group,
        athletes: List<Individual>,
        event: TestingEvent,
        eventResults: List<TestResult>
    ): Int {
        val tests = testing.getTestsForEvent(event.id).first()
        // Dedupe to latest attempt per (athlete, test) — see note in computeGroupFlags.
        val deduplicatedResults = eventResults
            .groupBy { it.individualId to it.testId }
            .map { (_, list) -> list.maxBy { it.createdAt } }
        val prev = mutableMapOf<Pair<String, String>, TestResult>()
        for (a in athletes) {
            for (test in tests) {
                val hist = testing.getHistoryForTest(a.id, test.id).first()
                val p = hist.filter { it.createdAt < event.date }.maxByOrNull { it.createdAt } ?: continue
                prev[a.id to test.id] = p
            }
        }
        val expected = athletes.associate { a ->
            a.id to expectedTestsForAthlete(a).map { it.id }.toSet()
        }
        return getAthleteFlags(
            groupId = group.id,
            groupName = group.name,
            latestSessionResults = deduplicatedResults,
            previousSessionResultsByAthleteAndTest = prev,
            athletesInGroup = athletes.map { it.id to it.fullName },
            expectedTestsByAthlete = expected
        ).distinctBy { it.individualId to it.type }.size
    }

    private suspend fun previousResultPercentile(
        individualId: String,
        testId: String,
        beforeCreatedAt: Long
    ): Int? {
        val hist = testing.getHistoryForTest(individualId, testId).first()
        return hist.filter { it.createdAt < beforeCreatedAt }.maxByOrNull { it.createdAt }?.percentile
    }

    /**
     * Tests the athlete is "expected" to have completed (a norm exists for their age + sex)
     * but has no result for in their entire history. Used by Athlete Profile and Insights
     * "Needs Attention" so MISSING_DATA flags reflect the athlete's true current state and
     * clear once any session records the missing tests.
     */
    private suspend fun computeOutstandingTests(
        athlete: Individual,
        expectedLookup: (suspend (Individual) -> List<FitnessTest>)? = null
    ): List<FitnessTest> {
        val allResults = testing.getAllResultsForIndividual(athlete.id).first()
        val takenIds = allResults.map { it.testId }.toSet()
        val expected = expectedLookup?.invoke(athlete) ?: expectedTestsForAthlete(athlete)
        return expected.filter { it.id !in takenIds }
    }

    private suspend fun expectedTestsForAthlete(athlete: Individual): List<FitnessTest> {
        // A test is "expected" if at least one norm row exists matching the athlete's age + sex
        val ageYears = athlete.currentAge.toDouble()
        val all = standards.getAllTests().first()
        val out = mutableListOf<FitnessTest>()
        for (t in all) {
            // probe with a midpoint score; if any norm exists for the bracket the test is expected
            val anyNorm = standards.getNormResult(t.id, athlete.sex, ageYears, Double.MIN_VALUE)
                ?: standards.getNormResult(t.id, athlete.sex, ageYears, 0.0)
                ?: standards.getNormResult(t.id, athlete.sex, ageYears, Double.MAX_VALUE)
            if (anyNorm != null) out += t
        }
        return out
    }

    private suspend fun approxRawAtPercentile(
        testId: String,
        sex: BiologicalSex,
        ageYears: Double,
        percentile: Int,
        @Suppress("UNUSED_PARAMETER") isHigherBetter: Boolean
    ): Double? {
        // We can't sweep the score space from the existing repo, so probe a few candidate raw scores
        // and pick the one whose returned percentile is closest to the requested target.
        // This is an approximation but stable across norm shapes.
        val probes = listOf(0.0, 1.0, 2.0, 5.0, 10.0, 15.0, 20.0, 30.0, 45.0, 60.0, 90.0, 120.0, 200.0, 500.0)
        var bestScore: Double? = null
        var bestDiff = Int.MAX_VALUE
        for (p in probes) {
            val n = standards.getNormResult(testId, sex, ageYears, p) ?: continue
            val diff = kotlin.math.abs(n.percentile - percentile)
            if (diff < bestDiff) {
                bestDiff = diff
                bestScore = p
            }
        }
        return bestScore
    }

    private suspend fun buildPeerLeaderboard(
        sessionId: String,
        groupId: String,
        test: FitnessTest
    ): List<LeaderboardRow> {
        val athletes = people.getIndividualsInGroup(groupId).first()
        val results = testing.getEventResults(sessionId).first().filter { it.testId == test.id }
        val rows = results.map { r ->
            LeaderboardRow(
                rank = 0,
                individualId = r.individualId,
                athleteName = athletes.firstOrNull { it.id == r.individualId }?.fullName ?: "Unknown",
                rawScore = r.rawScore,
                unit = test.unit,
                percentile = r.percentile,
                classification = classifyPercentile(r.percentile),
                classificationLabel = r.classification,
                deltaPercentile = null,
                flagged = false
            )
        }
        val sorted = if (test.isHigherBetter)
            rows.sortedByDescending { it.rawScore ?: Double.NEGATIVE_INFINITY }
        else
            rows.sortedBy { it.rawScore ?: Double.POSITIVE_INFINITY }
        return sorted.mapIndexed { idx, r -> r.copy(rank = idx + 1) }
    }

    private fun normalizeForSparkline(scores: List<Double>, isHigherBetter: Boolean): List<Float> {
        if (scores.isEmpty()) return emptyList()
        if (scores.size == 1) return listOf(0.5f)
        val min = scores.min()
        val max = scores.max()
        if (max == min) return scores.map { 0.5f }
        return scores.map { s ->
            val n = ((s - min) / (max - min)).toFloat()
            if (isHigherBetter) n else 1f - n
        }
    }

}
