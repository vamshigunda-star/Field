# Spec: Select all tests in a category when creating a testing event

## 0. Options considered (pick one in a line if you disagree)

| Option | Verdict |
|---|---|
| **A. Tri-state checkbox on the category header** (empty / some / all) — tap the box to select or clear the whole category, tap anywhere else on the card to expand as today | **Chosen.** One tap, visible without expanding, and the "some selected" dash state tells the coach at a glance that a category is partly picked. Same pattern as Gmail / Files bulk select. |
| B. "Select all · Clear" text row at the top of the expanded test list | Needs expand → tap → collapse. Fine as an addition, not as the only way. Dropped: redundant with A. |
| C. Long-press the category card to select all | Undiscoverable; no coach will find it. |
| D. Presets / "Save as list" | Already exists for cross-category bundles; doesn't answer "everything in Flexibility". |

The header already shows "N selected" when tests are picked; the checkbox replaces nothing, it sits
before the chevron.

## 1. Objective & scope
- **As a coach**, I want to tick a whole category on the Create Testing Event screen so that I don't tap
  every test in it one by one.
- **In scope**
  - Tri-state checkbox in `CategoryAccordionHeader`, opt-in via new optional params (other callers unchanged).
  - New `ToggleCategorySelection` action in `CreateEventViewModel`.
  - Hoist the per-category test list out of the composable (it filters `allTests` per category on every
    recomposition today — see §2).
- **Out of scope**
  - Quick Test, Recommendations and Test Library screens (they also use `CategoryAccordionHeader`; they keep
    the defaults and render no checkbox).
  - Changes to presets.

## 2. Verified context
- Read:
  - `ui/testing/CreateEventScreen.kt:483-534` — loops `uiState.categories`, and **in composition** does
    `uiState.allTests.filter { it.categoryId == category.id }` and `categoryTests.count { it.id in selectedTestIds }`
    for every category; renders `CategoryAccordionHeader(..., isDocked = true)` and, when expanded,
    `TestSelectionCard` per test.
  - `ui/testing/CreateEventViewModel.kt` — `CreateEventUiState(selectedTestIds: Set<String>, expandedCategoryId, categories, allTests, …)`,
    `ToggleTest`, `ToggleCategoryExpanded`; collects `getTestLibrary.getCategories()` + `getAllTests()`.
  - `ui/components/testing/CategoryAccordion.kt` — `CategoryAccordionHeader(name, totalCount, isExpanded, onClick, modifier, radarAxis, accentColorOverride, iconOverride, selectedCount = 0, subtitle, isDocked)`;
    whole `Surface(onClick = onClick)` is the expand target; Material3 `Checkbox` already imported in this file.
    Callers: CreateEvent, QuickTest, Recommendations, TestLibrary.
- No schema change. Database stays at **v17**, no migration.
- **Drift noticed**
  - `CreateEventViewModel` injects `PeopleRepository` and `data.storage.CustomPresetsStore` (a `data.*` import in `ui/`). Not pulled into scope.
  - The render-phase filter above breaks the CLAUDE.md "no O(N) in composition" rule; fixed here because this change touches those exact lines.

## 3. Files
| Action | Path | Purpose |
|---|---|---|
| MODIFY | `app/src/main/java/com/vamshi/field/ui/testing/CreateEventViewModel.kt` | `testsByCategory`, `ToggleCategorySelection` |
| MODIFY | `app/src/main/java/com/vamshi/field/ui/components/testing/CategoryAccordion.kt` | optional tri-state checkbox slot |
| MODIFY | `app/src/main/java/com/vamshi/field/ui/testing/CreateEventScreen.kt` | wire checkbox, read hoisted map |
| NEW | `app/src/test/java/com/vamshi/field/ui/testing/CreateEventCategorySelectionTest.kt` | pure-function tests |

## 4. Contracts

```kotlin
// CreateEventUiState — add
val testsByCategory: Map<String, List<FitnessTest>> = emptyMap(), // categoryId -> tests, built when the catalog loads

// CreateEventAction — add
data class ToggleCategorySelection(val categoryId: String) : CreateEventAction

// CreateEventViewModel — internal, testable without Android
internal fun toggleCategory(selected: Set<String>, categoryTestIds: Set<String>): Set<String> =
    if (categoryTestIds.isNotEmpty() && selected.containsAll(categoryTestIds)) selected - categoryTestIds
    else selected + categoryTestIds

// CategoryAccordionHeader — two new trailing optional params
selectionState: androidx.compose.ui.state.ToggleableState? = null, // null = no checkbox (all existing callers)
onToggleSelection: (() -> Unit)? = null,
```

## 5. Implementation
1. **ViewModel.** In the catalog `collect`, also set `testsByCategory = tests.groupBy { it.categoryId }`.
   Handle `ToggleCategorySelection` with `_uiState.update { s -> s.copy(selectedTestIds = toggleCategory(s.selectedTestIds, s.testsByCategory[action.categoryId].orEmpty().map { it.id }.toSet())) }`.
   Rule: any unselected test in the category → select all; all selected → clear the category. Selections in other
   categories are untouched.
2. **Header.** When `selectionState != null && onToggleSelection != null`, render
   `TriStateCheckbox(state = selectionState, onClick = onToggleSelection, colors = CheckboxDefaults.colors(checkedColor = accentColor))`
   between the title column and the chevron. `TriStateCheckbox` is its own 48dp touch target, so taps on it don't
   reach the `Surface` expand handler. `contentDescription` via `Modifier.semantics { contentDescription = "Select all $name tests" }`.
3. **Screen.** Replace the in-composition filter with `val categoryTests = uiState.testsByCategory[category.id].orEmpty()`.
   Derive state: `selectedCount == 0 → Off`, `== size → On`, else `Indeterminate`. Pass
   `selectionState = if (categoryTests.isEmpty()) null else state` and
   `onToggleSelection = { onAction(CreateEventAction.ToggleCategorySelection(category.id)) }`.
   (`count` over one category's handful of tests is fine; the full-catalog filter was the problem.)

## 6. Constraints for this change
- `CategoryAccordionHeader` is shared by four screens — defaults must leave Quick Test, Recommendations and Test Library pixel-identical.
- Tapping the checkbox must **not** toggle expansion; tapping the card must **not** change selection.
- Selecting a whole category must not clear tests picked in other categories, nor an applied preset's tests outside it.

## 7. Verification
- **Unit** (`CreateEventCategorySelectionTest`): none selected → all added; some selected → all added; all selected → category cleared, other category's ids kept; empty category → no-op.
- **Build:** `gradlew assembleDebug` then `gradlew test`
- **Manual:** Home → Start Group Testing Event → tick "Flexibility" box: badge reads "N selected", box checked, card stays collapsed → expand: every test card selected → untick one test: header box shows dash → tick header box: all selected again → tick again: cleared → bottom bar count matches throughout. Open Quick Test and Test Library: no checkbox appears.
