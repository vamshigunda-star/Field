package com.vamshi.field.domain.usecase.analytics

import com.vamshi.field.domain.model.standards.RadarAxis
import com.vamshi.field.domain.model.testing.TestResult
import com.vamshi.field.domain.model.standards.TestCategory
import com.vamshi.field.domain.model.standards.FitnessTest
import com.vamshi.field.domain.usecase.testing.AthleteRadarData
import com.vamshi.field.domain.usecase.testing.RadarAxisScore
import javax.inject.Inject

data class IndividualEventSummary(
    val testName: String,
    val categoryName: String,
    val rawScore: Float,
    val unit: String,
    val percentile: Int,
    val zone: PerformanceZone
)

enum class PerformanceZone {
    GREEN, YELLOW, RED
}

data class TrajectoryPoint(
    val dateMs: Long,
    val score: Float
)

enum class TrajectoryTrend {
    IMPROVING, PLATEAU, DECLINE, INSUFFICIENT_DATA
}

data class LongitudinalTrend(
    val categoryName: String,
    val history: List<TrajectoryPoint>,
    val trend: TrajectoryTrend
)

data class IndividualAnalyticsSnapshot(
    val latestEventSummary: List<IndividualEventSummary>,
    val longitudinalTrends: List<LongitudinalTrend>,
    val radarData: AthleteRadarData?
)

class GetIndividualAnalyticsUseCase @Inject constructor() {
    
    operator fun invoke(
        individualId: String,
        allResults: List<TestResult>,
        categories: List<TestCategory>,
        tests: List<FitnessTest>
    ): IndividualAnalyticsSnapshot {
        val individualResults = allResults.filter { it.individualId == individualId }
        if (individualResults.isEmpty()) {
            return IndividualAnalyticsSnapshot(emptyList(), emptyList(), null)
        }

        val testById = tests.associateBy { it.id }
        val categoryById = categories.associateBy { it.id }

        // 1. Module 1: Single Testing Event Summary (Latest per test)
        val latestResults = individualResults
            .groupBy { it.testId }
            .mapNotNull { (_, results) -> results.maxByOrNull { it.createdAt } }

        val eventSummary = latestResults.mapNotNull { result ->
            val test = testById[result.testId] ?: return@mapNotNull null
            val category = categoryById[test.categoryId] ?: return@mapNotNull null
            val percentile = result.percentile ?: 0
            
            val zone = when {
                percentile >= 80 -> PerformanceZone.GREEN
                percentile >= 40 -> PerformanceZone.YELLOW
                else -> PerformanceZone.RED
            }
            
            IndividualEventSummary(
                testName = test.name,
                categoryName = category.name,
                rawScore = result.rawScore.toFloat(),
                unit = test.unit,
                percentile = percentile,
                zone = zone
            )
        }.sortedBy { it.categoryName }

        // 2. Module 2: Longitudinal Tracking
        val resultsByCategory = individualResults.groupBy { result ->
            val test = testById[result.testId]
            categoryById[test?.categoryId]?.name ?: "Unknown"
        }.filterKeys { it != "Unknown" }

        val longitudinalTrends = resultsByCategory.map { (catName, results) ->
            // Assume we want normalized score or percentile for trend. We'll use percentile.
            val history = results.mapNotNull {
                if (it.percentile != null) {
                    TrajectoryPoint(it.createdAt, it.percentile.toFloat())
                } else null
            }.sortedBy { it.dateMs }
            
            val trend = calculateTrend(history)
            
            LongitudinalTrend(
                categoryName = catName,
                history = history,
                trend = trend
            )
        }.sortedBy { it.categoryName }

        // 3. Module 3: Radar Chart
        val perAxisPercentiles = mutableMapOf<RadarAxis, MutableList<Int>>()
        
        for (result in latestResults) {
            val test = testById[result.testId] ?: continue
            val category = categoryById[test.categoryId] ?: continue
            val axis = category.radarAxis ?: continue
            val percentile = result.percentile ?: continue
            perAxisPercentiles.getOrPut(axis) { mutableListOf() }.add(percentile)
        }

        val axisScores = perAxisPercentiles.map { (axis, percentiles) ->
            RadarAxisScore(
                axis = axis,
                normalizedScore = (percentiles.average().toFloat() / 100f).coerceIn(0f, 1f),
                testCount = percentiles.size
            )
        }
        
        val radarData = if (axisScores.size >= 3) AthleteRadarData(individualId, axisScores) else null

        return IndividualAnalyticsSnapshot(
            latestEventSummary = eventSummary,
            longitudinalTrends = longitudinalTrends,
            radarData = radarData
        )
    }
    
    private fun calculateTrend(history: List<TrajectoryPoint>): TrajectoryTrend {
        if (history.size < 2) return TrajectoryTrend.INSUFFICIENT_DATA
        
        val first = history.first().score
        val last = history.last().score
        
        // Using +/- 3% for plateau
        val diff = last - first
        return when {
            diff > 3f -> TrajectoryTrend.IMPROVING
            diff < -3f -> TrajectoryTrend.DECLINE
            else -> TrajectoryTrend.PLATEAU
        }
    }
}
