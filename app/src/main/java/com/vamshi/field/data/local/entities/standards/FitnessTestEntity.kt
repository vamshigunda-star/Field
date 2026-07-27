package com.vamshi.field.data.local.entities.standards


import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import com.vamshi.field.data.local.entities.standards.TestCategoryEntity
import java.util.UUID

@Entity(
    tableName = "fitness_tests",
    foreignKeys = [
        ForeignKey(
            entity = TestCategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT // Don't delete a category if tests exist
        )
    ],
    indices = [Index(value = ["categoryId"])]
)
data class FitnessTestEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    val categoryId: String,

    val name: String,            // e.g., "20m Shuttle Run (Beep Test)"
    val unit: String,            // e.g., "level", "reps", "min:sec", "cm"

    // Critical for Analytics
    val isHigherBetter: Boolean, // True = Pushups, False = 1 Mile Run

    val description: String? = null, // Instructions for the teacher

    // Stopwatch configuration
    val timingMode: String = "MANUAL_ENTRY",  // INDIVIDUAL, GROUP_START, MANUAL_ENTRY
    val inputParadigm: String = "NUMERIC",    // NUMERIC, INCREMENTAL, CHRONO, MULTI_STAGE, SCALE
    val athletesPerHeat: Int? = null,          // null for INDIVIDUAL/MANUAL, 4-8 for GROUP_START
    val trialsPerAthlete: Int = 1,             // e.g., 2 for best-of-2

    // Score validity range and interpretation strategy
    val validMin: Double? = null,
    val validMax: Double? = null,
    val interpretationStrategy: String = "NORM_LOOKUP",
    val calculationConfig: String? = null,

    // Sync Metadata
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,

    val youtubeId: String? = null,

    // "SEED" (came from assets/tests.csv) or "USER" (authored in-app).
    // Scopes the CSV re-import so it can only delete rows it owns — see
    // StandardsDao.replaceSeedNorms. Defaults to USER so a call site that forgets
    // to set it fails safe (row is never bulk-deleted) rather than being wiped on
    // the next seed-key bump.
    val source: String = "USER"
)
