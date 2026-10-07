# Spec: "View Session Report" after Finish Testing traps the coach in the testing grid

## 0. Decisions made on the coach's behalf (correct any in one line)

1. **Finishing hands off to the Reports tab.** "View report" in the Finish Testing dialog closes the
   testing grid and opens Reports → Event Report with this event selected. Back from there goes to
   Home, exactly as if the coach had tapped the Reports tab — not back into the grid.
2. **The grid's top-bar report icon is unchanged.** That icon is a mid-testing peek: back from it
   *should* return to the grid. Only the Finish Testing exit changes.
3. **"Dashboard" in the same dialog goes to Home for real.** Today it is `popBackStack()`, so a grid
   opened from Reports → Edit results sends "Dashboard" back to Reports. Same dialog, same exit
   problem — fixed here.
4. **Copy:** "View Session Report" → "View Event Report" (lexicon: testing event, not session; and
   it is now literally the Event Report tab).

## 1. Objective & scope
- **As a coach**, when I finish testing and choose to view the report, I want to land in the normal
  Event Report for that event, so that back takes me out of testing instead of into it.
- **In scope:** the two exits in `TestingCompleteDialog`; a way to open Reports on a given event.
- **Out of scope:** the grid's top-bar Leaderboard / Session Report icons; the standalone
  `SessionReport` screen (still used by Group Overview and that top-bar icon); Home's "Continue
  testing" hero (`specs/2026-10-02-home-next-action-hero.md`).

## 2. Verified context
- `ui/testing/TestingGridScreen.kt:72-79` — `onViewReport` → `onNavigateToGroupReport(eventId, groupId)`;
  `onBackToDashboard` → `onNavigateBack()`.
- `ui/navigation/NavGraph.kt` (TestingGrid entry) — `onNavigateToGroupReport` pushes
  `Screen.SessionReport` **on top of** the grid, and `onNavigateBack = { navController.popBackStack() }`.
  So back from that report pops to the grid, whose back now asks "Leave testing?" — the loop the coach hit.
- `ui/navigation/Screen.kt` — `Report : Screen("reports")`, no arguments. `BottomNavItem.Reports.route`
  is that same string, and both `AdaptiveNavigationWrapper` (`items.any { it.route == currentRoute }`)
  and `NavGraph.MainTabRoutes` compare it literally.
- `ui/report/ReportsHubViewModel.kt` — already has `SavedStateHandle`, `selectedTab: ReportsHubTab`,
  `SelectTab`, and `loadEvent(eventId, groupId)`, which cancels any in-flight load and sets
  `selectedEventId` immediately (so the first-event auto-load in `init` can't override it).
- No schema change. **Database stays at v17, no migration.**
- **Drift noticed:** `ReportsHubViewModel` injects four repositories directly; not pulled into scope.

**Why not a route argument** (`reports?eventId=…`): the route string is the bottom-nav identity.
Turning it into a pattern breaks the literal comparisons above and makes the tab's
`navigate(screen.route)` pass the text `{eventId}` as a value. Handing the request through the new
Reports back-stack entry's `SavedStateHandle` leaves the route untouched, and survives process death.

## 3. Files
| Action | Path | Purpose |
|---|---|---|
| MODIFY | `app/src/main/java/com/vamshi/field/ui/report/ReportsHubViewModel.kt` | `OpenEvent` action |
| MODIFY | `app/src/main/java/com/vamshi/field/ui/report/ReportScreen.kt` | consume an open-event request |
| MODIFY | `app/src/main/java/com/vamshi/field/ui/navigation/NavGraph.kt` | new exits from the grid; pass request to Reports |
| MODIFY | `app/src/main/java/com/vamshi/field/ui/testing/TestingGridScreen.kt` | two new callbacks for the dialog |
| MODIFY | `app/src/main/java/com/vamshi/field/ui/testing/TestingGridComponents.kt` | button copy |

## 4. Contracts
```kotlin
// ReportsHubAction — add
data class OpenEvent(val eventId: String, val groupId: String) : ReportsHubAction
// handled as: SelectTab(EVENT_REPORT) semantics + loadEvent(eventId, groupId)

// ReportScreen — two new params, defaulted so other callers are unaffected
openEventRequest: Pair<String, String>? = null,   // eventId to groupId
onOpenEventRequestConsumed: () -> Unit = {},

// TestingGridScreen — two new params
onFinishToEventReport: (eventId: String, groupId: String) -> Unit,
onFinishToHome: () -> Unit,

// NavGraph — keys on the Reports entry's SavedStateHandle
const val OPEN_EVENT_ID = "open_event_id"
const val OPEN_EVENT_GROUP_ID = "open_event_group_id"
```

## 5. Implementation
1. **ViewModel.** `OpenEvent` → persist `EVENT_REPORT` to `KEY_SELECTED_TAB`, update `selectedTab`,
   then `loadEvent(action.eventId, action.groupId)`.
2. **ReportScreen.** `LaunchedEffect(openEventRequest) { openEventRequest?.let { (e, g) -> viewModel.onAction(ReportsHubAction.OpenEvent(e, g)); onOpenEventRequestConsumed() } }`.
3. **NavGraph — Reports entry.** Read `entry.savedStateHandle.getStateFlow<String?>(OPEN_EVENT_ID, null)`
   and the group id with `collectAsState()`; pass as `openEventRequest`; `onOpenEventRequestConsumed`
   removes both keys (so returning to the tab later doesn't re-select).
4. **NavGraph — TestingGrid entry.**
   - `onFinishToEventReport = { e, g -> navController.navigate(Screen.Report.route) { popUpTo(Screen.Dashboard.route); launchSingleTop = true }; navController.currentBackStackEntry?.savedStateHandle?.run { set(OPEN_EVENT_ID, e); set(OPEN_EVENT_GROUP_ID, g) } }`.
     `popUpTo(Dashboard)` (non-inclusive) clears the grid and anything between it and Home — including
     an older Reports entry when the grid was opened via Edit results — leaving Home → Reports.
   - `onFinishToHome = { navController.popBackStack(Screen.Dashboard.route, inclusive = false) }`.
5. **TestingGridScreen.** Dialog's `onViewReport` → `onFinishToEventReport(eventId, groupId)`;
   `onBackToDashboard` → `onFinishToHome()`. The top-bar `OnNavigateToGroupReport` keeps its current route.
6. **Copy.** "View Session Report" → "View Event Report".

## 6. Constraints for this change
- Don't change the `"reports"` route string — bottom-nav selection and tab transitions depend on it.
- The request must be consumed once: back to Home and re-tapping Reports must not snap back to this event
  on its own (it's fine if the tab simply remembers it was last on Event Report).
- `LeaveTestingDialog` must not appear on either Finish exit; both are deliberate.

## 7. Verification
- **Build:** `gradlew assembleDebug` then `gradlew test` (no new unit test: `ReportsHubViewModel` has no
  fakes for its four repositories, and the change is navigation wiring — verified on device).
- **Manual (emulator):**
  1. Home → Start Group Testing → record one score → Finish Testing → View Event Report → Reports tab
     highlighted, Event Report tab selected, this event in the card, pencil present.
  2. Press back → Home (not the grid, no leave prompt).
  3. Reports → Event Report → pencil → Finish Testing → View Event Report → same event; back → Home.
  4. From step 3's grid: Finish Testing → Dashboard → Home; back exits the app (nothing left above Home).
  5. Mid-testing: grid top-bar report icon → standalone report → back → grid (unchanged).
