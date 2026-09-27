package com.vamshi.field.domain.model.reports

import com.vamshi.field.domain.model.people.Group
import com.vamshi.field.domain.model.people.Individual
import com.vamshi.field.domain.model.standards.FitnessTest
import com.vamshi.field.domain.model.testing.TestResult
import com.vamshi.field.domain.model.testing.TestingEvent

data class GroupCardData(
    val group: Group,
    val size: Int,
    val distribution: Distribution
)

data class RecentSessionRow(
    val event: TestingEvent,
    val groupId: String?,
    val groupName: String?,
    val testCount: Int,
    val athleteTestedCount: Int
)

data class ReportsHomeData(
    val totalAthletes: Int,
    val totalHealthy: Int,
    val totalFlagged: Int,
    val sessionsThisMonth: Int,
    val flags: List<AthleteFlag>,
    val groups: List<GroupCardData>,
    val recentSessions: List<RecentSessionRow>,
    val allAthletes: List<Pair<String, String>>
)

data class TestTrendStrip(
    val test: FitnessTest,
    val points: List<Pair<Long, Float>>     // (date, avg pctile)
)

data class SessionRow(
    val event: TestingEvent,
    val testCount: Int,
    val athletesTested: Int,
    val totalAthletes: Int,
    val flagCount: Int
)

data class GroupOverviewData(
    val group: Group,
    val athletes: List<Individual>,
    val distribution: Distribution,
    val sessions: List<SessionRow>,
    val trends: List<TestTrendStrip>
)

data class LeaderboardRow(
    val rank: Int,
    val individualId: String,
    val athleteName: String,
    val rawScore: Double?,
    val unit: String,
    val percentile: Int?,
    val classification: Classification,
    val classificationLabel: String?,
    val deltaPercentile: Int?,    // null if no previous
    val flagged: Boolean,
    val absent: Boolean = false
)

data class SessionReportData(
    val event: TestingEvent,
    val group: Group,
    val groupSessions: List<TestingEvent>,    // for switcher
    val tests: List<FitnessTest>,
    val leaderboardByTest: Map<String, List<LeaderboardRow>>,
    val absentByTest: Map<String, List<LeaderboardRow>>,
    val missingByTest: Map<String, List<String>>, // testId -> athlete names lacking result
    val groupTrendByTest: Map<String, List<Pair<Long, Float>>>,
    val athletesTested: Int,
    val totalAthletes: Int
)

data class AthleteTestTile(
    val test: FitnessTest,
    val latestResult: TestResult?,
    val classification: Classification,
    val sparkline: List<Float>,        // raw scores oldest -> newest, normalized externally
    val rawSparkline: List<Double>,
    val deltaPercentile: Int? = null   // latest minus previous attempt; null if < 2 attempts or percentile missing
)

data class AthleteDashboardData(
    val athlete: Individual,
    val groups: List<Group>,
    val contextSession: TestingEvent?,
    val athleteSessionAvgPctile: Int?,
    val sessionTestCount: Int,
    val tiles: List<AthleteTestTile>,
    val flags: List<AthleteFlag>,
    val outstandingTests: List<FitnessTest>
)

data class AttemptRow(
    val resultId: String,
    val sessionId: String,
    val sessionName: String,
    val date: Long,
    val rawScore: Double,
    val percentile: Int?,
    val classification: Classification,
    val classificationLabel: String?,
    val deltaRaw: Double?,
    val deltaPercentile: Int?
)

data class NormBandsForAge(
    val date: Long,
    val ageYears: Float,
    val superiorMin: Double?,    // raw at p70
    val healthyMin: Double?,     // raw at p35
    val needsMax: Double?        // raw at p35 - 1 (informational)
)

data class AthleteTestDetailData(
    val athlete: Individual,
    val test: FitnessTest,
    val attempts: List<AttemptRow>,
    val bandsByDate: List<NormBandsForAge>,
    val peerLeaderboard: List<LeaderboardRow>?,
    val contextSession: TestingEvent?
)
