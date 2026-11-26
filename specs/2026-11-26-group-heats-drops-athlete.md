# Spec: Switching to Group Heats shows only one athlete of a two-athlete group

## 0. Open question (answer changes scope)

**Heat size when a test is normally timed one at a time.** The fix below caps a heat at **6
athletes**. That is the default the code already uses for custom tests, which have no heat size
set. A group of 2 therefore runs as a single heat of 2, and a group of 14 runs as 6 + 6 + 2.

The alternative is to let the coach choose the heat size (lanes or cones available) when they
switch to Group Heats. That needs a small picker and a new action, so it is more work. This spec
assumes the fixed 6.

## 1. Objective & scope

- **Symptom:** in a group of 2 athletes, tapping **Group Heats** on the stopwatch screen leaves
  only one athlete visible.
- **In scope:** how heats are built when the test's own heat size is 1, the athlete count in the
  header, and multi-trial tests in Group Heats (D2, found on the same path).
- **Out of scope:**
  - A coach-chosen heat size (see §0).
  - Group Heats layout changes.
  - The 6 catalog tests that are natively `GROUP_START` with 6 or 8 per heat. They are unaffected,
    except that they also get the D2 fix.

## 2. Verified context

- **Read:**
  - `domain/usecase/testing/StopwatchSessionUseCase.kt`, where heats are built.
  - `ui/testing/stopwatch/StopwatchViewModel.kt`: `advanceToNextHeat`, `buildHeatAthletesList`,
    `findFirstIncompleteHeatIndex`.
  - `StopwatchScreen.kt`: `buildSessionContextLine`.
  - `assets/tests.csv`, `athletesPerHeat` column.
- **Data:** among the CHRONO tests,
  - 31 of 32 `INDIVIDUAL` tests have `athletesPerHeat = 1` (5-10-5, sprints, agility runs and so on);
  - the `GROUP_START` tests have 6 or 8;
  - custom tests store `null`.
- **Schema:** none. No migration, no catalog or seed change.
- **Drift noticed:** none beyond what the previous stopwatch spec recorded.

## 3. Root cause

### D1 — Heats are sized by the test's one-at-a-time setting *(the reported symptom)*

`StopwatchSessionUseCase` builds the heats once, for both modes:

```kotlin
val perHeat = test.athletesPerHeat ?: 6
val heats = if (athletes.isNotEmpty()) athletes.chunked(perHeat) else emptyList()
```

For the 5-10-5, `athletesPerHeat = 1`, so a 2-athlete group becomes **two heats of one**. Group
Heats then shows only heat 1, which holds one athlete. The second athlete is waiting in heat 2,
which only appears after heat 1 is saved. That is why they look gone.

The header makes it worse. `buildSessionContextLine` counts `heatAthletes.size` in Group Heats, so
the top bar changes from "2 Athletes" to "1 Athlete". The coach reads that as the app having
dropped someone, not as a heat split.

In the catalog, a value of 1 means "timed one at a time". As a Group Heats size it makes no sense:
a one-athlete heat is just Individual mode with extra steps. *(Verified in code and data. Not yet
reproduced on a device.)*

### D2 — Group Heats runs only one trial per athlete

`advanceToNextHeat()` marks the session complete as soon as the last heat is run:

```kotlin
currentHeatIndex++
if (currentHeatIndex >= heats.size) { state.copy(isSessionComplete = true) }
```

The 5-10-5 has 3 trials. In Group Heats, each athlete runs once, and then the completion card says
the session is finished with trials 2 and 3 never offered. *(Verified in code.)*

## 4. Files

| Action | Path | Purpose |
|---|---|---|
| MODIFY | `app/src/main/java/com/vamshi/field/domain/usecase/testing/StopwatchSessionUseCase.kt` | Treat a heat size of 1 or less, or null, as the default 6 |
| MODIFY | `app/src/main/java/com/vamshi/field/ui/testing/stopwatch/StopwatchViewModel.kt` | After the last heat, go back to the first heat that still has trials left |
| MODIFY | `app/src/main/java/com/vamshi/field/ui/testing/stopwatch/StopwatchScreen.kt` | Header counts the whole group, not the current heat |
| MODIFY | `app/src/test/java/com/vamshi/field/ui/testing/StopwatchViewModelTest.kt` | Regression tests |

## 5. Fix

1. **`StopwatchSessionUseCase`:**
   `val perHeat = test.athletesPerHeat?.takeIf { it > 1 } ?: DEFAULT_HEAT_SIZE`, with
   `private const val DEFAULT_HEAT_SIZE = 6` in a companion object.
   - Individual mode never reads `heats` except through `totalHeats`, which its screen doesn't show,
     so Individual timing is unchanged.
   - *Why not change `tests.csv` to 6:* the 1 is correct for what the catalog means, which is
     "timed one at a time", and other code may come to rely on that. The heat builder is the one
     place that misreads it.
2. **`StopwatchViewModel.advanceToNextHeat()`:** after the increment, if `currentHeatIndex >=
   heats.size`, call `findFirstIncompleteHeatIndex()`.
   - If any heat still has a present (not absent) athlete with trials left, go to that heat as the
     next round, and update `heatAthletes` and `currentHeatNumber`.
   - Only when no heat has trials left, set `isSessionComplete = true`.
   - `findFirstIncompleteHeatIndex` must skip absent athletes, or an absent athlete keeps the
     session open forever. Today it doesn't check absence, so add `!absentAthletes.contains(it.id)`.
   - The `heatsExhausted` clause in `recomputeSummaryCounts` covers the "Leave as Pending" case.
     Keep it, but make it true only once the wrap-around search above has found nothing. Simplest
     form: a `heatsDone` flag set at the same moment as `isSessionComplete`.
3. **`buildSessionContextLine`:** count the whole group in both modes. `uiState.allAthletes` is
   empty in a session that opened in Group Heats, so add `val athleteCount: Int` to
   `StopwatchUiState`, set it from `athletes.size` on load, and read it here. Group Heats already
   shows "Heat n of N" under the bar, which is enough to explain a split.

## 6. Constraints for this change

- **Natively `GROUP_START` tests must keep their own heat sizes** of 6 and 8. Only values of 1 or
  less, or null, fall back to the default.
- **Absent athletes must never block completion.** This applies to both the wrap-around search and
  the completion check.
- **Don't touch Individual mode's rotation** or anything else from the previous stopwatch fix.

## 7. Verification

**Unit, added to `StopwatchViewModelTest`. The first two fail today.**
1. `groupHeats_twoAthletes_oneTimedOneAtATimeTest_bothInOneHeat`: 5-10-5 (`athletesPerHeat = 1`),
   2 athletes, toggle to Group Heats. `heatAthletes.size == 2` and `totalHeats == 1`.
2. `groupHeats_multiTrial_runsEveryRound`: 2 athletes, 3 trials. Run 3 heats, capturing both
   athletes each time. Six results are stored, and `isSessionComplete` becomes true only after the
   third round.
3. `groupHeats_absentAthlete_doesNotBlockCompletion`.
4. `groupStartTest_keepsItsOwnHeatSize`: `athletesPerHeat = 8` with 10 athletes gives heats of
   8 and 2.

**Build:** `gradlew assembleDebug`, then `gradlew test`.

**Manual:**
1. In a 2-athlete group, open the 5-10-5 and tap Group Heats. Both athletes are listed, the screen
   shows "Heat 1 of 1", and the header still says "2 Athletes".
2. Start the clock and tap both athletes to finish. The same two come back for round 2, then
   round 3.
3. After round 3 the completion card appears.

*Why this wasn't caught:* `GroupTestingSelectAllTestsTest` only checks that each test *loads* in
both modes, never how many athletes a heat holds, and no test has ever driven a Group Heats round.
