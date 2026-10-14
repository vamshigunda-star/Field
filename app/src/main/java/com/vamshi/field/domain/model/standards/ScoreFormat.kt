package com.vamshi.field.domain.model.standards

import java.util.Locale
import kotlin.math.roundToInt

/**
 * Encoding for [InputParadigm.MULTI_STAGE] scores (Beep, Yo-Yo, custom "Levels" tests).
 *
 * A stage result is a level and a shuttle, stored as one Double so it still sorts, ranks
 * and range-checks like any other score: `level + shuttle / 100`. Level 8 shuttle 1 is 8.01,
 * level 8 shuttle 10 is 8.10.
 *
 * It used to be the string "level.shuttle" parsed with toDouble(), so shuttle 10 ("8.10")
 * and shuttle 1 ("8.1") were the same number, and every Beep level has 7–16 shuttles.
 *
 * Coaches read and type the same "level.shuttle" notation ("8.10"), so a stage score must
 * never be shown with plain decimal formatting — use [formatScore], and [parseScore] for text
 * a coach typed. norms.csv and tests.csv encode their Beep/Yo-Yo bounds the same way.
 */
object StageScore {
    const val MAX_SHUTTLE = 99

    fun encode(level: Int, shuttle: Int): Double = (level * 100 + shuttle) / 100.0

    fun level(raw: Double): Int = hundredths(raw) / 100

    fun shuttle(raw: Double): Int = hundredths(raw) % 100

    /** "8.10" — the notation coaches use and the stage picker shows. */
    fun format(raw: Double): String = "${level(raw)}.${shuttle(raw)}"

    /** Unambiguous outside the app, where a spreadsheet would turn "8.10" back into 8.1. */
    fun longLabel(raw: Double): String = "Level ${level(raw)}, Shuttle ${shuttle(raw)}"

    /** "8.10" → 8.10, "8.1" → 8.01, "8" → 8.00. Null when it isn't level[.shuttle]. */
    fun parse(text: String): Double? {
        val parts = text.trim().split(".")
        if (parts.size > 2) return null
        val level = parts[0].toIntOrNull()?.takeIf { it >= 0 } ?: return null
        val shuttleText = parts.getOrNull(1).orEmpty()
        val shuttle = if (shuttleText.isEmpty()) 0 else shuttleText.toIntOrNull() ?: return null
        if (shuttle !in 0..MAX_SHUTTLE) return null
        return encode(level, shuttle)
    }

    // Rounded, not truncated: 8.10 is 8.0999… in binary.
    private fun hundredths(raw: Double): Int = (raw * 100).roundToInt()
}

/** Display text for a score: "8.10" for stage tests, else "12" or "12.5". */
fun formatScore(raw: Double, paradigm: InputParadigm?): String = when {
    paradigm == InputParadigm.MULTI_STAGE -> StageScore.format(raw)
    raw % 1.0 == 0.0 -> raw.toInt().toString()
    else -> String.format(Locale.getDefault(), "%.1f", raw)
}

/** Inverse of [formatScore] for text a coach typed or a picker produced. */
fun parseScore(text: String, paradigm: InputParadigm?): Double? =
    if (paradigm == InputParadigm.MULTI_STAGE) StageScore.parse(text) else text.toDoubleOrNull()

/** Editable text for a saved score — the stage notation, or the plain number. */
fun scoreInputText(raw: Double, paradigm: InputParadigm?): String =
    if (paradigm == InputParadigm.MULTI_STAGE) StageScore.format(raw) else raw.toString()
