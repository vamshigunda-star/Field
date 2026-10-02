# Spec: App reported "stuck on loading" and "not building" — neither reproduces; cold start is 13s

## 0. Open question (answer changes scope)

**The reported premise did not reproduce.** On this machine, at `cf73716` with a clean working
tree, the app builds (clean and incremental) and launches to a fully rendered screen on both
startup paths. So the document has to branch:

| Branch | What it means | What this spec does |
|---|---|---|
| **A — the coach gave up during a slow cold start** | 13.6s first launch, 6.9s later cold starts. Splash then a spinner, with no progress feedback. | **Assumed.** Sections 3–7 address this. |
| **B — a real failure on a device/build not tested here** | A physical device, a release build, or an Android Studio run configuration fails in a way the CLI + emulator do not. | Needs one artifact from the user (below). Out of scope until then. |

**The rest of this document assumes Branch A.** If it is actually B, what distinguishes them is
a single logcat capture from the failing device:

```bash
D:\Android\Sdk\platform-tools\adb.exe logcat -c; D:\Android\Sdk\platform-tools\adb.exe logcat | findstr /C:"FieldApplication" /C:"MainActivity" /C:"AuthGateViewModel" /C:"ALearningNavGraph" /C:"FATAL"
```

If that capture ends at `Auth gate is loading...` and never prints `Auth gate resolved:`, it is B
and this spec is void. If it prints `Auth gate resolved:` — as it does here — it is A.

## 1. Objective & scope

- **Symptom as reported:** "the app is not working — it is on loading phase", "the app itself is not building."
- **Symptom as observed:** the app builds and opens. It is *slow*: 13.6s to first frame on first
  launch after install, 6.9s on later cold starts, 33ms warm. During that window the coach sees the
  system splash followed by an unlabelled `CircularProgressIndicator`, which is indistinguishable
  from a hang.

- **In scope:**
  - Remove the leftover `Log.e` debug instrumentation from the previous session (4 files).
  - Cut measured cold-start cost: add `androidx.profileinstaller` + a baseline profile.
  - Make the auth-gate wait legible instead of a bare spinner.
- **Out of scope:**
  - Any change to the auth gate's *logic*. It is correct (§3).
  - The 14 ViewModels that inject repositories directly — pre-existing, unrelated.
  - The demo data shipped in the prepackaged DB — intentional (§3, note).

## 2. Verified context

Read, and what each confirmed:

| File | Confirmed |
|---|---|
| `app/build.gradle.kts:40-42,187-201` | `isMinifyEnabled = true` on release; **no `profileinstaller`, no baseline profile**. `generatePrepackagedDb` is an `Exec` task shelling out to `python`. |
| `FieldApplication.kt` | Seeding is correctly off the main thread (`CoroutineScope(SupervisorJob() + Dispatchers.IO)`). Carries 2 `Log.e` breadcrumbs. |
| `MainActivity.kt` | `installSplashScreen()` then `setContent`. Carries 4 `Log.e` breadcrumbs. |
| `ui/auth/AuthGateViewModel.kt` | `withTimeoutOrNull(5000)` **plus** a `catch (e: Exception)`, both falling back to `UnauthenticatedNoUsers`. Carries 6 `Log.e`/`Log.w` breadcrumbs. |
| `ui/navigation/NavGraph.kt:50-62` | The reported "loading phase" is this `CircularProgressIndicator`, shown while `AuthGateState.Loading`. Carries 2 `Log.d` breadcrumbs. |
| `data/repository/SessionManagerImpl.kt` | `currentUserIdOnce()` is `withContext(Dispatchers.IO)`. **Not** a main-thread block. |
| `di/DatabaseModule.kt` | Room 3 `createFromAsset` + 13 migrations through `MIGRATION_15_16`, `AndroidSQLiteDriver`. |
| `tools/build_prepackaged_db.py:14-15,173-230` | Deletes and recreates the DB each run; **deliberately** generates demo data under `random.seed(42)`. |

**Build — both green, exit code 0:**

| Command | Result |
|---|---|
| `gradlew assembleDebug` (incremental) | BUILD SUCCESSFUL in 28s |
| `gradlew clean assembleDebug` | BUILD SUCCESSFUL in 4m 7s, 43 tasks executed |

Only warnings surfaced: an unnecessary safe call at `ui/report/components/AthleteRow.kt:129` and a
deprecated `Modifier.menuAnchor()` at `ui/testing/CreateEventScreen.kt:359`. Neither fails a build.

**Tooling:** adb did **not** need installing — it was already at
`D:\Android\Sdk\platform-tools\adb.exe` (SDK root `D:\Android\Sdk`, per `local.properties` and
`ANDROID_HOME`). Tested on the `Medium_Phone` AVD, `sdk_gphone16k_x86_64`.

**No schema change is implied. The database stays at version 16.** The v15→v16 migration ran
cleanly; `SeedDataManager` reported `testCount=83, normCount=2453` on both a seeded and a
freshly-cleared install. An implementer should not invent a migration for this work.

- **Drift noticed:**
  - `AuthGateViewModel` uses `_state.value = …` rather than `_uiState.update { … }`. Harmless here
    (single writer, one terminal assignment), but it is not the house convention.
  - This ViewModel exposes `state`, not `uiState`, and a bare `AuthGateState` rather than a
    `…UiState` data class. Deliberate and reasonable for a gate with no other fields — noted so it
    is not "fixed" into conformity by mistake.
  - `generatePrepackagedDb` declares `inputs.dir("src/main/assets")` while its own output,
    `src/main/assets/database/alearning.db`, lives **inside** that directory. Gradle tolerates it
    today but it defeats up-to-date checking and is exactly the kind of thing that becomes an error
    in Gradle 10, which the build is already warned about. Observation, not scope.

## 3. Root cause

### 3.1 The auth gate cannot hang indefinitely — verified

`AuthGateViewModel.init` wraps the whole resolution in `withTimeoutOrNull(5000)` and wraps *that*
in `try/catch`, with both failure paths assigning `AuthGateState.UnauthenticatedNoUsers`. There is
no path that leaves `_state` at `Loading` forever. The spinner in `NavGraph.kt:53` is therefore
bounded by construction, and "stuck on loading" cannot be a literal permanent hang in this code.

Logcat confirms it resolves on both paths:

```
E AuthGateViewModel: currentUserIdOnce: 2daacdef-413b-40e8-bc12-7231eaefbd6f
E AuthGateViewModel: Resolved to: Authenticated          → Dashboard rendered, 6 athletes / 12 events
```
```
E AuthGateViewModel: currentUserIdOnce: null
E AuthGateViewModel: userCount: 0
E AuthGateViewModel: Resolved to: UnauthenticatedNoUsers  → Onboarding rendered, "Let's get set up"
```

Both were screenshotted and render correctly. No `FATAL`, no `AndroidRuntime`, process alive
throughout.

### 3.2 What the coach actually experienced — measured

`ActivityTaskManager: Displayed com.vamshi.field/.MainActivity for user 0: +13s626ms`

Breakdown of that first launch, from timestamps in a single logcat capture:

| Phase | Cost | Thread | Verified? |
|---|---|---|---|
| Process start → `FieldApplication.onCreate` | 3.47s | main | verified |
| `FieldApplication.onCreate` → `super.onCreate` returns (Hilt injection) | 0.73s | main | verified |
| `MainActivity.super.onCreate` → `setContent` body entered | **4.56s** | main | verified |
| `setContent` → `Surface composed` | 0.77s | main | verified |
| → auth gate resolved, first frame | ~2.5s | main | verified |
| *(concurrent)* `SeedDataManager` parsing 2453 norms from CSV | 5.45s | `Dispatchers.IO` | verified |

Subsequent measurements:

| Launch type | TotalTime |
|---|---|
| First cold start after install | 13,626ms |
| Cold start, already seeded | 6,959ms |
| Warm start | 33ms |

The two dominant terms — 3.47s to reach `Application.onCreate` and 4.56s of Compose setup before
the first composable body runs — are **class loading and first-time composition in a non-optimised
debug build**, with no baseline profile to pre-compile the startup path. That attribution is
**inference**, though a well-supported one: it is the classic debug-build profile, the work is not
in any app code that logs, and the same binary warm-starts in 33ms.

The 5.45s of CSV seeding runs on `Dispatchers.IO`, correctly off the main thread, but on an
emulator it still contends for CPU with startup — which is why the first launch costs ~6.7s more
than the next one.

**So: the app was never broken. It was slow, and a bare spinner gave the coach nothing to
distinguish "working" from "hung."** On a physical device a debug build will be faster than these
emulator numbers, and a release build faster still — which is consistent with this never having
been reported as a hard failure.

### 3.3 Leftover debug instrumentation

The previous session left `Log.e`/`Log.w`/`Log.d` breadcrumbs across four startup files. They are
how this investigation was possible, and they should not ship: `Log.e` is the error channel, and
using it for `"onCreate started"` means any future crash triage is reading noise. 14 call sites
across `FieldApplication`, `MainActivity`, `AuthGateViewModel` and `NavGraph`.

**Note, not a defect:** the prepackaged DB ships 5 athletes, 2 groups, 10 testing events and 200
test results. This is **intentional** — `tools/build_prepackaged_db.py:206` seeds it under
`random.seed(42)`. Flagged only because it is a product decision worth confirming before release:
every new coach starts with a populated roster they did not create. No code change proposed here.

## 4. Files

| Action | Path | Purpose |
|---|---|---|
| MODIFY | `app/src/main/java/com/vamshi/field/FieldApplication.kt` | Drop 2 `Log.e` breadcrumbs + the `android.util.Log` import |
| MODIFY | `app/src/main/java/com/vamshi/field/MainActivity.kt` | Drop 4 `Log.e` breadcrumbs + import |
| MODIFY | `app/src/main/java/com/vamshi/field/ui/auth/AuthGateViewModel.kt` | Drop 6 breadcrumbs; keep one real `Log.w` on the timeout path |
| MODIFY | `app/src/main/java/com/vamshi/field/ui/navigation/NavGraph.kt` | Drop 2 `Log.d`; give the loading state a label |
| MODIFY | `gradle/libs.versions.toml` | Add `androidx.profileinstaller` |
| MODIFY | `app/build.gradle.kts` | Add the `profileinstaller` dependency |
| NEW | `app/src/main/baseline-prof.txt` | Baseline profile covering the startup path |

## 5. Fix

Ordered so each step compiles on the last.

### Step 1 — remove the debug instrumentation

In all four files, delete the breadcrumb calls and the now-unused `import android.util.Log`.

**Keep exactly one**, in `AuthGateViewModel`, because it reports a genuine abnormal condition:

```kotlin
if (result == null) {
    Log.w("AuthGateViewModel", "Auth resolution timed out after 5s; falling back to onboarding.")
    _state.value = AuthGateState.UnauthenticatedNoUsers
}
```

Keep the `catch` block's `Log.e("AuthGateViewModel", "Error resolving auth state", e)` too — that
one is an actual error with a throwable, which is what `Log.e` is for.

*Why not leave them all in:* they were scaffolding for exactly this investigation, and they have
now done their job. Leaving `Log.e("MainActivity", "onCreate started")` in a release build means
the first thing a future maintainer sees in a crash report is a false error.

### Step 2 — label the loading state

`NavGraph.kt:50-62` currently renders a bare `CircularProgressIndicator`. Wrap it in a `Column`
with the spinner and a `Text("Loading your roster…")` beneath, using
`MaterialTheme.typography.bodyMedium` and `onSurfaceVariant`.

*Why this and not a timeout-and-error-screen:* the gate already resolves within 5s by construction
(§3.1), so there is no state to escape to. The defect is that the coach cannot tell a working app
from a hung one — that is a communication problem, and a label fixes it. Adding an error path here
would be inventing a failure mode that the code cannot produce.

### Step 3 — baseline profile

Add `androidx.profileinstaller:profileinstaller` to the version catalog and to `app/build.gradle.kts`
as an `implementation` dependency, and add `app/src/main/baseline-prof.txt`.

*Why this and not "move work off the main thread":* there is no app work left to move. Seeding is
already on `Dispatchers.IO`; `currentUserIdOnce()` is already on `Dispatchers.IO`. The 8s that
dominates cold start is class loading and first-time composition *inside the framework*, and a
baseline profile is the mechanism that exists for precisely that. Chasing it with more coroutines
would change nothing and would make the startup path harder to reason about.

Generate the profile against a release build rather than hand-writing it; hand-written rules that
drift from the real startup path are worse than none.

**Expected effect, stated honestly:** baseline profiles typically recover 20–30% of cold start on
release builds. That would take the ~7s steady-state cold start into the ~5s range on this
emulator, and materially lower on real hardware. It will **not** make the debug build fast — debug
cold start stays slow by nature, which is worth telling the user plainly so the next "it's stuck"
report is calibrated.

## 6. Constraints for this change

- **Do not touch the auth gate's resolution logic.** The `withTimeoutOrNull(5000)` + `catch`
  pairing is what makes an indefinite hang impossible. Step 1 edits log lines inside that block and
  must leave both fallback assignments intact.
- **Do not move seeding onto the main thread** to make it "finish before the UI." It writes to
  `norm_references` and the recommendation tables; blocking startup on it would turn a 13s slow
  launch into a guaranteed ANR.
- **No schema change.** Database stays at 16, no new migration, no `MigrationTest` addition, and
  `tools/build_prepackaged_db.py` does not need rerunning for this work.
- **Do not "fix" `AuthGateState` into the `…UiState` house shape** as drive-by cleanup — it is a
  navigation gate, not a screen, and the rename would touch `NavGraph` for no behavioural gain.

## 7. Verification

**The regression test that fails today, first.** There is currently no test asserting the auth gate
terminates. Add `app/src/test/java/com/vamshi/field/ui/auth/AuthGateViewModelTest.kt`:

- `authGate_whenRepositoryHangs_fallsBackToOnboardingWithinTimeout` — inject a fake
  `AuthRepository` whose `userCount()` suspends indefinitely; advance virtual time past 5s; assert
  the state leaves `Loading` and lands on `UnauthenticatedNoUsers`. Use
  `kotlinx-coroutines-test` with a `StandardTestDispatcher` so the timeout is virtual, not wall
  clock. **This test does not exist today** — which is why a suspected startup hang had to be
  diagnosed by hand with `Log.e` breadcrumbs instead of being caught by the suite.
- `authGate_whenSessionExists_resolvesToAuthenticated` — fake returns a user id; assert `Authenticated`.
- `authGate_whenRepositoryThrows_fallsBackToOnboarding` — fake throws; assert the `catch` path.

Copy the existing fakes under `app/src/test/…` (e.g. `FakeAuthRepository`) rather than mocking.

**Build:**
```bash
gradlew assembleDebug
```
```bash
gradlew test
```

**Manual — walks the original symptom:**
1. `adb install -r app/build/outputs/apk/debug/app-debug.apk`
2. `adb shell pm clear com.vamshi.field` (forces the first-run seeding path)
3. `adb shell am start -W -n com.vamshi.field/.MainActivity` — record `TotalTime`.
4. Confirm the loading state now reads "Loading your roster…" rather than a bare spinner.
5. Confirm Onboarding ("Let's get set up") appears and accepts a coach name.
6. `adb logcat -d | findstr FieldApplication MainActivity AuthGateViewModel` — confirm **no**
   breadcrumb output remains.
7. Relaunch cold (`am force-stop` then `am start -W`) and confirm the Dashboard path still renders.

**Why this wasn't caught:** there is no test covering `AuthGateViewModel` at all, and no
startup-time assertion anywhere in the suite. The unit suite (107 tests) exercises domain logic but
nothing about whether the app reaches a first frame. The gate test above closes the correctness
half; the timing half is only observable on a device, which is an argument for keeping the
`am start -W` step in the release checklist rather than for adding a macrobenchmark module now.
