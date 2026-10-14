package com.vamshi.field.domain.model.reports

/**
 * English ordinal for a percentile: 1st, 2nd, 3rd, 11th, 71st. Percentile labels used to
 * append a fixed "th" and read "71th avg" and "81th Percentile".
 */
fun ordinal(n: Int): String {
    val suffix = when {
        n % 100 in 11..13 -> "th"
        n % 10 == 1 -> "st"
        n % 10 == 2 -> "nd"
        n % 10 == 3 -> "rd"
        else -> "th"
    }
    return "$n$suffix"
}
