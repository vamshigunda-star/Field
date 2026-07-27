package com.vamshi.field.domain.usecase.standards

import com.vamshi.field.domain.model.standards.CustomTestDraft
import com.vamshi.field.domain.model.standards.CustomTestError
import com.vamshi.field.domain.model.standards.CustomTestField
import javax.inject.Inject

/**
 * Field-level validation for a custom test draft.
 *
 * Deliberately pure — no repository, no coroutine — so the whole rule set is unit-testable
 * without a database. The one rule that cannot live here is name uniqueness, which needs a
 * query; [SaveCustomTestUseCase] owns that and appends it to this list.
 *
 * Returns every problem it finds rather than the first, so the form can mark all the bad
 * fields at once instead of making the coach fix them one save at a time.
 */
class ValidateCustomTestUseCase @Inject constructor() {

    operator fun invoke(draft: CustomTestDraft): List<CustomTestError> {
        val errors = mutableListOf<CustomTestError>()

        val name = draft.name.trim()
        when {
            name.isEmpty() ->
                errors += CustomTestError(CustomTestField.NAME, "Give the test a name")
            name.length > MAX_NAME_LENGTH ->
                errors += CustomTestError(CustomTestField.NAME, "Keep the name under $MAX_NAME_LENGTH characters")
        }

        if (draft.categoryId.isBlank()) {
            errors += CustomTestError(CustomTestField.CATEGORY, "Pick a category")
        }

        val unit = draft.unit.trim()
        when {
            unit.isEmpty() ->
                errors += CustomTestError(CustomTestField.UNIT, "Give the score a unit, e.g. sec or reps")
            unit.length > MAX_UNIT_LENGTH ->
                errors += CustomTestError(CustomTestField.UNIT, "Keep the unit under $MAX_UNIT_LENGTH characters")
        }

        if (draft.trialsPerAthlete !in MIN_TRIALS..MAX_TRIALS) {
            errors += CustomTestError(
                CustomTestField.TRIALS,
                "Trials must be between $MIN_TRIALS and $MAX_TRIALS"
            )
        }

        // Only meaningful when the coach set both ends. A single open bound is valid —
        // "at least 0" with no ceiling is a normal way to describe a distance.
        val min = draft.validMin
        val max = draft.validMax
        if (min != null && max != null && min >= max) {
            errors += CustomTestError(
                CustomTestField.VALID_RANGE,
                "Lowest valid score must be less than the highest"
            )
        }

        errors += validateBands(draft)

        return errors
    }

    /**
     * Cut-point rules.
     *
     * Strict monotonicity is a correctness requirement, not tidiness: the generated bands
     * are contiguous, so two equal cut points would produce a zero-width band, and cut
     * points out of order would produce bands that genuinely overlap. `findNormResult`
     * takes `LIMIT 1`, so overlapping bands mean an athlete's percentile depends on row
     * order rather than their score.
     *
     * Direction matters — for a lower-is-better test the ladder from worst to best runs
     * downward (8.0 → 6.5 → 5.5), which is also the single easiest thing for a coach to
     * get backwards.
     */
    private fun validateBands(draft: CustomTestDraft): List<CustomTestError> {
        val scoring = draft.scoring ?: return emptyList()

        val sets: List<Pair<String, List<Double?>>> = if (scoring.sameForAllSexes) {
            listOf("" to scoring.shared)
        } else {
            listOf("Male: " to scoring.male, "Female: " to scoring.female)
        }

        val errors = mutableListOf<CustomTestError>()
        for ((prefix, cutPoints) in sets) {
            val filled = cutPoints.filterNotNull()
            if (filled.size != cutPoints.size) {
                errors += CustomTestError(
                    CustomTestField.BANDS,
                    "${prefix}Fill in all ${cutPoints.size} band boundaries, or switch to raw scores only"
                )
                continue
            }

            val ordered = if (draft.isHigherBetter) {
                filled.zipWithNext().all { (a, b) -> a < b }
            } else {
                filled.zipWithNext().all { (a, b) -> a > b }
            }
            if (!ordered) {
                errors += CustomTestError(
                    CustomTestField.BANDS,
                    if (draft.isHigherBetter) {
                        "${prefix}Each boundary must be higher than the one above it"
                    } else {
                        "${prefix}Each boundary must be lower than the one above it — a smaller score is better here"
                    }
                )
                continue
            }

            // A cut point outside the valid range would produce an inverted band, since the
            // outer bounds come from validMin/validMax.
            val min = draft.validMin
            val max = draft.validMax
            if (min != null && filled.any { it < min } || max != null && filled.any { it > max }) {
                errors += CustomTestError(
                    CustomTestField.BANDS,
                    "${prefix}Boundaries must sit inside the valid score range"
                )
            }
        }
        return errors
    }

    private companion object {
        const val MAX_NAME_LENGTH = 60
        const val MAX_UNIT_LENGTH = 12
        const val MIN_TRIALS = 1
        const val MAX_TRIALS = 5
    }
}
