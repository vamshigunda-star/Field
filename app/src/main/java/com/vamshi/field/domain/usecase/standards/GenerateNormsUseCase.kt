package com.vamshi.field.domain.usecase.standards

import com.vamshi.field.domain.model.people.BiologicalSex
import com.vamshi.field.domain.model.standards.BandLevel
import com.vamshi.field.domain.model.standards.CustomTestDraft
import com.vamshi.field.domain.model.standards.NormReference
import com.vamshi.field.domain.model.standards.ScoringBands
import com.vamshi.field.domain.model.standards.TestSource
import java.util.UUID
import javax.inject.Inject

/**
 * Expands a coach's three cut points into the [NormReference] rows the lookup queries.
 *
 * Shape mirrors the seeded `norms.csv`: four score bands per sex over a single wide age
 * band. Age is not split, because the seeded data doesn't split it either (8 of its 9
 * tests use one 1.0–99.0 band) and asking a coach for per-age-year standards would make
 * the feature unusable in a gym.
 *
 * Bands are generated **contiguous** — band N's max is band N+1's min. Combined with the
 * `ORDER BY percentile DESC` in `StandardsDao.findNormResult`, a score landing exactly on
 * a cut point resolves deterministically to the better band, and no score can fall into a
 * hole between bands (which is a real failure mode in the seeded CSV, where a 9.5 push-up
 * count matches nothing).
 */
class GenerateNormsUseCase @Inject constructor() {

    operator fun invoke(testId: String, draft: CustomTestDraft): List<NormReference> {
        val scoring = draft.scoring ?: return emptyList()

        val floor = draft.validMin ?: DEFAULT_FLOOR
        val ceiling = draft.validMax ?: DEFAULT_CEILING

        return if (scoring.sameForAllSexes) {
            // UNSPECIFIED included deliberately: the lookup matches sex exactly, so without
            // a row here an athlete recorded as UNSPECIFIED would never match a norm.
            SEXES_FOR_SHARED.flatMap { sex ->
                bandsFor(testId, sex, scoring.shared, draft.isHigherBetter, floor, ceiling)
            }
        } else {
            bandsFor(testId, BiologicalSex.MALE, scoring.male, draft.isHigherBetter, floor, ceiling) +
                bandsFor(testId, BiologicalSex.FEMALE, scoring.female, draft.isHigherBetter, floor, ceiling)
        }
    }

    private fun bandsFor(
        testId: String,
        sex: BiologicalSex,
        cutPoints: List<Double?>,
        isHigherBetter: Boolean,
        floor: Double,
        ceiling: Double
    ): List<NormReference> {
        val cuts = cutPoints.filterNotNull()
        if (cuts.size != BandLevel.CUT_POINT_COUNT) return emptyList()

        // Worst→best. For higher-is-better the boundaries ascend, so the worst band starts
        // at the floor; for lower-is-better they descend and the worst band ends at the
        // ceiling. Sorting into score order lets one loop handle both.
        val boundaries = if (isHigherBetter) {
            listOf(floor) + cuts + listOf(ceiling)
        } else {
            listOf(floor) + cuts.reversed() + listOf(ceiling)
        }

        // Levels run worst→best in score order for higher-is-better, and best→worst for
        // lower-is-better (a small time is a good time).
        val levels = if (isHigherBetter) BandLevel.entries else BandLevel.entries.reversed()

        return levels.mapIndexed { index, level ->
            NormReference(
                id = UUID.randomUUID().toString(),
                testId = testId,
                // Left null rather than "Custom": a variant string surfaces in reports as
                // the provenance of the standard, and "Custom" adds no information the
                // coach doesn't already have.
                variant = null,
                sex = sex,
                ageMin = AGE_MIN,
                ageMax = AGE_MAX,
                minScore = boundaries[index],
                maxScore = boundaries[index + 1],
                percentile = level.percentile,
                classification = level.label,
                source = TestSource.USER
            )
        }
    }

    private companion object {
        val SEXES_FOR_SHARED = listOf(
            BiologicalSex.MALE,
            BiologicalSex.FEMALE,
            BiologicalSex.UNSPECIFIED
        )

        // Single wide age band, matching the seeded convention.
        const val AGE_MIN = 0f
        const val AGE_MAX = 99f

        // Wide enough to cover any real score when the coach sets no valid range.
        // Negative floor is deliberate — sit-and-reach scores go below zero.
        const val DEFAULT_FLOOR = -99_999.0
        const val DEFAULT_CEILING = 99_999.0
    }
}
