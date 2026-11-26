# Spec: Triage of Gemini's whole-app bug audit (Reports, Roster, Settings, Library)

## 0. Open question (answer changes scope)
**Deleting an athlete erases their whole history (finding 6).** `PeopleDao.deleteIndividual` is a
Room `@Delete`, and `test_results.individualId` is `ON DELETE CASCADE`, so every result the athlete
ever recorded disappears from past event reports, leaderboards and group trends. The dialog does
say "cannot be undone", so this is a product decision, not a code defect:

- **(a) Keep the hard delete, say what it does** — dialog copy names the result count. Copy-only.
- **(b) Soft delete** (`isDeleted = 1`, which every `PeopleDao` read already filters on) — history
  stays in event reports. Needs decisions about how a deleted athlete renders in old reports and
  whether backup carries them (it already does: `BackupIndividual.isDeleted`).

This spec assumes **(a)** and leaves (b) for its own spec. Everything else below is independent of it.

## 1. Objective & scope
- **Symptom:** Gemini reported 13 bugs outside the testing workflow. This spec verifies each and
  fixes the real ones.
- **In scope:** findings 1, 2, 3, 4, 5, 7, 8, 9, plus one Gemini missed (N1, below).
- **Out of scope:** 6 (decision above), 10 (not a bug), 11–13 (cosmetic / feature gaps — listed
  for a later pass), the in-flight testing-workflow changes in the working tree.

## 2. Verified context
Read: `ReportsRepositoryImpl.kt`, `ReportsHubViewModel.kt`, `GetAthleteRadarDataUseCase.kt`,
`AthleteDashboardViewModel.kt`, `RosterViewModel.kt`, `RosterScreen.kt`, `NavGraph.kt`,
`SettingsViewModel.kt`, `RestoreBackupViewModel.kt`, `CompleteRestoreUseCase.kt`,
`AuthRepositoryImpl.kt`, `BackupDao.kt`, `PeopleDao.kt`, `TestLibraryScreen.kt`,
`VideoPlayerModal.kt`, `TestVideoPreview.kt`, `SessionReportViewModel.kt`, `PerformanceThresholds.kt`,
`norms.csv` (head). Grep confirmed `recentSessions` is consumed only by the two Event Report paths,
both of which filter `groupId != null`.

**No schema change. Database stays at 17. No migration, no backup-payload change.**

**Drift noticed:**
- This skill's own text says Room v15 and 60/30 grid thresholds; `CLAUDE.md` (v17, 80/40
  everywhere) is current. The skill needs updating.
- ViewModels touched here inject repositories directly (`ReportsHubViewModel`, `RosterViewModel`,
  `SessionReportViewModel`, `SettingsViewModel` also injects `data.backup.DriveBackupHelper` — a
  `data.*` import in `ui/`). Known debt per `DEVELOPMENT_CONTEXT.md` §2; not pulled into scope.

## 3. Verdict per finding

| # | Gemini claim | Verdict | Severity |
|---|---|---|---|
| 1 | Norm-band chart draws "Healthy" at the Needs-Improvement floor | **Confirmed** | High — wrong benchmark shown to coaches |
| 2 | Reports-hub radar stale after results change; custom tests missed | **Confirmed** (both halves) | Medium |
| 3 | Roster stacks duplicate Flow collectors on every emission | **Confirmed** | Medium — grows with every roster edit |
| 4 | Restore from Settings leaves session on a deleted user | **Confirmed** | Medium (greeting lost; reports unaffected) |
| 5 | `take(15)` before group filter hides group events | **Confirmed** | Medium |
| 6 | Athlete delete cascades history | Real behaviour, **decision** | see §0 |
| 7 | Duplicate athletes in Peers sheet | **Confirmed** | Low–Medium |
| 8 | Quick Test no-op in Roster's athlete pane | **Confirmed** | Medium — four buttons silently dead |
| 9 | Library snackbar outside Scaffold, never auto-dismisses | **Confirmed** | Low |
| 10 | VideoPlayerModal not keyed on `youtubeId` | **Not a bug** | — |
| 11 | Header-only CSV on 0 results | Real, trivial | Later |
| 12 | Presets not backed up; local USER tests survive restore | Real, feature gap | Later |
| 13 | Success text in `errorMessage` | Real, cosmetic | Later |
| N1 | *(missed)* Session switcher leaks collectors and keeps a stale test | **Confirmed** | Medium |

## 4. Root cause

### 1 — `findScoreForPercentile` picks the nearest band, and 35 is nearer to 20 than to 60
```kotlin
healthyMin = findScoreForPercentile(normBands, 35, test.isHigherBetter),   // ReportsRepositoryImpl:507
needsMax   = findScoreForPercentile(normBands, 34, test.isHigherBetter)
...
val bestBand = normBands.minByOrNull { abs(it.percentile - targetPercentile) }
```
Norm rows encode bands as 20 / 60 / 90 (CLAUDE.md norm contract). |20−35| = 15 < |60−35| = 25, so
`healthyMin` resolves to the **Needs Improvement** row and returns its `minScore` (0 for push-ups).
The 35/34 targets are leftovers from the pre-80/40 thresholds. `superiorMin` (target 80) happens to
land on the 90 row, so only the Healthy line is wrong. Every chart in Athlete × Test detail draws
"Healthy (0)" at the bottom of the axis.

### 2 — radar computed once per load
`ReportsHubViewModel.loadAthlete` starts `async { getAthleteRadarData(id) }` *before*
`observeAthleteDashboard(...).collect`, then `await()`s the same Deferred on every emission — a
completed Deferred returns its first value forever. `AthleteDashboardViewModel` does it right
(async inside collect). Separately, `GetAthleteRadarDataUseCase` memoises `cachedCategories` /
`cachedTestMap` for the life of the ViewModel; a custom test created afterwards is absent from the
map and its results are dropped with a "Test not found" warning.

### 3 — Roster collectors
Every emission of `getAllGroups()` calls `loadGroupMembers(id)` for every group, each of which
`launch`es a new infinite Room collector; same for `getAllIndividuals()` → `loadAthleteGroups`.
Nothing cancels the old ones (`groupMembersJob` is declared and never used). After *k* roster edits
there are ~*k* collectors per group and per athlete, all re-querying on every table invalidation.

### 4 — Settings restore skips `CompleteRestoreUseCase`
`restoreAllData` clears `users` and inserts the backup's user row. A backup taken on another device
or install carries a different user id. `RestoreBackupViewModel` calls `CompleteRestoreUseCase`
(`establishPrimarySession`) afterwards; `SettingsViewModel.handleRestoreData` does not. The session
pref keeps the old id, `observeCurrentUser()` emits null, Dashboard greets "Coach".
*Verified:* the missing call. *Inferred:* next cold start still works, because
`ResumeSessionUseCase` only checks that an id is set — so this persists across launches.

### 5 — `observeHome`
`val recent = events.take(15).map { … }` then both consumers filter `groupId != null`. Fifteen
recent Individual/Quick Tests (personal sessions) leave the Event Report tab showing
"No Testing Events" while group events exist.

### 7 — `buildPeerLeaderboard` maps every result row
`testing.getEventResults(sessionId).first().filter { it.testId == test.id }.map { … }` — one row per
trial or re-entry. `observeSessionReport` dedupes by `(individualId, testId)` latest; this path doesn't.

### 8 — `RosterScreen.kt:125`
`onStartQuickTest = { _, _ -> /* no-op … */ }`. Affects "Start Quick Test (N)", a missing-test row,
a Missing-tests flag and an untested radar spoke — whenever an athlete is opened from Roster.

### 9 — `TestLibraryScreen`
`Snackbar(...)` is emitted after the `Scaffold`, outside any `SnackbarHost`, so it draws at the
parent's top-left over the top bar and stays until OK is tapped.

### 10 — not a bug
Both callers (`TestVideoPreview`, `TestLibraryScreen`) hold one id per modal instance; the modal is
a full-screen dialog, so the id cannot change while it is composed — it must be dismissed first,
which disposes the WebView.

### N1 — `SessionReportViewModel.load` (Gemini missed this)
```kotlin
private fun load(sessionId: String) {
    viewModelScope.launch { observeSessionReport(groupId, sessionId).collect { data ->
        _uiState.update { it.copy(data = data, selectedTestId = it.selectedTestId ?: …) } } }
}
```
`OnSwitchSession` calls `load` again without cancelling the previous collector. Any later result
write re-emits **both** flows and the screen can flip back to the session the coach switched away
from. And `selectedTestId` survives the switch: if the new session didn't run that test, the
leaderboard renders empty. (`ReportsHubViewModel.loadEvent` already does both correctly.)

## 5. Files

| Action | Path | Finding |
|---|---|---|
| MODIFY | `data/repository/ReportsRepositoryImpl.kt` | 1, 5, 7 |
| MODIFY | `ui/report/ReportsHubViewModel.kt` | 2 |
| MODIFY | `domain/usecase/testing/GetAthleteRadarDataUseCase.kt` | 2 |
| MODIFY | `ui/roster/RosterViewModel.kt` | 3 |
| MODIFY | `ui/settings/SettingsViewModel.kt` | 4 |
| MODIFY | `ui/roster/RosterScreen.kt`, `ui/navigation/NavGraph.kt` | 8 |
| MODIFY | `ui/testlibrary/TestLibraryScreen.kt` | 9 |
| MODIFY | `ui/session/SessionReportViewModel.kt` | N1 |
| MODIFY | `ui/roster/RosterDialogs.kt` | 6 (option a, copy only) |
| NEW | `app/src/test/.../data/repository/NormBandThresholdTest.kt` | 1 |
| NEW | `app/src/test/.../ui/session/SessionReportSwitchTest.kt` | N1 |

All paths under `app/src/main/java/com/vamshi/field/` unless shown.

## 6. Fix

1. **Norm bands.** Replace nearest-percentile matching with classification matching: extract an
   `internal fun bandScore(bands, target: Classification, isHigherBetter): Double?` that picks the
   band whose `PerformanceThresholds.classify(band.percentile) == target` (min score if higher is
   better, else max). Call it with `SUPERIOR`, `HEALTHY`, `NEEDS_IMPROVEMENT`. No magic 35/34; it
   follows the thresholds if they ever move, and works for coach-authored bands (also 20/60/90).
2. **Radar.** Move the `async { getAthleteRadarData(id) }` inside `.collect`, exactly as
   `AthleteDashboardViewModel` does. Delete the two cache fields in `GetAthleteRadarDataUseCase` —
   two small Room reads per call; correctness beats a micro-cache.
3. **Roster.** Keep `Map<String, Job>` for member collectors and for athlete-group collectors. On
   each list emission: cancel and remove jobs whose id is gone, launch only for new ids (the Flows
   already re-emit on change, so existing ids need nothing). Drop the unused `groupMembersJob`.
   Also prune removed ids from `groupMembers` / `athleteGroups`.
4. **Settings restore.** Inject `CompleteRestoreUseCase`; after `restoreDataUseCase(backupId)`
   succeeds, call it before reporting success. Reuse, don't re-implement, the restore-screen path.
5. **Recent sessions.** `events.filter { it.groupId != null }.take(15)` — safe, both consumers
   already discard personal sessions. Rename nothing.
6. **Athlete delete copy (option a).** Dialog: "Delete <name>? Their N recorded results will also
   be removed from past event reports. This can't be undone." Needs a result count — reuse
   `TestingRepository.getAllResultsForIndividual(id).first().size` via a small
   `CountAthleteResultsUseCase` in `domain/usecase/people/` (new code goes through a use case).
7. **Peers.** Group `results` by `individualId`, keep `maxBy { createdAt }` — the same rule as
   `observeSessionReport`, so the sheet and the Event Report never disagree.
8. **Roster Quick Test.** Add `onStartQuickTest: (String, List<String>) -> Unit` to `RosterScreen`,
   pass it to the detail pane, and wire it in `NavGraph` the way the AthleteDashboard route does
   (`Screen.QuickTest.createRoute(aId, testIds)`).
9. **Library snackbar.** `remember { SnackbarHostState() }`, `snackbarHost = { SnackbarHost(it) }`
   on the Scaffold, `LaunchedEffect(uiState.snackbarMessage)` → `showSnackbar` →
   `OnDismissSnackbar` — the pattern `RosterContent` already uses.
10. **N1.** `private var loadJob: Job?`; `loadJob?.cancel()` at the top of `load`. In
    `OnSwitchSession` also reset `selectedTestId = null`.

## 7. Constraints for this change
- Fix 1 must not touch stored percentiles — it only changes the benchmark lines drawn.
- Fix 3: `combine`-style rewrites are tempting but change emission timing on a screen with swipe
  and selection state; the job-map is the smaller change.
- Fix 4: restore must still report success if the session step fails? **No** — mirror
  `RestoreBackupViewModel`, which surfaces "The backup didn't contain a valid account".
- Don't touch the files already modified in the working tree for the testing-workflow work
  (`StandardsRepositoryImpl`, grid, stopwatch, quick test, create event).

## 8. Verification
**Regression tests that fail today:**
- `NormBandThresholdTest.healthyLineUsesHealthyBand` — bands {0–21 @20, 22–35 @60, 36–100 @90},
  higher-is-better → Healthy = 22 (today returns 0).
- `SessionReportSwitchTest.switchingSessionDropsOldCollectorAndStaleTest` — fake repo emits for
  session A after switching to B; state must stay on B with B's first test.

Then:
- Unit: peers dedupe (two results, one athlete → one row); `observeHome` with 15 personal events
  + 1 group event → group event present.
- `gradlew testDebugUnitTest`, then `gradlew assembleDebug`.
- **Manual (device):**
  1. Athlete × Test chart for push-ups → "Healthy" line at the healthy cut, not 0.
  2. Reports → Athlete Profile, record a result elsewhere, return → radar updated.
  3. Roster → open athlete → "Start Quick Test" opens Quick Test.
  4. Library → delete a custom test → snackbar at bottom, auto-dismisses.
  5. Event report → switch session → leaderboard shows the new session's first test.
  6. Settings → restore a backup from another device → Dashboard greets the restored coach.

**Why not caught:** none of these ViewModels or the reports repository helpers have unit tests;
finding 1 is invisible without a chart and a known norm table to compare against.

## Later (not in this spec)
- 11: hide CSV export when there are no results.
- 12: back up `CustomPresetsStore` presets; decide whether restore should drop local USER tests absent
  from the backup.
- 13: split `statusMessage` from `errorMessage` in Settings.
- Restore screen never writes `google_account_name`, so Settings shows "Connect Google Drive" after a
  reinstall restore.
- Option (b) soft delete for athletes, if chosen in §0.
