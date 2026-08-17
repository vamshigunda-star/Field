package com.vamshi.field.domain.model.standards

enum class InputParadigm {
    NUMERIC,      // Basic keypad entry (Distance, Weight)
    INCREMENTAL,  // +/- Buttons (Pushups, Situps)
    CHRONO,       // Stopwatch/Timing (Sprints)
    MULTI_STAGE,  // Level/Shuttle (Beep Test)
    SCALE         // 1-10 scores (RPE)
}

enum class InterpretationStrategy {
    NONE,
    NORM_LOOKUP,
    CALCULATED
}

data class FitnessTest(
    val id: String,
    val categoryId: String,
    val name: String,
    val unit: String,
    val isHigherBetter: Boolean,
    val description: String? = null,
    val timingMode: TimingMode = TimingMode.MANUAL_ENTRY,
    val inputParadigm: InputParadigm = InputParadigm.NUMERIC, // Driving modular UI
    val athletesPerHeat: Int? = null,
    val trialsPerAthlete: Int = 1,
    val validMin: Double? = null,
    val validMax: Double? = null,
    val interpretationStrategy: InterpretationStrategy = InterpretationStrategy.NORM_LOOKUP,
    val calculationConfig: String? = null,
    val youtubeId: String? = null,
    val source: TestSource = TestSource.USER
) {
    val isTimeBased: Boolean
        get() = unit.trim().lowercase() in listOf("s", "sec", "second", "seconds", "min", "minute", "minutes", "ms", "time")

    val canUseStopwatch: Boolean
        get() = timingMode != TimingMode.MANUAL_ENTRY && isTimeBased
}

