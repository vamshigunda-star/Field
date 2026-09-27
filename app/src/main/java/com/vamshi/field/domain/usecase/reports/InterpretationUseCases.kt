package com.vamshi.field.domain.usecase.reports

import com.vamshi.field.domain.model.reports.Classification
import com.vamshi.field.domain.model.reports.Distribution
import com.vamshi.field.domain.model.reports.PerformanceThresholds
import com.vamshi.field.domain.model.reports.AthleteFlag
import com.vamshi.field.domain.model.reports.FlagType
import com.vamshi.field.domain.model.testing.TestResult
import com.vamshi.field.domain.repository.StandardsRepository
import javax.inject.Inject
import kotlin.math.roundToInt

class ClassifyPercentileUseCase @Inject constructor() {
    /**
     * Delegates to [PerformanceThresholds] so the rule exists in exactly one place.
     * Kept as an injectable use case because every existing call site injects it.
     */
    operator fun invoke(percentile: Int?): Classification =
        PerformanceThresholds.classify(percentile)
}

class CalculateAthleteSessionAvgUseCase @Inject constructor() {
    operator fun invoke(sessionResults: List<TestResult>): Int? {
        val pctiles = sessionResults.mapNotNull { it.percentile }
        if (pctiles.isEmpty()) return null
        return pctiles.average().roundToInt()
    }
}

class CalculateGroupDistributionUseCase @Inject constructor(
    private val classifyPercentile: ClassifyPercentileUseCase,
) {
    operator fun invoke(athleteSessionAvgPctiles: List<Int?>): Distribution {
        var sup = 0; var hea = 0; var ni = 0; var nd = 0
        for (p in athleteSessionAvgPctiles) {
            when (classifyPercentile(p)) {
                Classification.SUPERIOR -> sup++
                Classification.HEALTHY -> hea++
                Classification.NEEDS_IMPROVEMENT -> ni++
                Classification.NO_DATA -> nd++
            }
        }
        return Distribution(sup, hea, ni, nd)
    }
}

class GetAthleteFlagsUseCase @Inject constructor(
    private val standards: StandardsRepository,
    private val calculateAthleteSessionAvg: CalculateAthleteSessionAvgUseCase
) {
    suspend operator fun invoke(
        groupId: String,
        groupName: String,
        latestSessionResults: List<TestResult>,
        previousSessionResultsByAthleteAndTest: Map<Pair<String, String>, TestResult>,
        athletesInGroup: List<Pair<String, String>>, // id -> name
        expectedTestsByAthlete: Map<String, Set<String>>
    ): List<AthleteFlag> {
        val flags = mutableListOf<AthleteFlag>()
        val resultsByAthlete = latestSessionResults.groupBy { it.individualId }
        val testNameCache = mutableMapOf<String, String>()

        for ((athleteId, athleteName) in athletesInGroup) {
            val mine = resultsByAthlete[athleteId].orEmpty()

            // ABSENT
            if (mine.isEmpty()) {
                flags += AthleteFlag(
                    individualId = athleteId,
                    athleteName = athleteName,
                    groupId = groupId,
                    groupName = groupName,
                    type = FlagType.ABSENT,
                    message = "Did not test in latest session"
                )
                continue
            }

            // BELOW HEALTHY (athlete-session average below PerformanceThresholds.HEALTHY_MIN)
            val avg = calculateAthleteSessionAvg(mine)
            if ((avg != null) && (avg < PerformanceThresholds.HEALTHY_MIN)) {
                flags += AthleteFlag(
                    individualId = athleteId,
                    athleteName = athleteName,
                    groupId = groupId,
                    groupName = groupName,
                    type = FlagType.BELOW_HEALTHY,
                    message = "Session avg ${avg}th — below Healthy zone"
                )
            }

            // REGRESSION (any test dropped ≥12 percentiles vs previous attempt)
            for (r in mine) {
                val prev = previousSessionResultsByAthleteAndTest[athleteId to r.testId]
                val cur = r.percentile
                val old = prev?.percentile
                if (cur != null && old != null && (old - cur) >= 12) {
                    val testName = testNameCache[r.testId] ?: run {
                        val name = standards.getTestById(r.testId)?.name ?: "Unknown Test"
                        testNameCache[r.testId] = name
                        name
                    }
                    flags += AthleteFlag(
                        individualId = athleteId,
                        athleteName = athleteName,
                        groupId = groupId,
                        groupName = groupName,
                        type = FlagType.REGRESSION,
                        message = "Dropped ${old - cur} percentiles vs last session in $testName",
                        testId = r.testId,
                        testName = testName
                    )
                }
            }

            // MISSING DATA (expected tests for age missing)
            val expected = expectedTestsByAthlete[athleteId].orEmpty()
            val taken = mine.asSequence().map { it.testId }.toSet()
            val missing = expected - taken
            if (expected.isNotEmpty() && missing.isNotEmpty()) {
                val missingNames = missing.map { id ->
                    testNameCache[id] ?: run {
                        val name = standards.getTestById(id)?.name ?: "Unknown Test"
                        testNameCache[id] = name
                        name
                    }
                }
                flags += AthleteFlag(
                    individualId = athleteId,
                    athleteName = athleteName,
                    groupId = groupId,
                    groupName = groupName,
                    type = FlagType.MISSING_DATA,
                    message = "Missing ${missing.size} expected test${if (missing.size == 1) "" else "s"}",
                    testIds = missing.toList(),
                    testNames = missingNames
                )
            }
        }
        return flags
    }
}
