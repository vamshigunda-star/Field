# Spec: The athlete's time vanishes the moment the coach presses STOP

## 0. Open question (answer changes scope)

**Trial order.** After a trial is saved, the code moves on to the *next* athlete who still has
trials left, so a 3-trial test runs A1 → B1 → C1 → A2 → … (each athlete rests between runs). The
other option is to stay on the same athlete until all their trials are done (A1 → A2 → A3 → B1).

The rest of this spec **keeps rotation**, because it gives athletes rest between sprint and agility
trials, and fixes only the case where rotation breaks (see D6). If you want "stay on the same
athlete", the fix in §5 step 6 becomes a one-line change and nothing else moves.

## 1. Objective & scope

- **Symptom (coach's words):** in the 5-10-5 Shuttle Run, and in other tests timed one athlete at
  a time, pressing STOP does not show the athlete's time.
- **Affected tests:** the 32 catalog tests with `timingMode = INDIVIDUAL`, `inputParadigm = CHRONO`
  (5-10-5, the 5/10/20/30/40 m sprints, Illinois, SEMO, zig-zag, holds, balance stands,
  reaction tests), plus custom stopwatch tests. All of them open
  `ui/testing/stopwatch/StopwatchScreen.kt`.
- **In scope:** the **Individual** timing flow on that screen, fully. Of the **Group Heats** mode on
  the same screen, only the shared defects: the session-complete exit, ms rounding, and
  editing/clearing a saved trial.
- **Out of scope:** the manual-entry dialog in the testing grid, Group Heats layout changes,
  performance tuning of the 10 ms ticker (see §6), moving the ViewModel off repositories.

## 2. Verified context

- **Read:** `ui/testing/stopwatch/StopwatchViewModel.kt`, `StopwatchScreen.kt`, `StopwatchState.kt`;
  `domain/usecase/testing/RecordTestResultUseCase.kt`; `domain/repository/TestingRepository.kt`;
  `domain/model/testing/TestResult.kt`; `data/local/daos/testing/TestingDao.kt` (`insertResult` is
  `@Insert(onConflict = REPLACE)`); `assets/tests.csv` (5-10-5 row: `INDIVIDUAL, CHRONO, 1 per heat,
  3 trials, validMin 3.7, validMax 9`).
- **Schema:** none. No migration, and the database stays at its current version.
  `saveResult` already replaces by primary key, which is all the edit fix needs.
- **Drift noticed:**
  - `StopwatchViewModel` injects `TestingRepository` and `PeopleRepository` directly, and uses
    `Channel<StopwatchUiEvent>` for scroll and snackbar events. Both are noted here, not in scope.
  - `StopwatchPhase.CONFIRMING` / `ConfirmationData` / `confirmationJob` are left over from an
    earlier confirm-then-save flow. Nothing sets them any more.
  - The `field-prompt-architect` skill says the database is at version 15. `CLAUDE.md` says 17.
    That doesn't matter here, because there is no schema change.

## 3. Root cause

The symptom has three causes that stack on top of each other, plus five more defects on the same
screen.

### D1 — The clock resets and the athlete changes in the same frame as STOP *(the reported symptom)*

`stopAndStageIndividual()` auto-saves, then `submitPending()` does this in its success branch:

```kotlin
it.copy(pendingResults = emptyMap(), …, elapsedMs = 0L, stopwatchPhase = READY)
…
_uiEvents.send(StopwatchUiEvent.ShowSnackbar("$savedName's time saved"))
handleNext()   // sets selectedAthleteId = next athlete, elapsedMs = 0L
```

So the frame after STOP shows `00:00.00` under the **next** athlete's name. The only trace of the
result is a snackbar saying "Aarav's time saved", and **that snackbar doesn't include the time**.

### D2 — Times for an athlete who hasn't finished all trials are never drawn

`IndividualAthleteRow` draws times only when `isCompleted` is true, which means all trials are done:

```kotlin
isCompleted && trials.isNotEmpty() -> Text(trials.joinToString(" · ") …)
athlete.status == CAPTURED && … -> …     // a pending score: lasts ~one frame because of auto-save
isSelected -> "CURRENT"
else -> "Absent?" switch
```

After trial 1 of 3 the row shows "Trial 2 of 3" and an Absent switch, with no time. The 5-10-5 has
3 trials, so **the coach sees no time for any athlete until that athlete's third run**. This is why
the problem feels total, not just a flash.

### D3 — The last trial of the session exits the screen

In Individual mode, `recomputeSummaryCounts()` marks the session complete once every athlete is done
and `selectedAthleteId == null`. `handleNext()` sets exactly that when nobody is left. Then
`StopwatchScreen` has:

```kotlin
LaunchedEffect(uiState.isSessionComplete) { if (uiState.isSessionComplete) onNavigateBack() }
```

The final STOP pops the screen. The final time is never shown, and `CompletionSummaryCard` with its
"Finish Session" button can never be reached (dead UI). Group Heats has the same auto-exit after the
last heat.

### D4 — Editing or clearing a trial you just saved doesn't work

Every trial saved on this screen is also kept in `sessionResults` (the comment says "Never
cleared"). The screen displays the union of that list and the DB rows (`buildDisplayTrials`), and
counts trials as `max(dbCount, sessionCount)`.

- **Clear:** `handleClearEntry` deletes the DB row, but the copy in `sessionResults` stays. The time
  stays on screen and the athlete's trial count doesn't go down.
- **Edit:** `handleSaveEditedTime` deletes the row, then calls `recordResult` without updating
  `sessionResults`. The old time *and* the new time both show, and the count goes up.
- **Edit to an out-of-range value:** `deleteResultById` runs first. `RecordTestResultUseCase` then
  throws `IllegalArgumentException` (it enforces `validMin`/`validMax`). **The original trial is
  lost.** The two calls are not atomic either: any failure between them loses the trial.
- **Edit changes the order:** the replacement gets a fresh `createdAt`, so an edited trial 1 moves
  to the end and shows as trial 3.

### D5 — A false start can't be cancelled, and an out-of-range run just disappears

- While the clock is running, the only button is STOP, and STOP always saves. A false start or a
  slipped cone has to be saved, then found and cleared.
- If the time is outside `validMin..validMax` (for 5-10-5 that is below 3.7 s or above 9 s), the
  clock stops, the time is thrown away, and a snackbar formats it with `%.1f`, which breaks the
  `formatScore` rule in CLAUDE.md. The coach doesn't get a clear next step.

### D6 — Rotation stalls on the last athlete with trials left

`handleNext()` searches only the athletes after and before the current one:

```kotlin
all.drop(currentIndex + 1).find { WAITING } ?: all.take(currentIndex).find { WAITING }
```

The current athlete is never considered. When they are the only one with trials left, which is true
of every 1-athlete group and of the last athlete in any group, the selection goes to `null` and the
coach has to tap the athlete again before every remaining trial.

### D7 — An athlete who has finished can be timed a 4th time

`OnSelectAthlete` accepts a COMPLETED athlete, and `handleStartStop` doesn't check how many trials
they have. START → STOP saves a 4th result. The row shows only `take(totalTrials)`, so the extra
result is invisible on screen, but it still lands in reports.

### D8 — Some times show one hundredth too low

Seconds are converted to ms with truncation: `(rawScore * 1000).toLong()` in `buildDisplayTrials`,
`buildAllAthletesList` and `buildHeatAthletesList`. Floating-point error makes some exact values
fall just short. I verified this: 37 whole-hundredth values under 60 s are affected, including
**4.02 → 4.01, 4.06 → 4.05, 8.03 → 8.02, 8.04 → 8.03**, all inside the 5-10-5 range. The value
stored in the DB is correct. Only the display is wrong.

### D9 — Scroll-to-next-athlete goes to the wrong row

`ScrollToAthlete` looks up the index in `uiState.allAthletes`, which is in roster order. The list on
screen is different: `[header, completed…, header, upcoming…]`. Once anyone is completed, the
indices no longer line up, and the list scrolls to the wrong athlete or not at all.

Inferred, not verified on a device: D1 and D2 together are what the coach is reporting. D3 is the
same symptom for the final athlete.

## 4. Files

| Action | Path | Purpose |
|---|---|---|
| MODIFY | `domain/usecase/testing/RecordTestResultUseCase.kt` | Optional `resultId` / `createdAt` parameters so an edit replaces the row in place |
| MODIFY | `ui/testing/stopwatch/StopwatchState.kt` | `LastTrial` state, new actions, remove CONFIRMING leftovers |
| MODIFY | `ui/testing/stopwatch/StopwatchViewModel.kt` | D1, D3–D9 logic |
| MODIFY | `ui/testing/stopwatch/StopwatchScreen.kt` | Result card, trial chips on every row, Cancel button, completion card, scroll index |
| NEW | `app/src/test/java/com/vamshi/field/ui/testing/StopwatchViewModelTest.kt` | Regression tests, using `FakeTestingRepository` / `FakePeopleRepository` / `FakeStandardsRepository` |

All paths are under `app/src/main/java/com/vamshi/field/` unless they say otherwise.

## 5. Fix

Each step compiles on top of the one before.

**1. `RecordTestResultUseCase`: replace in place.** Add two parameters,
`resultId: String = UUID.randomUUID().toString()` and `createdAt: Long = System.currentTimeMillis()`,
and use them in the `TestResult(...)` it builds. All existing callers keep compiling unchanged.
Because `insertResult` uses REPLACE, passing the old id and `createdAt` overwrites the row and keeps
the trial in its position, and the percentile is recalculated. *Why not a new
`UpdateTestResultUseCase`:* it would duplicate the range check and the percentile lookup, which is
exactly how two versions of one rule drift apart.

**2. `StopwatchState.kt`.**

```kotlin
/** The trial just captured, shown on the timer panel until the next START. */
data class LastTrial(
    val athleteId: String,
    val athleteName: String,
    val trialNumber: Int,
    val totalTrials: Int,
    val timeMs: Long,
    val resultId: String?,          // null when it was rejected as out of range
    val outOfRangeMessage: String?  // non-null → not saved
)
```

- Add `val lastTrial: LastTrial? = null` to `StopwatchUiState`.
- Add `data object OnCancelRun : StopwatchAction` and `data object OnUndoLastTrial : StopwatchAction`.
- Delete `StopwatchPhase.CONFIRMING`, `ConfirmationData`, the `confirmationData` field and
  `confirmationJob`. Nothing writes them, and the screen's `!= CONFIRMING` guard goes with them.

**3. A single ms conversion (D8).** Add a private `fun Double.toMs(): Long = (this * 1000).roundToLong()`
to the ViewModel. Use it in all three builders and wherever seconds are turned into ms.

**4. Keep the result on screen after STOP (D1, D5).** In `stopAndStageIndividual()`:

- Freeze `elapsedMs` at the captured value. Don't reset it to 0 anywhere in the save path. The
  `elapsedMs = 0L` in `submitPending` and in `handleNext` both go.
- When the time is out of range, set `lastTrial` with `outOfRangeMessage` built using
  `formatScore(rawScore, InputParadigm.CHRONO)`, and remove the snackbar. Selection stays on the
  same athlete, so START simply re-runs the trial.
- When it's in range, save it, then set `lastTrial` with the returned `TestResult.id`.
  Change the snackbar to include the time, e.g. "Aarav · Trial 1: 4.87 s saved", or drop it since
  the card shows the same thing. Prefer dropping it, because the card is the single place the
  result appears.
- Clear `lastTrial` in `startTimer()`, `OnSelectAthlete` and mode toggle.

**5. Cancel and undo (D5).**
- `OnCancelRun`: allowed only when RUNNING. Cancel the ticker, set READY, `elapsedMs = 0`. Saves
  nothing and keeps the same athlete selected.
- `OnUndoLastTrial`: delete `lastTrial.resultId` through the same path as a clear (step 7), then
  select `lastTrial.athleteId` again and set `lastTrial = null`. This is the "they knocked a cone,
  run it again" button.

**6. Rotation includes the current athlete (D6).** In `handleNext()`, after searching after and
before the current athlete, fall back to the current athlete if they are still WAITING. (If the
answer to §0 is "stay on the same athlete", check the current athlete *first* instead.) Also scroll
by key, not index, as described in step 9.

**7. Edit and clear actually change what's shown (D4).**
- **Clear** a saved trial: `deleteResultById(id)`, then remove the entry with that id from
  `sessionResults[athleteId]`, then `completionState[athleteId] -= 1` (never below 0), then
  `withRefreshedAllAthletes()` and `recomputeSummaryCounts()`.
- **Edit** a saved trial: look up the existing `TestResult` (from `sessionResults` or
  `historicalResults`). Check the new score against `validMin`/`validMax` **before writing
  anything**, and show the same out-of-range message on failure without touching the DB. Then call
  `recordResult(…, resultId = old.id, createdAt = old.createdAt)`. **Don't delete.** Replace the
  entry in `sessionResults` with the returned result.
- If `lastTrial.resultId` matches the edited or cleared id, update or clear `lastTrial` too.

**8. Block a 4th trial (D7).** In `handleStartStop()`, return early when the selected athlete has
`completionState >= totalTrialsPerAthlete`. Leave `OnSelectAthlete` alone, so a completed athlete
can still be selected to view and edit their times.

**9. `StopwatchScreen.kt`.**
- **Timer panel:**
  - When `lastTrial != null` and READY, show a result card under the clock: athlete name,
    "Trial n of N", the time in large monospace text, and either a green ✓ "Saved" or the red
    out-of-range message. The card has an **Undo** text button (or **Retry** when out of range).
  - The clock above it keeps showing the frozen time.
  - The athlete line underneath becomes "Up next: <name> · Trial n of N".
  - The START button stays visible and starts the next athlete with one tap.
- **While RUNNING:** show STOP (large) plus a smaller **Cancel run** text button underneath it.
- **Selected athlete with all trials done:** disable START and give it the label
  "All N trials recorded".
- **`IndividualAthleteRow`:**
  - Every athlete with ≥ 1 trial shows trial chips with their times on the right. Completed athletes
    in green, partial ones in neutral, next to a "Trial n of N" subtitle.
  - Each chip is tappable and opens `OnOpenEditDialog(athleteId, resultId)` for that trial. This
    replaces the "long-press edits last trial only" behaviour, and the comment saying so goes.
  - The Absent switch shows only for athletes with 0 trials who are not selected.
- **Completion (D3):** delete the `LaunchedEffect(isSessionComplete) { onNavigateBack() }`. Show
  `CompletionSummaryCard` (it already exists) above the list in Individual mode, and in place of
  the spinner in Group Heats. "Finish Session" is the only way out. Move the card above the list so
  it doesn't push the result card off small screens.
- **Scroll (D9):** handle `ScrollToAthlete` by finding the item's position in the list as actually
  laid out: header + completed rows + header + upcoming rows, computed in the event handler and
  never during composition.
- **Plurals:** use `countOf(...)` in `DiscardPendingDialog`, `SubmitBar` and `CompletionSummaryCard`.
  These are in files this spec already touches.

## 6. Constraints for this change

- **Don't move result-saving later.** Auto-save on STOP is right for field use: a crash or a dead
  battery shouldn't lose a run. The fix is about *showing* the result, not about adding a
  confirmation step.
- **An edit must never delete before it has validated.** Today's ordering loses data, and the
  regression test in §7 pins this.
- **Keep `elapsedMs` in `StopwatchUiState`.** Splitting it into a second StateFlow would break the
  "single StateFlow<UiState>" rule in CLAUDE.md. If the ticker turns out to cause visible jank on a
  low-end tablet, measure that first and raise it as its own change.
- **No new O(N) work in composition.** Building chips per row comes from `displayTrials`, which the
  ViewModel already computes. The scroll position is computed inside the event handler.

## 7. Verification

**Unit, in `StopwatchViewModelTest`. The first four fail on today's code.**
1. `stop_keepsTimeAndAthleteOnScreen`: start, advance fake time, stop. `elapsedMs` is still the
   captured value, and `lastTrial` has the right athlete, trial 1 and time.
2. `clearJustSavedTrial_removesItFromDisplayAndCount`: save one trial, then clear it.
   `displayTrials` is empty and the trial count is 0.
3. `editToOutOfRange_keepsOriginalTrial`: save 4.50, then edit to 2.00 (below 3.7). The result
   still exists, and `FakeTestingRepository` saw no delete.
4. `editKeepsTrialPosition`: save 3 trials and edit trial 1. It's still first, with the same id.
5. `lastAthleteWithTrialsLeft_staysSelected` (D6).
6. `completedAthlete_cannotStartFourthTrial` (D7).
7. `cancelRun_savesNothing`.
8. `msConversion_4_02_displaysAs4020` (D8).

**Build:** `gradlew assembleDebug`, then `gradlew test`.

**Manual, on device. Create a testing event with the 5-10-5 Shuttle Run and a group of 3 athletes.**
1. Start, then stop at about 5 s. The clock holds the time, the card reads "<A> · Trial 1 of 3 ·
   5.xx ✓ Saved", and the line below says "Up next: B".
2. A's row shows a chip with 5.xx.
3. Tap START. The card clears and B's run is timing.
4. Start, then tap **Cancel run**. Nothing is saved and the athlete is still B.
5. Start and stop at under 3.7 s. A red card shows the time with "below the minimum 3.7 s" and a
   **Retry** button.
6. Tap A's 5.xx chip and change it to 6.00. The chip updates in place and stays the first trial.
7. Clear a trial. The chip disappears and the progress bar drops by one.
8. Finish all 9 trials. The screen stays open with the last time visible and the completion card
   shown, and only Finish Session leaves the screen.
9. Repeat step 8 in Group Heats mode.

*Why this wasn't caught:* `StopwatchViewModel` has no unit tests. Every defect above is in logic
that a fake-repository test reaches in a few lines.
