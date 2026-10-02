package com.vamshi.field.domain.model.standards

import java.io.File

/**
 * Loads the shipped catalog CSVs straight from the source tree, so tests that need the real
 * 83-test catalog stay plain JVM tests with no Android assets and no Room.
 *
 * [rawRows] preserves the cells as strings because some tests need to see a malformed value
 * before [FitnessTestMapper][com.vamshi.field.data.mapper.standards.toDomain] silently defaults
 * it away. [fitnessTests] applies the same fallbacks that mapper does, so callers get the
 * domain objects the app would actually build from these rows.
 */
internal object TestCatalogCsv {

    fun asset(name: String): File =
        sequenceOf(File("app/src/main/assets/$name"), File("src/main/assets/$name"))
            .firstOrNull { it.exists() }
            ?: error("$name not found relative to ${File("").absolutePath}")

    /**
     * Quote-aware: two description columns contain commas, so a naive `split(",")` silently
     * shifts every later column on those rows.
     */
    fun splitCsvLine(line: String): List<String> {
        val cells = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        for (ch in line) {
            when {
                ch == '"' -> inQuotes = !inQuotes
                ch == ',' && !inQuotes -> {
                    cells += current.toString()
                    current.clear()
                }
                else -> current.append(ch)
            }
        }
        cells += current.toString()
        return cells
    }

    fun rawRows(fileName: String = "tests.csv"): List<Map<String, String>> {
        val lines = asset(fileName).readLines().filter { it.isNotBlank() }
        val header = splitCsvLine(lines.first().removePrefix("﻿")).map { it.trim() }
        return lines.drop(1).map { line ->
            val cells = splitCsvLine(line)
            header.withIndex().associate { (i, name) -> name to (cells.getOrNull(i) ?: "").trim() }
        }
    }

    /** The catalog as the app would build it, mirroring the mapper's defaults for bad values. */
    fun fitnessTests(): List<FitnessTest> = rawRows().map { row ->
        FitnessTest(
            id = row.getValue("id"),
            categoryId = row.getValue("categoryId"),
            name = row.getValue("name"),
            unit = row.getValue("unit"),
            isHigherBetter = row.getValue("isHigherBetter").equals("TRUE", ignoreCase = true),
            description = row["description"]?.ifBlank { null },
            timingMode = enumOrDefault(row.getValue("timingMode"), TimingMode.MANUAL_ENTRY),
            inputParadigm = enumOrDefault(row.getValue("inputParadigm"), InputParadigm.NUMERIC),
            athletesPerHeat = row.getValue("athletesPerHeat").toDoubleOrNull()?.toInt(),
            trialsPerAthlete = row.getValue("trialsPerAthlete").toDoubleOrNull()?.toInt() ?: 1,
            validMin = row.getValue("validMin").toDoubleOrNull(),
            validMax = row.getValue("validMax").toDoubleOrNull(),
            interpretationStrategy = enumOrDefault(
                row.getValue("interpretationStrategy"),
                InterpretationStrategy.NORM_LOOKUP
            ),
            calculationConfig = row["calculationConfig"]?.ifBlank { null },
            youtubeId = row["youtube_id"]?.ifBlank { null },
            source = TestSource.SEED
        )
    }

    fun normTestIds(): Set<String> = rawRows("norms.csv").map { it.getValue("testId") }.toSet()

    fun categoryIds(): Set<String> = rawRows("test_categories.csv").map { it.getValue("id") }.toSet()

    private inline fun <reified T : Enum<T>> enumOrDefault(value: String, default: T): T =
        enumValues<T>().firstOrNull { it.name == value } ?: default
}
