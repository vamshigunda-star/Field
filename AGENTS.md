# AGENTS.md
Guidance for Codex and other coding agents working in this repository.

**Read `CLAUDE.md` first, then `DEVELOPMENT_CONTEXT.md`.** They are the only two places project
facts live (DB version, seed keys, thresholds, data rules). This file used to carry its own copy
and drifted — it said Room v15 and seed key v29 long after both had moved on — so it now only
points there. Despite its name, `CLAUDE.md` applies to every agent.

## The rules most often broken
- Clean Architecture: `ui/ → domain/ ← data/`. `domain/` has no Android or Room imports.
  ViewModels expose one `StateFlow<UiState>`, take a sealed `onAction`, and never navigate.
- Zone colours come only from `performanceZoneColors(...)`; thresholds only from
  `domain/model/reports/PerformanceThresholds.kt` (≥80 / 40–79 / <40 / grey = no norm).
- Scores: `formatScore` / `parseScore` (`domain/model/standards/ScoreFormat.kt`), never `%.1f`,
  `toString()` or `toDouble()`. Beep/Yo-Yo store `level + shuttle/100`.
- Age: `Individual.currentAge` (whole years). Birth dates before 1970 are negative millis.
- Any change to `assets/*.csv` bumps `CATALOG_VERSION` in **both** `SeedDataManager` and
  `tools/build_prepackaged_db.py`, plus `KEY_SEEDED_VERSION`.
- Seeding never deletes user data or `source = 'USER'` catalog rows.

## Build & test
```bash
./gradlew assembleDebug          # Windows: gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew connectedAndroidTest
./gradlew lint
```
Release builds are minified; verify Drive backup/restore on a release APK.
