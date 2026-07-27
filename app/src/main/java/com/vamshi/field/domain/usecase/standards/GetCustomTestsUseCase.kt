package com.vamshi.field.domain.usecase.standards

import com.vamshi.field.domain.model.people.BiologicalSex
import com.vamshi.field.domain.model.standards.BandLevel
import com.vamshi.field.domain.model.standards.CustomTestDraft
import com.vamshi.field.domain.model.standards.FitnessTest
import com.vamshi.field.domain.model.standards.MeasurementMethod
import com.vamshi.field.domain.model.standards.NormReference
import com.vamshi.field.domain.model.standards.ScoringBands
import com.vamshi.field.domain.model.standards.TestSource
import com.vamshi.field.domain.repository.StandardsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Coach-authored tests, for the "My tests" section of the library. */
class ObserveCustomTestsUseCase @Inject constructor(
    private val repository: StandardsRepository
) {
    operator fun invoke(): Flow<List<FitnessTest>> = repository.getCustomTests()
}

/**
 * Hydrates the authoring form when editing an existing custom test.
 *
 * Returns null for a seeded test as well as an unknown id — seeded tests are not editable,
 * and the caller should treat both the same way (there is nothing to edit).
 */
class GetCustomTestDraftUseCase @Inject constructor(
    private val repository: StandardsRepository
) {
    suspend operator fun invoke(testId: String): CustomTestDraft? {
        val test = repository.getTestById(testId) ?: return null
        if (test.source != TestSource.USER) return null

        return CustomTestDraft(
            id = test.id,
            name = test.name,
            categoryId = test.categoryId,
            unit = test.unit,
            isHigherBetter = test.isHigherBetter,
            description = test.description,
            measurementMethod = MeasurementMethod.from(test.inputParadigm, test.timingMode),
            trialsPerAthlete = test.trialsPerAthlete,
            validMin = test.validMin,
            validMax = test.validMax,
            scoring = rebuildScoring(repository.getNormsForTest(test.id), test.isHigherBetter)
        )
    }

    /**
     * Recovers the coach's cut points from the stored norm rows.
     *
     * The bands are contiguous, so the interior boundaries *are* the cut points — read
     * them off the band edges rather than storing the coach's input separately, which
     * would be a second source of truth that could drift from the rows actually used for
     * lookups.
     *
     * Returns null if the rows don't look like something this builder produced (a test
     * whose norms were hand-seeded, say), so the form falls back to raw-scores-only rather
     * than showing invented numbers.
     */
    private fun rebuildScoring(norms: List<NormReference>, isHigherBetter: Boolean): ScoringBands? {
        if (norms.isEmpty()) return null

        fun cutPointsFor(sex: BiologicalSex): List<Double?>? {
            val bands = norms.filter { it.sex == sex }
            if (bands.size != BandLevel.entries.size) return null
            // Interior boundaries in score order, worst→best per the test's direction.
            val ascending = bands.sortedBy { it.minScore }.drop(1).map { it.minScore }
            return if (isHigherBetter) ascending else ascending.reversed()
        }

        val male = cutPointsFor(BiologicalSex.MALE)
        val female = cutPointsFor(BiologicalSex.FEMALE)
        if (male == null || female == null) return null

        return if (male == female) {
            ScoringBands(sameForAllSexes = true, shared = male, male = male, female = female)
        } else {
            ScoringBands(sameForAllSexes = false, male = male, female = female)
        }
    }
}
