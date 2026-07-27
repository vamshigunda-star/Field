package com.vamshi.field.data.mapper.standards

import com.vamshi.field.data.local.entities.standards.TestCategoryEntity
import com.vamshi.field.domain.model.standards.RadarAxis
import com.vamshi.field.domain.model.standards.TestCategory
import com.vamshi.field.domain.model.standards.TestSource

fun TestCategoryEntity.toDomain(): TestCategory {
    return TestCategory(
        id = this.id,
        name = this.name,
        description = this.description,
        sortOrder = this.sortOrder,
        radarAxis = this.radarAxis?.let {
            try { RadarAxis.valueOf(it) } catch (_: Exception) { null }
        },
        source = TestSource.from(this.source)
    )
}

fun TestCategory.toEntity(
    createdAt: Long = System.currentTimeMillis()
): TestCategoryEntity {
    return TestCategoryEntity(
        id = this.id,
        name = this.name,
        description = this.description,
        sortOrder = this.sortOrder,
        radarAxis = this.radarAxis?.name,
        createdAt = createdAt,
        updatedAt = System.currentTimeMillis(),
        isDeleted = false,
        source = this.source.name
    )
}
