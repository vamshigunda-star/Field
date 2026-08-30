package com.vamshi.field.data.repository

import com.vamshi.field.domain.model.people.BiologicalSex
import com.vamshi.field.domain.model.people.Group
import com.vamshi.field.domain.model.people.Individual
import com.vamshi.field.domain.model.standards.FitnessTest
import com.vamshi.field.domain.model.standards.NormReference
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
import java.util.concurrent.ConcurrentHashMap

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

    private val expectedTestsCache = ConcurrentHashMap<Pair<BiologicalSex, Int>, List<FitnessTest>>()

    private suspend fun cachedExpectedTests(athlete: Individual): List<FitnessTest> =
        expectedTestsCache.getOrPut(athlete.sex to athlete.currentAge) {
            expectedTestsForAthlete(athlete)
        }

    // ---------- Home ----------

    override fun observeHome(): Flow<ReportsHomeData> = combine(
        people.getAllGroups(),
        people.getAllIndividuals(),
        testing.getAllEvents(),
        testing.getAllLatestResults(),
        testing.getAllResults()
    ) { groups, individuals, events, allLatestResults, allResults ->
        val groupMap = groups.associateBy { it.id }
        val latestByAthlete = allLatestResults.groupBy { it.individualId }
        val allResultsByAthlete = allResults.groupBy { it.individualId }
        val eventsByGroup = events.groupBy { it.groupId }

        // 1. Group Cards
        val groupCards = groups.map { g ->
            val members = people.getIndividualsInGroup(g.id).first()
            val athleteAvgs = members.map { ind ->
                val latest = latestByAthlete[ind.id].orEmpty()
                calculateAthleteSessionAvg(latest)
            }
            val distribution = calculateGroupDistribution(athleteAvgs)
            val lastSession = eventsByGroup[g.id]?.firstOrNull()?.date
            GroupCardData(g, members.size, distribution, lastSession)
        }

        // 2. Recent Sessions (in-memory aggregation)
        val recent = events.take(15).map { e ->
            val results = allResults.filter { it.eventId == e.id }
            val testCount = results.map { it.testId }.distinct().size
            RecentSessionRow(
                event = e,
                groupId = e.groupId,
                groupName = e.groupId?.let { groupMap[it]?.name },
                testCount = testCount,
                athleteTestedCount = results.map { it.individualId }.distinct().size
            )
        }

        // 3. Flags from groups' latest sessions
        val flags = mutableListOf<AthleteFlag>()
        val primaryGroupByAthlete = mutableMapOf<String, String>()
        val athleteGroupMap = mutableMapOf<String, Set<String>>()
        for (g in groups) {
            val members = people.getIndividualsInGroup(g.id).first()
            for (m in members) {
                primaryGroupByAthlete.putIfAbsent(m.id, g.id)
                athleteGroupMap[m.id] = (athleteGroupMap[m.id] ?: emptySet()) + g.id
            }
            val latestSession = eventsByGroup[g.id]?.firstOrNull()
            if (latestSession != null && members.isNotEmpty()) {
                flags += computeGroupFlagsInMemory(g, members, latestSession, allResults).filter { it.type != FlagType.MISSING_DATA }
            }
        }

        // 4. Group-scoped MISSING_DATA per athlete (only tests conducted by athlete's groups)
        val allTestsMap = standards.getAllTests().first().associateBy { it.id }

        for (ind in individuals) {
            val gids = athleteGroupMap[ind.id].orEmpty()
            if (gids.isEmpty()) continue
            val groupEventIds = events.filter { it.groupId != null && it.groupId in gids }.map { it.id }.toSet()
            if (groupEventIds.isEmpty()) continue
            val groupTestIds = allResults.filter { it.eventId in groupEventIds }.map { it.testId }.toSet()
            if (groupTestIds.isEmpty()) continue

            val takenIds = allResultsByAthlete[ind.id].orEmpty().map { it.testId }.toSet()
            val missedIds = groupTestIds - takenIds
            if (missedIds.isEmpty()) continue

            val gid = primaryGroupByAthlete[ind.id] ?: gids.first()
            val missedTests = missedIds.mapNotNull { allTestsMap[it] }
            if (missedTests.isEmpty()) continue

            flags += AthleteFlag(
                individualId = ind.id,
                athleteName = ind.fullName,
                groupId = gid,
                groupName = groupMap[gid]?.name ?: "",
                type = FlagType.MISSING_DATA,
                message = "Missing ${missedTests.size} expected test${if (missedTests.size == 1) "" else "s"}: ${missedTests.joinToString { it.name }}",
                testIds = missedTests.map { it.id },
                testNames = missedTests.map { it.name }
            )
        }

        val flaggedIds = flags.map { it.individualId }.toSet()
        val flagged = flaggedIds.size

        // 5. Healthy Count
        var healthy = 0
        for (ind in individuals) {
            val latest = latestByAthlete[ind.id].orEmpty()
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
        testing.getAllResults(),
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

        // Distribution from latest results
        val athleteAvgs = distinctAthletes.map { ind ->
            val latest = allResultsInDb
                .filter { it.individualId == ind.id }
                .groupBy { it.testId }
                .map { (_, list) -> list.maxBy { it.createdAt } }
            calculateAthleteSessionAvg(latest)
        }
        val distribution = calculateGroupDistribution(athleteAvgs)

        val resultsForTheseSessions = allResultsInDb.filter { it.eventId in sessionIds }

        val sessionRows = distinctSessions.map { ev ->
            val results = resultsForTheseSessions.filter { it.eventId == ev.id }
            val testsWithResults = results.map { it.testId }.distinct()
            val tested = results.map { it.individualId }.distinct().size
            val flags = computeSessionFlagCountInMemory(group, distinctAthletes, ev, results, allResultsInDb)
            SessionRow(
                event = ev,
                testCount = testsWithResults.size,
                athletesTested = tested,
                totalAthletes = distinctAthletes.size,
                flagCount = flags
            )
        }

        // Per-test trend strips
        val relevantResults = allResultsInDb.filter { it.individualId in athleteIds }
        val byTest = relevantResults.groupBy { it.testId }
        val trends = byTest.mapNotNull { (testId, testResults) ->
            val test = testMap[testId] ?: return@mapNotNull null
            val groupedByDate = testResults.groupBy { r -> allEventsMap[r.eventId]?.date }
            val pts = groupedByDate.mapNotNull { (date, items) ->
                if (date == null) return@mapNotNull null
                val pctiles = items.mapNotNull { it.percentile }
                if (pctiles.isEmpty()) null else date to pctiles.average().toFloat()
            }.sortedBy { it.first }

            if (pts.isEmpty()) null else TestTrendStrip(test, pts)
        }.sortedBy { it.test.name }

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
            testing.getAllResults()
        ) { tests, results, allResults -> Triple(tests, results, allResults) }
    ) { event, group, athletes, sessions, (tests, sessionResults, allResults) ->
        if (event == null || group == null) return@combine null

        val distinctAthletes = athletes.distinctBy { it.id }
        val distinctSessions = sessions.distinctBy { it.id }.sortedBy { it.date }
        val distinctTests = tests.distinctBy { it.id }

        // Deduplicate session results: latest per athlete/test
        val deduplicatedSessionResults = sessionResults
            .filter { r -> distinctAthletes.any { a -> a.id == r.individualId } }
            .groupBy { it.individualId to it.testId }
            .map { (_, results) -> results.maxBy { it.createdAt } }

        val groupFlagsByAthlete = computeGroupFlagsInMemory(group, distinctAthletes, event, allResults).groupBy { it.individualId }

        // Build leaderboard per test with delta vs prior session for that athlete/test
        val athleteNameById = distinctAthletes.associateBy({ it.id }, { it.fullName })
        val leaderboardByTest = mutableMapOf<String, List<LeaderboardRow>>()
        val absentByTest = mutableMapOf<String, List<LeaderboardRow>>()
        val missingByTest = mutableMapOf<String, List<String>>()

        for (test in distinctTests) {
            val rows = mutableListOf<LeaderboardRow>()
            val testedIds = mutableSetOf<String>()

            val testResults = deduplicatedSessionResults.filter { it.testId == test.id }
            for (r in testResults) {
                testedIds += r.individualId
                val curPct = r.percentile
                val prevPct = allResults
                    .filter { it.individualId == r.individualId && it.testId == test.id && it.createdAt < event.date }
                    .maxByOrNull { it.createdAt }?.percentile
                val delta = if (curPct != null && prevPct != null) curPct - prevPct else null
                rows += LeaderboardRow(
                    rank = 0,
                    individualId = r.individualId,
                    athleteName = athleteNameById[r.individualId] ?: "Unknown",
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
                val eventResults = allResults
                    .filter { it.eventId == ev.id && it.testId == test.id && distinctAthletes.any { a -> a.id == it.individualId } }
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
            groupSessions = distinctSessions.reversed(),
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
        testing.getAllEvents(),
        standards.getAllTests()
    ) { athlete, groups, allResults, allEvents, allTests ->
        if (athlete == null) return@combine null

        val testMap = allTests.associateBy { it.id }
        val ctxEvent = (contextSessionId?.let { cid -> allEvents.find { it.id == cid } })
            ?: allResults.maxByOrNull { it.createdAt }?.let { r -> allEvents.find { it.id == r.eventId } }

        val deduplicatedAllResults = allResults.groupBy { it.testId to it.eventId }
            .map { (_, list) -> list.maxBy { it.createdAt } }

        val sessionResults = if (ctxEvent != null) deduplicatedAllResults.filter { it.eventId == ctxEvent.id } else emptyList()
        val sessionAvg = calculateAthleteSessionAvg(sessionResults)

        val resultsByTest = deduplicatedAllResults.groupBy { it.testId }
        val tiles = resultsByTest.mapNotNull { (testId, results) ->
            val test = testMap[testId] ?: return@mapNotNull null
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

        val athleteGroupIds = groups.map { it.id }.toSet()
        val athleteGroupEvents = allEvents.filter { it.groupId != null && it.groupId in athleteGroupIds }
        val athleteGroupEventIds = athleteGroupEvents.map { it.id }.toSet()

        // Only tests that were actually performed in the athlete's group testing sessions
        val testsConductedInGroupEvents = if (athleteGroupEventIds.isNotEmpty()) {
            allResults
                .filter { it.eventId in athleteGroupEventIds }
                .map { it.testId }
                .toSet()
        } else {
            emptySet()
        }

        val takenIds = allResults.filter { it.individualId == athleteId }.map { it.testId }.toSet()
        // An athlete only has outstanding tests if their group performed them and this athlete missed them
        val outstanding = if (testsConductedInGroupEvents.isNotEmpty()) {
            val missedIds = testsConductedInGroupEvents - takenIds
            allTests.filter { it.id in missedIds }
        } else {
            emptyList()
        }

        val flags = mutableListOf<AthleteFlag>()
        for (g in groups) {
            val latestSession = allEvents
                .filter { it.groupId == g.id }
                .maxByOrNull { it.date }
            if (latestSession != null) {
                flags += computeSingleAthleteFlagsInMemory(g, athlete, latestSession, allResults)
                    .filter { it.type != FlagType.MISSING_DATA }
            }
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
        testing.getAllEvents(),
        standards.getAllTests()
    ) { athlete, history, allEvents, allTests ->
        val test = allTests.find { it.id == testId } ?: return@combine null
        if (athlete == null) return@combine null

        val sortedHistory = history.sortedBy { it.createdAt }

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

        // Norm bands: in-memory evaluation using cached norm bands (0 probe sweeps!)
        val bands = try {
            sortedHistory.map { r ->
                val ageYears = if (r.ageAtTime > 0f) r.ageAtTime else athlete.currentAge.toFloat()
                val normBands = try {
                    standards.getNormBandsForAthleteTest(testId, athlete.sex, ageYears.toDouble())
                } catch (_: Exception) {
                    emptyList()
                }
                NormBandsForAge(
                    date = r.createdAt,
                    ageYears = ageYears,
                    superiorMin = findScoreForPercentile(normBands, 70, test.isHigherBetter),
                    healthyMin = findScoreForPercentile(normBands, 35, test.isHigherBetter),
                    needsMax = findScoreForPercentile(normBands, 34, test.isHigherBetter)
                )
            }
        } catch (_: Exception) {
            emptyList()
        }

        val ctxEvent = contextSessionId?.let { cid -> allEvents.find { it.id == cid } }
            ?: sortedHistory.lastOrNull()?.let { r -> allEvents.find { it.id == r.eventId } }
        val peer = try {
            ctxEvent?.let { ev ->
                ev.groupId?.let { gid -> buildPeerLeaderboard(ev.id, gid, test) }
            }
        } catch (_: Exception) {
            null
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

    private fun findScoreForPercentile(
        normBands: List<NormReference>,
        targetPercentile: Int,
        isHigherBetter: Boolean
    ): Double? {
        if (normBands.isEmpty()) return null
        val bestBand = normBands.minByOrNull { kotlin.math.abs(it.percentile - targetPercentile) } ?: return null
        return if (isHigherBetter) bestBand.minScore else bestBand.maxScore
    }

    private suspend fun expectedTestsForAthlete(athlete: Individual): List<FitnessTest> {
        val ageYears = athlete.currentAge.toDouble()
        val all = standards.getAllTests().first()
        val out = mutableListOf<FitnessTest>()
        for (t in all) {
            val normBands = standards.getNormBandsForAthleteTest(t.id, athlete.sex, ageYears)
            if (normBands.isNotEmpty()) out += t
        }
        return out
    }

    private suspend fun computeSingleAthleteFlagsInMemory(
        group: Group,
        athlete: Individual,
        latestSession: TestingEvent,
        allResults: List<TestResult>
    ): List<AthleteFlag> {
        val latestResults = allResults
            .filter { it.eventId == latestSession.id && it.individualId == athlete.id }
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
            val prev = allResults
                .filter { it.individualId == athlete.id && it.testId == r.testId && it.createdAt < latestSession.date }
                .maxByOrNull { it.createdAt } ?: continue
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

    private suspend fun computeGroupFlagsInMemory(
        group: Group,
        athletes: List<Individual>,
        latestSession: TestingEvent,
        allResults: List<TestResult>
    ): List<AthleteFlag> {
        if (athletes.isEmpty()) return emptyList()
        val athleteIds = athletes.map { it.id }.toSet()
        val latestResults = allResults
            .filter { it.eventId == latestSession.id && it.individualId in athleteIds }
            .groupBy { it.individualId to it.testId }
            .map { (_, list) -> list.maxBy { it.createdAt } }

        val prevMap = mutableMapOf<Pair<String, String>, TestResult>()
        val distinctTestsInEvent = latestResults.map { it.testId }.distinct()
        for (a in athletes) {
            for (testId in distinctTestsInEvent) {
                val prev = allResults
                    .filter { it.individualId == a.id && it.testId == testId && it.createdAt < latestSession.date }
                    .maxByOrNull { it.createdAt } ?: continue
                prevMap[a.id to testId] = prev
            }
        }

        val expected = athletes.associate { a ->
            a.id to distinctTestsInEvent.toSet()
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

    private suspend fun computeSessionFlagCountInMemory(
        group: Group,
        athletes: List<Individual>,
        event: TestingEvent,
        eventResults: List<TestResult>,
        allResults: List<TestResult>
    ): Int {
        val athleteIds = athletes.map { it.id }.toSet()
        val deduplicatedResults = eventResults
            .filter { it.individualId in athleteIds }
            .groupBy { it.individualId to it.testId }
            .map { (_, list) -> list.maxBy { it.createdAt } }
        val prev = mutableMapOf<Pair<String, String>, TestResult>()
        val distinctTestsInEvent = deduplicatedResults.map { it.testId }.distinct()
        for (a in athletes) {
            for (testId in distinctTestsInEvent) {
                val p = allResults
                    .filter { it.individualId == a.id && it.testId == testId && it.createdAt < event.date }
                    .maxByOrNull { it.createdAt } ?: continue
                prev[a.id to testId] = p
            }
        }
        val expected = athletes.associate { a ->
            a.id to distinctTestsInEvent.toSet()
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
