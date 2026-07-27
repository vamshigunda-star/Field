package com.vamshi.field.data.local.entities.standards

import androidx.room3.Entity
import androidx.room3.PrimaryKey
import java.util.UUID

@Entity(tableName = "test_categories")
data class TestCategoryEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    val name: String,          // e.g., "Cardiorespiratory Endurance"
    val description: String? = null, // Short explanatory text shown under the category selector
    val sortOrder: Int = 0,    // To control display order in the UI
    val radarAxis: String? = null, // SPEED, AGILITY, STRENGTH, ENDURANCE, FLEXIBILITY, BALANCE; null = not on radar

    // Sync Metadata
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,

    /** "SEED" or "USER" — see [FitnessTestEntity.source]. */
    val source: String = "USER"
)
