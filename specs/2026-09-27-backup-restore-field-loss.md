# Spec: Drive backup/restore loses most columns on the round trip

## 0. Open question (answer changes scope)

Two different failures produce "the backup data isn't coming back". This spec fixes the
second one. Confirm which you are seeing:

- **(a) The restore screen lists no backups**, or errors before any data is written.
  That is an auth/Drive-visibility problem, not this. Note that backups are written to
  Drive's `appDataFolder` (scope `DRIVE_APPDATA` in `DriveBackupHelper`), which is
  **deliberately invisible in the Drive web UI** — you cannot see or upload the file from
  your email account by hand, and a different app signing key (debug vs release) gets a
  different `appDataFolder`, so a release build cannot see a debug build's backups.
- **(b) The restore reports success, the app reloads, and the data is partly or wholly
  missing from the screens you look at.** That is this spec.

Everything below assumes (b). If it is (a), say so and this becomes a different spec.

## 1. Objective & scope

- **As a coach**, when I restore from Drive I want my athletes, groups, events and results
  to come back exactly as they were, so that I can keep working on a new device.
- **In scope:** making the backup payload lossless for the seven user-data tables it
  already covers, in both directions, without breaking backups already sitting in Drive.
- **Out of scope:** Drive auth, the `appDataFolder` visibility question above, backing up
  `pending_test_entries` (in-flight staging is intentionally not backed up), and the
  seeded catalog (reproduced from assets by design).

## 2. Verified context

Read:

- `data/repository/BackupRepositoryImpl.kt` — both directions of the mapping; this is where
  the loss happens, in plain sight.
- `domain/model/backup/BackupPayload.kt` — the DTOs, and the load-bearing comment about Gson.
- `data/local/daos/backup/BackupDao.kt` — `restoreAllData` insert order, already correct.
- `data/local/entities/{people,testing}/*.kt` — the real column sets.
- `data/local/daos/testing/TestingDao.kt:33` and `data/local/daos/people/PeopleDao.kt` —
  what the app actually queries by.
- `data/mapper/people/GroupMapper.kt` — confirms a bad category string degrades to null
  rather than crashing (`runCatching`).
- `app/src/test/java/com/vamshi/field/data/repository/BackupRepositoryImplTest.kt` — the
  Robolectric harness to extend.

Versions in play: Room `3.0.0` (androidx.room3), Gson `2.11.0`, Hilt `2.60.1`.

**No schema change.** Every column named below already exists. Database stays at version
15; no migration, no `MigrationTest` addition, no prepackaged-DB rebuild.

**Drift noticed:**

- `BackupRepositoryImpl` is in `data/` and reaches `AppDatabase` directly. That is correct
  for a repository implementation — noted only because it reads like a layering smell and is not.
- `SettingsViewModel` injects `BackupRepository` and `DriveBackupHelper` (a `data/` class)
  directly. Pre-existing; not pulled into scope.
- `BackupTestResult.notes` exists in the DTO but `test_results` has no notes column. It is
  written as a literal `null` and read into nothing. Dead field — leave it, removing it
  would change the JSON shape for no gain.

## 3. Root cause

The backup DTOs are narrower than the entities, so `backupToDrive` never writes several
columns and `restoreEntities` reconstructs the entity with constructor defaults. The data
is not corrupted — it was never in the file.

**`testing_events` — this is the headline.** `BackupTestingEvent` carries
`(id, name, timestamp, notes)`. `groupId` is not in it, so on restore
`TestingEventEntity` takes its default of `null`. Every restored event becomes a detached
personal session. `TestingDao.getEventsForGroup` is
`SELECT * FROM testing_events WHERE groupId = :groupId`, so **group overview, session
reports and every group-scoped list come back empty even though the rows are in the
database.** This alone explains "the backup data is not being added into the app": the
coach restores, opens the group they care about, and sees nothing.

**`individuals` — safety-critical.** `medicalAlert` and `isRestricted` are not backed up.
After a restore every athlete reads as having no medical alert and no restriction. The app
treats these as prominent red warnings before testing; silently dropping them in a restore
is the one data loss here that can hurt somebody.

**`test_results`.** `percentile` is written out as `standardizedScore` and then never read
back — a write-only field. `classification`, `normVariantUsed`, `weightAtTime` and
`bodyWeightKg` are not written at all, and `ageAtTime` is hard-coded to `0f` on restore.
Restored results therefore render in the grey "no reference" zone everywhere, and any
age-derived report computes against age zero.

**`groups`.** `location` and `cycle` are not backed up and restore to `null`, which breaks
cycle-based roster filtering. `category` survives only by luck: it is written as
`it.category ?: ""` and an empty string degrades to `null` in `GroupMapper` instead of
throwing.

Full loss table:

| Table | Survives today | Lost today |
|---|---|---|
| `testing_events` | id, name, date, notes | **groupId**, location, createdAt |
| `individuals` | id, firstName, lastName, dateOfBirth, sex, notes | **medicalAlert**, **isRestricted**, email, isActive, isDeleted, createdAt, updatedAt |
| `groups` | id, name, category, isDeleted | location, cycle |
| `test_results` | id, eventId, individualId, testId, rawScore, createdAt, captureMethod | **percentile**, classification, normVariantUsed, weightAtTime, bodyWeightKg, ageAtTime (zeroed) |
| `users`, `group_members`, `event_test_cross_ref`, custom catalog | complete | — |

## 4. Files

| Action | Path | Purpose |
|---|---|---|
| MODIFY | `domain/model/backup/BackupPayload.kt` | Widen four DTOs; add a payload schema version |
| MODIFY | `data/repository/BackupRepositoryImpl.kt` | Write the new fields out; read them back with fallbacks |
| MODIFY | `app/src/test/java/com/vamshi/field/data/repository/BackupRepositoryImplTest.kt` | Round-trip and legacy-payload regression tests |

## 5. Contracts

**Every new DTO field must be nullable with a `= null` default, and must be read through
`?:` or `.orEmpty()`.** This is not style. `BackupPayload` already documents why: Gson
instantiates data classes through `Unsafe`, bypassing the constructor, so a Kotlin default
is *not* applied for a field missing from the JSON. A non-null `Boolean` or `Float` added
here would come back as a boxed null through a non-null type and throw on first use —
aborting the restore as `CorruptedBackup`, which is exactly the data loss this change
exists to stop. Every coach with a backup already in Drive has a payload without these
fields.

```kotlin
data class BackupPayload(
    // …existing fields unchanged, order unchanged…
    val customCategories: List<BackupTestCategory>? = null,
    val customTests: List<BackupFitnessTest>? = null,
    val customNorms: List<BackupNormReference>? = null,
    /** 1 = first lossless payload. Absent (null) = any backup written before this change. */
    val schemaVersion: Int? = null
)

data class BackupTestingEvent(
    val id: String,
    val name: String,
    val timestamp: Long,
    val notes: String?,
    val groupId: String? = null,
    val location: String? = null,
    val createdAt: Long? = null
)

data class BackupIndividual(
    val id: String,
    val firstName: String,
    val lastName: String,
    val dateOfBirth: Long,
    val gender: String,
    val notes: String?,
    val medicalAlert: String? = null,
    val isRestricted: Boolean? = null,
    val email: String? = null,
    val isActive: Boolean? = null,
    val isDeleted: Boolean? = null,
    val createdAt: Long? = null,
    val updatedAt: Long? = null
)

data class BackupGroup(
    val id: String,
    val name: String,
    val type: String,
    val isActive: Boolean,
    val location: String? = null,
    val cycle: String? = null,
    val createdAt: Long? = null
)

data class BackupTestResult(
    val id: String,
    val eventId: String,
    val individualId: String,
    val testId: String,
    val rawScore: Double,
    val standardizedScore: Double?,   // legacy: percentile-as-Double, still written
    val timestamp: Long,
    val captureMethod: String,
    val notes: String?,
    val ageAtTime: Float? = null,
    val percentile: Int? = null,
    val classification: String? = null,
    val normVariantUsed: String? = null,
    val weightAtTime: Double? = null,
    val bodyWeightKg: Double? = null
)
```

## 6. Implementation

**Step 1 — `BackupPayload.kt`.** Apply the declarations above. Append new fields only; do
not reorder existing ones — Gson matches by name, but the positional constructor calls in
`BackupRepositoryImpl.backupToDrive` are positional and reordering would silently
mis-assign. Extend the existing nullability comment to cover the new fields so the next
person does not "tidy" them into non-null.

**Step 2 — `BackupRepositoryImpl.backupToDrive`.** Fill the new fields from the entities.
Switch the four widened DTOs from positional to **named arguments** while you are in there;
these constructors are now long enough that a positional mistake would be invisible.
Set `schemaVersion = 1` on the payload. Keep writing `standardizedScore = it.percentile?.toDouble()`
so a new backup stays readable by an older build.

**Step 3 — `BackupRepositoryImpl.restoreEntities`.** Read the new fields with explicit
fallbacks that preserve today's behaviour for a legacy payload:

- `TestingEventEntity`: `groupId = it.groupId`, `location = it.location`,
  `createdAt = it.createdAt ?: it.timestamp`.
- `IndividualEntity`: `medicalAlert = it.medicalAlert`, `isRestricted = it.isRestricted ?: false`,
  `email = it.email`, `isActive = it.isActive ?: true`, `isDeleted = it.isDeleted ?: false`,
  `createdAt = it.createdAt ?: System.currentTimeMillis()`, `updatedAt` likewise.
- `GroupEntity`: `location = it.location`, `cycle = it.cycle`,
  `category = it.type.takeIf { t -> t.isNotBlank() }` — stop storing `""` where `null` is meant.
- `TestResultEntity`: `ageAtTime = it.ageAtTime ?: 0f`,
  `percentile = it.percentile ?: it.standardizedScore?.toInt()` — the `standardizedScore`
  fallback **recovers the percentile from every backup already in Drive**, which is the
  cheapest win in this change. Plus `classification`, `normVariantUsed`, `weightAtTime`,
  `bodyWeightKg` straight through.

No change to `BackupDao.restoreAllData`. Its insert order is already correct and the
transaction boundary is already right.

**Step 4 — restore-time integrity guard.** A restored event whose `groupId` names a group
not present in the payload would hit the `SET NULL` foreign key and silently detach again.
Before inserting, null out any `groupId` not in the restored group set, and count them. If
the count is above zero, that is worth surfacing rather than swallowing — thread it back
through `restoreFromDrive` so the UI can say "N events could not be matched to a group".
If you would rather keep the return type as `Unit` for now, log it and note the follow-up;
do not drop the check.

## 7. Constraints for this change

- A field added non-null here is a crash on every pre-existing Drive backup. Nullable plus
  `?:` is the whole contract.
- `restoreAllData` must stay one transaction. The existing test exists because a partial
  restore once wiped a coach's data.
- Do not start backing up the seeded catalog. `BackupDao.getUserTests()` and friends filter
  `source = 'USER'` on purpose.
- Medical alert and restriction flags are the reason this is not a cosmetic fix. Whatever
  else gets deferred, those two round-trip.

## 8. Verification

**Unit (`BackupRepositoryImplTest`, Robolectric, extends the existing harness):**

1. `restore_preservesEventGroupAssociation` — seed a group and an event with `groupId`, run
   `backupToDrive`'s mapping into a payload, `restoreEntities`, assert
   `getEventsForGroup(groupId)` returns the event. This is the regression that matters; it
   fails today.
2. `restore_preservesMedicalAlertAndRestriction` — athlete with `medicalAlert = "Asthma"`,
   `isRestricted = true`, assert both survive.
3. `restore_preservesPercentileAndAgeAtTime` — result with `percentile = 92`,
   `ageAtTime = 14.5f`, assert both survive.
4. `restore_legacyPayloadWithoutNewFields_doesNotThrow` — build the JSON **as a string with
   the new keys absent** and deserialize it with Gson, rather than constructing the DTO in
   Kotlin. Constructing it in Kotlin applies the defaults and tests nothing; the whole risk
   is Gson's constructor bypass.
5. `restore_legacyPayload_recoversPercentileFromStandardizedScore` — legacy JSON with
   `standardizedScore = 92.0` and no `percentile`, assert `percentile == 92`.
6. `restore_eventWithUnknownGroup_detachesAndCounts` — covers step 4.

**Build:** `gradlew test` then `gradlew assembleDebug`.

**Manual, on device:** register an athlete with a medical alert, put them in a group with a
cycle and location, run a testing event against that group, record a result that scores a
percentile. Back up. Clear app data (or use a second device with the same signing key and
account). Restore. Then check, in order: the group appears with its cycle and location; the
event appears **under that group**; the result shows a coloured zone, not grey; the athlete
still shows the medical alert. Each of those four is a separate bug above and they fail
independently.

---

**Note on why this went unnoticed:** the round trip was never tested end to end. The
existing test covers transaction atomicity — that a *failed* restore does not destroy data —
which is why the dangerous version of this bug was caught while the quiet version was not.
Test 1 is the one to write first.
