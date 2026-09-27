# Spec: Unify percentile thresholds and age brackets across Field

## 0. Decisions taken (resolved 2026-09-27)

1. **"Needs Improvement" re-encoded from percentile 40 to 20.** 40 sat exactly on the HEALTHY
   boundary, so every below-standard result rendered Yellow. 20 is the band midpoint, matching
   how Healthy (60) and Superior (90) are already encoded. 836 rows changed per CSV.
2. **Adulthood stays split at 20–40 / 41–62.** The email specified a single 20–62 bracket, but
   all 420 adult band pairs carry different cutoffs and the 41–62 set is consistently more
   lenient; merging would judge a 60-year-old against 20-year-old standards. Field therefore
   uses **five** brackets and coverage is 2 × 5 × 3 = **30 rows per test**, not 24.
3. **The radar chart's five-tier labels collapsed to the three categories.** Elite / Advanced /
   Proficient / Developing / Needs Focus at 85/70/45/25 was a second vocabulary for the same
   numbers; it now reads Superior / Healthy / Needs Improvement at 80/40.

**Still open — 11 adult-only tests.** push-up, curl-up, BMI, waist circumference, waist-to-hip,
Cooper 12-min, Rockport walk, trunk flexion, flexed-arm hang, BESS and back-scratch still carry
the source standards' decade bands (20–29, 30–39, …) and have **no childhood or adolescence rows
at all**, so they return no norm for an athlete under 20. Rebracketing them means collapsing two
validated decade bands into each Field bracket, which discards real age-grading — a data
decision, not a code change. `SeedNormsConsistencyTest` prints the list rather than failing.

## 1. Objective & scope

- **As a coach**, I want one performance-zone rule and one set of age brackets, so the same
  athlete never reads Yellow on one screen and Red on another, and so a result means the
  same thing wherever it appears.
- **In scope:** a single source of truth for the 80/40 thresholds; every drifted call site
  routed through it; a single `AgeBracket` definition; all agent-instruction and reference
  docs corrected.
- **Out of scope:** changing the three-category model itself, and re-deriving normative
  score cutoffs from source literature.

## 2. Verified context

Read: `domain/usecase/reports/InterpretationUseCases.kt` (canonical classifier),
`domain/model/reports/InterpretationModels.kt` (`Classification` enum),
`domain/usecase/analytics/GetIndividualAnalyticsUseCase.kt` (`PerformanceZone`),
`ui/theme/Color.kt` (zone tokens), `ui/roster/RosterViewModel.kt` (`AthleteAgeRange`),
`domain/usecase/standards/CalculatePercentileUseCase.kt`,
`data/local/daos/standards/StandardsDao.kt` (`:age BETWEEN ageMin AND ageMax`),
`data/seed/SeedDataManager.kt`, `app/src/main/assets/norms.csv` and `norms_v2.csv`,
`tools/harmonize_norms.py`.

**No schema change.** `norm_references` already stores `ageMin`/`ageMax` as Float and
`percentile` as Int. Database stays at version **15**; no migration, no `MigrationTest`
change. Re-seeding is required to pick up CSV edits (bump the seed key), and
`tools/build_prepackaged_db.py` must be re-run if the prepackaged DB is to match.

**Drift noticed:**

- `DEVELOPMENT_CONTEXT.md` (60/30), `README.md` (60/30), `design.md` (60/30) and
  `.agents/skills/devils-advocate-reviewer/SKILL.md` (60/30) all contradict `CLAUDE.md`
  (80/40) and the code. `design.md:180` additionally names `CalculatePercentileUseCase` as
  the place thresholds live; it is not — `ClassifyPercentileUseCase` is.
- `ReportsRepositoryImpl:510` derives a "superior" raw score at percentile **70**.
- `UNSPECIFIED` is a valid `BiologicalSex` but `norms.csv` has only MALE/FEMALE rows, so
  those athletes never match a norm. Pre-existing; not in scope, but worth a decision later.

## 3. Root cause of the visible inconsistency

Two independent causes, which is why it looks random on screen.

**Cause A — six different thresholds in UI code.** One rule exists in the domain
(`ClassifyPercentileUseCase`, ≥80 / ≥40) but the UI never calls it; each screen re-derives
its own:

| Site | Thresholds | Effect vs canonical |
|---|---|---|
| `ui/testing/TestingGridComponents.kt:469` | ≥60 / ≥30 | 30–39 reads Yellow, should be Red; 60–79 Green, should be Yellow |
| `ui/quicktest/QuickTestScreen.kt:757` | ≥60 / ≥30 | same — and line 1007 in the *same file* uses ≥80 / ≥40 |
| `ui/components/charts/RadarChart.kt:761` | ≥75 / ≥40 | 75–79 Green, should be Yellow |
| `ui/groupoverview/GroupOverviewScreen.kt:299` | ≥70 / ≥35 | band shading disagrees with the rows beside it |
| `data/repository/ReportsRepositoryImpl.kt:510` | p70 as "superior" | norm-band chart marks Superior 10 points early |
| Leaderboard / AthleteDashboard / ReportScreen | ≥80 / ≥40 | correct, but duplicated inline |

**Cause B — the seed data contradicts the contract.** Every row in `norms.csv` carries one
of three percentile values:

| CSV classification | CSV percentile | `ClassifyPercentileUseCase` verdict | Rows |
|---|---|---|---|
| Needs Improvement | **40** | **HEALTHY** — wrong | 858 |
| Healthy Fitness Zone | 60 | HEALTHY — correct | 848 |
| Superior | 90 | SUPERIOR — correct | 813 |

Verified, not inferred: a "Needs Improvement" band lands exactly on the HEALTHY boundary, so
**every below-standard result in the app currently displays as Yellow/Healthy**. Fixing the
UI thresholds alone would not surface a single additional Red, because no seeded row is
below 40. Using the email's rule (Needs Improvement < 40) and band midpoints, the consistent
triple is **20 / 60 / 90**.

## 4. Age brackets

The email specifies four brackets: Childhood 5–12, Adolescence 13–19, Adulthood 20–62,
Older Adults 63–115. Three different schemes exist today:

- `norms.csv`, 68 of 81 tests: 5–12, 13–19, **20–40, 41–62**, 63–99 — five brackets.
- `norms.csv`, 13 tests: legacy decade bands (20–29, 30–39, 40–49, 50–59, 60–69, plus
  50–54, 55–59, 60–64, 60–65, 65–69).
- `ui/roster/RosterViewModel.kt`: Under 10, 10–12, 13–15, 16–18, 18+ — a roster filter,
  unrelated to norms.

The email's formula (2 sexes × 4 brackets × 3 classifications = 24 rows/test) confirms the
intent: 72 tests currently have 30 rows, which is 2 × **5** × 3. Collapsing the two adult
bands gives exactly 24.

**Why this is the open question:** all 420 adult-band pairs differ, and the 41–62 cutoffs are
consistently more lenient — e.g. `test_toe_touch`, MALE, Healthy is −5…5 cm at 20–40 but
−8…2 cm at 41–62. Merging means picking one, and a 60-year-old judged on 20–40 cutoffs will
read worse than the source standards intend. The LTAD rationale is strong for childhood and
adolescence, where the brackets are developmental; adult fitness norms are conventionally
age-graded within adulthood, which is what the current split encodes.

## 5. Implementation

**Step 1 — one source of truth (new).** `domain/model/reports/PerformanceThresholds.kt`:
a pure Kotlin object holding `SUPERIOR_MIN = 80`, `HEALTHY_MIN = 40` and
`classify(percentile: Int?): Classification`. Domain layer so every layer may read it.

**Step 2 — existing classifiers delegate.** `ClassifyPercentileUseCase` and
`GetIndividualAnalyticsUseCase`'s zone mapping call it instead of inlining the numbers. The
use case keeps its shape so no call site changes.

**Step 3 — one UI mapping (new).** `ui/theme/PerformanceZoneColors.kt`: a `@Composable`
helper returning background/text/border and label for a percentile, dark-theme aware,
built from the `Performance*` tokens in `Color.kt`. Composables cannot inject a use case,
which is the reason each screen grew its own copy; this closes that gap.

**Step 4 — route every site through it.** The six drifted sites plus the three already-correct
inline copies. `ReportsRepositoryImpl:510` takes `PerformanceThresholds.SUPERIOR_MIN`.

**Step 5 — one age bracket definition (new).** `domain/model/people/AgeBracket.kt` with the
four brackets and `of(age)`. `AthleteAgeRange` in `RosterViewModel` is replaced by it, so the
roster filter and the norms tables finally agree.

**Step 6 — docs.** `DEVELOPMENT_CONTEXT.md`, `README.md`, `design.md`,
`.agents/skills/devils-advocate-reviewer/SKILL.md` corrected to 80/40; `CLAUDE.md` gains the
age brackets and the seed-encoding rule; `design.md:180` repointed at the new object.

**Step 7 — data (done).** `norms.csv` and `norms_v2.csv`: Needs Improvement re-encoded to 20
(836 rows each); top bracket extended 63–99 → 63–115 (432 rows each); duplicate legacy decade
rows removed from `test_wall_sit` and `test_shoulder_flexibility`, which carried *both* schemes
at once — 66 rows each, which also fixes a live lookup bug (see below). `KEY_SEEDED_VERSION`
bumped to `data_seeded_version_v30`; `assets/database/alearning.db` rebuilt.

**Bug found while rebracketing.** `test_wall_sit` and `test_shoulder_flexibility` had overlapping
age ranges — a 25-year-old matched both a 20–29 row and a 20–40 row. The lookup is
`:age BETWEEN ageMin AND ageMax`, so which norm won was down to row order. Now asserted against
in `SeedNormsConsistencyTest`.

**Note on `tools/build_prepackaged_db.py`.** It hardcodes `c:\Users\APF\...` as `ASSETS_DIR`,
so it only runs on that machine and from that path. Worth making it resolve relative to the
script's own location.

## 6. Constraints

- The seed key must be bumped or no existing install picks up the CSV change.
- Re-seeding replaces `norm_references` wholesale but must not touch coach data
  (`test_results` and friends) — `SeedDataManager` already honours this; don't change it.
- `TestResult.percentile` is a **stored snapshot**. Re-seeding norms does not retroactively
  reclassify historical results; they keep the percentile written at capture time. Say so to
  the coach rather than implying old reports will change.
- Changing a percentile band does not change `isHigherBetter` handling — the clamp logic in
  `CalculatePercentileUseCase` picks bands by percentile order, so a Needs Improvement band
  at 20 instead of 40 keeps the same relative ordering.

## 7. Verification

- **Unit (new, `PerformanceThresholdsTest`):** boundary cases 39/40/79/80 and null, asserting
  Red/Yellow/Yellow/Green/NoData.
- **Unit (new, `SeedNormsConsistencyTest`):** parse `norms.csv` and assert every row's
  `classification` agrees with `PerformanceThresholds.classify(percentile)`. This is the test
  that would have caught Cause B, and it fails today.
- **Grep acceptance:** no bare `>= 60`, `>= 30`, `>= 75`, `>= 70`, `>= 35` against a
  percentile remains under `app/src/main/java`.
- **Build:** `gradlew assembleDebug` then `gradlew test`.
- **Manual:** one athlete scoring in 30–39 on a test — grid cell, QuickTest cell, radar
  vertex, leaderboard pill, session report row must all read Red.

**Why this wasn't caught:** there was no test asserting that the seeded data satisfies the
classification contract, and no test asserting the UI uses the domain classifier. Both are
cheap; both are above.
