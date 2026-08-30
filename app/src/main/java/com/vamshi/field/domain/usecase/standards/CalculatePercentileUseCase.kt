package com.vamshi.field.domain.usecase.standards

import android.util.Log
import com.vamshi.field.domain.model.people.BiologicalSex
import com.vamshi.field.domain.repository.StandardsRepository
import javax.inject.Inject

data class PercentileResult(
    val percentile: Int,
    val classification: String?,
    // Provenance of which standard set produced this percentile, e.g. "Standard 2025".
    // Null when the matched norm row had no explicit variant.
    val variant: String? = null
)

class CalculatePercentileUseCase @Inject constructor(
    private val repository: StandardsRepository
) {
    suspend operator fun invoke(
        testId: String,
        rawScore: Double,
        age: Double,
        sex: BiologicalSex
    ): PercentileResult? {
        val norm = repository.getNormResult(testId, sex, age, rawScore)
        if (norm != null) {
            return PercentileResult(
                percentile = norm.percentile,
                classification = norm.classification,
                variant = norm.variant
            )
        }

        // Fallback: fetch norm bands for athlete test (with closest age fallback)
        val bands = repository.getNormBandsForAthleteTest(testId, sex, age)
        if (bands.isNotEmpty()) {
            val test = repository.getTestById(testId)
            val isHigherBetter = test?.isHigherBetter ?: true

            // 1. Check if score falls inside any band's range
            val insideBand = bands.find { band ->
                val minS = minOf(band.minScore, band.maxScore)
                val maxS = maxOf(band.minScore, band.maxScore)
                rawScore in minS..maxS
            }
            if (insideBand != null) {
                return PercentileResult(
                    percentile = insideBand.percentile,
                    classification = insideBand.classification,
                    variant = insideBand.variant
                )
            }

            // 2. Score is out of table bounds — clamp to top or bottom tier
            val maxScoreInBands = bands.maxOf { maxOf(it.minScore, it.maxScore) }
            val minScoreInBands = bands.minOf { minOf(it.minScore, it.maxScore) }

            val chosenBand = if (rawScore > maxScoreInBands) {
                if (isHigherBetter) {
                    bands.maxByOrNull { it.percentile }
                } else {
                    bands.minByOrNull { it.percentile }
                }
            } else if (rawScore < minScoreInBands) {
                if (isHigherBetter) {
                    bands.minByOrNull { it.percentile }
                } else {
                    bands.maxByOrNull { it.percentile }
                }
            } else {
                // Find band with nearest mid-point
                bands.minByOrNull { band ->
                    val mid = (band.minScore + band.maxScore) / 2.0
                    kotlin.math.abs(mid - rawScore)
                }
            }

            if (chosenBand != null) {
                return PercentileResult(
                    percentile = chosenBand.percentile,
                    classification = chosenBand.classification,
                    variant = chosenBand.variant
                )
            }
        }

        Log.w(
            "CalculatePercentile",
            "No norm match: testId=$testId sex=$sex age=$age score=$rawScore"
        )
        return null
    }
}

