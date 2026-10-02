# Spec: Visual polish pass — lessons from competitor testing apps

## 1. Objective & scope

- **As a coach**, I want every result in Field to tell me at a glance *where the athlete sits
  against the norm and whether they're improving*, in a UI whose colours never mean two things,
  so that I can read a report in the field without decoding it.
- **In scope (appearance only, no domain or schema changes):**
  1. Bundle the app typeface instead of downloading it (offline-first).
  2. Tabular figures for scores.
  3. Result row → "metric tile" (value + percentile chip + delta + sparkline), using data the
     tile model already carries.
  4. Radar stops faking untested axes.
  5. One zone-colour mapping (kill the blue "Healthy" band and the two duplicate maps).
  6. Brand orange stops doubling as a warning colour (flags, selected nav tab).
  7. Distinct category icons (fix the shared-icon collisions).
  8. Roster age-bracket chips stop truncating.
- **Out of scope (follow-ups, see §8):** Home quick-actions rework, back arrow on top-level tabs,
  Material 3 Expressive components (needs a Compose BOM upgrade), tap-to-capture testing grid,
  roster completion status. Each is a separate, reviewable change.

## 2. Verified context

**How this was gathered.** Field was run on the `Medium_Phone` emulator in dark and light mode and
Home, Roster, Tests Library and Reports (athlete profile) were screenshotted. A research pass
covered VALD Hub/ForceDecks, FitnessGram, team beep-test apps, BridgeAthletic, TrainHeroic, Strava,
WHOOP, Oura and Google's M3 Expressive research. VALD's norm tiles were confirmed visually in the
browser (valdperformance.com/news/introducing-vald-norms): each tile is *metric name → large value
+ unit → green "↑ 0.4%" delta → "54th pct." chip → sparkline over a shaded norm band*. The rest is
from vendor docs and design write-ups (text only).

**Patterns that recur across polished apps, and where Field stands:**

| Pattern (who does it) | Field today |
|---|---|
| Norm as context *around* the value: percentile + band (VALD) | Result row shows value + "SUPERIOR" only; the percentile, delta and sparkline are computed and then not drawn |
| Colour carries meaning and nothing else (WHOOP, VALD) | Orange is brand CTA, selected nav, flags *and* reads like Healthy-yellow text in light mode; Healthy is blue on one chart |
| Numerals get their own treatment (WHOOP: DIN for numbers) | Proportional figures; typeface likely falling back to Roboto |
| Self-vs-history alongside self-vs-norm (Strava, VALD) | Delta exists in the model, not shown |
| Expressive only where it aids clarity; keep labelled lists (Google M3 research) | Radar spends 7 of 11 axes on "—" |

**Files read and what they confirmed:**
- `ui/theme/Type.kt` — Plus Jakarta Sans loaded only via `GoogleFont.Provider`; no `res/font/`
  directory exists. Nothing preloads it.
- `ui/theme/Color.kt` — header says *AUTO-GENERATED from design.md*. This spec adds **no** colour
  tokens, so Color.kt is not edited.
- `ui/theme/PerformanceZoneColors.kt` — the canonical `performanceZoneColors(percentile, alpha)`.
- `ui/report/components/ZoneChip.kt:37` `zoneColors(...)` and
  `ui/report/components/PercentileChip.kt:52` `percentileChipColors(...)` — two more copies of the
  same token mapping.
- `ui/report/components/NormBandLineChart.kt:73` — default `healthyColor` is **blue**
  (`0xFF60A5FA` / `0xFF0D47A1`) while every other surface paints Healthy yellow.
- `ui/components/charts/RadarChart.kt:~455` — `val rawVal = if (score.testCount > 0)
  score.normalizedScore else 0.04f`: untested axes are plotted at 4%, so a 4-of-11 profile draws as a
  spike collapsing into the centre (seen in the Sarah Connor screenshot).
- `ui/theme/CategoryVisualConfig.kt:34` — the `radarAxis` branch wins over the name branch.
  Cardiovascular *and* Muscular Endurance both map to `RadarAxis.ENDURANCE → Icons.Default.Repeat`;
  Balance and Coordination both to `BALANCE → AccessibilityNew`. The name branch already has the
  right answers (`cardio → Favorite`) but is never reached for those.
- `ui/roster/RosterComponents.kt:91–98` — five `AppFilterChip`s each `Modifier.weight(1f)` in a
  fixed `Row`; labels from `AgeBracket.label` are `"Adults 20-40"` / `"Adults 41-62"` but render as
  "Adults" / "Adults" and "Adolescen".
- `ui/athlete/AthleteDashboardScreen.kt:481` `TestBreakdownItemRow` — score always
  `colorScheme.primary`; chip label prefers `latestResult.classification` (raw `"SUPERIOR"` string)
  over `zoneLabel`. `:572` `FlagListRow` uses `secondaryContainer` (brand orange) for every flag.
- `domain/model/reports/ReportsModels.kt:82` `AthleteTestTile` — verbatim:
  ```kotlin
  data class AthleteTestTile(
      val test: FitnessTest,
      val latestResult: TestResult?,
      val classification: Classification,
      val sparkline: List<Float>,        // raw scores oldest -> newest, normalized externally
      val rawSparkline: List<Double>,
      val deltaPercentile: Int? = null   // latest minus previous attempt; null if < 2 attempts or percentile missing
  )
  ```
  Everything the metric tile needs is already here. **No domain change.**
- Reusable components that already exist: `ui/report/components/MiniSparkline.kt`
  (`points: List<Float>`, default size 80×24dp, hardcoded default colour `0xFF0D47A1`),
  `PercentileChip(percentile: Int?)` (renders ordinal, e.g. "54th").
- `gradle/libs.versions.toml` — `composeBom = "2024.11.00"` (Material3 1.3.x). M3 Expressive APIs
  need material3 1.4+, hence out of scope.

**Schema:** no migration, database stays at **17**. No seed or catalog change.

**Drift noticed:**
- Zone colour mapping exists in three places (above) despite CLAUDE.md's "one place" rule; values
  agree today except `NormBandLineChart`'s blue Healthy.
- `RadarChart.kt` and `NormBandLineChart.kt` hold raw hex colours (grid, rings, bands) rather than
  tokens.
- The typeface claim is an inference from glyph shapes in the screenshots (they read as Roboto);
  step 5.1 verifies it before anything changes. The offline argument for bundling holds either way:
  a coach whose first launch is offline gets the fallback.

## 3. Files

| Action | Path | Purpose |
|---|---|---|
| NEW | `app/src/main/res/font/plus_jakarta_sans_{regular,medium,semibold,bold,extrabold}.ttf` | Bundled typeface (SIL OFL 1.1) |
| NEW | `app/src/main/assets/licenses/OFL-PlusJakartaSans.txt` | Licence text shipped with the font |
| MODIFY | `ui/theme/Type.kt` | Resource fonts; `tnum` on score-bearing styles |
| MODIFY | `ui/athlete/AthleteDashboardScreen.kt` | `TestBreakdownItemRow` → metric tile; `FlagListRow` severity colours |
| MODIFY | `ui/report/components/MiniSparkline.kt` | Default colour from theme instead of hex |
| MODIFY | `ui/report/components/ZoneChip.kt` | `zoneColors` delegates to the canonical mapping; label "Needs Improvement" |
| MODIFY | `ui/report/components/PercentileChip.kt` | Delete `percentileChipColors`, use the canonical mapping |
| MODIFY | `ui/report/components/NormBandLineChart.kt` | Healthy band defaults to the yellow zone token |
| MODIFY | `ui/theme/PerformanceZoneColors.kt` | Add a `Classification` overload (single source for the above) |
| MODIFY | `ui/components/charts/RadarChart.kt` | Plot tested axes only; list untested ones |
| MODIFY | `ui/theme/CategoryVisualConfig.kt` | Name match first, axis as fallback; unique icon per category |
| MODIFY | `ui/roster/RosterComponents.kt` | Age chips in a horizontally scrolling row |
| MODIFY | bottom-nav composable (locate with `grep -rn "NavigationBarItem\|selectedIndicator" app/src/main/java/com/vamshi/field/ui`) | Selected indicator → `primaryContainer` |
| NEW | `app/src/test/java/com/vamshi/field/ui/theme/CategoryVisualConfigTest.kt` | Icon uniqueness |
| NEW | `app/src/test/java/com/vamshi/field/ui/components/charts/RadarAxisPartitionTest.kt` | Tested/untested split |

## 4. Contracts

```kotlin
// ui/theme/PerformanceZoneColors.kt — new overload; the Int? version delegates to it
@Composable
fun performanceZoneColors(classification: Classification, alpha: Float = 1f): PerformanceZoneColors

// ui/report/components/ZoneChip.kt — keeps its public shape so callers don't move
@Composable
fun zoneColors(c: Classification): ZoneColors   // body: performanceZoneColors(c).let { ZoneColors(it.background, it.text) }
// The isDark parameter goes away (the canonical function reads the theme itself). Fix call sites.

// ui/components/charts/RadarChart.kt — pure, internal, unit-testable
internal data class RadarAxisPartition<T>(val tested: List<T>, val untested: List<T>)
internal fun <T> partitionAxes(scores: List<T>, testCount: (T) -> Int): RadarAxisPartition<T>
```

`AthleteTestTile`, `TestResult`, `Classification`, `PerformanceThresholds`: **unchanged.**

## 5. Implementation, layer by layer

No data, domain or DI changes. Everything is in `ui/` and `res/`.

### 5.1 Typeface (do first, verify first)
1. **Verify the fallback.** In `MainActivity`, or a throwaway `@Preview` on device, render "Ag 97.1"
   once with `AppFontFamily` and once with `FontFamily.SansSerif` and compare. Record the result in
   the PR description. Proceed either way, because of the offline argument.
2. Add the five static TTFs (weights 400/500/600/700/800 — the ones `Type.kt` declares, minus Black,
   which no style uses. Confirm with `grep -rn "FontWeight.Black" app/src/main`; keep it if used).
   Source: the official Plus Jakarta Sans release (github.com/tokotype/PlusJakartaSans). **Downloading
   the files needs the user's go-ahead at execution time.**
3. `Type.kt`: replace the `GoogleFont` entries with
   `Font(R.font.plus_jakarta_sans_bold, FontWeight.Bold)` etc. Delete `provider`, `fontName` and the
   `ui-text-google-fonts` dependency if nothing else uses it (`grep -rn googlefonts`), plus
   `res/values/font_certs.xml` if it becomes unused.
4. Tabular figures: add `fontFeatureSettings = "tnum"` to `displayLarge…displaySmall`,
   `headlineLarge…headlineSmall` and `titleLarge`. Those are the styles scores render in. Body and label
   styles stay proportional for prose.

### 5.2 One zone-colour mapping
1. Add the `Classification` overload to `PerformanceZoneColors.kt` and move the `when` into it.
   The `Int?` overload becomes `performanceZoneColors(PerformanceThresholds.classify(percentile), alpha)`.
2. `ZoneChip.kt`: `zoneColors` delegates. `zoneLabel(NEEDS_IMPROVEMENT)` → `"Needs Improvement"`.
   Where space is tight (the athlete picker subtitle "Needs Imp. • 1 Test"), let the text ellipsize
   rather than abbreviating at the source. Check every `zoneLabel` call site.
3. `PercentileChip.kt`: delete `percentileChipColors`; use `performanceZoneColors(percentile)`.
4. `NormBandLineChart.kt`: default `healthyColor` → `performanceZoneColors(Classification.HEALTHY).text`.
   Superior and Needs defaults likewise. `lineColor` default stays `SportOrange` for now; see 5.4.

### 5.3 Metric tile (`TestBreakdownItemRow`)
Target layout. It keeps the row's existing height budget and stays a labelled list row, per the
M3 research caution:

```
┌───────────────────────────────────────────────┐
│ 1RM Squat                         97.1 kg   › │
│ [90th] Superior   ↑ 12 pts   ╱╲_╱‾  (spark)   │
└───────────────────────────────────────────────┘
```
- Value colour: `performanceZoneColors(tile.classification).text` instead of `colorScheme.primary`.
  It keeps `titleLarge` (now tabular).
- Line 2: `PercentileChip(tile.latestResult?.percentile)`, then the zone label from
  `zoneLabel(tile.classification)`. **Stop preferring `latestResult.classification`**, which leaks the
  raw `"SUPERIOR"` string.
- Delta: render only when `tile.deltaPercentile != null && != 0`. Use `↑`/`↓` plus "N pts" in
  `labelMedium`. Colour comes from the green zone token for an improvement and the red one for a
  decline. `deltaPercentile` is already in percentile space, so "higher is better" is already resolved
  and no `isHigherBetter` logic belongs here.
- Sparkline: `MiniSparkline(points = tile.sparkline)` only when `tile.sparkline.size >= 2`.
  `MiniSparkline`'s default `color` becomes `MaterialTheme.colorScheme.onSurfaceVariant`. Neutral
  is deliberate: the line shows shape, and the zone is already carried by the value colour.
- Content description for TalkBack: "1RM Squat, 97.1 kilograms, 90th percentile, Superior, up 12
  points".

### 5.4 Orange stops meaning "warning"
- Rule: **orange = "start something" (primary CTA) only.** Zone and warning colours come from the
  performance tokens.
- `FlagListRow`: container and text from `performanceZoneColors(...)`. `BELOW_HEALTHY` maps to the
  NEEDS_IMPROVEMENT pair, and `MISSING_DATA` to the NO_DATA pair (grey is "absence", per CLAUDE.md).
  List the other `FlagType` entries and map each one explicitly, with no `else`. Title from a
  `when (flag.type)` copy table rather than `name.replace('_',' ')`.
- Bottom nav selected indicator and icon: `primaryContainer` / `onPrimaryContainer`.
- `NormBandLineChart.lineColor` default: `colorScheme.primary`.

### 5.5 Radar shows only what was measured
1. Add `partitionAxes`. Plot the polygon over `tested` axes only, using the existing geometry with
   `n = tested.size`. Delete the `0.04f` placeholder.
2. If `tested.size < 3`, don't draw the radar (a polygon needs 3 points). Render the tested axes as a
   short labelled bar list using the zone tokens.
3. Below the chart, render untested axes as a single wrapped row of outlined chips: "Not yet tested:
   Speed · Power · Flexibility …". Tapping a chip calls the existing `onCategoryClick(categoryId)`.
4. Card subtitle: "11-dimension athletic percentile profile" → "{tested} of {total} areas tested".
   Find the string with `grep -rn "dimension athletic" app/src/main`.
5. Leave the grid/ring raw hex colours alone in this change. They're noted as drift and are not
   user-visible bugs.

### 5.6 Category icons
`getCategoryVisual`: evaluate the **name** branch first and fall back to `radarAxis` only when the
name branch hits `else`. Then make the name branch unique:

| Category | Icon |
|---|---|
| Cardiovascular Endurance | `Icons.Default.Favorite` (existing) |
| Muscular Strength | `Icons.Default.FitnessCenter` (existing) |
| Muscular Endurance | `Icons.Default.Repeat` (existing) |
| Flexibility | `Icons.Default.SelfImprovement` (existing) |
| Speed | `Icons.AutoMirrored.Filled.DirectionsRun` (existing) |
| Agility | `Icons.AutoMirrored.Filled.AltRoute` (existing) |
| Power | `Icons.Default.ElectricBolt` (existing) |
| Balance | `Icons.Default.AccessibilityNew` (existing) |
| Coordination | `Icons.Default.SportsHandball` — **split out of the balance keyword** |
| Body composition / reaction time | check the remaining category names in `assets/` CSV and give each a distinct icon (`Icons.Default.MonitorWeight`, `Icons.Default.TouchApp`), all from `material-icons-extended`, which is already a dependency |

Colour stays single-accent (`ElectricBlue` on `BlueIconBg`). A rainbow of category colours would
break the "colour = meaning" rule this spec is enforcing.

### 5.7 Roster age chips
Replace the `Row` with `Row(Modifier.horizontalScroll(rememberScrollState()))`, drop
`Modifier.weight(1f)`, and let each chip size to its full `range.label`. Keep `spacedBy(6.dp)` and
add 16dp start/end content padding so the first and last chips line up with the cards.

## 6. Constraints for this change

- **No new colour tokens and no Color.kt edits.** It is generated from `design.md`. Everything here
  reuses existing tokens.
- **Zone thresholds are never re-typed.** Every colour decision goes through `performanceZoneColors`
  or `PerformanceThresholds.classify`. The delta arrow's colour is chosen from the sign of
  `deltaPercentile`, not from a percentile.
- **Medical-alert chips are untouched.** The "⚠ Asthma" chip and the roster warning icon keep their
  current prominence; the flag recolouring must not restyle them.
- **No work in composition.** `partitionAxes` runs inside `remember(scores)`, and sparkline/delta
  come pre-computed from the tile.
- Lists keep their `key = { … }`; the new untested-axis chip row is a plain `FlowRow`, not lazy.

## 7. Verification

- **Unit (JVM):**
  - `CategoryVisualConfigTest`: for each real category name in the seed CSV, called both with and
    without its `RadarAxis`, all returned icons are distinct. **Fails today** on Cardio/Muscular
    Endurance and on Balance/Coordination.
  - `RadarAxisPartitionTest`: 4 tested of 11 returns 4/7 with order preserved; 0 tested returns all
    untested; all tested returns an empty untested list.
- **Build:** `gradlew assembleDebug`, then `gradlew test`, then `gradlew lint` (watch for an unused
  google-fonts resource).
- **Manual, on `Medium_Phone`, light and dark (`adb shell cmd uimode night yes|no`):**
  1. Airplane mode on, clear app data, launch: headings render in Plus Jakarta Sans (double-storey
     "a", geometric "G").
  2. Reports → Athlete Profile → Sarah Connor: the radar shows 4 axes as a proper polygon; the chips
     list the 7 untested areas, and tapping one opens its category; the subtitle reads "4 of 11 areas
     tested".
  3. The same screen's Individual Test Breakdown: the 1RM Squat row shows a percentile chip, "Superior"
     in title case and a green value; an athlete with 2+ attempts shows a delta and a sparkline; an
     athlete with 1 attempt shows neither.
  4. The Flags card is red-zone tinted, not orange.
  5. Athlete test detail → norm band chart: the Healthy band is yellow.
  6. Roster: chips read "Childhood 5-12 … Older Adults 63+" in full and scroll horizontally.
  7. Tests Library: no two categories share an icon.
  8. Bottom nav: the selected tab is blue-tinted, and the orange appears only on Start buttons.

**Why this wasn't caught:** there's no screenshot or Compose UI test on these screens, and
`SeedNormsConsistencyTest` guards zone *data*, not zone *rendering*. A follow-up could add Paparazzi/
Roborazzi snapshots for the athlete profile.

## 8. Follow-ups found during the review (not in this spec)

| Finding | Evidence | Why separate |
|---|---|---|
| Home "Quick Actions" duplicates 3 of 4 bottom-nav tabs; the hero card shows counts but not "what's next" (WHOOP/Oura lead with one actionable thing) | Home screenshot | Needs new `DashboardUiState` data (last event, athletes untested) — a ViewModel change |
| Roster/Tests/Reports show a back arrow while reached as top-level tabs | Screenshots; `onNavigateBack` in each screen | Navigation behaviour, not appearance |
| Roster permanent checkboxes + "← Swipe left to delete" hint text | Roster screenshot | Selection-mode UX |
| Tap-a-tile group capture (beep-test apps), roster "not yet tested" status (FitnessGram's Missing Data filter) | Research | UX/flow work — `ux-implementor` territory |
| Leaderboard straight after a testing event (Bleep Test, Output) | Research | New surface |
| M3 Expressive (button groups, wavy progress, spring motion, emphasized type) | Google research: up to 4× faster element finding, but also "don't break lists or remove labels" | Requires `composeBom` upgrade past 2024.11.00 → material3 1.4+; do as its own dependency PR |
| Raw hex in `RadarChart.kt` / `NormBandLineChart.kt` grids | Code | Cosmetic drift, no visible bug |
