# Spec: Leaving the testing grid loses the event, and finished events can't be edited

## 0. What "data saves" means here (correct in one line if wrong)

Scores are **not** lost. Every score is written to Room the moment the coach taps Save in the score
dialog (`RecordTestResultUseCase` → `repository.saveResult`), and the event row itself is written when
the coach taps Start on Create Event — before any score exists. What the coach experiences as "it
saved and closed" is three separate things:

1. **Back leaves silently.** The back arrow / system back pops straight to Home with no prompt.
2. **There's no door back in.** Once off the grid, the only route back is a small "Resume testing"
   link in Reports that appears *only* when some athlete is missing a result. A fully-tested event has
   no edit entry point at all.
3. **An empty event is kept.** Starting an event and backing out with zero scores leaves an empty
   event in Reports.

This spec assumes the coach wants: a confirmation before leaving, the option to discard an empty
event, and an always-visible **Edit results** in the event report. It does **not** block leaving —
trapping a coach on a screen with no exit is worse than the bug.

App switching (Home button / recents) is covered in §3.4: Android already keeps the grid on the back
stack; the only thing that resets is the on-screen timer and a half-typed score.

## 1. Objective & scope
- **Symptom:** "Whether we add data or not, it saves when we go back, and there's no way to edit it."
- **In scope**
  - Leave-confirmation dialog on the grid's back arrow **and** system back.
  - "Discard event" option in that dialog when the event has zero results.
  - "Edit results" button in the event report, shared by the standalone Session Report and the
    Reports-tab detail pane.
  - Rename the grid's "Save Results" button to "Finish Testing" (it never saved anything — scores are
    already saved; it only opens the completion dialog).
  - Timer and half-typed score survive rotation / app switch (`rememberSaveable`).
  - Make editing a score non-destructive on failure (§3.3).
- **Out of scope**
  - "Continue testing" on Home — already specified in `specs/2026-10-02-home-next-action-hero.md`
    (unimplemented). Together with this spec it closes the "no way back in" gap; ship either first.
  - Ungrouped Quick Test events (Reports hides them; `SessionReport` needs a `groupId`).
  - Draft/unsaved-scores mode. Instant persistence is the right model for field use and stays.

## 2. Verified context
- Read:
  - `ui/testing/CreateEventViewModel.kt:createEvent()` → `CreateEventUseCase` → `repository.createEvent(event, testIds)` on Start.
  - `ui/navigation/NavGraph.kt:218-223` — Create Event navigates to `TestingGrid` with `popUpTo(Screen.Dashboard.route)`, so back from the grid lands on Home.
  - `ui/navigation/NavGraph.kt:353-372` — `TestingGridScreen(onNavigateBack = { navController.popBackStack() }, …)`.
  - `ui/testing/TestingGridScreen.kt` — no `BackHandler`; top-bar arrow emits `OnNavigateBack`; timer is `remember { mutableIntStateOf(0) }`; bottom button "Save Results" → `OnRequestSaveSession` → `TestingCompleteDialog`.
  - `ui/testing/TestingGridViewModel.kt:saveScore()` — deletes the existing result **then** records the new one.
  - `ui/testing/TestingGridComponents.kt:556` — `ScoreEntryDialog` keeps `scoreText` in `remember`. Tapping a filled cell already opens it with `currentResult`, and it already offers delete — **the grid is already a working editor.**
  - `ui/session/SessionReportScreen.kt` — `SessionReportBody(uiState, padding, onAction, headerContent)` is shared; "Resume testing" appears only in `MissingDataCard` and `AbsentAthleteRow`.
  - `ui/report/ReportScreen.kt:431-438` and `NavGraph.kt:298-304` — both hosts already route `SessionReportAction.OnResumeTesting` to `TestingGrid` for grouped events.
  - `ui/navigation/AdaptiveNavigationWrapper.kt` — bottom bar shows only on the four tab routes, so the grid has no in-app "Home" button; leaving is always via back.
  - `domain/repository/TestingRepository.kt` — `deleteEventById`, `getEventResults(eventId): Flow`, `deleteResultById` exist.
- Existing pattern to copy: `ui/testing/stopwatch/StopwatchScreen.kt:57` — `BackHandler(enabled = uiState.hasPendingChanges)`.
- **No schema change. Database stays at v17, no migration.**
- **Drift noticed**
  - `TestingGridViewModel` and `SessionReportViewModel` inject `TestingRepository` / `PeopleRepository` directly. New logic goes through a use case; existing calls left alone.
  - `TestingGridData.students` should be `athletes` per the lexicon — not renamed here.

## 3. Root cause

### 3.1 Back exits without a word
`TestingGridScreen` has no `BackHandler`, and `OnNavigateBack` goes straight to `popBackStack()`. Because
Create Event was popped (`popUpTo(Dashboard)`), the coach lands on Home with the grid gone.

### 3.2 No way back for a complete event
The only `OnResumeTesting` emitters in `SessionReportBody` are inside `MissingDataCard` (rendered only
when `missingByTest[activeTestId]` is non-empty) and `AbsentAthleteRow`. Once every athlete has every
score, neither renders, so the report has no path to the grid. The plumbing behind it works — only the
button is missing.

### 3.3 Editing can destroy the old score
```kotlin
if (currentResult != null) testingRepository.deleteResultById(currentResult.id)
… recordTestResult(…)   // throws on out-of-range score, missing athlete, IO error
```
If `recordTestResult` throws (e.g. a typo outside `validMin..validMax`), the old score is already gone.
Also, if `getIndividualById` returns null, nothing is recorded and the old score is still deleted.
Low frequency today; once Edit becomes a front-door feature it becomes a real data-loss path.

### 3.4 App switch
Verified: the grid's route args come through `SavedStateHandle` and Navigation Compose saves the back
stack, so switching apps and coming back returns to the grid. Inferred, **not verified on device**:
after process death the restore goes through the auth gate's `Loading` branch in `NavGraph` before the
`NavHost` is composed. It should still restore; §7 has the check. What definitely resets today is the
timer (`remember`) and any digits typed into an open score dialog (`remember`).

## 4. Files
| Action | Path | Purpose |
|---|---|---|
| NEW | `app/src/main/java/com/vamshi/field/domain/usecase/testing/DiscardEmptyEventUseCase.kt` | delete an event only if it has no results |
| NEW | `app/src/test/java/com/vamshi/field/domain/usecase/testing/DiscardEmptyEventUseCaseTest.kt` | |
| NEW | `app/src/test/java/com/vamshi/field/ui/testing/TestingGridSaveOrderTest.kt` | regression for §3.3 |
| MODIFY | `app/src/main/java/com/vamshi/field/ui/testing/TestingGridViewModel.kt` | leave dialog state, discard, save order |
| MODIFY | `app/src/main/java/com/vamshi/field/ui/testing/TestingGridScreen.kt` | `BackHandler`, dialog, rename button, saveable timer |
| MODIFY | `app/src/main/java/com/vamshi/field/ui/testing/TestingGridComponents.kt` | `LeaveTestingDialog`; `scoreText` → `rememberSaveable` |
| MODIFY | `app/src/main/java/com/vamshi/field/ui/session/SessionReportScreen.kt` | "Edit results" button in `SessionReportBody` |

`NavGraph.kt` and `ReportScreen.kt` need **no** change — both already handle `OnResumeTesting`.

## 5. Fix

1. **Domain — `DiscardEmptyEventUseCase`**
   ```kotlin
   class DiscardEmptyEventUseCase @Inject constructor(private val repository: TestingRepository) {
       /** @return true if the event was deleted; false if it has results and was kept. */
       suspend operator fun invoke(eventId: String): Boolean {
           if (repository.getEventResults(eventId).first().isNotEmpty()) return false
           repository.deleteEventById(eventId)
           return true
       }
   }
   ```
   The guard lives in the domain, not the dialog, so a score saved from a stopwatch screen a moment
   earlier can never be discarded by a stale UI.

2. **ViewModel** — inject `DiscardEmptyEventUseCase`.
   ```kotlin
   // TestingGridUiState — add
   val showLeaveDialog: Boolean = false,
   val leaveConfirmed: Boolean = false,   // consumed by screen → onNavigateBack()

   // TestingGridAction — add
   data object OnRequestLeave : TestingGridAction
   data object OnDismissLeave : TestingGridAction
   data object OnConfirmLeave : TestingGridAction
   data object OnDiscardAndLeave : TestingGridAction
   data object OnLeaveConsumed : TestingGridAction
   ```
   `OnDiscardAndLeave` calls the use case; on `false` (results appeared) show
   `errorMessage = "Scores were recorded, so the event was kept."` and still leave.
   `leaveConfirmed` follows the existing `eventCreated` / `NavigationConsumed` pattern in
   `CreateEventViewModel` — the ViewModel doesn't navigate.

3. **Save order (§3.3)** — in `saveScore`: record the new result first, delete `currentResult` only after
   `recordTestResult` returns; if `fullAthlete == null`, surface an error and delete nothing. A brief
   window with two results for one cell is harmless — the grid's flow re-emits and the delete follows
   immediately; losing the only score is not harmless.

4. **Screen**
   - `BackHandler { onAction(TestingGridAction.OnRequestLeave) }`, and the top-bar arrow emits
     `OnRequestLeave` instead of `OnNavigateBack`. `OnNavigateBack` stays for the completion dialog's
     "Back to dashboard", which is already a deliberate exit.
   - `LaunchedEffect(uiState.leaveConfirmed) { if (it) { onAction(OnLeaveConsumed); onNavigateBack() } }`.
   - Timer: `rememberSaveable { mutableIntStateOf(0) }`.
   - Button label "Save Results" → "Finish Testing".

5. **`LeaveTestingDialog`** (in `TestingGridComponents.kt`, next to `TestingCompleteDialog`)
   - Has results — title "Leave testing?", body "Scores are saved as you enter them. You can come back to this event from Reports." Buttons: **Keep testing** (confirm slot, primary) · **Leave**.
   - Zero results — title "No scores recorded", body "This event has no scores yet." Buttons: **Keep testing** · **Keep event for later** · **Discard event** (`colorScheme.error`).
   - `scoreText` in `ScoreEntryDialog`: `rememberSaveable(athleteName, currentResult?.id)`.

6. **Edit results in Reports** — in `SessionReportBody`, as the first item after `headerContent`, an
   `OutlinedButton` (full width, `Icons.Default.Edit`) labelled **Edit results** that emits the existing
   `SessionReportAction.OnResumeTesting`. Render only when `data.group != null`. Because it lives in
   the shared body, both the standalone Session Report and the Reports-tab detail pane get it without
   touching either host.

## 6. Constraints for this change
- **Never delete an event that has results.** The emptiness check is in the use case, re-read at click time.
- **Medical alerts.** The grid is unchanged and still shows `Individual.medicalAlert` / `isRestricted` on resume and edit — don't build a separate "edit" screen that bypasses that.
- Completion dialog "Back to dashboard" must **not** trigger the leave dialog (double prompt).
- Back on an open score dialog or timing dialog closes that dialog first (the dialogs own back while shown); the leave prompt fires only from the bare grid.

## 7. Verification
- **Regression that fails today — `TestingGridSaveOrderTest`:** a fake `TestingRepository` holding one result; save an out-of-range score through the edit path; assert the original result still exists. Fails on current delete-then-record.
- **Unit — `DiscardEmptyEventUseCaseTest`:** no results → deleted, returns true; one result → not deleted, returns false.
- **Build:** `gradlew assembleDebug` then `gradlew test`
- **Manual (device):**
  1. Create event, record nothing, press system back → "No scores recorded" → Discard → Home; Reports has no such event.
  2. Repeat, choose Keep event for later → event in Reports with Edit results → opens the grid.
  3. Record two scores, tap back arrow → "Leave testing?" → Keep testing stays; Leave → Home.
  4. Reports → that event → Edit results → tap a filled cell → change score → report updates. Enter an out-of-range value → error banner, old score still shown.
  5. Mid-grid with a score dialog half-typed: rotate, then press Home and reopen from recents → grid, dialog text and timer intact.
  6. Process death: background the app, run `adb shell am kill com.vamshi.field`, reopen from recents → should land on the grid. **If it lands on Home, stop and report** — that's a nav-restore issue in the auth gate, out of this spec's scope.

Why this wasn't caught: there's no test around `TestingGridViewModel.saveScore`, and the only route
from Reports back to the grid was conditional on missing data, so manual testing on a fully-scored
event never exercised it.
