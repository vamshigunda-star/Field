package com.vamshi.field.domain.model.reports

/**
 * The single source of truth for Field's performance-zone rule.
 *
 * The app consolidates the five classifications used by the published normative standards
 * (Superior / Above Average / Average / Below Average / Poor) into three, and represents each
 * as a percentile band:
 *
 * | Band              | Percentile | Colour |
 * |-------------------|------------|--------|
 * | Superior          | >= 80      | Green  |
 * | Healthy / Average | 40 - 79    | Yellow |
 * | Needs Improvement | < 40       | Red    |
 * | (no norm matched) | null       | Grey   |
 *
 * Grey is the absence of data, not a category.
 *
 * This lives in `domain/model` rather than inside a use case because Composables cannot
 * inject a use case, and every screen that could not reach [ClassifyPercentileUseCase] grew
 * its own copy of the numbers instead -- which is how the grid came to disagree with the
 * report about the same athlete. Read the thresholds from here; never re-type them.
 *
 * Seeded norm rows must satisfy the same contract: a row classified "Needs Improvement"
 * has to carry a percentile below [HEALTHY_MIN], or the app renders it as Healthy no matter
 * what the UI does. `SeedNormsConsistencyTest` asserts this.
 */
object PerformanceThresholds {

    /** Lowest percentile still classified [Classification.SUPERIOR]. */
    const val SUPERIOR_MIN: Int = 80

    /** Lowest percentile still classified [Classification.HEALTHY]. Below this is red. */
    const val HEALTHY_MIN: Int = 40

    /**
     * Representative percentile to store for each band when encoding normative data --
     * the midpoint of the band, so a value can never sit on a boundary the way the old
     * "Needs Improvement = 40" encoding did.
     */
    const val ENCODE_NEEDS_IMPROVEMENT: Int = 20
    const val ENCODE_HEALTHY: Int = 60
    const val ENCODE_SUPERIOR: Int = 90

    fun classify(percentile: Int?): Classification = when {
        percentile == null -> Classification.NO_DATA
        percentile >= SUPERIOR_MIN -> Classification.SUPERIOR
        percentile >= HEALTHY_MIN -> Classification.HEALTHY
        else -> Classification.NEEDS_IMPROVEMENT
    }

    /** Same rule for a 0-100 value already expressed as a Float (radar vertices, band shading). */
    fun classify(percentage: Float?): Classification =
        classify(percentage?.let { kotlin.math.round(it).toInt() })
}
