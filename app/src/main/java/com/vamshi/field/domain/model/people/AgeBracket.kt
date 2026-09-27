package com.vamshi.field.domain.model.people

/**
 * Field's age brackets, used both for norm lookup and for filtering a roster.
 *
 * These are not invented. Childhood and adolescence follow established developmental
 * frameworks (LTAD) -- the same raw score means different things at different stages, which
 * is why norms are bracketed at all. Adulthood stays split at 40/41 because the published
 * normative tables grade adults by age: collapsing 20-62 into one band would judge a
 * 60-year-old against 20-year-old cutoffs, and in this data every one of the 420 adult band
 * pairs differs.
 *
 * They live here in one place because the app previously carried three different schemes --
 * the norm tables, a second split inside adulthood, and a roster filter with its own bands --
 * so "13-19" meant something different depending on the screen.
 */
enum class AgeBracket(val label: String, val minAge: Int, val maxAge: Int) {
    CHILDHOOD("Childhood 5-12", 5, 12),
    ADOLESCENCE("Adolescence 13-19", 13, 19),
    YOUNG_ADULT("Adults 20-40", 20, 40),
    MIDDLE_ADULT("Adults 41-62", 41, 62),
    // Literal rather than UPPER_AGE_CAP: an enum entry is constructed before the companion
    // object is initialised, so referencing it here is fragile. AgeBracketTest pins them equal.
    OLDER_ADULT("Older Adults 63+", 63, 115);

    operator fun contains(age: Int): Boolean = age in minAge..maxAge

    companion object {
        /** Closes the top bracket so a norm row always has a finite range. */
        const val UPPER_AGE_CAP: Int = 115

        /** The bracket an age falls in, or null when outside every bracket. */
        fun of(age: Int): AgeBracket? = entries.firstOrNull { age in it }
    }
}
