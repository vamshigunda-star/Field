package com.vamshi.field.data.local.entities.standards

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import com.vamshi.field.domain.model.people.BiologicalSex
import java.util.UUID

@Entity(
    tableName = "norm_references",
    foreignKeys = [
        ForeignKey(
            entity = FitnessTestEntity::class,
            parentColumns = ["id"],
            childColumns = ["testId"],
            onDelete = ForeignKey.CASCADE // If test is deleted, delete its norms
        )
    ],
    // FAST LOOKUP INDEX:
    // Enables instant querying for specific Age/Sex/Variant combinations
    indices = [
        Index(value = ["testId", "variant", "sex", "ageMin"])
    ]
)
data class NormReferenceEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    val testId: String,

    // Allows different standard sets (e.g., "General", "Elite", "State 2024")
    // If empty/null, treat as "Default"
    val variant: String? = null,

    // Matching Criteria
    val sex: BiologicalSex,     // Match against Individual's sex
    val ageMin: Float,          // e.g., 14.0
    val ageMax: Float,          // e.g., 14.99 (covers the whole year)

    // The Performance Standard
    val minScore: Double,       // The raw score required (e.g., 8.5 shuttles)
    val maxScore: Double,       // Upper bound for this bracket

    // The Result
    val percentile: Int,        // e.g., 85 (The score the student gets)
    val classification: String? = null, // e.g., "Superior", "Needs Improvement"

    // Sync Metadata
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),

    /** "SEED" or "USER" — see [FitnessTestEntity.source]. */
    val source: String = "USER"
)
