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

    /**
     * Whether tapping this test's cell should open the stopwatch rather than the score editor.
     *
     * [isTimeBased] alone is not enough. The stopwatch submits `elapsedMs / 1000.0`, so it can
     * only ever produce **seconds** — but `ms`, `min` and `minutes` are time units too, and a
     * test denominated in one of those had every stopwatch save rejected by
     * `RecordTestResultUseCase`'s range check. `test_light_response` (unit `ms`, valid range
     * 120-800) was unrecordable through its default tap path for exactly this reason.
     *
     * Requiring [InputParadigm.CHRONO] fixes it at the source: a test reaches the stopwatch only
     * if the catalog says it is stopwatch-operated, and the unit check still guards against a
     * CHRONO row whose unit is not a time at all.
     */
    val canUseStopwatch: Boolean
        get() = isTimeBased && inputParadigm == InputParadigm.CHRONO
}

