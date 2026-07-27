package com.vamshi.field.data.mapper.standards

import com.vamshi.field.data.local.entities.standards.FitnessTestEntity
import com.vamshi.field.domain.model.standards.FitnessTest
import com.vamshi.field.domain.model.standards.InterpretationStrategy
import com.vamshi.field.domain.model.standards.TimingMode
import com.vamshi.field.domain.model.standards.InputParadigm
import com.vamshi.field.domain.model.standards.TestSource

fun FitnessTestEntity.toDomain(): FitnessTest {
    return FitnessTest(
        id = this.id,
        categoryId = this.categoryId,
        name = this.name,
        unit = this.unit,
        isHigherBetter = this.isHigherBetter,
        description = this.description,
        timingMode = try { TimingMode.valueOf(this.timingMode) } catch (_: Exception) { TimingMode.MANUAL_ENTRY },
        inputParadigm = try { InputParadigm.valueOf(this.inputParadigm) } catch (_: Exception) { InputParadigm.NUMERIC },
        athletesPerHeat = this.athletesPerHeat,
        trialsPerAthlete = this.trialsPerAthlete,
        validMin = this.validMin,
        validMax = this.validMax,
        interpretationStrategy = try {
            InterpretationStrategy.valueOf(this.interpretationStrategy)
        } catch (_: Exception) { InterpretationStrategy.NORM_LOOKUP },
        calculationConfig = this.calculationConfig,
        youtubeId = this.youtubeId,
        source = TestSource.from(this.source)
    )
}

fun FitnessTest.toEntity(
    createdAt: Long = System.currentTimeMillis()
): FitnessTestEntity {
    return FitnessTestEntity(
        id = this.id,
        categoryId = this.categoryId,
        name = this.name,
        unit = this.unit,
        isHigherBetter = this.isHigherBetter,
        description = this.description,
        timingMode = this.timingMode.name,
        inputParadigm = this.inputParadigm.name,
        athletesPerHeat = this.athletesPerHeat,
        trialsPerAthlete = this.trialsPerAthlete,
        validMin = this.validMin,
        validMax = this.validMax,
        interpretationStrategy = this.interpretationStrategy.name,
        calculationConfig = this.calculationConfig,
        youtubeId = this.youtubeId,
        createdAt = createdAt,
        updatedAt = System.currentTimeMillis(),
        // NOTE: this resets isDeleted. Archiving goes through StandardsDao.archiveUserTest,
        // never through an upsert, so an archived test is never resurrected by a round trip.
        isDeleted = false,
        source = this.source.name
    )
}
