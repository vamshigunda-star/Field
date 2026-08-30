package com.vamshi.field.domain.usecase.testing

import android.util.Log
import com.vamshi.field.domain.model.people.BiologicalSex
import com.vamshi.field.domain.model.standards.InterpretationStrategy
import com.vamshi.field.domain.model.testing.CaptureMethod
import com.vamshi.field.domain.model.testing.TestResult
import com.vamshi.field.domain.repository.StandardsRepository
import com.vamshi.field.domain.repository.TestingRepository
import com.vamshi.field.domain.usecase.standards.CalculatePercentileUseCase
import java.util.UUID
import javax.inject.Inject

class RecordTestResultUseCase @Inject constructor(
    private val repository: TestingRepository,
    private val calculatePercentile: CalculatePercentileUseCase,
    private val standardsRepository: StandardsRepository
) {
    suspend operator fun invoke(
        eventId: String,
        individualId: String,
        testId: String,
        rawScore: Double,
        ageAtTime: Float,
        sex: BiologicalSex,
        captureMethod: CaptureMethod = CaptureMethod.MANUAL_ENTRY
    ): TestResult {
        val test = standardsRepository.getTestById(testId)
        if (test == null) {
            // FK on test_results.testId would reject a save anyway, but a missing
            // test here also means we have no isHigherBetter / interpretation hint.
            // Loud warning so it shows up in logcat instead of failing opaquely on insert.
            Log.w("RecordTestResult", "Unknown testId=$testId — proceeding without interpretation")
        } else {
            if (test.validMin != null && rawScore < test.validMin) {
                throw IllegalArgumentException("Score $rawScore is below minimum valid value (${test.validMin}) for ${test.name}")
            }
            if (test.validMax != null && rawScore > test.validMax) {
                throw IllegalArgumentException("Score $rawScore exceeds maximum valid value (${test.validMax}) for ${test.name}")
            }
        }

        val percentileResult = when (test?.interpretationStrategy) {
            InterpretationStrategy.NORM_LOOKUP ->
                calculatePercentile(testId, rawScore, ageAtTime.toDouble(), sex)
            InterpretationStrategy.CALCULATED ->
                null // placeholder until CalculationEngine is built
            InterpretationStrategy.NONE, null ->
                null
        }

        val result = TestResult(
            id = UUID.randomUUID().toString(),
            eventId = eventId,
            individualId = individualId,
            testId = testId,
            rawScore = rawScore,
            ageAtTime = ageAtTime,
            percentile = percentileResult?.percentile,
            classification = percentileResult?.classification,
            normVariantUsed = percentileResult?.variant,
            captureMethod = captureMethod,
            createdAt = System.currentTimeMillis()
        )
        try {
            repository.saveResult(result)
        } catch (e: Exception) {
            // FK violation, IO failure, etc. Log loudly so submit-loop callers can
            // correlate which (event, athlete, test) failed instead of seeing a bare
            // "Submit failed" toast.
            Log.e(
                "RecordTestResult",
                "saveResult FAILED eventId=$eventId individualId=$individualId testId=$testId rawScore=$rawScore",
                e
            )
            throw e
        }
        Log.d(
            "RecordTestResult",
            "saved id=${result.id} event=$eventId athlete=$individualId test=$testId score=$rawScore pct=${result.percentile}"
        )
        return result
    }
}
