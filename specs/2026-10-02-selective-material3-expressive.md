# Spec: Material 3 Expressive, applied selectively

> **Correction found during execution (2026-10-02).** The table below overstates 1.4.0. The
> compiler rejected `MaterialExpressiveTheme` and `MaterialTheme.motionScheme` as Kotlin
> `internal`. They're JVM-public, which is what misled the bytecode check. **No Expressive API
> is usable on stable 1.4.0.** As implemented: `FieldTheme` keeps `MaterialTheme`, and the same
> springs come from Field-owned tokens in `ui/theme/Motion.kt` (`FieldMotion`), with values
> copied from 1.4.0's `ExpressiveMotionTokens`. The press corner-radius morph was dropped:
> `pressInteraction` only gets a generic `Shape`. `TestSelectorHeroCard` was dropped: it never
> displays its counts.

## 0. Open question (answer changes scope): which material3?

**Finding, from the material3 1.4.0 bytecode in the Gradle cache (not the release notes, which
were wrong about this):** stable 1.4.0 contains very little Expressive *component* surface.

| Expressive piece | 1.4.0 (BOM 2026.09.00, current) | 1.5.0-alpha29 |
|---|---|---|
| `MaterialExpressiveTheme(colorScheme, motionScheme, shapes, typography, content)` | **public, no opt-in** | public |
| `MaterialTheme.motionScheme` + `MotionScheme.default/fast/slow Spatial/Effects Spec()` | **public** | public |
| `MotionScheme.expressive()` / `.standard()` | `internal`, but `MaterialExpressiveTheme` installs the expressive scheme by default | public |
| Emphasized type scale (`titleLargeEmphasized`, …) | `internal` | public |
| `LoadingIndicator`, `LinearWavyProgressIndicator` / `CircularWavyProgressIndicator` | **absent** | present |
| `ButtonGroup`, `SplitButton`, `FloatingActionButtonMenu` | **absent** | present |

- **A, recommended: stay on stable 1.4.0.** Turn on the expressive motion scheme through
  `MaterialExpressiveTheme`, and build the hero, progress and entry treatments below from stable
  APIs driven by `MaterialTheme.motionScheme`. Field is meant to be picked up later by maintainers
  who aren't known yet. An alpha that has shipped 29 times reshuffles its signatures, so it
  doesn't belong in that kind of project.
- **B: pin `androidx.compose.material3:material3:1.5.0-alpha29` over the BOM.** That gets the real
  `LoadingIndicator` and the wavy progress indicators. It adds an alpha to a release build, and
  every later BOM bump becomes a manual version reconciliation.

The rest of this document assumes **A**. Under A, every place that would use `LoadingIndicator`
or wavy progress goes through one shared component (§5.2, §5.3). Moving to B later is then a
change to two files, not twenty.

## 1. Objective & scope
- **As a coach**, the moments that matter (a result landing, starting a test, waiting on
  something) should read faster and feel responsive. The dense lists I scan all day stay exactly
  as they are.
- **In scope**
  1. Expressive motion scheme app-wide via `MaterialExpressiveTheme` in `FieldTheme`.
  2. One shared loading component replacing 9 near-identical local copies.
  3. Progress bars that animate to new values with the theme's spatial spring.
  4. Hero results: a count-up number and a spring entrance, colours from `performanceZoneColors(...)`.
  5. Motion on result entry: a single cell's one-shot spring when *its* score is saved.
  6. Primary start actions: brand orange plus a press-shape spring.
- **Out of scope**
  - Roster lists, test-library lists, the testing grid's layout and density, leaderboard rows.
    No restructuring, and no label removed anywhere.
  - `ButtonGroup`, `SplitButton`, the FAB menu (not in 1.4.0; no screen needs them).
  - Colour token changes. `Color.kt` is generated from `design.md` §12 by
    `generateColorsFromDesign` and is never edited by hand.
  - Typography changes. Plus Jakarta Sans stays, and no emphasized styles are added (they're
    internal in 1.4.0).

## 2. Verified context
- Versions: `composeBom = "2026.09.00"` → material3 1.4.0, compose ui 1.12.1. `agp = "9.1.1"`,
  `compileSdk = 37` (from step 1).
- `ui/theme/Theme.kt:89` — `FieldTheme` calls `MaterialTheme(colorScheme, typography = Typography,
  shapes = Shapes, content)`. Dynamic colour off by default.
- `ui/theme/Type.kt` — `AppFontFamily` = Plus Jakarta Sans Regular…ExtraBold (Black maps to ExtraBold).
- `ui/theme/KineticPhysics.kt` — `Modifier.acceleratorClick` (press scale 0.92 with a 120 ms
  `tween`, plus haptic), used by the Stopwatch START/STOP card. `ui/theme/PressInteraction.kt` —
  `Modifier.pressInteraction(shape, baseElevation, onClick)`, used by Dashboard cards.
- Loading copies: private `LoadingState` in `leaderboard/LeaderboardScreen.kt:116`,
  `quicktest/QuickTestScreen.kt:1088`, `recommendations/RecommendationsScreen.kt:116`,
  `testing/CreateEventScreen.kt:252`, `testlibrary/TestLibraryScreen.kt:176`; public
  `roster/RosterComponents.kt:695` and `testing/TestingGridComponents.kt:663`; `LoadingBox` in
  `testing/stopwatch/StopwatchScreen.kt:412`; `LoadingIndicator(message)` in
  `auth/restore/RestoreBackupScreen.kt:198`. 11 `CircularProgressIndicator` sites in total
  (`grep -c` per file).
- Linear progress: `dashboard/components/GettingStartedCard.kt:142`, `report/ReportScreen.kt:341`,
  `testing/stopwatch/StopwatchScreen.kt:331`, `testing/TestingGridComponents.kt:647`.
- Hero results: `QuickTestScreen.kt` ~1005–1035 "Overall Standing" card (`zoneFor()` →
  `performanceZoneColors`, `displaySmall` + `FontWeight.Black` average percentile);
  `session/components/TestSelectorHeroCard.kt` (testedCount / totalAthletes);
  `athlete/AthleteDashboardScreen.kt` "Nth avg" chip.
- Result-entry surfaces: `TestingGridComponents.kt:513` (ScoreCell value) and
  `QuickTestScreen.kt:781` (QuickTest cell), both zone-tinted.
- Start actions today: Dashboard "Start Group Testing Event" = `SportOrange`;
  `CreateEventScreen.kt:121` "Start Group Testing" = `colorScheme.primary` gradient;
  Stopwatch "START" = `ElectricBlue`.
- **Drift noticed**
  - `SportOrange` is used outside start actions: `components/RegisterAthleteSheet.kt` (5),
    `report/ReportScreen.kt` (4), `session/SessionReportScreen.kt` (4),
    `session/components/GroupTrendChart.kt` (7), `GettingStartedCard.kt` (3). Listed only;
    re-tinting them is a separate change.
  - `Theme.kt` holds raw hex in the colour schemes (e.g. `Color(0xFFF3F4F6)`,
    `Color(0xFFDC2626)`), outside the generated tokens.
  - **No schema change; DB stays at v17.**

## 3. Files
| Action | Path | Purpose |
|---|---|---|
| MODIFY | `ui/theme/Theme.kt` | `MaterialTheme` → `MaterialExpressiveTheme` (same colour/type/shapes) |
| NEW | `ui/components/FieldLoading.kt` | `FieldLoadingState(message)`, `FieldProgressBar(progress)` |
| MODIFY | the 9 files with a local loading composable (above) | Delete the local copy, call `FieldLoadingState` |
| MODIFY | the 4 linear progress sites (above) | Use `FieldProgressBar` |
| NEW | `ui/components/HeroNumber.kt` | `HeroNumber(value: Int, suffix, style, color)`: count-up and entrance |
| MODIFY | `QuickTestScreen.kt`, `TestSelectorHeroCard.kt`, `AthleteDashboardScreen.kt` | Hero numbers use `HeroNumber` |
| NEW | `ui/theme/ResultEntryMotion.kt` | `Modifier.resultEntrance(key: Any?)`: a one-shot spring |
| MODIFY | `TestingGridComponents.kt`, `QuickTestScreen.kt` | Apply `resultEntrance(savedResult?.id)` to the value text only |
| MODIFY | `ui/theme/PressInteraction.kt`, `ui/theme/KineticPhysics.kt` | Press animation reads `MaterialTheme.motionScheme.fastSpatialSpec()` instead of fixed `tween`s |
| MODIFY | `CreateEventScreen.kt`, `StopwatchScreen.kt` | Start buttons → `SportOrange` (decision 1) |

## 4. Contracts
```kotlin
// ui/components/FieldLoading.kt
@Composable fun FieldLoadingState(message: String? = null, modifier: Modifier = Modifier)
/** Animates to [progress] (0f..1f) with MaterialTheme.motionScheme.defaultSpatialSpec(). */
@Composable fun FieldProgressBar(progress: Float, modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant)

// ui/components/HeroNumber.kt
/** Counts from the previous value to [value] with slowSpatialSpec; plays a scale/fade entrance on first show. */
@Composable fun HeroNumber(value: Int, modifier: Modifier = Modifier, suffix: String = "",
    style: TextStyle, color: Color, fontWeight: FontWeight? = null)

// ui/theme/ResultEntryMotion.kt
/** Plays a single 0.85→1 spring scale when [key] changes from one non-null value to another,
 *  or from null to non-null. Never on first composition, so scrolling a grid back into view
 *  doesn't replay it. */
fun Modifier.resultEntrance(key: Any?): Modifier
```

## 5. Implementation
1. **Theme.** In `FieldTheme`, replace `MaterialTheme(...)` with `MaterialExpressiveTheme(
   colorScheme = colorScheme, typography = Typography, shapes = Shapes, content = content)`.
   `motionScheme` is left at its default, which is the expressive scheme. Nothing else in the
   theme changes.
2. **Loading.** `FieldLoadingState` = centred `CircularProgressIndicator` + optional
   `bodyMedium` `onSurfaceVariant` label. It is the single place a later move to option B would
   swap in `LoadingIndicator`. Each local copy is deleted and its call sites keep their messages.
   Inline spinners inside buttons (e.g. "Creating…", "Exporting…") stay as they are, because
   they're button content, not loading states.
3. **Progress.** `FieldProgressBar` wraps `LinearProgressIndicator(progress = { animated })`,
   where `animated` comes from `animateFloatAsState(progress, MaterialTheme.motionScheme.defaultSpatialSpec())`.
   Each of the 4 sites keeps its existing colours.
4. **Hero numbers.** `HeroNumber` uses `Animatable(value)` and `animateTo(value, slowSpatialSpec())`,
   rendering `roundToInt()` with `fontFeatureSettings = "tnum"` so the digits don't jitter.
   Callers keep their text style and their zone colour from `performanceZoneColors(...)`. Nothing
   recomputes a threshold.
5. **Result entry.** `resultEntrance` holds the previous key in `remember` and runs a one-shot
   `Animatable(1f)` 0.85→1 using `fastSpatialSpec()`, applied with `graphicsLayer`. It goes only
   on the score `Text`, not on the cell, so the grid's layout never moves. There is no infinite
   animation and nothing runs per frame for idle cells.
6. **Start actions.** `PressInteraction` and `acceleratorClick` swap their `tween(120)` for
   `MaterialTheme.motionScheme.fastSpatialSpec()`. `pressInteraction` also animates the card's
   corner radius down by 6 dp while pressed: the Expressive "shape responds to touch" cue, done
   in stable code. `acceleratorClick` is a `composed` modifier, so it can read the theme.
   Then decision 1.

**Decisions made on the coach's behalf**
1. **The three "begin testing" commits become `SportOrange`:** Dashboard Start (already orange),
   CreateEvent "Start Group Testing", Stopwatch "START". STOP stays `colorScheme.error`. Today
   two of the three primary start actions are blue, which makes "orange means start" untrue.
   CreateEvent's disabled state keeps its current disabled styling.
2. **No Expressive in the testing grid beyond the one-cell entrance.** Its layout, density and
   labels are untouched.

## 6. Constraints for this change
- The grid renders athletes × tests. `resultEntrance` must not animate on first composition or
  on recomposition with the same key. Otherwise scrolling replays animations across hundreds of
  cells.
- Zone colours come only from `performanceZoneColors(...)`. Hero and entry motion animate scale,
  alpha and number, never the zone colour through an intermediate hue (no colour tween between
  red and green).
- `Color.kt` is generated, so it isn't touched. New composables take colours as parameters or
  from `MaterialTheme.colorScheme`.
- Every existing text label stays. Expressive here adds emphasis; it never replaces a label
  with an icon.

## 7. Verification
- **Build:** `.\gradlew.bat :app:testDebugUnitTest :app:installDebug`, then `gradlew lint`, one
  at a time.
- **Unit:** none needed. No domain logic changes.
- **Screenshots, light and dark, compared with the step-1 set in `Claude outputs/bom-upgrade/`:**
  Home, Roster, Tests, Reports and the athlete profile should be pixel-identical at rest, except
  the start buttons' new colour. Any other at-rest difference is a regression, because at rest
  this change should alter only the start buttons.
- **Manual on emulator:**
  - Save a score in the testing grid: only that cell's number springs, and scrolling away and
    back replays nothing.
  - Finish a QuickTest: the Overall Standing percentile counts up.
  - Open Reports cold: one consistent loading state.
  - Press Start on Dashboard, CreateEvent and Stopwatch: orange, with the shape spring and
    haptic intact.
  - With Developer options → Animator duration scale = off, check that the motion is
    suppressed and nothing is left mid-state (e.g. a number stuck partway through counting up).
