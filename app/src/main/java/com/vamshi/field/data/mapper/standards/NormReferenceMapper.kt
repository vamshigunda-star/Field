package com.vamshi.field.data.mapper.standards

import com.vamshi.field.data.local.entities.standards.NormReferenceEntity
import com.vamshi.field.domain.model.standards.NormReference
import com.vamshi.field.domain.model.standards.TestSource

fun NormReferenceEntity.toDomain(): NormReference {
    return NormReference(
        id = this.id,
        testId = this.testId,
        variant = this.variant,
        sex = this.sex,
        ageMin = this.ageMin,
        ageMax = this.ageMax,
        minScore = this.minScore,
        maxScore = this.maxScore,
        percentile = this.percentile,
        classification = this.classification,
        source = TestSource.from(this.source)
    )
}

fun NormReference.toEntity(
    createdAt: Long = System.currentTimeMillis()
): NormReferenceEntity {
    return NormReferenceEntity(
        id = this.id,
        testId = this.testId,
        variant = this.variant,
        sex = this.sex,
        ageMin = this.ageMin,
        ageMax = this.ageMax,
        minScore = this.minScore,
        maxScore = this.maxScore,
        percentile = this.percentile,
        classification = this.classification,
        createdAt = createdAt,
        updatedAt = System.currentTimeMillis(),
        source = this.source.name
    )
}
