# Spec: Roster, Tests Library and Reports show a back arrow as bottom-nav tabs

## 0. Open question (answer changes what "pushed from elsewhere" means)

**Finding: no entry point to these screens is different from a tab tap on the back stack.**

| Screen | Entry points in `NavGraph.kt` | Back stack after it |
|---|---|---|
| Roster | bottom nav; Dashboard `onNavigateToRoster` (Roster tile, Getting Started card) | `[Dashboard, Roster]` |
| Tests Library | bottom nav; Dashboard `onNavigateToTestLibrary` (tile, Getting Started); `CustomTest.onTestSaved` (`popUpTo(CustomTest){inclusive}` + `launchSingleTop`, so it lands back on the Tests Library that opened the builder) | `[Dashboard, TestLibrary]` |
| Reports | bottom nav; Dashboard `onNavigateToReports` (Reports tile) | `[Dashboard, Report]` |

A tab tap in `AdaptiveNavigationWrapper` pops to the start destination (Dashboard) before it
navigates, so a tab tap and a Home-tile push give the **same** stack. `isMainTab` is decided by
route, so the bottom bar (or rail) is showing in both cases. There are no deep links (the manifest
has only the launcher `intent-filter`).

**Assumed rule:** a tab screen shows Back only when the entry *beneath* it is something other than
Home. Back from a Home tile would only lead to Home, and the Home tab is already showing. So after
this change **no current path shows the arrow**. The rule stays correct if someone later pushes one
of these screens from a detail screen (e.g. Roster from Athlete Dashboard).

*Alternative, if you want the arrow after a Home-tile tap:* tag those pushes with a route
argument. That makes the route string differ from `BottomNavItem.route`, which breaks the
`isMainTab` check. The Home redesign spec also removes those three tiles, so I haven't specced it.

## 1. Objective & scope
- **In scope:** one `showNavigationIcon: Boolean` parameter on `RosterScreen`, `TestLibraryScreen`
  and `ReportScreen`, computed in one helper in `NavGraph.kt` and passed the same way to all three.
- **Out of scope:** changing how Home shortcuts navigate (they could reuse the tab-switch
  `navigate { popUpTo; saveState; restoreState }` so tab state is restored). This is noted but not done.

## 2. Verified context
- Read: `NavGraph.kt` (entry points above), `AdaptiveNavigationWrapper.kt` (tab click =
  `popUpTo(findStartDestination()) { saveState = true }; launchSingleTop; restoreState`; `isMainTab`
  route-based), `Screen.kt` (`BottomNavItem` routes = `Screen.Dashboard/Roster/TestLibrary/Report.route`),
  `AppTopBar.kt` (`navigationIcon` slot defaults to `{}`, so an empty slot draws nothing),
  `RosterScreen.kt:148`, `TestLibraryScreen.kt:78`, `ReportScreen.kt:120`, `AndroidManifest.xml`.
- Versions: `navigationCompose = "2.8.4"`. Uses only `NavHostController.previousBackStackEntry`.
- Roster's arrow sends `RosterAction.OnNavigateBack`, which closes the detail pane first
  (`navigator.canNavigateBack()`). Hiding the arrow loses nothing: the system `BackHandler` and
  the detail pane's own back still close it.
- **Drift noticed:** the three screens wire Back differently: Roster and Tests Library go through
  a nav-only Action, Reports through a direct `onNavigateBack` call. This spec doesn't change that.
  No ViewModel is involved. **No schema change; DB stays at v17.**

## 3. Files
| Action | Path | Purpose |
|---|---|---|
| MODIFY | `ui/navigation/NavGraph.kt` | `rememberShowBack(entry)` helper, pass to 3 screens |
| MODIFY | `ui/roster/RosterScreen.kt` | param on `RosterScreen` + `RosterContent`; arrow only if true |
| MODIFY | `ui/testlibrary/TestLibraryScreen.kt` | same for `TestLibraryScreen` + `TestLibraryContent` |
| MODIFY | `ui/report/ReportScreen.kt` | same for `ReportScreen` |

## 4. Contract
```kotlin
// NavGraph.kt (private)
/**
 * Tab screens show Back only when pushed over something other than Home. A tab switch always
 * pops to Home first (AdaptiveNavigationWrapper), so a tab tap never gets an arrow.
 */
@Composable
private fun NavHostController.rememberShowBack(entry: NavBackStackEntry): Boolean = remember(entry) {
    val below = previousBackStackEntry?.destination?.route
    below != null && below != Screen.Dashboard.route
}

// each screen
fun RosterScreen(showNavigationIcon: Boolean, onNavigateBack: () -> Unit, …)
fun RosterContent(uiState: RosterUiState, showNavigationIcon: Boolean = true, onAction: …)
fun TestLibraryScreen(showNavigationIcon: Boolean, onNavigateBack: () -> Unit, …)
fun TestLibraryContent(uiState, showNavigationIcon: Boolean = true, onAction)
fun ReportScreen(showNavigationIcon: Boolean, onNavigateBack: () -> Unit, …)
```
`remember(entry)` reads the stack once, when the entry first composes as the top destination.
That keeps the value fixed when a child screen is pushed over it and later popped.
In each screen: `navigationIcon = { if (showNavigationIcon) IconButton(…) { … } }`.

## 5. Verification
- `.\gradlew.bat :app:testDebugUnitTest :app:installDebug`.
- Emulator: tap Roster, Tests and Reports in the bottom nav, starting from Home and from each
  other: no arrow. Then open Roster and Tests Library from the Getting Started card, and Reports
  from the Home tile: also no arrow, because Home is beneath (see §0). System back returns to Home.
  Saving a custom test lands on Tests Library with no arrow. No current entry point shows the
  arrow, so the "arrow shown" case can't be checked on device.
