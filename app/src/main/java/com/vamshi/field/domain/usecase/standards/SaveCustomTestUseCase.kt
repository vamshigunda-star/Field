package com.vamshi.field.domain.usecase.standards

import com.vamshi.field.domain.model.standards.CustomTestDraft
import com.vamshi.field.domain.model.standards.CustomTestError
import com.vamshi.field.domain.model.standards.CustomTestField
import com.vamshi.field.domain.model.standards.FitnessTest
import com.vamshi.field.domain.model.standards.InterpretationStrategy
import com.vamshi.field.domain.model.standards.TestSource
import com.vamshi.field.domain.repository.StandardsRepository
import java.util.UUID
import javax.inject.Inject

sealed interface SaveCustomTestResult {
    data class Success(val testId: String) : SaveCustomTestResult
    data class Invalid(val errors: List<CustomTestError>) : SaveCustomTestResult
}

/**
 * Creates or updates a coach-authored test.
 *
 * Owns the two derivations the UI must never make for itself:
 *  - `interpretationStrategy` follows from whether the test has scoring bands. With none,
 *    it is [InterpretationStrategy.NONE], which makes [com.vamshi.field.domain.usecase.testing.RecordTestResultUseCase]
 *    skip the norm lookup and store a null percentile — rendering as the grey "no
 *    reference" zone rather than a misleading one.
 *  - `source` is always [TestSource.USER], which is what keeps the row out of reach of the
 *    CSV importer's source-scoped deletes.
 */
class SaveCustomTestUseCase @Inject constructor(
    private val repository: StandardsRepository,
    private val validate: ValidateCustomTestUseCase,
    private val generateNorms: GenerateNormsUseCase
) {
    suspend operator fun invoke(draft: CustomTestDraft): SaveCustomTestResult {
        val errors = validate(draft).toMutableList()

        // Uniqueness needs a query, so it can't live in the pure validator. Only worth
        // checking once the name is otherwise valid — no point telling a coach their blank
        // name is also a duplicate.
        val name = draft.name.trim()
        if (errors.none { it.field == CustomTestField.NAME } &&
            repository.isTestNameTaken(name, draft.id)
        ) {
            errors += CustomTestError(CustomTestField.NAME, "A test called \"$name\" already exists")
        }

        if (errors.isNotEmpty()) return SaveCustomTestResult.Invalid(errors)

        val testId = draft.id ?: "$CUSTOM_ID_PREFIX${UUID.randomUUID()}"
        val norms = generateNorms(testId, draft)

        repository.saveCustomTest(
            test = FitnessTest(
                id = testId,
                categoryId = draft.categoryId,
                name = name,
                unit = draft.unit.trim(),
                isHigherBetter = draft.isHigherBetter,
                description = draft.description?.trim()?.ifBlank { null },
                timingMode = draft.measurementMethod.timingMode,
                inputParadigm = draft.measurementMethod.inputParadigm,
                // Only meaningful for GROUP_START heats, which the authoring flow doesn't offer.
                athletesPerHeat = null,
                trialsPerAthlete = draft.trialsPerAthlete,
                validMin = draft.validMin,
                validMax = draft.validMax,
                // Derived, never asked: with bands there is something to look up, without
                // them a lookup would only ever return null and log a spurious warning.
                interpretationStrategy = if (norms.isEmpty()) {
                    InterpretationStrategy.NONE
                } else {
                    InterpretationStrategy.NORM_LOOKUP
                },
                calculationConfig = null,
                youtubeId = null,
                source = TestSource.USER
            ),
            norms = norms
        )

        return SaveCustomTestResult.Success(testId)
    }

    private companion object {
        // Distinguishes coach-authored ids from the seeded `test_*` ids at a glance in the
        // database and in logs, and removes any chance of colliding with a future CSV row.
        const val CUSTOM_ID_PREFIX = "custom_"
    }
}
