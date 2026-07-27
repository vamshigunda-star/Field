package com.vamshi.field.domain.model.standards

import com.vamshi.field.domain.model.people.BiologicalSex

data class NormReference(
    val id: String,
    val testId: String,
    val variant: String? = null,
    val sex: BiologicalSex,
    val ageMin: Float,
    val ageMax: Float,
    val minScore: Double,
    val maxScore: Double,
    val percentile: Int,
    val classification: String? = null,
    val source: TestSource = TestSource.USER
)
