package com.vamshi.field.domain.model.standards

/**
 * How a coach captures a score for a test.
 *
 * This exists so the authoring UI offers one plain-language choice instead of asking a
 * coach to understand [InputParadigm] and [TimingMode] as separate concepts. The seeded
 * catalog sets those two independently, but every combination the app actually renders
 * collapses to one of these four, and the mapping lives here rather than in the UI so it
 * stays testable.
 *
 * [STOPWATCH] maps to [TimingMode.INDIVIDUAL] rather than MANUAL_ENTRY on purpose: the
 * testing grid routes any test whose timing mode is not MANUAL_ENTRY to the stopwatch
 * screen (offering the coach a stopwatch-or-type choice per test).
 */
enum class MeasurementMethod(
    val inputParadigm: InputParadigm,
    val timingMode: TimingMode
) {
    /** Type a number — distances, weights, heights. */
    KEYPAD(InputParadigm.NUMERIC, TimingMode.MANUAL_ENTRY),

    /** Tap +/- to count — push-ups, sit-ups. */
    COUNTER(InputParadigm.INCREMENTAL, TimingMode.MANUAL_ENTRY),

    /** Time it with the in-app stopwatch — sprints, timed runs. */
    STOPWATCH(InputParadigm.CHRONO, TimingMode.INDIVIDUAL),

    /** Record a level or stage reached — beep-test style. */
    LEVELS(InputParadigm.MULTI_STAGE, TimingMode.MANUAL_ENTRY);

    companion object {
        /** Best-fit reverse mapping, for hydrating the edit form from a saved test. */
        fun from(inputParadigm: InputParadigm, timingMode: TimingMode): MeasurementMethod =
            entries.firstOrNull { it.inputParadigm == inputParadigm && it.timingMode == timingMode }
                ?: entries.firstOrNull { it.inputParadigm == inputParadigm }
                ?: KEYPAD
    }
}

/**
 * The four performance bands, worst to best, with the percentile each one stores.
 *
 * The percentiles are chosen to land squarely inside the app's zone thresholds
 * (`ClassifyPercentileUseCase`: ≥60 green, 30–59 yellow, <30 red) rather than near an
 * edge, so a band always renders as the colour its label implies.
 */
enum class BandLevel(val label: String, val percentile: Int) {
    NEEDS_WORK("Needs work", 15),   // red
    FAIR("Fair", 45),               // yellow
    GOOD("Good", 70),               // green
    EXCELLENT("Excellent", 90);     // green

    companion object {
        /** Boundaries between adjacent bands — three cut points produce four bands. */
        const val CUT_POINT_COUNT = 3
    }
}

/**
 * Coach-entered performance standards for a custom test.
 *
 * Modelled as **three cut points**, not four explicit ranges. That is the whole reason
 * this is viable to fill in on a phone: the seeded `norms.csv` uses only 4 score bands
 * per sex over a single age band, so matching its resolution takes three numbers, not a
 * grid. [com.vamshi.field.domain.usecase.standards.GenerateNormsUseCase] expands them.
 *
 * Cut points are ordered worst→best, so for a lower-is-better test they descend
 * (8.0, 6.5, 5.5) and for higher-is-better they ascend (20, 30, 40).
 */
data class ScoringBands(
    /**
     * When true, [shared] applies to every athlete and the generated rows cover MALE,
     * FEMALE *and* UNSPECIFIED — which is also the only way an UNSPECIFIED athlete ever
     * matches a norm, since the lookup matches sex exactly.
     */
    val sameForAllSexes: Boolean = true,
    val shared: List<Double?> = List(BandLevel.CUT_POINT_COUNT) { null },
    val male: List<Double?> = List(BandLevel.CUT_POINT_COUNT) { null },
    val female: List<Double?> = List(BandLevel.CUT_POINT_COUNT) { null }
)

/**
 * Everything the coach fills in on the custom-test form.
 *
 * A draft is the authoring-time input; [FitnessTest] is the persisted result. They are
 * separate types because the draft carries a nullable id (null = create) and omits fields
 * the coach never sets — the app derives `interpretationStrategy`, `athletesPerHeat`, and
 * `source` rather than asking.
 */
data class CustomTestDraft(
    /** Null when creating; the existing test's id when editing. */
    val id: String? = null,
    val name: String = "",
    val categoryId: String = "",
    val unit: String = "",
    val isHigherBetter: Boolean = true,
    val description: String? = null,
    val measurementMethod: MeasurementMethod = MeasurementMethod.KEYPAD,
    val trialsPerAthlete: Int = 1,
    val validMin: Double? = null,
    val validMax: Double? = null,
    /** Null = record raw scores only, which shows the grey "no reference" zone. */
    val scoring: ScoringBands? = null
) {
    val isEdit: Boolean get() = id != null
}

/** Form field an error attaches to, so the UI can render it inline. */
enum class CustomTestField { NAME, CATEGORY, UNIT, TRIALS, VALID_RANGE, BANDS }

data class CustomTestError(
    val field: CustomTestField,
    val message: String
)
