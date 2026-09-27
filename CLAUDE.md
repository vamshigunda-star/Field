# CLAUDE.md
This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

---

## Central Source of Truth
**CRITICAL**: The definitive architecture reference for Field is `DEVELOPMENT_CONTEXT.md`. Always read it before making structural changes. 

## App Overview
**Field** is an offline-first fitness testing and performance tracking app for coaches and fitness professionals.
**Primary user:** A single coach managing multiple athlete groups.
**Platform:** Android (Kotlin, Jetpack Compose, Material 3).
**Data:** Fully offline. Room database (Current Version: 16).

---

## Build & Test Commands

```bash
./gradlew assembleDebug          # Build debug APK
./gradlew test                   # Unit tests
./gradlew connectedAndroidTest   # Instrumented tests
./gradlew lint                   # Run lint checks
```
On Windows, use `gradlew` (without `./`).

---

## Architecture Strictness (Clean Architecture)
```
Presentation (ui/) → Domain (domain/) ← Data (data/)
```
- **`domain/`** — Zero Android or Room imports. Pure Kotlin.
- **`data/`** — Room entities, DAOs, repository implementations.
- **`ui/`** — Jetpack Compose, ViewModels (Hilt injected).
  - *Strict Rule*: ViewModels NEVER import from `data/`. They depend solely on use cases from `domain/`.
  - *Strict Rule*: ViewModels do not navigate. They expose a single `StateFlow<UiState>` and accept intents via `onAction`.

---

## Database & Seeding
- **Version:** Room database is currently at **Version 16**.
- **Seeding:** The DB ships prepackaged (`createFromAsset("database/alearning.db")`, built by `tools/build_prepackaged_db.py` from the CSVs in `assets/`). `SeedDataManager` then tops up from those CSVs unless the prepackaged catalog is already present.
- **Seed Flag:** Guarded by a versioned SharedPreferences key (`KEY_SEEDED_VERSION` in `SeedDataManager`, currently `data_seeded_version_v30`).
- **Reseeding is safe:** bumping the seed key re-imports the catalog by upserting `test_categories`/`fitness_tests` and wholesale-replacing `norm_references` and the recommendation tables. It must NEVER delete user-generated data (`testing_events`, `test_results`, `event_test_cross_ref`, athletes, groups).

---

## Presentation / UI Component Rules
- Use adaptive layouts (`ListDetailPaneScaffold`) for dynamic screen sizing (Tablets vs Mobile).
- Performance color zones are defined in **one** place: `domain/model/reports/PerformanceThresholds.kt`.
  - Green / Superior (≥ 80th percentile)
  - Yellow / Healthy-Average (40–79th percentile)
  - Red / Needs Improvement (< 40th percentile)
  - Grey — no norm matched. The absence of data, not a fourth category.
  - *Never re-type these numbers in a Composable.* Call `performanceZoneColors(percentile)` from
    `ui/theme/PerformanceZoneColors.kt`. Composables can't inject `ClassifyPercentileUseCase`,
    and every screen that worked around that grew its own thresholds — the grid sat at 60/30,
    the radar at 75/40 and the trend bands at 70/35 while the reports used 80/40, so one athlete
    could read Yellow on one screen and Red on the next.

---

## Age Brackets
Defined once in `domain/model/people/AgeBracket.kt` and shared by norm lookup and the roster filter:

| Bracket | Ages |
|---|---|
| Childhood | 5–12 |
| Adolescence | 13–19 |
| Adults | 20–40 |
| Adults | 41–62 |
| Older Adults | 63–115 |

Adulthood stays split at 40/41 because the published normative tables grade adults by age —
collapsing it would judge a 60-year-old against 20-year-old cutoffs.

Brackets follow established developmental and fitness-assessment frameworks (LTAD) — the same raw
score means different things at different stages, which is why norms are bracketed at all.

---

## Norm Data Contract (`assets/norms.csv`)
The published standards use five classifications (Superior / Above Average / Average / Below
Average / Poor). Field consolidates them into three and stores a representative percentile per band.
**A row's `percentile` must classify to its own `classification`** under `PerformanceThresholds`:

| Classification | Encoded percentile |
|---|---|
| Needs Improvement | 20 (any value < 40) |
| Healthy Fitness Zone | 60 (any value 40–79) |
| Superior | 90 (any value ≥ 80) |

This is not cosmetic. "Needs Improvement" was previously encoded as **40**, which sits exactly on
the Healthy boundary, so every below-standard result in the app rendered as Yellow regardless of
what the UI did. `SeedNormsConsistencyTest` asserts the contract.

Expected coverage per test: 2 sexes × 5 age brackets × 3 classifications = **30 rows**.

**Known gap:** 11 adult-only tests (push-up, curl-up, BMI, waist circumference, waist-to-hip,
Cooper 12-min, Rockport walk, trunk flexion, flexed-arm hang, BESS, back-scratch) still carry
the source standards' decade bands (20–29, 30–39, …) rather than Field's brackets, and have no
childhood or adolescence rows at all. Those tests return no norm for an athlete under 20.
Rebracketing them means discarding validated age-grading, so it is a data decision, not a
code change — `SeedNormsConsistencyTest` prints the current list rather than failing on it.
- Lists inside Compose MUST use `key = { it.id }` to avoid `O(N)` recomposition lag.
- Complex state derivations (like O(N) list find operations) must be hoisted to the ViewModel/UseCase and never computed in the Composable render phase.