package com.vamshi.field.domain.model.standards

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every row in `tests.csv` becomes a test a coach can put on a testing event and tap in the
 * grid. This asserts that each one can actually form a working capture path.
 *
 * The failure mode this exists to catch is quiet, not loud. [com.vamshi.field.data.mapper.standards.toDomain]
 * wraps all four of its enum parses in `try/catch` with a default, so a typo in `inputParadigm`
 * does not crash — it silently downgrades that test to a numeric keypad, and nobody notices
 * until a coach is standing on a field wondering why the stopwatch never appeared. The same
 * goes for a valid range in the wrong unit: the save throws, the throw is caught, the coach
 * gets a snackbar, and the test is simply unrecordable forever.
 *
 * Reads the CSVs from the source tree via [TestCatalogCsv] so it stays a plain JVM test,
 * matching [com.vamshi.field.domain.model.reports.SeedNormsConsistencyTest].
 */
class TestCatalogCapturePathTest {

    private val rows = TestCatalogCsv.rawRows()

    private fun cell(row: Map<String, String>, column: String) = row.getValue(column)

    /** Mirrors [FitnessTest.canUseStopwatch]. */
    private fun canUseStopwatch(row: Map<String, String>) =
        cell(row, "unit").lowercase() in TIME_UNITS &&
            cell(row, "inputParadigm") == InputParadigm.CHRONO.name

    @Test
    fun `catalog is non-empty and every id is unique`() {
        val ids = rows.map { cell(it, "id") }
        assertTrue("tests.csv has no rows", ids.isNotEmpty())
        val dupes = ids.groupBy { it }.filterValues { it.size > 1 }.keys
        assertTrue("duplicate test ids: $dupes", dupes.isEmpty())
    }

    /**
     * `Screen.Stopwatch.createRoute` interpolates the test id straight into a nav route path
     * with no URL encoding, so an id carrying a slash, space or `?` would blow up navigation
     * for that test only — precisely the "one specific test kills the app" shape.
     */
    @Test
    fun `every test id is safe to interpolate into a navigation route`() {
        // `.` is allowed: it is unreserved in a URI path segment, so `test_1.6_mile_run` routes
        // fine. What is not allowed is anything that would end the segment or open a query.
        val offenders = rows.map { cell(it, "id") }.filterNot { it.matches(Regex("[A-Za-z0-9._-]+")) }
        assertTrue("test ids unsafe in a nav route: $offenders", offenders.isEmpty())
    }

    /**
     * A bad enum string does not throw — the mapper defaults it. So the catalog is the only
     * place this can be caught.
     */
    @Test
    fun `every inputParadigm and timingMode parses to a real enum constant`() {
        val paradigms = InputParadigm.entries.map { it.name }.toSet()
        val modes = TimingMode.entries.map { it.name }.toSet()
        val offenders = rows.mapNotNull { row ->
            val id = cell(row, "id")
            when {
                cell(row, "inputParadigm") !in paradigms ->
                    "$id: inputParadigm='${cell(row, "inputParadigm")}'"
                cell(row, "timingMode") !in modes ->
                    "$id: timingMode='${cell(row, "timingMode")}'"
                else -> null
            }
        }
        assertTrue(
            "catalog rows whose enum would silently fall back to a default:\n" +
                offenders.joinToString("\n"),
            offenders.isEmpty()
        )
    }

    @Test
    fun `every interpretationStrategy parses to a real enum constant`() {
        val strategies = InterpretationStrategy.entries.map { it.name }.toSet()
        val offenders = rows.filterNot { cell(it, "interpretationStrategy") in strategies }
            .map { "${cell(it, "id")}: '${cell(it, "interpretationStrategy")}'" }
        assertTrue("unparseable interpretationStrategy:\n" + offenders.joinToString("\n"), offenders.isEmpty())
    }

    /**
     * `StopwatchSessionUseCase` does `athletes.chunked(test.athletesPerHeat ?: 6)`, and
     * `chunked(0)` throws `IllegalArgumentException`. Blank is fine — it becomes null and
     * takes the `?: 6`. Zero is not.
     */
    @Test
    fun `athletesPerHeat is blank or at least one`() {
        val offenders = rows.filter {
            val raw = cell(it, "athletesPerHeat")
            raw.isNotEmpty() && (raw.toDoubleOrNull() ?: 0.0) < 1.0
        }.map { "${cell(it, "id")}: athletesPerHeat='${cell(it, "athletesPerHeat")}'" }
        assertTrue(
            "athletesPerHeat values that would make chunked() throw:\n" + offenders.joinToString("\n"),
            offenders.isEmpty()
        )
    }

    @Test
    fun `trialsPerAthlete is at least one`() {
        val offenders = rows.filter { (cell(it, "trialsPerAthlete").toDoubleOrNull() ?: 0.0) < 1.0 }
            .map { "${cell(it, "id")}: trialsPerAthlete='${cell(it, "trialsPerAthlete")}'" }
        assertTrue(
            "a test needing zero trials can never be completed:\n" + offenders.joinToString("\n"),
            offenders.isEmpty()
        )
    }

    /**
     * `RecordTestResultUseCase` rejects a score outside [validMin, validMax] by throwing. An
     * inverted or absent range makes every score for that test unsaveable.
     */
    @Test
    fun `every test has a valid, non-inverted score range`() {
        val offenders = rows.mapNotNull { row ->
            val id = cell(row, "id")
            val min = cell(row, "validMin").toDoubleOrNull()
            val max = cell(row, "validMax").toDoubleOrNull()
            when {
                min == null || max == null ->
                    "$id: min='${cell(row, "validMin")}' max='${cell(row, "validMax")}'"
                min >= max -> "$id: min=$min >= max=$max"
                else -> null
            }
        }
        assertTrue("unusable score ranges:\n" + offenders.joinToString("\n"), offenders.isEmpty())
    }

    @Test
    fun `every test belongs to a category that exists`() {
        val known = TestCatalogCsv.categoryIds()
        val offenders = rows.filterNot { cell(it, "categoryId") in known }
            .map { "${cell(it, "id")} -> '${cell(it, "categoryId")}'" }
        assertTrue("tests pointing at a missing category:\n" + offenders.joinToString("\n"), offenders.isEmpty())
    }

    /**
     * A stopwatch-routed test is scored in **seconds**: `StopwatchViewModel` computes
     * `rawScore = elapsedMs / 1000.0` unconditionally. So a test whose unit makes
     * [FitnessTest.canUseStopwatch] true must also be denominated in seconds, or every
     * stopwatch save it receives is rejected by `RecordTestResultUseCase` and the coach gets
     * "Save failed" every single time.
     *
     * `test_light_response` was the live instance: unit `ms`, range 120-800, so a real reaction
     * time of ~0.35s was compared against a floor of 120 and always threw. Its declared paradigm
     * is NUMERIC — it was never meant to reach the stopwatch — but [FitnessTest.canUseStopwatch]
     * used to key off the unit alone. That property now also requires [InputParadigm.CHRONO],
     * which is what keeps this set empty.
     */
    @Test
    fun `stopwatch-routed tests are denominated in seconds`() {
        // Checked against the unit rather than the magnitude of the range: 120-800 is a
        // perfectly plausible range in seconds, so no bounds heuristic can see this. The unit
        // is the whole signal — `ms`, `min` and `minutes` all satisfy isTimeBased, but the
        // stopwatch only ever emits seconds.
        val offenders = rows
            .filter { canUseStopwatch(it) }
            .filterNot { cell(it, "unit").lowercase() in SECOND_UNITS }
            .map { cell(it, "id") }
            .toSet()

        assertEquals(
            "These tests route to the stopwatch but are not denominated in seconds, so every " +
                "stopwatch save they receive is rejected as out of range.",
            emptySet<String>(),
            offenders
        )
    }

    /**
     * The other half of the same fix: a CHRONO test whose unit is not a time never reaches the
     * stopwatch at all, so the coach is left hand-typing a duration into a numeric keypad.
     */
    @Test
    fun `every CHRONO test is denominated in a stopwatch-compatible unit`() {
        val offenders = rows
            .filter { cell(it, "inputParadigm") == InputParadigm.CHRONO.name }
            .filterNot { cell(it, "unit").lowercase() in SECOND_UNITS }
            .map { "${cell(it, "id")}: unit='${cell(it, "unit")}'" }

        assertTrue(
            "CHRONO tests that cannot reach the stopwatch:\n" + offenders.joinToString("\n"),
            offenders.isEmpty()
        )
    }

    /**
     * A `NORM_LOOKUP` test with no norm rows at all scores fine but can never be interpreted —
     * the cell stays grey and the athlete never appears in a report band. Distinct from the
     * documented age-bracket gap, which is about *which* brackets exist rather than whether any
     * rows do.
     *
     * Pinned as an exact set for the same reason as above: remove an id when its norms land.
     */
    @Test
    fun `every norm-interpreted test has at least one norm row`() {
        // Held at empty deliberately. A find/replace of `test_1_5_mile_run` ->
        // `test_1.6_mile_run` once moved all 30 of the 1.5-mile run's rows onto the new test and
        // left the old one normless; this assertion is what caught it. Both now carry their own
        // 30 rows.
        val knownMissing = emptySet<String>()

        val normIds = TestCatalogCsv.normTestIds()
        val missing = rows
            .filter { cell(it, "interpretationStrategy") == InterpretationStrategy.NORM_LOOKUP.name }
            .map { cell(it, "id") }
            .filterNot { it in normIds }
            .toSet()

        assertEquals(
            "The set of NORM_LOOKUP tests with no norms has changed. These score but never " +
                "interpret: the coach sees a grey cell and no report band.",
            knownMissing,
            missing
        )
    }

    /**
     * A non-numeric cell in a numeric norm column means the same row is read differently
     * depending on how the coach's database was created, because the two importers disagree:
     *
     *  - `tools/build_prepackaged_db.py` regex-extracts the first number, so `"2099 m"` -> 2099.0
     *  - `SeedDataManager` uses `toDoubleOrNull() ?: 999.0`, so `"2099 m"` -> 999.0
     *
     * A fresh install takes the prepackaged path and an upgrade takes the CSV path, so the same
     * athlete's score can land in a different band on two different phones. `test_cooper_12_min_run`
     * carried exactly this: a "Needs Improvement" ceiling of 2099 m collapsing to 999 on the CSV
     * path, which leaves a genuinely below-standard 1500 m result matching no band at all.
     */
    @Test
    fun `every numeric column in norms csv is actually numeric`() {
        val numericColumns = listOf("ageMin", "ageMax", "minScore", "maxScore", "percentile")
        val offenders = TestCatalogCsv.rawRows("norms.csv").flatMapIndexed { index, row ->
            numericColumns.mapNotNull { column ->
                val raw = row[column].orEmpty()
                if (raw.toDoubleOrNull() == null) {
                    "row ${index + 2} (${row["testId"]}): $column='$raw'"
                } else {
                    null
                }
            }
        }
        assertTrue(
            "non-numeric norm cells — these import differently on a fresh install than on an " +
                "upgrade:\n" + offenders.joinToString("\n"),
            offenders.isEmpty()
        )
    }

    /**
     * Two norm rows for the same test, sex and age bracket must not claim overlapping score
     * ranges under *different* classifications. The lookup is a `BETWEEN` on both age and score,
     * so an overlap means SQLite returns whichever row it reaches first and the same performance
     * can be graded Superior on one device and Needs Improvement on another.
     *
     * `SeedNormsConsistencyTest` does not cover this. Its overlap check compares *distinct* age
     * ranges pairwise, so a test carrying two complete band sets over identical brackets — which
     * is exactly what `test_shuttle_run` has — collapses to one range and reports no overlap.
     *
     * All three offenders are pre-existing — verified against the previous committed norms.csv,
     * none was introduced by a recent edit — and each needs a data decision, so they are pinned
     * rather than fixed:
     *
     *  - `test_shuttle_run` and `test_toe_touch` each hold 60 rows where every other test holds
     *    30: two complete band sets, both labelled variant "Default", on different scales. Toe
     *    touch MALE 5-12 reads Superior as both 8.1-40 and 30-50, so a 20 is Superior under one
     *    set and Healthy under the other. They look like two measurement conventions merged.
     *  - `test_push_up` overlaps within a single set: MALE 30-39 grades 17-29 Healthy and 27-100
     *    Superior, so 27 and 28 are both. It also carries two Superior rows for that bracket.
     */
    @Test
    fun `no test has conflicting norm bands for the same sex and age bracket`() {
        val knownOffenders = setOf("test_shuttle_run", "test_toe_touch", "test_push_up")

        fun num(v: String) = v.toDoubleOrNull() ?: 0.0
        val offenders = TestCatalogCsv.rawRows("norms.csv")
            .groupBy { listOf(it["testId"], it["sex"], it["ageMin"], it["ageMax"]) }
            .filterValues { group ->
                group.any { a ->
                    group.any { b ->
                        a["classification"] != b["classification"] &&
                            num(a.getValue("minScore")) <= num(b.getValue("maxScore")) &&
                            num(b.getValue("minScore")) <= num(a.getValue("maxScore"))
                    }
                }
            }
            .keys.mapNotNull { it.first() }.toSet()

        assertEquals(
            "Tests whose norm bands overlap across classifications, making the grade " +
                "non-deterministic, have changed.",
            knownOffenders,
            offenders
        )
    }

    private companion object {
        /** Kept in sync with [FitnessTest.isTimeBased]. */
        val TIME_UNITS = setOf("s", "sec", "second", "seconds", "min", "minute", "minutes", "ms", "time")

        /** The subset of [TIME_UNITS] that `rawScore = elapsedMs / 1000.0` actually produces. */
        val SECOND_UNITS = setOf("s", "sec", "second", "seconds", "time")
    }
}
