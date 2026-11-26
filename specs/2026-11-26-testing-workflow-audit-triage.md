# Spec: Testing-workflow audit, verified against the code, with fixes for the real defects

Source: an external (Gemini) audit of the testing workflow, pasted into the session on
2026-11-26. Every claim below was checked against the current code on `master` (after the
stopwatch and Group Heats fixes `4ae3b6e` and later). Parts of the audit were written against
older code, so some of its findings are stale.

## 0. Open questions (answers change scope)

1. **Multi-trial result: best or latest?** When an athlete has 2–3 trials of the same test in
   one event (sprints, jumps, agility), the leaderboard and reports need one number per athlete.
   - Today the reports take the **latest** trial (`maxBy { createdAt }`).
   - The event leaderboard takes **every** trial, which is the crash in D1.
   - Standard field-testing practice records the **best** trial. This spec assumes best: lowest
     for lower-is-better, highest for higher-is-better.
   - This spec applies that rule to the leaderboard (D1) only. Reports stay on latest until you
     confirm, because changing them shifts every historical report the coach has already seen.
2. **Children on adult-only tests.** CLAUDE.md says the 11 adult-only tests "return no norm for
   an athlete under 20". **That is false** (see D6): the closest-age fallback grades a
   10-year-old against the 20–29 adult bands.
   - This spec assumes the documented behaviour is the intended one: grey, no zone, for an
     athlete younger than the test's youngest bracket.
   - Athletes older than the oldest bracket still fall back to that oldest bracket.
   - The alternative is to keep grading children on adult norms, which is not defensible.

## 1. Objective & scope

- **Symptom:** an audit listed about 30 problems across testing. The aim is to separate the real
  ones from the false and stale ones, then fix the defects that crash the app, corrupt data or
  hide safety information.
- **In scope:** D1–D9 below (Phase 1).
- **Out of scope:**
  - The Phase 2 backlog in §8. Those are real but are UX polish or data decisions, and each is
    small enough for its own change.
  - Repairing `norms.csv` rows. These are already pinned as known offenders in
    `TestCatalogCapturePathTest` and need a data decision.

## 2. Verified context

- **Read:**
  - `GetGroupLeaderboardUseCase.kt`, `LeaderboardScreen.kt`
  - `StopwatchScreen.kt` (`EditTimeDialog`, `GroupStartModeContent`, `GroupAthleteRow`),
    `StopwatchViewModel.kt` (`handleSaveEditedTime`)
  - `QuickTestViewModel.kt` (`saveScore`, `confirmDelete`)
  - `StandardsRepositoryImpl.kt`, `StandardsDao.kt` (`getNormBandsClosestToAge`),
    `CalculatePercentileUseCase.kt`
  - `ScoreFormat.kt`, `TestingGridComponents.kt` (`LiveEntryPhase`, `handleCellAction`,
    `AthleteRow`)
  - `TestingDao.kt`, `PeopleDao.kt`, `HowToUseGuides.kt`, `tests.csv`, `norms.csv`
- **Schema:** none. No migration and no catalog or seed bump; `norms.csv` is untouched.
- **Drift noticed:**
  - CLAUDE.md's "Known gap" paragraph says under-20s get no norm on adult-only tests. The code
    grades them against adult bands (D6). After D6 the doc becomes true again.
  - `QuickTestViewModel` injects `TestingRepository` and `PeopleRepository` directly. Noted, not
    changed.

## 3. Audit triage

### Confirmed. Fixed in this spec.

| # | Audit claim | Verdict and mechanism |
|---|---|---|
| D1 | Event leaderboard crashes after multi-trial tests | **True.** `GetGroupLeaderboardUseCase` event branch: `getEventResults(eventId).first().filter { it.testId == testId && … }` keeps every trial. `LeaderboardScreen.kt:188` uses `key = { it.individualId }`, so two trials by one athlete give a duplicate LazyColumn key and an `IllegalArgumentException`. Reached from the grid's top-bar Leaderboard icon after any stopwatch test with `trialsPerAthlete > 1`. |
| D2 | Editing a time ≥ 60 s corrupts it | **True.** `EditTimeDialog` seeds `inputString = formatStopwatchTime(ms).replace(Regex("[^0-9]"), "")`, so "1:02.35" becomes "10235". It then reads that as centiseconds: `displayMs = 10235 * 10` = 102 350 ms = 1:42.35. Every edit of a hold or distance-run time over a minute adds 40 s per minute, even when the coach changes nothing and taps Save. |
| D2b | Editing an unsaved Group Heats time skips the range check | **Partly true.** `handleSaveEditedTime` with `resultId == null` stores the pending value unvalidated. The later `submitPending` then fails in `RecordTestResultUseCase` with "Save failed: Score … exceeds…". No data is lost, but the error surfaces late and is unclear. |
| D3 | Tapping a completed athlete in Group Heats doesn't edit their trial | **True, and it is a regression from the Group Heats fix.** `GroupAthleteRow` sends `OnOpenEditDialog(athlete.athleteId)` with no `resultId`. The dialog opens on `capturedTimeMs`, which is now the latest saved trial, and Save adds it as a new pending score. Submitting then records an **extra** trial instead of correcting the old one. |
| D4 | Group Heats has no way to cancel a false start | **True.** `OnCancelRun` exists in the ViewModel but is only shown in Individual mode (`StopwatchScreen.kt:530`). `GroupStartModeContent` hides START once running and shows nothing in its place. |
| D5 | Editing a Quick/Individual Test score leaves the old row | **True.** `QuickTestViewModel.saveScore` always calls `recordResult(...)` with a new UUID and replaces only the in-memory list. The old row stays in Room and in every report. |
| D5b | "Delete These Results" leaves an empty event | **True.** `confirmDelete` deletes each result but never the event, so a 0-result event remains in Reports. Worse, when the screen was opened to resume an existing event (`eventId` route arg), it deletes **all** that event's results, including ones from earlier sessions. |
| D6 | Under-20s graded on adult-only norms | **True.** When the bracket lookup is empty, `StandardsRepositoryImpl.getNormBandsForAthleteTest` falls back to `getNormBandsClosestToAge`. That query has **no LIMIT** and returns every bracket for the test, ordered by `ABS(ageMin - age)`. `CalculatePercentileUseCase` then takes the first band containing the score, or clamps across all brackets. A 10-year-old's push-up count is graded against the 20–29 bands. |
| D7 | Stopwatch hundredths rounded to one decimal outside the stopwatch screen | **True.** `formatScore` uses `"%.1f"` for every non-stage score, so a 4.87 s sprint shows "4.9" in the grid, Quick Test, the leaderboard and reports. On the 5 m sprint (0.9–3.5 s) that rounding is the whole difference between athletes. |
| D8 | Grid can't reach the stopwatch for trials 2 and 3 | **True.** `LiveEntryPhase` sends both tap and long-press on a saved cell to `OnStartEditing`, which opens the keypad. After trial 1, the stopwatch is only reachable for that athlete by tapping a different athlete. |
| D9 | Medical alert hidden while timing | **True.** Only `TestingGridComponents.kt:420` shows anything: a 16 dp icon with no text. The stopwatch screen (both modes) and Quick Test show neither `medicalAlert` nor `isRestricted`. That breaks the "surface prominently wherever an athlete is about to be tested" rule. |

### Real, but deferred to the Phase 2 backlog (§8)

Reports pick the latest trial, not the best (blocked on §0 Q1). The stage picker needs about 20
taps for high levels. Quick Test has no stopwatch for CHRONO tests. Guest results aren't saved,
yet the screen says "successfully recorded". The Roster tablet pane's Quick Test is a no-op.
Custom tests set to Stopwatch don't validate their unit. Saved test lists aren't in Drive backups.
The register sheet has no `isRestricted` toggle. The grid's score dialog has no Close button and
ignores `testDescription`. The how-to guide says "Save Results" where the button reads "Finish
Testing" (`HowToUseGuides.kt:120,127,129`). In `tests.csv`, `test_1.6_mile_run` is described as
"1.5 kilometers". The `norms.csv` overlaps in push-up, toe touch and shuttle run need a data
decision.

### Stale, overstated or wrong

| Audit claim | Why it doesn't stand |
|---|---|
| Group Heats splits a group by `athletesPerHeat ?: 6` | Fixed already: a heat size of 1 or null now uses 6, and rounds wrap. |
| Out-of-range SAVE "silently" does nothing | Overstated. The dialog shows a red "Valid range: …" line. The button still *looks* enabled, so this is polish only (backlog). |
| Grid editing "orphans other stopwatch trials" | Wrong. A grid edit deletes the shown (latest) result and records one new one, so the trial count is unchanged. The real gap is D8. |
| Quick Test ignores the `eventId` route argument | Partly wrong. New scores are saved to that event. What's missing is loading its existing scores, which is backlog. |
| Grid and stopwatch rosters disagree (`isActive` vs `isDeleted`) | Latent only. No code path sets `isActive = false` (athlete delete is a hard delete), so the two queries return the same athletes today. |
| `SCALE` / `CALCULATED` "dead branches" | Not defects. They are unused enum values with safe fallbacks. |
| Snackbars drawn over the top bar; Decimal keyboard has no minus | Not verified. Both depend on the device or on rendering. Check on a phone before acting. |

## 4. Files

| Action | Path | Purpose |
|---|---|---|
| MODIFY | `domain/usecase/testing/GetGroupLeaderboardUseCase.kt` | D1: best trial per athlete in event mode |
| MODIFY | `ui/testing/stopwatch/StopwatchScreen.kt` | D2 dialog parsing, D3 row taps, D4 cancel button, D9 alert |
| MODIFY | `ui/testing/stopwatch/StopwatchViewModel.kt` | D2b validate pending edits |
| MODIFY | `ui/testing/stopwatch/StopwatchState.kt` | D9: `medicalAlert` and `isRestricted` on `AthleteQueueItem` |
| MODIFY | `ui/quicktest/QuickTestViewModel.kt` | D5 in-place edit, D5b delete scope |
| MODIFY | `data/repository/StandardsRepositoryImpl.kt` | D6 fallback rules |
| MODIFY | `domain/model/standards/ScoreFormat.kt` | D7: two decimals for CHRONO |
| MODIFY | `ui/testing/TestingGridComponents.kt` | D8 route to stopwatch while trials remain |
| MODIFY | `ui/quicktest/QuickTestScreen.kt` | D9 alert in the athlete banner |
| MODIFY / NEW | tests under `app/src/test/...` | Regression tests (§7) |

All paths are under `app/src/main/java/com/vamshi/field/` unless they say otherwise.

## 5. Fix

1. **D7 `formatScore`.** Add a branch before the whole-number check:
   `paradigm == InputParadigm.CHRONO -> String.format(Locale.getDefault(), "%.2f", raw)`.
   - 12.0 s then reads "12.00", which is correct for a timed score.
   - NUMERIC and INCREMENTAL output doesn't change, so `StageScoreTest` still passes.
2. **D2 `EditTimeDialog`.** Seed the input from `initialTimeMs / 10` as plain centiseconds
   (`(initialTimeMs / 10).toString()`), not from the formatted string.
   - Read it back the same way: `displayMs = centis * 10`, and display through
     `formatStopwatchTime`.
   - Raise the length cap from 6 to 7 digits, so values up to 9999.99 s can be entered, which
     covers the 2400 s ceiling of the 1.5-mile run.
   - *Why not parse "m:ss.cc":* the keypad types digits right to left as a cash register does, so
     a centisecond integer is the honest model and needs no parser.
3. **D2b.** In `handleSaveEditedTime`, run `outOfRangeMessage` for the `resultId == null` branch
   too, with the same snackbar and early return as the saved-trial branch.
4. **D3.** In `GroupAthleteRow`:
   - a **CAPTURED** athlete (pending, unsaved) keeps `onEdit()` with no id;
   - a **COMPLETED** athlete edits their latest *saved* trial, sending
     `OnOpenEditDialog(athleteId, displayTrials.last().resultId)`.
   - Add a `resultId: String?` parameter to the row's `onEdit` lambda and pass it through from
     `GroupStartModeContent`.
5. **D4.** In `GroupStartModeContent`, while RUNNING, show the same "Cancel run (false start),
   nothing saved" `TextButton` that Individual mode has, firing `OnCancelRun`.
   - `handleCancelRun` must also clear `pendingResults` captured in that run, so that cancelling
     after two athletes have been tapped saves nothing. Today it keeps them.
6. **D5.** In `QuickTestViewModel.saveScore`, look up the existing `RecordedTestResult` for
   `testId`.
   - If it has an `id`, pass `resultId = existing.id`. `createdAt` keeps the default "now",
     because Quick Test has one score per test, so its position is meaningless.
   - `saveResult` replaces by primary key, so this needs no delete. It is the same mechanism the
     stopwatch edit fix used.
7. **D5b.** Track `private var eventCreatedHere = false` in the ViewModel, set it in
   `ensureEventExists()` when it creates the event, and change `confirmDelete` as follows:
   - delete only the ids in `recordedResults` (this session's scores), never every result of the
     event;
   - if `eventCreatedHere`, and `testingRepository.getEventResults(eventId).first()` is empty
     after those deletions, also call `deleteEventById(eventId)`, as `DiscardEmptyEventUseCase`
     does. Reuse that use case: inject it and call it.
8. **D6.** In `StandardsRepositoryImpl.getNormBandsForAthleteTest`, when the direct bracket query
   is empty:
   - take the closest-age rows, find the single bracket nearest to the athlete (the first row's
     `ageMin` to `ageMax`), and keep only rows from that bracket;
   - if the athlete is **younger** than that bracket's `ageMin`, return an empty list, so the
     score is grey and has no zone (§0 Q2);
   - an athlete older than the oldest bracket still gets the oldest bracket;
   - the cache key is unchanged.
9. **D1.** In the `GetGroupLeaderboardUseCase` event branch, after filtering, group by
   `individualId` and keep each athlete's best result: `minBy { rawScore }` when
   `!test.isHigherBetter`, otherwise `maxBy`.
   - The all-time branch already returns one result per athlete and is unchanged.
10. **D8.** In `LiveEntryPhase`, tapping a cell that already has a saved result now checks the
    test first:
    - if `test.canUseStopwatch` and the athlete's recorded trial count for that test is below
      `test.trialsPerAthlete`, call `handleCellAction(...)`, which opens the stopwatch or uses
      the remembered preference;
    - otherwise open the editor, as today.
    - Long-press always opens the editor.
    - The trial count comes from a new `TestingGridData.trialCount: Map<Pair<String, String>, Int>`,
      built next to `resultByAthleteAndTest` in the same `buildMap` pass, so nothing is counted
      during composition.
11. **D9.** Add `medicalAlert: String?` and `isRestricted: Boolean` to `AthleteQueueItem` and
    fill them in both builders.
    - Individual mode: under the selected athlete's name, show a full-width error-container strip
      with a warning icon and the alert text, or "Restricted". Use the same tokens as the strip in
      `AthleteDashboardScreen`.
    - Group Heats: show a warning icon and the first line of the alert on each affected row.
    - Quick Test: show the same strip in the `EnterScoresStep` athlete banner.

## 6. Constraints for this change

- **D5 and D6 change what gets written to new results only.** Stored percentiles are snapshots
  (DEVELOPMENT_CONTEXT §7), so existing results keep their old grade. Say so in the commit
  message, so that nobody "fixes" history by re-grading it.
- **D5b must never delete scores this screen didn't record.** The resume path shares an event
  with earlier sessions.
- **D8:** building the trial count must not add per-row scanning during composition. The grid is
  athletes × tests.
- **D9:** don't put the alert only in a tooltip or a long-press. It has to be visible without any
  interaction.

## 7. Verification

**Regression tests. Each fails today.**
1. `GetGroupLeaderboardUseCaseTest.eventMode_multiTrial_oneEntryPerAthlete_bestTrial`: 2 athletes
   × 3 sprint trials give 2 entries, each holding the lowest time. Use `FakeTestingRepository`,
   `FakePeopleRepository` and `FakeStandardsRepository`.
2. `EditTimeDialog` round trip: extract the seed and parse logic into
   `internal fun editSeed(ms: Long): String` and `parseEditCentis(s: String): Long` in
   `StopwatchState.kt`, then assert 62 350 ms → seed → 62 350 ms. Today this gives 102 350.
3. `StopwatchViewModelTest.groupHeats_editCompletedAthlete_replacesTrial_notAdds`: the stored
   result count is unchanged and the value is updated.
4. `StopwatchViewModelTest.groupHeats_cancelRun_discardsCaptured`.
5. `QuickTestViewModelTest.editScore_replacesRow` (one row stored) and
   `deleteResults_resumedEvent_keepsEarlierScores`.
6. `CalculatePercentileUseCaseTest.underTwenty_onAdultOnlyTest_returnsNull`, plus
   `aboveOldestBracket_usesOldestBracket`.
7. `StageScoreTest`: `formatScore(4.87, CHRONO) == "4.87"` and
   `formatScore(12.0, CHRONO) == "12.00"`. Use `Locale.US` in the test, or assert via the same
   format call.

**Build:** `gradlew assembleDebug`, then `gradlew test`. Expect all existing tests to pass.

**Manual:**
1. Run a 40 m sprint with 3 trials in Individual mode for 2 athletes, then open the grid's
   Leaderboard icon. There is no crash, and each athlete appears once with their best time.
2. Time a plank of over 60 s, tap its chip, and save without changing anything. The time is
   unchanged.
3. In Group Heats, start the clock and tap Cancel run. Nothing is saved.
4. In Group Heats, tap a completed athlete and edit the time. The trial count stays the same.
5. In Quick Test, enter a score, edit it, and check the athlete report. There is one result.
6. For a 10-year-old's push-up in Quick Test, the score cell is grey.
7. For an athlete with a medical alert, open the stopwatch. The alert strip is visible under
   their name.

## 8. Phase 2 backlog (separate small changes, in suggested order)

1. Reports use the best trial per event (after §0 Q1 is answered).
2. Stage picker: a direct level keypad or level chips.
3. Quick Test: offer the stopwatch for CHRONO tests.
4. Guest copy: "Not saved — guests aren't stored", plus a share action.
5. Roster tablet pane: wire `onStartQuickTest`.
6. Custom tests set to Stopwatch must use a seconds unit (validation in
   `ValidateCustomTestUseCase`).
7. Register sheet: add an `isRestricted` toggle.
8. Grid score dialog: add a Close button and show the test description.
9. Disable SAVE while the value is out of range.
10. How-to copy says "Finish Testing"; fix the `tests.csv` 1.6-mile description (needs a catalog
    bump).
11. Saved test lists: include them in Drive backups (a new nullable backup field).
12. Data decision on the overlapping norms for push-up, toe touch and shuttle run.

*Why the audit found these:* none of these paths has a test that records more than one trial and
then reads it back through a second screen (leaderboard, grid, reports). Test 1 is the first.
