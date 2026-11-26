# Spec: Create Testing Event keeps the group, the event name and Start reachable while the coach picks tests

## 1. Objective & scope

- **As a coach**, I want to see and fix the athlete group and event name without scrolling back
  up past 11 test categories, and to be told what is missing when I can't start yet.
- **Decisions from the coach (2026-11-26):**
  1. **Layout: collapse while a category is open.**
     - With every category closed, the full Athlete Group and Event Name fields show at the top,
       as they do today.
     - While a category is open, those fields shrink to a one-line summary pinned under the title
       bar, and the open category scrolls up to sit directly under it.
     - Closing the category brings the full fields back.
  2. **Missing info: hint and jump.** A line above Start names what's missing. Tapping Start
     while something is missing takes the coach to that field.
  3. **Custom lists stay below the categories.** No change there.
  4. **This screen only.** Quick Test setup is unchanged.
- **In scope:** `CreateEventScreen.kt`, `CreateEventViewModel.kt`.
- **Out of scope:**
  - Quick Test setup.
  - Moving the error snackbar inside the `Scaffold`.
  - Moving the custom lists.
  - `CategoryAccordion.kt`, whose header and test cards are reused unchanged.

## 2. Verified context

- **Read:**
  - `ui/testing/CreateEventScreen.kt`: a single `LazyColumn` holds the fields, the "Select
    Tests" header, 11 category items keyed `category_${id}`, the scroll hint, custom lists and
    Save as Custom List. Start sits in `Scaffold.bottomBar` with
    `enabled = isReady && !isCreating`.
  - `ui/testing/CreateEventViewModel.kt`: `expandedCategoryId` gives a single-open accordion;
    `createEvent()` validates group, name and tests and sets `errorMessage`.
  - `ui/components/testing/CategoryAccordion.kt`.
  - `assets/test_categories.csv` (11 categories) and `tests.csv` (3–9 tests each).
  - `ui/quicktest/QuickTestScreen.kt`: its bottom bar already shows a "what's missing" line
    above Start. That is the pattern to reuse.
- **Schema:** none.
- **Drift noticed:**
  - `CreateEventViewModel` imports `data.storage.CustomPresetsStore` (a `data.*` import in
    `ui/`) and injects `PeopleRepository`. Both already exist; noted, not changed.
  - The Gemini rewrite proposed moving the snackbar into the `Scaffold`. That's real but
    unrelated, so it's deferred.

## 3. Files

| Action | Path | Purpose |
|---|---|---|
| MODIFY | `app/src/main/java/com/vamshi/field/ui/testing/CreateEventViewModel.kt` | Derived `missingRequirement`, one-shot `jumpTo`, two new actions |
| MODIFY | `app/src/main/java/com/vamshi/field/ui/testing/CreateEventScreen.kt` | Summary bar, conditional fields item, scroll-on-open, hint line, jump handling |
| NEW | `app/src/test/java/com/vamshi/field/ui/testing/CreateEventMissingRequirementTest.kt` | Unit tests for the derivation and the jump |

## 4. Contracts

```kotlin
// CreateEventViewModel.kt
/** First thing stopping Start, in on-screen order. Null when the event can be created. */
enum class MissingRequirement(val hint: String) {
    GROUP("Select an athlete group"),
    EVENT_NAME("Enter an event name"),
    TESTS("Select at least one test")
}

internal fun missingRequirement(groupId: String?, eventName: String, testIds: Set<String>): MissingRequirement? =
    when {
        groupId == null -> MissingRequirement.GROUP
        eventName.isBlank() -> MissingRequirement.EVENT_NAME
        testIds.isEmpty() -> MissingRequirement.TESTS
        else -> null
    }

data class CreateEventUiState(
    // …all existing fields unchanged…
    /** Derived on every update; drives the hint line and the button's look. */
    val missingRequirement: MissingRequirement? = MissingRequirement.GROUP,
    /** One-shot: the screen scrolls and focuses this field, then sends OnJumpConsumed. */
    val jumpTo: MissingRequirement? = null
)

sealed interface CreateEventAction {
    // …existing…
    /** Tapping the summary bar: close the open category so the full fields come back. */
    data object CollapseCategories : CreateEventAction
    data object JumpConsumed : CreateEventAction
}
```

## 5. Implementation

### ViewModel

1. Add the enum and the pure `missingRequirement(...)` function at file level, next to
   `toggleCategory`, so a unit test can call it directly.
2. Keep `missingRequirement` in state current with a single private helper,
   `private fun CreateEventUiState.withDerived() = copy(missingRequirement = missingRequirement(selectedGroupId, eventName, selectedTestIds))`.
   Apply it in every `_uiState.update` that touches the group, the name or the selection:
   `SetEventName`, `SelectGroup`, `ToggleTest`, `ToggleCategorySelection`, `applyPreset`, and the
   recommendation preselect. Recompute it in `combine` collectors too, for safety.
3. `CollapseCategories` sets `expandedCategoryId = null`. `JumpConsumed` sets `jumpTo = null`.
4. In `createEvent()`, if `missingRequirement != null`, set
   `jumpTo = missingRequirement` and `expandedCategoryId = null` when the missing thing is GROUP
   or EVENT_NAME, then return.
   - Don't set `errorMessage` for these three cases any more: the hint line replaces the
     snackbar.
   - Keep `errorMessage` for genuine failures, such as `createEventUseCase` failing.

### Screen

5. **Start button.**
   - Make it `enabled = !uiState.isCreating`, so a tap always reaches the ViewModel.
   - Keep the grey look whenever `missingRequirement != null` by keying the existing
     `gradientBrush` and text colour on `isReady = missingRequirement == null`.
   - Above the button, inside the same bottom-bar `Column`, show
     `missingRequirement?.hint` as a `labelSmall` line in `onSurfaceVariant`, matching
     `QuickTestScreen`'s bottom bar.
6. **Collapsed summary bar.** When `uiState.expandedCategoryId != null`, render a
   `CompactEventDetailsBar` *above* the `LazyColumn`, not inside it, so it stays pinned:
   - one row with a Groups icon, then
     `"${groupName ?: "No group"} · ${eventName.ifBlank { "No event name" }}"`, one line with
     ellipsis, then an Edit icon;
   - a blank value shows in `MaterialTheme.colorScheme.error`, so a missing name is obvious
     while the coach is picking tests;
   - tapping anywhere on the bar sends `CollapseCategories`;
   - resolve `groupName` in the ViewModel (add `selectedGroupName: String?` to the state, set on
     `SelectGroup` and when groups load), not with a `find` during composition. That also
     replaces today's in-render `groups.find`.
7. **Fields item.** Emit the existing group-plus-name `item { … }` only when
   `expandedCategoryId == null`. Give it `key = "event_details"` so the list keeps its scroll
   position correctly when it reappears.
8. **Scroll when a category opens.**
   - Hoist `val listState = rememberLazyListState()` into `CreateEventBody` and pass it to the
     `LazyColumn`.
   - Add `LaunchedEffect(uiState.expandedCategoryId)`: when it isn't null, find the item whose
     key is `"category_$id"` and call `listState.animateScrollToItem(index)`.
   - Compute that index inside the effect from `uiState.categories.indexOfFirst { it.id == id }`
     plus the leading items: the "Select Tests" header, plus the fields item when it's present
     (it isn't, because a category is open). This is 11 items, inside an effect rather than in
     composition, so the CLAUDE.md render-phase rule doesn't apply.
   - When it becomes null, scroll to item 0 so the restored fields are in view.
9. **Jump.** Add `LaunchedEffect(uiState.jumpTo)`. Each case ends with
   `onAction(CreateEventAction.JumpConsumed)`.
   - **GROUP:** scroll to item 0, then open the group dropdown by setting the existing
     `groupDropdownExpanded = true`.
   - **EVENT_NAME:** scroll to item 0, then `eventNameFocusRequester.requestFocus()`. Attach a
     `FocusRequester` to the name `OutlinedTextField`, and show the keyboard through
     `LocalSoftwareKeyboardController`.
   - **TESTS:** scroll to the "Select Tests" header item. Give it `key = "select_tests_header"`
     and find its index the same way as in step 8.

## 6. Constraints for this change

- **Selections must survive every collapse and expand.** All of it lives in the ViewModel state,
  so no `remember` should hold the group, the name or the tests.
- **Only one category open at a time**, as today. The summary bar depends on that.
- **Keep stable keys on every `LazyColumn` item.** Adding and removing the fields item without a
  key makes the list jump.
- **Recommendation pre-selection is unchanged**, but `missingRequirement` must be recomputed
  after it. Otherwise the hint keeps saying "Select at least one test" when tests are already
  selected.

## 7. Verification

- **Unit (`CreateEventMissingRequirementTest`):**
  - `missingRequirement` reports GROUP, then EVENT_NAME, then TESTS, then null as each is filled,
    and a whitespace-only name still counts as missing;
  - `createEvent` with no name sets `jumpTo = EVENT_NAME`, closes the open category, and leaves
    `errorMessage` null;
  - `CollapseCategories` clears `expandedCategoryId`;
  - recommendation preselect clears the TESTS hint. Build the ViewModel with the existing fakes;
    `CustomPresetsStore` needs a Context, so use Robolectric, as `GroupTestingSelectAllTestsTest`
    does.
- **Build:** `gradlew assembleDebug`, then `gradlew test`.
- **Manual, on a phone:**
  1. Open Create Testing Event and pick a group, leaving the name empty. The hint reads "Enter an
     event name".
  2. Open Speed (low in the list). It scrolls up under the summary bar, which shows
     "Grade 8A · No event name" with the second part in red.
  3. Tick 2 tests and tap Start. The category closes, the screen scrolls to the top, the name
     field is focused and the keyboard opens.
  4. Type a name, then reopen Speed. The 2 ticks are still there.
  5. Tap the summary bar. The full fields come back and the list is at the top.
  6. Tap Start. The event is created.
