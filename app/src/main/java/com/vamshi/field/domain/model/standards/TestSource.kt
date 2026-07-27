package com.vamshi.field.domain.model.standards

/**
 * Who authored a catalog row (test, category, or norm reference).
 *
 * The CSV importer is scoped to [SEED] rows so it can never delete something a coach
 * created in-app. Anything the coach authors is [USER] and is additionally the only
 * thing the app allows editing, archiving, or deleting.
 *
 * [USER] is the default everywhere on purpose: a call site that forgets to set this
 * produces a row the importer will leave alone, rather than one it silently destroys
 * on the next seed-key bump.
 */
enum class TestSource {
    SEED,
    USER;

    companion object {
        /** Tolerant parse — an unrecognised value is treated as [USER] (never bulk-deleted). */
        fun from(raw: String?): TestSource =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: USER
    }
}
