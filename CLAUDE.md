# CLAUDE.md
This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

---

## Central Source of Truth
**CRITICAL**: The definitive architecture reference for Field is `DEVELOPMENT_CONTEXT.md`. Always read it before making structural changes. 

## App Overview
**Field** is an offline-first fitness testing and performance tracking app for coaches and fitness professionals.
**Primary user:** A single coach managing multiple athlete groups.
**Platform:** Android (Kotlin, Jetpack Compose, Material 3).
**Data:** Fully offline. Room database (Current Version: 13).

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
- **Version:** Room database is currently at **Version 12**.
- **Seeding:** Data is seeded from CSVs in `assets/` on first launch.
- **Seed Flag:** Guarded by a versioned SharedPreferences key (`KEY_DATA_SEEDED` in `SeedDataManager`, currently `data_seeded_csv_v14`).
- **Reseeding is safe:** bumping the seed key re-imports the catalog by upserting `test_categories`/`fitness_tests` and wholesale-replacing `norm_references` and the recommendation tables. It must NEVER delete user-generated data (`testing_events`, `test_results`, `event_test_cross_ref`, athletes, groups).

---

## Presentation / UI Component Rules
- Use adaptive layouts (`ListDetailPaneScaffold`) for dynamic screen sizing (Tablets vs Mobile).
- Performance color zones are strictly defined:
  - Green (≥ 60th percentile)
  - Yellow (30-59th percentile)
  - Red (< 30th percentile)
- Lists inside Compose MUST use `key = { it.id }` to avoid `O(N)` recomposition lag.
- Complex state derivations (like O(N) list find operations) must be hoisted to the ViewModel/UseCase and never computed in the Composable render phase.