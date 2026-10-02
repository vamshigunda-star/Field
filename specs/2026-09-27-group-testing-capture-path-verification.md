# Spec: Verifying every test in the group testing pipeline (and catching the crash-on-select)

## Status — 2026-09-27

**Tiers 1 and 2 are built and green.** 16 new tests, full suite at 128 tests / 0 failures.

| Tier | File | State |
|---|---|---|
| 1 | `app/src/test/.../standards/TestCatalogCapturePathTest.kt` | Done — 10 tests |
| — | `app/src/test/.../standards/TestCatalogCsv.kt` | Done — shared loader for the real 83-row catalog |
| — | `app/src/test/.../testing/FakeTestingRepository.kt`, `FakePeopleRepository.kt` | Done |
| 2 | `app/src/test/.../ui/testing/GroupTestingSelectAllTestsTest.kt` | Done — 6 tests, each looping all 83 |
| 3 | `TestingGridCaptureTest.kt` (instrumented) | Not started — optional, see 5.4 |
| 5.0 | Crash capture via logcat | **Waiting on a device reproduction** |

### Second pass — 2026-09-27, after the norms edit

Catalog stamp bumped to **v31** (`build_prepackaged_db.py` + `SeedDataManager`, both keys) and
`app/src/main/assets/database/alearning.db` regenerated: 83 tests, 2483 norm rows, schema v17,
identity hash unchanged. Suite at **130 tests / 0 failures**.

Applied:

- **`canUseStopwatch` now requires `InputParadigm.CHRONO`** as well as `isTimeBased`
  (`domain/model/standards/FitnessTest.kt`). Exactly one test changes routing —
  `test_light_response` — and it changes to the correct one. 40 stopwatch-routed tests become 39.
- **`norms.csv` line 122 held `maxScore = "2099 m"`.** The two importers disagreed on it:
  `build_prepackaged_db.py` regex-extracts the leading number (2099.0), `SeedDataManager` uses
  `toDoubleOrNull() ?: 999.0` (999.0). So a fresh install and an upgrade graded the same Cooper
  run differently. Corrected to `2099`, which is what the prepackaged DB already contained, and
  pinned by a new assertion over every numeric norm column.

Still open — see the issue list handed to the owner: the `test_1_5_mile_run` / `test_1_mile_run`
norm mix-up, the ghost `test_1_5_km_run` row on upgrades, two scratch CSVs shipping in the APK,
and the crash itself.

### Two defects found, neither of them the crash

**1. `test_light_response` is unrecordable through its default tap path.** Its unit is `ms`, and
`FitnessTest.canUseStopwatch` keys off the unit alone — `ms` is in the time-unit list — so tapping
its cell routes to the stopwatch even though its declared paradigm is `NUMERIC`. The stopwatch
computes `rawScore = elapsedMs / 1000.0` unconditionally, so it always submits **seconds**, while
the test's valid range is 120–800 **milliseconds**. `RecordTestResultUseCase` throws on every such
save; the throw is caught; the coach gets "Save failed" forever. Reachable only by long-pressing
to force manual entry.

*Candidate fix (not applied):* make `canUseStopwatch` require `inputParadigm == CHRONO` as well as
`isTimeBased`. Exactly one catalog test changes behaviour — this one — and it changes to the
correct one. Cheap, but it is a behaviour change, so it is the owner's call.

**2. `test_pull_up` and `test_1_5_km_run` declare `NORM_LOOKUP` but have zero rows in
`norms.csv`.** They score fine and then never interpret: grey cell, no report band, forever. This
is distinct from the documented age-bracket gap, which is about *which* brackets exist rather than
whether any rows do. It is a data decision, so it is pinned rather than fixed.

Both are pinned as exact-set assertions, so the suite fails if either set grows *or* if one is
fixed without updating the test.

## 0. Open question (answer changes scope)

Two things are being asked at once, and they have different answers:

1. **"Can Claude tap through the app entering dummy data like I would?"** — Yes, technically, via
   adb + UI Automator against the emulator. No, it should not be the primary strategy. Section 3
   explains why and what it costs.
2. **"When I select a specific test the app switches off."** — This is a crash, and it cannot be
   diagnosed from reading the code alone. I traced the whole select path (section 3.3) and every
   throw site I found is already caught. That means the crash is either in the Compose render
   phase (which no ViewModel `try/catch` can contain), or in a path that depends on data I do not
   have — *which* test, and whether that athlete already had a saved result.

**The document below assumes both**: section 5.0 is the two-minute logcat capture that turns the
crash into a stack trace, and sections 5.1–5.3 are the harness that stops it recurring across all
83 catalog tests. If you already know which test kills the app, say so — it collapses section 5.0
and makes the fix a one-file change.

## 1. Objective & scope

- **As a coach**, I want confidence that every test I put on a testing event can actually be
  scored and saved, rather than discovering mid-session on a field that one of them closes the app.
- **In scope:**
  - A repeatable capture procedure for the crash (adb logcat, one reproduction).
  - Tier 1 — a JVM test over `assets/tests.csv` asserting every catalog row can form a valid
    capture path (paradigm, timing mode, heat size, valid range, trial count).
  - Tier 2 — a Robolectric test driving `TestingGridViewModel` + `StopwatchViewModel` over **all
    83 tests**, which is the layer where "selecting test X explodes" lives.
  - Tier 3 — one Compose instrumented test per input paradigm (4 total) that types a dummy score
    and asserts it persists.
- **Out of scope:**
  - Fixing the crash. It is not identified yet, and specifying a fix for an undiagnosed defect is
    how you get a confident patch to the wrong file.
  - Full-app UI regression, screenshot testing, CI wiring.
  - Any change to the 83-row catalog itself.

## 2. Verified context

Files read, and what each confirmed:

| File | Confirmed |
|---|---|
| `ui/testing/TestingGridViewModel.kt` | `TestingGridUiState` / `TestingGridAction` shape; `saveScore` and `deleteResult` both wrap in `try/catch` and surface `errorMessage` + `failedAction`. |
| `ui/testing/TestingGridComponents.kt:338` | `handleCellAction` — the select-a-test branch. `canUseStopwatch` routes to the stopwatch route, otherwise to the inline editor. |
| `ui/testing/TestingGridComponents.kt:243` | `gridData.tests.getOrNull(idx) ?: gridData.tests.first()` — guarded one line earlier by `if (gridData.tests.isEmpty()) return`, so not a crash today, but it is a `first()` kept alive only by a distant guard. |
| `ui/navigation/Screen.kt:40` | Stopwatch route is string-interpolated with **no URL encoding**. Safe for the shipped catalog (all ids are `test_[a-z0-9_]+`), but see Drift. |
| `domain/usecase/testing/StopwatchSessionUseCase.kt:35` | `athletesPerHeat ?: 6` then `chunked(perHeat)` — `chunked(0)` would throw; the 5 catalog rows with a blank value arrive as null, so `?: 6` covers them. Verified against the CSV, not assumed. |
| `domain/usecase/testing/RecordTestResultUseCase.kt` | **Throws** `IllegalArgumentException` for a score outside `validMin`/`validMax`. Both call sites (grid and stopwatch) catch it. |
| `ui/components/testing/TestInputModules.kt:30` | `when (paradigm)` handles NUMERIC / INCREMENTAL / MULTI_STAGE with an `else` fallback — `CHRONO` and `SCALE` fall to the numeric keypad rather than crashing. |
| `data/mapper/standards/FitnessTestMapper.kt` | All four enum parses are `try/catch` with defaults; a malformed catalog row degrades, it does not throw. |
| `app/build.gradle.kts` + `gradle/libs.versions.toml` | Everything the harness needs is already on the classpath. |
| `app/src/test/.../SeedNormsConsistencyTest.kt` | The precedent for a JVM test that reads `app/src/main/assets/*.csv` from the source tree. Tier 1 copies its file-locating idiom verbatim. |
| `app/src/androidTest/.../ui/ResponsiveGridTest.kt`, `HiltTestRunner.kt` | The Compose-test precedent and the Hilt runner already registered as `testInstrumentationRunner`. |

Catalog shape, counted from `app/src/main/assets/tests.csv` (83 rows):

- `inputParadigm`: CHRONO 39, NUMERIC 25, INCREMENTAL 17, MULTI_STAGE 2. `SCALE` is declared in
  the enum and used by **zero** rows.
- `timingMode`: MANUAL_ENTRY 44, INDIVIDUAL 33, GROUP_START 6.
- `athletesPerHeat`: 1 ×71, 8 ×6, 6 ×1, **blank ×5** (`test_bmi`, `test_waist_circumference`,
  `test_waist_to_hip_ratio`, `test_bess`, `test_handgrip`).
- `trialsPerAthlete`: 1–5, never 0. `validMin`/`validMax`: never blank.
- `interpretationStrategy`: `NORM_LOOKUP` on all 83. `calculationConfig`: blank on all 83.
- `youtube_id`: blank on 6 rows.

**Drift noticed** — observations, not scope:

- `TestingGridViewModel` injects `TestingRepository` and `PeopleRepository` directly alongside its
  two use cases. That is the known house-wide violation; the harness works with it rather than
  triggering a refactor.
- `TestingGridComponents.kt` uses `items(gridData.students.size)` with no `key`, which CLAUDE.md
  forbids for exactly the athletes × tests screen this is.
- `Screen.Stopwatch.createRoute` does not encode its path segments. Coach-authored custom tests get
  UUID ids so they are safe too, but the guarantee is incidental, not enforced.
- The percentile drift the project docs warn about (60/30 inline in `QuickTestScreen` and
  `TestingGridComponents`) is **already fixed** — both call `performanceZoneColors(...)` from
  `ui/theme/PerformanceZoneColors.kt`. The doc is stale, the code is right.
- No schema change is implied anywhere in this spec. The database stays at its current version and
  no migration is needed.

## 3. Why blind tapping is the wrong primary strategy

### 3.1 What Claude can actually do on the emulator

With `adb` (on `D:\` per the device-testing notes) Claude can install a debug build, launch it,
dump the view hierarchy with `uiautomator dump`, tap coordinates, type text, and read logcat. So
"go through each feature entering dummy data" is genuinely possible.

The costs, stated plainly:

- **It is blind.** Compose renders to a canvas; `uiautomator dump` returns a semantics tree that is
  often sparse unless nodes carry `contentDescription` or `testTag`. Many cells in the testing grid
  come back as untitled nodes at coordinates, so every tap is a coordinate guess that breaks when
  the layout shifts by 8dp.
- **It is slow.** One athlete × one test is roughly six round trips (dump, tap cell, dump, tap
  keypad digits, tap submit, dump to verify). 83 tests is several hundred round trips — tens of
  minutes of wall clock and a large amount of context, for one pass on one device size.
- **It is not repeatable.** Nothing is asserted, nothing reruns, and the next change re-spends the
  whole cost.
- **It proves less than it looks like it does.** A tap that does not crash proves that test did not
  crash *in that state* — not that the score was saved, interpreted, or still there after rotation.

So: worth doing **once**, deliberately, to capture the crash (section 5.0). Not worth doing as the
way you gain confidence in 83 tests.

### 3.2 What the bug's shape argues for

"Sometimes when I select a specific test the app switches off" is a **data-driven** bug: the code
path is identical for every test, and only the row differs. The space is 83 rows × 4 paradigms × 3
timing modes × (saved result / no saved result) × (tap / long-press). That is a combinatorial space
a human cannot walk but a parameterized test walks in seconds.

This is the whole argument for the tiered harness: write the loop once, let it enumerate the
catalog, and every test in the app is exercised on every run forever.

### 3.3 What the select path does, and where it is and is not guarded

Tapping a cell in the grid runs `handleCellAction` (`TestingGridComponents.kt:338`):

```kotlin
if (test.canUseStopwatch) {
    // -> OnNavigateToStopwatch(eventId, test.id, groupId, athlete.id, mode.name)
} else {
    // -> OnStartEditing(athlete, test)   // inline editor + TestInputSwitcher
}
```

Traced through both branches:

- **Verified:** `StopwatchViewModel.loadSession()` wraps everything in `try/catch (e: Exception)`.
  `RecordTestResultUseCase`'s range `IllegalArgumentException` is caught at both call sites.
  `FitnessTestMapper` never throws on a bad enum. `chunked(0)` is unreachable for the shipped
  catalog. The paradigm `when` has an `else`.
- **Inferred, not verified:** because every throw I can see is caught, the crash most likely
  happens **during composition** — an exception thrown while a composable lays out is not contained
  by any of those `try/catch` blocks and takes the process down. The grid's cell rendering and the
  stopwatch screen's trial list are the two candidates, and both depend on per-test data (`unit`,
  `trialsPerAthlete`, saved-result presence). I am not going to name a line as the cause without
  the stack trace; that is what section 5.0 is for.

This distinction matters: a spec that guessed a line here would send an implementer to patch code
that may be fine.

### 3.4 Tools evaluated

**`panicgit/android-test-pilot`** (evaluated 2026-09-27, not adopted). A Claude Code plugin and MCP
server, Apache-2.0, forked from mobile-mcp, that drives a device over adb in three tiers —
`dumpsys`/`logcat` text, then accessibility tree, then screenshots — and executes scenarios written
as natural-language markdown. It does not generate test code.

Not adopted, for three reasons:

- Its cheap text tier requires the app's source to carry its own instrumentation logs
  (`ATP_SCREEN`, `ATP_RENDER`, `ATP_API`); without them it degrades to accessibility tree and
  screenshots. Field has just finished *removing* stray `android.util.Log` calls, so this runs
  directly against the grain of that cleanup.
- Scenarios execute against a live device each run. That is a better-ergonomics manual walk, not
  83 tests asserted in seconds that rerun after every refactor.
- 0 stars, 1 fork, last push 2026-04-21, single unknown author. Field is meant to be picked up by
  an unknown future maintainer, so a source-level dependency on an unmaintained tool is the wrong
  trade.

Where it would genuinely help is the exploratory half — reproducing a crash, poking a screen
nobody has automated. For that, plain `adb logcat` (section 5.0) gets the same stack trace with
nothing installed.

## 4. Files

| Action | Path | Purpose |
|---|---|---|
| NEW | `app/src/test/java/com/vamshi/field/domain/model/standards/TestCatalogCapturePathTest.kt` | Tier 1 — JVM invariants over `assets/tests.csv`. |
| NEW | `app/src/test/java/com/vamshi/field/domain/usecase/testing/FakeTestingRepository.kt` | In-memory `TestingRepository`, mirroring `FakeStandardsRepository`'s style. |
| NEW | `app/src/test/java/com/vamshi/field/domain/usecase/testing/FakePeopleRepository.kt` | In-memory `PeopleRepository`, same style. |
| NEW | `app/src/test/java/com/vamshi/field/ui/testing/GroupTestingSelectAllTestsTest.kt` | Tier 2 — Robolectric; selects and scores every catalog test through the two ViewModels. |
| NEW | `app/src/androidTest/java/com/vamshi/field/ui/testing/TestingGridCaptureTest.kt` | Tier 3 — Compose; one dummy-data entry per input paradigm. |
| MODIFY | `app/src/main/java/com/vamshi/field/ui/testing/TestingGridComponents.kt` | Add `testTag`s to the cell, keypad and submit control so Tier 3 (and any future automation) can address them by name instead of coordinates. Also add the missing `key` to `items(...)`. |

## 5. Implementation

### 5.0 First — capture the crash (do this before writing any test)

One reproduction on the emulator, with logcat already running:

```
D:\platform-tools\adb.exe logcat -c
D:\platform-tools\adb.exe logcat AndroidRuntime:E *:S
```

Reproduce by hand: open the group testing event, select the test that kills the app, note **which
test**, and whether that athlete already had a saved score. The `FATAL EXCEPTION` block names the
composable or ViewModel frame, and the fix follows from it directly. Paste that block here and the
remaining work is usually one file.

If it is intermittent, `adb logcat -b crash` after the fact still holds the last crash.

### 5.1 Tier 1 — catalog invariants (JVM, ~1 second)

Copy the file-locating idiom from `SeedNormsConsistencyTest` so it stays a plain JVM test with no
Android assets. Parse `app/src/main/assets/tests.csv` with a quote-aware split — descriptions
contain commas, and a naive `split(",")` mis-columns two rows. Assert, per row:

- `inputParadigm` and `timingMode` parse to real enum constants — a typo here silently degrades to
  `NUMERIC`/`MANUAL_ENTRY` via the mapper's `catch`, which is exactly the kind of "this test does
  the wrong thing but doesn't crash" defect that never gets reported.
- `athletesPerHeat` is blank or `>= 1` — pins the `chunked(0)` hazard so a future catalog edit
  cannot reintroduce it.
- `trialsPerAthlete >= 1`, `validMin < validMax`, both present.
- `id` matches `[A-Za-z0-9_-]+` — pins the unencoded stopwatch route.
- `interpretationStrategy == NORM_LOOKUP` implies the test id appears in `norms.csv`; otherwise the
  coach scores it and gets a grey, uninterpreted cell.

Print-don't-fail is appropriate for the known norms gap (the 11 adult-only tests), matching how
`SeedNormsConsistencyTest` already handles it.

### 5.2 Tier 2 — select and score every test (Robolectric, ~seconds)

This is the tier that answers the actual question. Write the two fakes first — hand-written, no
mocking framework is on the classpath; follow `FakeStandardsRepository`. Then, for each of the 83
catalog tests, in one `@Test` with a failure accumulator so the run reports *every* broken test
rather than stopping at the first:

1. Seed the fakes with one group, three athletes (one with `medicalAlert` set, one `isRestricted`,
   one plain) and an event whose test list is the whole catalog.
2. Build `TestingGridViewModel` with a `SavedStateHandle(mapOf("eventId" to …, "groupId" to …))`,
   `advanceUntilIdle()`, assert `isLoading == false` and `gridData!!.tests.size == 83`.
3. For each test index: `onAction(OnSelectTestTab(i))`, then `onAction(OnStartEditing(athlete, test))`,
   and assert `editingCell` is populated — this is the non-stopwatch select path.
4. `onAction(OnSaveScore(midpointOf(test.validMin, test.validMax)))`, `advanceUntilIdle()`, then
   assert `errorMessage == null` **and** that a result row now exists for (athlete, test). A
   silently swallowed save is the failure mode the current `try/catch` design makes easy.
5. Also save at exactly `validMin` and exactly `validMax` (boundary — `RecordTestResultUseCase`
   uses `<` and `>`, so both bounds must be accepted), and at `validMin - 1`, asserting that it
   sets `errorMessage` and `failedAction` rather than throwing.
6. For every test where `canUseStopwatch` is true, build `StopwatchViewModel` with that test's id
   under both timing modes, `advanceUntilIdle()`, and assert it reaches `sessionLoaded == true`
   rather than parking on `isLoading`.

Collect failures as `testId -> reason` and fail once at the end with the full list. One run then
tells you *which* of the 83 tests are broken, which is precisely the question that prompted this.

### 5.3 Tier 3 — dummy data through the real UI (instrumented, ~a minute)

Four cases, one per input paradigm, using `createComposeRule` in the `ResponsiveGridTest` style —
render the grid content directly with a hand-built `TestingGridUiState` rather than booting the
whole nav graph, so the test stays fast and deterministic:

- NUMERIC (`test_1rm_squat`) — tap digits on the keypad, submit, assert the cell shows the score.
- INCREMENTAL (`test_push_up`) — tap `+` five times, submit, assert `5`.
- MULTI_STAGE (`test_beep`) — enter a level, submit, assert it round-trips.
- CHRONO (`test_wall_sit`) — assert the cell routes to the stopwatch rather than opening the editor.

This needs the `testTag`s from section 4; adding them is also what would make any future adb-driven
pass reliable instead of coordinate-based.

### 5.4 Ordering

5.0 first — it may make everything else a smaller job. Then 5.1, then the fakes, then 5.2. 5.3 is
genuinely optional if 5.2 is green; it covers rendering and typing, which is the smaller half of
the risk.

## 6. Constraints for this change

- Tier 1 reads the CSV from the source tree, not through Android assets — otherwise it stops being
  a JVM test and the feedback loop goes from one second to one minute.
- The fakes must not resurrect `data/` types. `TestingRepository` and `PeopleRepository` are domain
  interfaces; the fakes implement those and traffic only in domain models.
- Tier 2 seeds athletes with `medicalAlert` and `isRestricted` populated. Any path that rebuilds an
  athlete record has to carry them, and a harness that only uses blank athletes cannot notice when
  one drops them.
- Do not "fix" `RecordTestResultUseCase`'s range throw into a silent no-op to make a test pass. The
  throw is the contract; the test asserts the caller handles it.
- The `items(...)` key fix is one line in a screen that renders athletes × tests. Keep it to that
  one line — it is not an invitation to restructure the grid.

## 7. Verification

- **Regression test that fails today:** none can be named until 5.0 produces the stack trace. That
  is the honest position, and it is why 5.0 is step one. Once the crash is identified, the test
  that reproduces it goes into Tier 2's loop and must fail before the fix and pass after.
- **Unit:** `gradlew test --tests "*TestCatalogCapturePathTest"` then
  `gradlew test --tests "*GroupTestingSelectAllTestsTest"`.
- **Instrumented:** `gradlew connectedAndroidTest --tests "*TestingGridCaptureTest"` (needs the
  emulator running).
- **Build:** `gradlew assembleDebug` then `gradlew test`.
- **Manual:** create an event with 3 athletes and all 83 tests, walk the tab strip end to end, and
  score one athlete on one test of each paradigm — including the empty state (no athletes in the
  group) and the error state (a score below `validMin`, which should show a message and a retry,
  not a crash).

One line on why this was not caught: there is no test anywhere that exercises the testing grid, the
single most-used screen in the app. `app/src/test/` covers auth, standards and backup; the grid,
the stopwatch and the capture path have zero coverage. Tier 2 is the test that should have existed.
