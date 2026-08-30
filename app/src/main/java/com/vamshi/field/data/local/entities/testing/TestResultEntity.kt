package com.vamshi.field.data.local.entities.testing

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import com.vamshi.field.data.local.entities.people.IndividualEntity
import com.vamshi.field.data.local.entities.standards.FitnessTestEntity
import java.util.UUID

@Entity(
    tableName = "test_results",
    foreignKeys = [
        ForeignKey(
            entity = TestingEventEntity::class,
            parentColumns = ["id"],
            childColumns = ["eventId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = IndividualEntity::class,
            parentColumns = ["id"],
            childColumns = ["individualId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = FitnessTestEntity::class,
            parentColumns = ["id"],
            childColumns = ["testId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index("eventId"),
        Index("individualId"),
        Index("testId"),
        Index(value = ["individualId", "testId", "createdAt"]),
        Index(value = ["individualId", "createdAt"])
    ]
)
data class TestResultEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val eventId: String,
    val individualId: String,
    val testId: String,

    val rawScore: Double,

    // We store these as a "snapshot" so history remains accurate
    // even if the Individual's profile or age changes later.
    val ageAtTime: Float,
    val weightAtTime: Double? = null,
    val bodyWeightKg: Double? = null, // Optional: useful for power-to-weight tests

    // 3. The Interpretation
    val percentile: Int? = null,           // e.g., 92
    val classification: String? = null,    // e.g., "High Performance"
    val normVariantUsed: String? = null,   // e.g., "Standard 2025"
    val captureMethod: String = "MANUAL_ENTRY",  // STOPWATCH or MANUAL_ENTRY

    val createdAt: Long = System.currentTimeMillis()
)
