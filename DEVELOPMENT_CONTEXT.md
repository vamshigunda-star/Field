# Field — Development Context

The architecture reference for **Field**, an offline-first Android app for coaches and PE
teachers to run fitness tests, record scores against published norms, and track athletes over
time. One coach per device; no server, no login password.

`CLAUDE.md` holds the short rules every change must follow. This file explains the system those
rules protect. When the two disagree, the code is right and both docs need fixing.

Last verified against the code: **2026-10-14** (Room v17, catalog `v32`, app 1.0.1, pre-release
on Play).

---

## 1. Stack

| Area | Choice |
|---|---|
| Language / UI | Kotlin, Jetpack Compose, Material 3 (BOM 2026.09.00 → material3 1.4, adaptive 1.3) |
| Build | AGP 9.1 (supplies Kotlin itself — never apply `org.jetbrains.kotlin.android`), Gradle 9.7, KSP |
| DI | Hilt |
| Persistence | Room 3 (`androidx.room3`), schema exported to `app/schemas/`, explicit migrations only |
| Backup | Google Drive app-data folder, JSON via Gson (`domain/model/backup/`) |
| SDK | minSdk 26, targetSdk 36, compileSdk 37 |

`com.google.android.material` (XML) is **not** dead weight: the activity theme parent
`Theme.Material3.DayNight.NoActionBar` comes from it. Gson is **not** redundant with
kotlinx-serialization: Drive backups and `CustomPresetsStore` depend on it.

The build runs `tools/build_prepackaged_db.py` (Python must be on PATH) to regenerate
`assets/database/alearning.db` from the CSVs on every `preBuild`.

---

## 2. Architecture

```
Presentation (ui/) ──> Domain (domain/) <── Data (data/)
```

- **`domain/`** — pure Kotlin: models, repository interfaces, use cases. No Android, no Room.
  `java.util` is fine. Formatting helpers that both layers need (score text, ordinals) live here.
- **`data/`** — Room entities/DAOs, repository implementations, entity↔domain mappers, CSV
  seeding, Drive backup.
- **`ui/`** — Compose screens and Hilt ViewModels.
- **`util/`** — Android-side helpers outside the layers (`CsvExporter`).

### ViewModel contract
- One `StateFlow<XxxUiState>`; state holds domain models and primitives, with
  `isLoading` and `errorMessage`.
- One `onAction(XxxAction)` taking a sealed interface. Screens receive `uiState` + `onAction`,
  never the ViewModel.
- Mutate with `_uiState.update { it.copy(...) }`. Dialog/sheet visibility is UiState.
- ViewModels never navigate. Nav-only actions (e.g. `TestLibraryAction.OnCreateTest`) are
  intercepted by the screen's `onAction` wrapper and are a no-op in the ViewModel's `when`.
- Route arguments arrive through `SavedStateHandle`.

**Known debt:** the rule is "ViewModels depend on use cases only", but ten ViewModels
(Dashboard, Roster, QuickTest, Leaderboard, SessionReport, GroupOverview, AthleteDashboard,
AthleteTestDetail, ReportsHub, Settings) inject `domain.repository` interfaces directly. That is
inside the domain boundary, so it is tolerated; new code should add a use case instead.

---

## 3. Screens and navigation

Routes are in `ui/navigation/Screen.kt`, wired in `ui/navigation/NavGraph.kt`. Bottom nav:
**Home · Roster · Tests · Reports**.

| Screen | Route | Purpose |
|---|---|---|
| Onboarding | `onboarding` | Coach name + optional email. No password (see §8). |
| RestoreBackup | `restore_backup` | Pre-onboarding Drive restore for a reinstalling coach. |
| Dashboard | `dashboard` | Home: stats, getting-started card, start group event / individual test. |
| Roster | `roster` | Groups and athletes; list-detail with the athlete dashboard on tablets. |
| TestLibrary | `test_library` | Catalog by category, test detail, video, **New Test** FAB. |
| CustomTest | `custom_test?testId=` | Custom Test Builder. No id = create, id = edit a USER test. |
| Recommendations | `recommendations` | Suggested test batteries by setting (school level etc.). |
| CreateEvent | `create_event?recommendationId=` | Name an event, pick a group and tests. |
| TestingGrid | `testing_grid/{eventId}/{groupId}` | Athletes × tests grid; tap a cell to score. |
| Stopwatch | `stopwatch/{eventId}/{fitnessTestId}/{groupId}?athleteId=&timingMode=` | Timed tests, individual or heats. |
| QuickTest | `quick_test?athleteId=&testIds=&eventId=&mode=` | "Individual Test": one athlete, several tests. |
| Report | `reports` | Reports hub (Athlete Profile and Event Report tabs). |
| GroupOverview | `group/{groupId}` | Group health, sessions, trends. |
| SessionReport | `group/{groupId}/session/{sessionId}` | One event: leaderboard per test, absentees, flags, CSV export. |
| AthleteDashboard | `athlete/{athleteId}?contextSessionId=` | Athlete profile, radar, test tiles. |
| AthleteTestDetail | `athlete/{athleteId}/test/{testId}?contextSessionId=` | One test's history, norm-band chart, attempts. |
| Leaderboard | `leaderboard/{eventId}/{groupId}/{mode}` | Ranked results for an event or all-time. |
| Settings / HowToUse | `settings`, `how_to_use` | Backup/restore, guides. |

Reports drill-down: Reports hub → Group → Session → Athlete → Athlete×Test.
**Shared-body trap:** `SessionReportBody` and `AthleteBody` render both inside the hub's tabs and in
their standalone screens. UI added to one entry point usually needs adding to the other.

Adaptive layouts use `NavigableListDetailPaneScaffold`. On a phone the detail pane replaces the
list, so any top bar outside the scaffold must offer Back when
`navigator.canNavigateBack()` (see `TestLibraryScreen`, `RosterScreen`). adaptive 1.3 auto-focuses
the destination pane; a list pane that starts with a text field needs
`shouldAutoFocusCurrentDestination = false`.

---

## 4. Domain model

| Model | Notes |
|---|---|
| `Individual` | Athlete. `dateOfBirth` is epoch millis — **negative before 1970**. `currentAge` is the one age calculation (whole years). |
| `Group` | Squad/class; many-to-many with athletes. |
| `FitnessTest` | `unit`, `isHigherBetter`, `inputParadigm`, `timingMode`, `validMin/validMax`, `interpretationStrategy`, `source` (SEED/USER), `youtubeId`. |
| `NormReference` | Test × sex × age range × score range → percentile + classification. |
| `TestingEvent` | A session; tests attached via `event_test_cross_ref`. |
| `TestResult` | `rawScore`, `ageAtTime`, stored `percentile`/`classification` snapshot, `captureMethod`. |

`InputParadigm` decides how a score is captured and printed:
`NUMERIC` keypad · `INCREMENTAL` +/- counter · `CHRONO` stopwatch · `MULTI_STAGE` level/shuttle
picker · `SCALE`. Custom tests choose a `MeasurementMethod` (Keypad / Counter / Stopwatch / Levels)
that maps onto paradigm + timing mode (`domain/model/standards/CustomTestDraft.kt`).

Use-case packages: `auth`, `backup`, `people`, `reports`, `standards`, `testing`
(`domain/usecase/*`). Score recording always goes through `RecordTestResultUseCase`, which range-
checks against `validMin/validMax` and calls `CalculatePercentileUseCase` for `NORM_LOOKUP` tests.

---

## 5. Data rules that look like trivia but are load-bearing

### 5.1 Performance zones
Superior ≥ 80 · Healthy 40–79 · Needs Improvement < 40 · Grey = no norm matched (absence of data,
not a fourth category). Numbers live only in `domain/model/reports/PerformanceThresholds.kt`.
Composables get colours from `performanceZoneColors(percentile)` in
`ui/theme/PerformanceZoneColors.kt`. An inline percentile threshold or a hard-coded colour for a
classification anywhere under `ui/` is a defect — four screens once used four different cut-offs.

### 5.2 Age
- Age is **whole completed years**: `Individual.currentAge`. Do not re-derive it from millis.
- `StandardsRepositoryImpl` floors any age before querying norms. Norm brackets are integers with
  gaps between rows (5–12, 13–19…), so a fractional 12.6 matched no row and fell through to a
  closest-age fallback that graded the child against 13–19 norms. Reports re-grade from the stored
  `ageAtTime`, so the floor must stay at the lookup boundary, not only at capture.
- Brackets (`domain/model/people/AgeBracket.kt`): Childhood 5–12, Adolescence 13–19,
  Adults 20–40, Adults 41–62, Older Adults 63–115.
- Date of birth: future dates are rejected (use case + date picker). Do **not** reject
  `dateOfBirth <= 0` — that is every birth date before 1970.

### 5.3 Multi-stage scores (Beep, Yo-Yo, custom "Levels" tests)
Stored as one Double: `level + shuttle / 100`. Level 8 shuttle 1 = `8.01`, level 8 shuttle 10 =
`8.10`. It still sorts, ranks and range-checks as a number. Coaches read and type `level.shuttle`
text (`"8.10"`).

- `domain/model/standards/ScoreFormat.kt` owns this: `StageScore`, `formatScore(raw, paradigm)`,
  `parseScore(text, paradigm)`, `scoreInputText(raw, paradigm)`.
- **Never print a `rawScore` with `%.1f` or `toString()`.** Use `formatScore` with the test's
  paradigm; a stage score printed as a decimal reads 8.1 for shuttle 10. Display models that carry
  a raw score also carry `inputParadigm` (`LeaderboardRow`, `LeaderboardEntry`,
  `RecordedTestResult`) for this reason.
- **Never parse score text with `toDouble()`.** Use `parseScore`; `"8.10".toDouble()` is shuttle 1.
- Averages of stage scores are meaningless (8.10 and 9.05 "average" to level 8 shuttle 58); the
  session summary shows the median for stage tests.
- CSV export writes `Level 8, Shuttle 10` because spreadsheets turn `8.10` into 8.1.
- `norms.csv` and `tests.csv` use the same encoding for Beep/Yo-Yo bounds (e.g. Beep max 21.16).
- Shuttles are capped at 99 (two digits of the encoding).

### 5.4 Negative scores
Reach tests (Toe Touch, Shoulder Flexibility, Back Scratch, Trunk Flexion) record a gap as a
negative number. The keypad shows `±` when the test's `validMin < 0`; the Custom Test Builder keeps
a leading minus.

### 5.5 Formatting helpers
- Ordinals: `ordinal(n)` in `domain/model/reports/Ordinal.kt` (71st, 92nd, 11th). Never append
  `"th"`. `HeroNumber(ordinal = true)` keeps the suffix right while the number animates.
- Plurals: `countOf(n, "athlete")` in `ui/util/Plurals.kt`.
- Locale-aware number formatting in Compose: `LocalLocale.current.platformLocale` (lint
  `NonObservableLocale`).

---

## 6. Database (Room v17)

Tables: `individuals`, `groups`, `group_members`, `test_categories`, `fitness_tests`,
`norm_references`, `testing_events`, `event_test_cross_ref`, `test_results`, `users`,
`recommendation_categories`, `recommendation_tests`, `catalog_metadata`.

- Schemas are exported to `app/schemas/com.vamshi.field.data.AppDatabase/<n>.json` and must be
  committed — `MigrationTest` (androidTest) needs the before and after JSON.
- Migrations are explicit. `fallbackToDestructiveMigration()` was removed on purpose; never re-add.
- `test_results.testId` is a RESTRICT foreign key: a catalog test with results can't be deleted,
  only archived (`isDeleted = 1`). `getTestById` must not filter archived tests — reports name them.
- `pending_test_entries` was dead scaffolding, dropped in v16. Don't resurrect it.

### Seeding and the catalog
The catalog source of truth is the CSVs in `app/src/main/assets/`: `test_categories.csv`,
`tests.csv`, `norms.csv`, `recommendation_categories.csv`, `recommendation_tests.csv`.

1. `build_prepackaged_db.py` builds `assets/database/alearning.db` from them (Room loads it with
   `createFromAsset`) and stamps `catalog_metadata.catalog_version`.
2. On launch `SeedDataManager` skips CSV import when that stamp equals its `CATALOG_VERSION`
   (fast fresh install); otherwise, or when the `KEY_SEEDED_VERSION` pref is unset, it re-imports.
3. Re-import upserts categories/tests and replaces norms and recommendations **scoped to
   `source = 'SEED'`**. It never touches events, results, athletes, groups, or USER tests/norms.

**Whenever a CSV changes:** bump `CATALOG_VERSION` in both `SeedDataManager` and the script, and
`KEY_SEEDED_VERSION` (currently `v32` / `data_seeded_version_v32`). **Whenever the schema changes:**
build once to export the JSON, copy its `identityHash` into `ROOM_IDENTITY_HASH` in the script, and
regenerate — Room refuses an asset whose hash disagrees.

### Norm data contract
Each `norms.csv` row's percentile must classify to its own label: Needs Improvement 20, Healthy
Fitness Zone 60, Superior 90. Coverage per test: 2 sexes × 5 brackets × 3 classes = 30 rows.
`SeedNormsConsistencyTest` enforces it. Known gap: 11 adult-only tests keep the source decade
bands and have no under-20 norms — a data decision, not a bug.

Custom-test norms: two coach cut points → three bands (20/60/90). "Same standard for all athletes"
writes MALE + FEMALE + UNSPECIFIED rows. Bands are contiguous; `findNormResult` orders by
percentile DESC to break boundary ties.

An empty or grey report almost always means **no norm matched** for that test/sex/age — check the
`CalculatePercentile` log tag before debugging UI.

---

## 7. Key flows

### Score entry (grid and Individual Test)
```
Cell tap → ScoreEntryDialog / QuickScoreEntryDialog
  → TestInputSwitcher (keypad | counter | stage picker), text in scoreText
  → parseScore(scoreText, paradigm) → range check → onSave(Double)
  → ViewModel → RecordTestResultUseCase(rawScore, ageAtTime = currentAge, sex)
      → validMin/validMax check → CalculatePercentileUseCase (whole-year age)
      → insert TestResult with percentile/classification snapshot
```
Timed tests (`timingMode != MANUAL_ENTRY`) route to the Stopwatch instead. Stopwatch results are
held in the ViewModel (`sessionResults`) and the Room Flow is only backfill — don't make post-write
UI depend on a Flow re-emitting; it can lag or miss an invalidation.

Stored percentiles are a snapshot at recording time. Changing norms does not regrade history.

### Auth gate
`AuthGateViewModel` → `ResumeSessionUseCase`: an existing coach on the device → Dashboard, none →
Onboarding (5 s timeout falls back to Onboarding). Field has **no password, lock screen or
sign-out by design** — a forgotten password must never lock a coach out of athlete data; the
device lock is the boundary. `users.passwordHash/passwordSalt` are vestigial, kept to avoid a
schema bump and for backup compatibility.

### Backup / restore
`BackupDataUseCase` / `RestoreDataUseCase` serialise to JSON (Gson) in the Drive app-data folder.
- Gson bypasses Kotlin constructor defaults: a new payload field missing from an older backup comes
  back `null` through a non-null type. New fields must be nullable and read with `.orEmpty()`.
- `BackupJsonContractTest` pins key names. Backup DTOs and `CustomPresetsStore.StoredPreset` need
  R8 keep rules in `app/proguard-rules.pro`. Release builds are minified; debug is not — verify
  backup/restore on a release APK.
- Backups carry USER catalog rows and restore them before results (the RESTRICT FK would otherwise
  abort the restore).

### Video
`TestVideoPreview` → `VideoPlayerModal` (WebView IFrame player). The `https://<packageName>` origin
passed to `loadDataWithBaseURL` and the player is load-bearing; YouTube rejects a missing or
spoofed origin. Dead video IDs still serve a grey thumbnail — verify `youtube_id`s with the oEmbed
endpoint, not the thumbnail.

---

## 8. Design system

- Every screen uses `ui/components/AppTopBar.kt`. Trailing actions use `AppTopBarActionButton`.
- Colour tokens only (`surface`, `onSurface`, `onSurfaceVariant`, `primary`, `outline`,
  `SportOrange`…). `ui/theme/Color.kt` is generated from `design.md` by `generateColorsFromDesign`.
- Filter chips: `ui/components/AppFilterChip.kt`. M3 default SegmentedButton/FilterChip colours
  render lavender in this theme — give them explicit colours.
- Motion: `ui/theme/Motion.kt` (`FieldMotion`). material3 1.4's Expressive motion APIs are internal.
- `ModalBottomSheet` already insets for the keyboard (its default `contentWindowInsets` includes the
  IME). Make sheet content scrollable; adding `imePadding()` on top double-pads.
- Compose draws children in order and hit-tests the last-placed child first, so an `IconButton`
  placed after an `AndroidView` in a `Box` does receive taps.
- Lazy lists use stable keys (`key = { it.id }`); O(N) derivations belong in the ViewModel.
- English only, by decision.

---

## 9. Testing and verification

```
gradlew assembleDebug            # also regenerates the prepackaged DB
gradlew testDebugUnitTest        # JVM unit tests (~160)
gradlew connectedAndroidTest     # instrumented, incl. MigrationTest
gradlew lint
```

- Domain rules have unit tests beside them (`app/src/test/.../domain/...`). Fakes:
  `FakePeopleRepository`, `FakeTestingRepository`, `FakeStandardsRepository`.
- A build failure in `mergeDebugJavaResource … zip-cache` is stale incremental state, not code:
  delete `app/build/intermediates/incremental/debug-mergeJavaRes`.
- First launch takes ~6 s in debug; that is not a hang.
- Drive the real app for anything involving score entry, sheets, or navigation — several defects
  in this file were invisible to unit tests.

---

## 10. Reviewing audit reports

Automated audits of this repo have repeatedly reported things that are not defects. Verify each
claim against the code before acting:

- "Remove `com.google.android.material` / Gson" — both are used (§1).
- "Add `imePadding()` to bottom sheets" — double-pads (§8); the fix is scrolling.
- "WebView intercepts the overlaid close button" — Compose hit-test order says otherwise (§8).
- "Room DB vN" — read `AppDatabase.kt`; audits often quote a stale version.
- Repo-root clutter (`room3-*.jar`, `.bak-*/`, scratch files) is untracked local state, not part of
  the build. Ask before deleting; it is unrecoverable.

Conversely, audits have found real crashes (the stage picker parsing `""`), so read them — just
don't apply their fixes blind.
