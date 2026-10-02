package com.vamshi.field.data.local.entities.standards

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey

/**
 * Key/value stamps describing the catalog that shipped inside the prepackaged database.
 *
 * Exists to answer one question a row count cannot: *is the catalog already current?*
 *
 * `createFromAsset` hands a fresh install a database that `tools/build_prepackaged_db.py`
 * generated from the very CSVs `SeedDataManager` would otherwise parse, so re-importing them
 * rewrites byte-identical rows — roughly 2.4k norms, and about 5.5s of the first cold start.
 * The seeder could not detect that, because the only "already seeded" signal was a
 * SharedPreferences flag that no fresh install can have set. The build script writes the
 * catalog generation here, and the seeder compares it against the generation this build
 * expects.
 *
 * An install that upgrades into this table gets it empty (the migration only creates it), so
 * its stamp reads null, it does not match, and the CSV import runs exactly as before. That is
 * the intended fallback: a missing or stale stamp always degrades to seeding, never to
 * silently keeping an out-of-date catalog.
 */
@Entity(tableName = "catalog_metadata")
data class CatalogMetadataEntity(
    // `key`/`value` are spelled out in the column names because both are awkward to quote in
    // raw queries, and this table is read by the build script as well as by Room.
    @PrimaryKey @ColumnInfo(name = "metaKey") val key: String,
    @ColumnInfo(name = "metaValue") val value: String
)
