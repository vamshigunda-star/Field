package com.vamshi.field.domain.model.reports

import com.vamshi.field.domain.model.people.AgeBracket
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The seeded norm tables must satisfy the same contract the UI classifies against.
 *
 * This is the test whose absence let the real bug ship: every "Needs Improvement" row was
 * encoded as percentile 40, which sits exactly on the HEALTHY boundary, so every
 * below-standard result rendered Yellow no matter what the UI did. Fixing UI thresholds
 * alone would not have surfaced a single extra Red.
 *
 * Reads the CSV from the source tree rather than through Android assets so it stays a plain
 * JVM test.
 */
class SeedNormsConsistencyTest {

    private fun normsCsv(): File =
        sequenceOf(File("app/src/main/assets/norms.csv"), File("src/main/assets/norms.csv"))
            .firstOrNull { it.exists() }
            ?: error("norms.csv not found relative to ${File("").absolutePath}")

    private data class Row(
        val testId: String, val sex: String, val ageMin: Int, val ageMax: Int,
        val percentile: Int, val classification: String
    )

    private fun rows(): List<Row> {
        val lines = normsCsv().readLines().filter { it.isNotBlank() }
        val header = lines.first().removePrefix("\uFEFF").split(",").map { it.trim() }
        fun idx(name: String) = header.indexOf(name).also { require(it >= 0) { "missing column $name" } }
        return lines.drop(1).map { line ->
            val c = line.split(",")
            Row(
                testId = c[idx("testId")].trim(),
                sex = c[idx("sex")].trim(),
                ageMin = c[idx("ageMin")].trim().toFloat().toInt(),
                ageMax = c[idx("ageMax")].trim().toFloat().toInt(),
                percentile = c[idx("percentile")].trim().toInt(),
                classification = c[idx("classification")].trim()
            )
        }
    }

    private fun expected(classification: String): Classification = when {
        classification.startsWith("Needs", ignoreCase = true) -> Classification.NEEDS_IMPROVEMENT
        classification.startsWith("Healthy", ignoreCase = true) -> Classification.HEALTHY
        classification.startsWith("Superior", ignoreCase = true) -> Classification.SUPERIOR
        else -> error("unrecognised classification: $classification")
    }

    @Test
    fun `every norm row's percentile classifies to its own classification`() {
        val offenders = rows().filter {
            PerformanceThresholds.classify(it.percentile) != expected(it.classification)
        }
        assertTrue(
            "${offenders.size} norm rows classify against their own label. " +
                "First few: " + offenders.take(5).joinToString { "${it.testId}/${it.sex}/${it.classification}=p${it.percentile}" },
            offenders.isEmpty()
        )
    }

    /**
     * Overlapping age ranges are a real defect, not a tidiness issue: the norm lookup is
     * `:age BETWEEN ageMin AND ageMax`, so a 25-year-old matching both a 20-29 row and a
     * 20-40 row gets whichever the query happens to return first. Wall-sit and
     * shoulder-flexibility carried both schemes at once.
     */
    @Test
    fun `no test has overlapping age ranges for a given sex`() {
        val offenders = mutableListOf<String>()
        rows().groupBy { it.testId to it.sex }.forEach { (key, group) ->
            val ranges = group.map { it.ageMin to it.ageMax }.distinct().sortedBy { it.first }
            ranges.zipWithNext { a, b ->
                if (a.second >= b.first) offenders += "${key.first}/${key.second}: $a overlaps $b"
            }
        }
        assertTrue("overlapping norm age ranges:\n" + offenders.joinToString("\n"), offenders.isEmpty())
    }

    /**
     * Age ranges still outside [AgeBracket] belong to 11 adult-only tests whose source
     * standards grade by decade (push-up, BMI, Cooper, Rockport, ...). Collapsing those into
     * the app's brackets would discard validated age-grading, so it is a data decision rather
     * than a code change. This test documents the remaining set instead of asserting on it;
     * turn it into an assertion once those tests are rebracketed.
     */
    @Test
    fun `report age ranges not yet aligned to AgeBracket`() {
        val stray = rows()
            .filter { row -> AgeBracket.entries.none { row.ageMin == it.minAge && row.ageMax == it.maxAge } }
            .map { it.testId }
            .distinct()
            .sorted()
        println("Tests still on source decade bands (${stray.size}): $stray")
    }
}
