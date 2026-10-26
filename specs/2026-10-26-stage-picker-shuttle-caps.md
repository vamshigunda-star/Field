# Spec: Beep / Yo-Yo picker accepts shuttles that don't exist (up to 99)

## 1. Objective & scope

- **Symptom:** In the Beep and Yo-Yo entry pickers, SHUTTLE counts up to 99 and LEVEL has no upper limit.
- **Fix:** cap each test at the highest level and highest shuttle on its score sheet:

  | Test | Max level | Max shuttle | Matches `tests.csv` `validMax` |
  |---|---|---|---|
  | `test_beep` | 21 | 16 | 21.16 |
  | `test_yo_yo` | 24 | 8 | 24.08 |

- **Out of scope:**
  - **Per-level shuttle counts.** Beep runs 7 to 16 shuttles per level; this fix uses a single cap. That's safe for grading:
    - Every Beep norm cut point falls on a level boundary (`x.99` / `y.00`), so a wrong shuttle inside a level never changes the band.
    - Yo-Yo has 8 shuttles on every level, so the cap admits only real stages.
    - The cost: a Beep typo such as 8.14 is stored and shown as typed, and ranks just above a real 8.11.
  - **Custom "Levels" tests.** They keep `StageScore.MAX_SHUTTLE` (99) and no level cap.
  - **Results already saved.** They stay unchanged.

## 2. Verified context

- `ui/components/testing/TestInputModules.kt` (`StageInputModule`):
  - LEVEL increments without bound.
  - SHUTTLE stops only at `StageScore.MAX_SHUTTLE`.
  - `TestInputSwitcher` receives no test identity.
- **Callers:**
  - `ScoreEntryDialog` (`ui/testing/TestingGridComponents.kt`), called from `TestingGridScreen.kt` with `cell.test`.
  - `QuickScoreEntryDialog` (`ui/quicktest/QuickTestScreen.kt`), called with `test`.
- **No schema change.** The database stays at 17. There are no catalog/CSV changes, so there's no `CATALOG_VERSION` bump.
- **Why the per-test cap matters:** a shared cap of 24 would let Yo-Yo record 15.20, which encodes above 16.08 and could lift an athlete into a higher band.

## 3. Files

| Action | Path | Purpose |
|---|---|---|
| MODIFY | `app/src/main/java/com/vamshi/field/domain/model/standards/ScoreFormat.kt` | Add `StageLimits` (max level/shuttle per catalog test) |
| MODIFY | `app/src/main/java/com/vamshi/field/ui/components/testing/TestInputModules.kt` | Picker caps LEVEL and SHUTTLE at the limits |
| MODIFY | `app/src/main/java/com/vamshi/field/ui/testing/TestingGridComponents.kt` | `ScoreEntryDialog` forwards `stageLimits` |
| MODIFY | `app/src/main/java/com/vamshi/field/ui/testing/TestingGridScreen.kt` | Pass `StageLimits.forTest(cell.test.id)` |
| MODIFY | `app/src/main/java/com/vamshi/field/ui/quicktest/QuickTestScreen.kt` | Same for `QuickScoreEntryDialog` |
| MODIFY | `app/src/test/java/com/vamshi/field/domain/model/standards/StageScoreTest.kt` | Pin limits to the catalog's `validMax` |

## 4. Contract

```kotlin
/** Highest level and shuttle on a published stage test's score sheet. */
data class StageLimits(val maxLevel: Int, val maxShuttle: Int) {
    companion object {
        fun forTest(testId: String): StageLimits? = when (testId) {
            "test_beep" -> StageLimits(maxLevel = 21, maxShuttle = 16)
            "test_yo_yo" -> StageLimits(maxLevel = 24, maxShuttle = 8)
            else -> null
        }
    }
}
```

`TestInputSwitcher(..., stageLimits: StageLimits? = null)` forwards the limits to `StageInputModule`. Both dialogs take the same trailing parameter.

## 5. Verification

- **Unit test:** `StageScore.encode(maxLevel, maxShuttle)` equals 21.16 for Beep and 24.08 for Yo-Yo, and `forTest` returns null for an unknown id.
- **Build:** run `gradlew assembleDebug` and `gradlew test`.
- **Manual check:**
  - In a Beep cell, SHUTTLE stops at 16 and LEVEL stops at 21.
  - In a Yo-Yo cell, SHUTTLE stops at 8 and LEVEL stops at 24.
  - Quick Test behaves the same way.
  - A custom Levels test still goes to 99.
