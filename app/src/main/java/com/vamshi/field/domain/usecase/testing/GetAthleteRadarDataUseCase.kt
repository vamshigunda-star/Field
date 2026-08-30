package com.vamshi.field.domain.usecase.testing

import android.util.Log
import com.vamshi.field.domain.model.standards.FitnessTest
import com.vamshi.field.domain.model.standards.RadarAxis
import com.vamshi.field.domain.repository.StandardsRepository
import com.vamshi.field.domain.repository.TestingRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One spoke on the radar chart.
 *
 * - `axis` is the legacy domain enum (used by Analytics aggregations).
 * - `label` is the display string the chart should render at the spoke (defaults
 *   to a title-cased version of `axis.name` so Analytics keeps working).
 * - `normalizedScore` is in [0f, 1f] — derived from average percentile (0–100).
 * - `categoryId` lets the Athlete radar identify which category this spoke
 *   represents (Analytics leaves it null since it aggregates by axis).
 */
data class RadarAxisScore(
    val axis: RadarAxis,
    val normalizedScore: Float,
    val testCount: Int,
    val label: String = axis.name.lowercase().replaceFirstChar { it.uppercase() },
    val categoryId: String? = null
)

data class AthleteRadarData(
    val individualId: String,
    val axisScores: List<RadarAxisScore>
)

class GetAthleteRadarDataUseCase @Inject constructor(
    private val testingRepository: TestingRepository,
    private val standardsRepository: StandardsRepository
) {
    /**
     * Builds the radar-chart payload for a single athlete.
     *
     * Per CLAUDE.md the radar must have **one spoke per fitness category** (6
     * total). We iterate categories — not `RadarAxis.values()` — so that:
     *   • the spoke set always matches the seeded categories (no phantom
     *     SPEED/BALANCE spokes that the seed data never populates), and
     *   • categories with no data still render a 0-length spoke instead of
     *     being silently dropped from the chart.
     *
     * Score = average percentile of the athlete's latest result per test in
     * that category. Tests with a null percentile (no matching norm) are
     * skipped so they don't drag the average toward zero.
     */
    private var cachedCategories: List<com.vamshi.field.domain.model.standards.TestCategory>? = null
    private var cachedTestMap: Map<String, FitnessTest>? = null

    suspend operator fun invoke(individualId: String): AthleteRadarData = withContext(Dispatchers.IO) {
        val latestResults = testingRepository
            .getLatestResultPerTestForIndividual(individualId)

        val categories = cachedCategories ?: standardsRepository.getAllCategories().first()
            .sortedBy { it.sortOrder }.also { cachedCategories = it }
        val testMap = cachedTestMap ?: standardsRepository.getAllTests().first()
            .associateBy { it.id }.also { cachedTestMap = it }
        val categoryMap = categories.associateBy { it.id }
        
        val testsByCategory = mutableMapOf<String, MutableList<Int>>()

        for (result in latestResults) {
            val test = testMap[result.testId]
            if (test == null) {
                Log.w("GetAthleteRadarData", "Test not found: ${result.testId}")
                continue
            }
            val percentile = result.percentile
            if (percentile == null) {
                // Result exists but no norm matched — don't penalise the average.
                Log.d("GetAthleteRadarData", "Skipping result ${result.id} (no percentile)")
                continue
            }
            // Check if test belongs to a known category
            val category = categoryMap[test.categoryId]
            if (category != null) {
                testsByCategory.getOrPut(category.id) { mutableListOf() }.add(percentile)
            } else {
                // Fallback: use the raw categoryId from test
                testsByCategory.getOrPut(test.categoryId) { mutableListOf() }.add(percentile)
            }
        }

        val axisScores = categories.map { category ->
            val percentiles = testsByCategory[category.id].orEmpty()
            val score = if (percentiles.isEmpty()) 0f
                        else (percentiles.average().toFloat() / 100f).coerceIn(0f, 1f)
            
            val axis = category.radarAxis ?: RadarAxis.STRENGTH
            RadarAxisScore(
                axis = axis,
                normalizedScore = score,
                testCount = percentiles.size,
                label = category.name,
                categoryId = category.id
            )
        }

        Log.d("GetAthleteRadarData", "Athlete=$individualId spokes=${axisScores.size} scored=${axisScores.count { it.testCount > 0 }}")

        AthleteRadarData(
            individualId = individualId,
            axisScores = axisScores
        )
    }
}
