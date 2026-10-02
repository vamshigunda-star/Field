package com.vamshi.field.data.mapper.testing

import com.vamshi.field.data.local.entities.testing.TestResultEntity
import com.vamshi.field.domain.model.testing.CaptureMethod
import com.vamshi.field.domain.model.testing.TestResult

fun TestResultEntity.toDomain(): TestResult {
    return TestResult(
        id = this.id,
        eventId = this.eventId,
        individualId = this.individualId,
        testId = this.testId,
        rawScore = this.rawScore,
        ageAtTime = this.ageAtTime,
        weightAtTime = this.weightAtTime,
        bodyWeightKg = this.bodyWeightKg,
        percentile = this.percentile,
        classification = normalizeClassificationLabel(this.classification),
        normVariantUsed = this.normVariantUsed,
        captureMethod = try { CaptureMethod.valueOf(this.captureMethod) } catch (_: Exception) { CaptureMethod.MANUAL_ENTRY },
        createdAt = this.createdAt
    )
}

/**
 * Real results store the matched norm row's label ("Superior" / "Healthy Fitness Zone" /
 * "Needs Improvement", straight from norms.csv). The demo roster seeded before 1.0 stored the
 * enum names instead, so the same zone read "HEALTHY" on one row and "Healthy Fitness Zone" on
 * the next -- in the CSV export and on every chip that shows the stored label. Normalised here on
 * read rather than rewritten in test_results, so no coach-owned row is ever modified.
 */
internal fun normalizeClassificationLabel(stored: String?): String? = when (stored) {
    "SUPERIOR" -> "Superior"
    "HEALTHY" -> "Healthy Fitness Zone"
    "NEEDS_IMPROVEMENT" -> "Needs Improvement"
    else -> stored
}

fun TestResult.toEntity(): TestResultEntity {
    return TestResultEntity(
        id = this.id,
        eventId = this.eventId,
        individualId = this.individualId,
        testId = this.testId,
        rawScore = this.rawScore,
        ageAtTime = this.ageAtTime,
        weightAtTime = this.weightAtTime,
        bodyWeightKg = this.bodyWeightKg,
        percentile = this.percentile,
        classification = this.classification,
        normVariantUsed = this.normVariantUsed,
        captureMethod = this.captureMethod.name,
        createdAt = this.createdAt
    )
}
