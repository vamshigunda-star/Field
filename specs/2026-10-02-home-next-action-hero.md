# Spec: Home leads with the next useful thing

## 0. Decisions made on the coach's behalf (correct any in one line)

1. **The hero tracks the most recent *group* testing event.** Ungrouped events (Quick Test /
   Individual Test) have no roster to measure "tested of total" against and no Session Report
   (`SessionReport` requires a `groupId`; `ReportScreen` already skips ungrouped rows with
   `row.groupId ?: return@EventPickerRow`). They are ignored by the hero.
2. **"Continue" vs "View report".** If any current group member is missing a result for any test
   on the event's menu, the primary action is **Continue testing** (→ `TestingGrid`) and, when at
   least one athlete has a result, a secondary **View report** (→ `SessionReport`). When every
   member has every test, the only action is **View report**.
3. **The ViewModel is made fully compliant**, not just for the new data. Today it injects
   `PeopleRepository`, `TestingRepository` and `data.storage.OnboardingPreferencesStore`. The new
   use case replaces both repositories; the onboarding store gets a domain interface + two tiny use
   cases so `DashboardViewModel` has zero `data.*` imports.
4. **Remaining tools (Leaderboard, Recommendations, Quick Test) become a "More tools" list card**,
   not grid tiles. Three tiles in `GridCells.Adaptive(150.dp)` leave an orphan on phones, and at
   three-across "Recommendations" no longer fits a tile. All three stay — none is reachable from
   a bottom-nav tab without an event already open (see §2).
5. **No orange on the tools or the hero.** Leaderboard and Quick Test tiles were `SportOrange`;
   they become `SportBlue`/`primary`. Orange stays only on "Start Group Testing Event".
   The hero shows no percentile data, so no zone colours are introduced.

## 1. Objective & scope
- **As a coach**, I want Home to show where my latest testing event stands and take me straight
  back into it, so I don't have to hunt for it through Reports.
- **In scope**
  - Remove the Roster, Tests Library and Reports tiles and the "Quick Actions" header.
  - Replace the `"$athleteCount Athletes • $eventCount Events"` line in the blue gradient card with
    a latest-event block: name, group, date, "12 of 20 athletes tested", progress bar,
    "8 not yet tested", Continue / View report.
  - Empty state (no group events) and loading state (no flash of the empty state).
  - New use case + unit test; onboarding preference behind a domain interface.
  - Keep "Start Group Testing Event", "Individual Test", Getting Started card, leaderboard picker.
- **Out of scope**
  - Zone summaries on Home, multi-event lists, any schema change (**DB stays at v17, no migration**).
  - The other ViewModels that inject repositories directly.

## 2. Verified context
- Read:
  - `ui/dashboard/DashboardViewModel.kt` — injects `PeopleRepository`, `TestingRepository`,
    `ObserveCurrentUserUseCase`, `SignOutUseCase`, `data.storage.OnboardingPreferencesStore`;
    `groups` in UiState is populated but never read by the screen.
  - `ui/dashboard/DashboardScreen.kt` — `ContextHeaderCard` (gradient, greeting + counts),
    `HeroCard` (orange start), `PrimaryActionCard` (Individual Test), 6 `QuickActionCard`s in a
    `LazyVerticalGrid(GridCells.Adaptive(150.dp))`; leaderboard picker filters
    `availableEvents.filter { it.groupId != null }` in composition and uses `event.groupId!!`.
  - `ui/navigation/NavGraph.kt` / `Screen.kt` — bottom nav = Home, Roster, Tests, Reports.
    Leaderboard is otherwise reachable only from `TestingGrid` (needs an open event);
    Recommendations only from Dashboard; generic Quick Test only from Dashboard (Reports/Athlete
    open it pre-filled for one athlete). `TestingGrid.createRoute(eventId, groupId)`,
    `SessionReport.createRoute(groupId, sessionId)` exist.
  - `domain/repository/TestingRepository.kt`, `PeopleRepository.kt` — `getAllEvents()`,
    `getEventResults(eventId)`, `getTestsForEvent(eventId)`, `getGroupFlow(id)`,
    `getIndividualsInGroup(groupId)`, `getAllIndividuals()` all return `Flow`.
  - `data/repository/ReportsRepositoryImpl.observeSessionReport` — counts *tested* as distinct
    athletes among **current group members** with a result; the hero uses the same rule so Home
    and the Session Report agree.
  - `domain/model/testing/TestingEvent.kt` — `id, groupId: String? = null, name, date: Long,
    location, notes`. `TestResult` — `eventId, individualId, testId, …`.
  - `data/storage/OnboardingPreferencesStore.kt` — `observeGettingStartedDismissed(): Flow<Boolean>`,
    `suspend setGettingStartedDismissed(Boolean)`; only consumer is `DashboardViewModel`.
  - `app/src/androidTest/.../ui/ResponsiveGridTest.kt` — asserts the "Roster" and "Quick Test"
    tiles lay out side by side / stacked. Its premise (a tile grid) goes away.
  - Test style: `app/src/test/.../usecase/people/DeleteGroupUseCaseTest.kt` (hand-written fake
    repository, `runTest`, JUnit4).
- Versions in play: `kotlinx-coroutines-test` 1.11.0 (`coroutines`), nothing new added.
- **Drift noticed**
  - `design.md` §3 says Dashboard uses `AppTopBar`; it uses a local `DashboardHeader`. TestLibrary
    "Entry: Dashboard" will be wrong after this change — update that row to "BottomNav".
  - `DashboardViewModel` was one of the repository-injecting ViewModels; fixed here (decision 3).

## 3. Files
| Action | Path | Purpose |
|---|---|---|
| NEW | `domain/usecase/testing/ObserveHomeSummaryUseCase.kt` | `HomeSummary`, `LatestEventProgress`, the use case |
| NEW | `domain/repository/OnboardingPreferencesRepository.kt` | Domain interface for the checklist flag |
| NEW | `domain/usecase/auth/ObserveGettingStartedDismissedUseCase.kt` | Wraps observe |
| NEW | `domain/usecase/auth/DismissGettingStartedUseCase.kt` | Wraps set(true) |
| MODIFY | `data/storage/OnboardingPreferencesStore.kt` | `: OnboardingPreferencesRepository`, `override` |
| MODIFY | `di/RepositoryModule.kt` | `@Binds` store → interface |
| MODIFY | `ui/dashboard/DashboardViewModel.kt` | Use cases only; new state fields/actions |
| MODIFY | `ui/dashboard/DashboardScreen.kt` | Hero, tools card, LazyColumn, previews |
| MODIFY | `ui/navigation/NavGraph.kt` | Add grid/report callbacks, drop `onNavigateToReports` |
| NEW | `app/src/test/.../domain/usecase/testing/ObserveHomeSummaryUseCaseTest.kt` | Unit tests |
| REPLACE | `app/src/androidTest/.../ui/ResponsiveGridTest.kt` → `DashboardContentTest.kt` | Tools present, duplicate tiles absent, empty hero copy |
| MODIFY | `design.md` | Screen inventory rows for Dashboard / TestLibrary |

(All main paths under `app/src/main/java/com/vamshi/field/`.)

## 4. Contracts

```kotlin
// domain/usecase/testing/ObserveHomeSummaryUseCase.kt
data class LatestEventProgress(
    val event: TestingEvent,
    val groupId: String,
    val groupName: String,
    val testCount: Int,          // tests on the event's menu
    val athletesTested: Int,     // current members with ≥1 result in this event
    val totalAthletes: Int,      // current group members
    val isComplete: Boolean      // totalAthletes > 0 && testCount > 0 && every member has every test
) {
    val athletesNotTested: Int get() = totalAthletes - athletesTested
}

data class HomeSummary(
    val athleteCount: Int,
    val eventCount: Int,
    val groupEvents: List<TestingEvent>,   // events with a groupId, newest first (leaderboard picker)
    val latestEvent: LatestEventProgress?  // null = no group events (or its group was deleted)
)

class ObserveHomeSummaryUseCase @Inject constructor(
    private val testingRepository: TestingRepository,
    private val peopleRepository: PeopleRepository
) {
    operator fun invoke(): Flow<HomeSummary>
}

// domain/repository/OnboardingPreferencesRepository.kt
interface OnboardingPreferencesRepository {
    fun observeGettingStartedDismissed(): Flow<Boolean>
    suspend fun setGettingStartedDismissed(dismissed: Boolean)
}

class ObserveGettingStartedDismissedUseCase @Inject constructor(private val repo: OnboardingPreferencesRepository) {
    operator fun invoke(): Flow<Boolean>
}
class DismissGettingStartedUseCase @Inject constructor(private val repo: OnboardingPreferencesRepository) {
    suspend operator fun invoke()
}

// ui/dashboard/DashboardViewModel.kt
data class DashboardUiState(
    val groupEvents: List<TestingEvent> = emptyList(),   // was availableEvents (unfiltered)
    val latestEvent: LatestEventProgress? = null,
    val activeAthletes: Int = 0,
    val scheduledTestCount: Int = 0,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val coachFirstName: String = "",
    val coachLastName: String = "",
    val navigateToSignIn: Boolean = false,
    val showLeaderboardPicker: Boolean = false,
    val isGettingStartedDismissed: Boolean = false
)   // `groups` removed — never read

sealed interface DashboardAction {
    data object OnCreateEventClick : DashboardAction
    data object OnQuickTestClick : DashboardAction
    data object OnIndividualTestClick : DashboardAction
    data object OnRecommendationsClick : DashboardAction
    data object OnSettingsClick : DashboardAction
    data object OnDismissError : DashboardAction
    data object OnLeaderboardClick : DashboardAction
    data object OnDismissLeaderboardPicker : DashboardAction
    data class OnPickLeaderboardEvent(val eventId: String, val groupId: String) : DashboardAction
    /** Nav-only: handled by the screen. */
    data class OnContinueEventClick(val eventId: String, val groupId: String) : DashboardAction
    /** Nav-only: handled by the screen. */
    data class OnViewEventReportClick(val eventId: String, val groupId: String) : DashboardAction
    data object OnSignOutClick : DashboardAction
    data object NavigationConsumed : DashboardAction
    data object OnDismissGettingStarted : DashboardAction
}   // OnRosterClick, OnTestLibraryClick, OnAnalyticsClick removed with their tiles
```

## 5. Implementation, layer by layer
1. **Domain interface + store.** `OnboardingPreferencesStore` implements
   `OnboardingPreferencesRepository` (add `override`, no behaviour change; its existing test keeps
   passing). Bind in `RepositoryModule` with `@Binds @Singleton`.
2. **Use cases.** `ObserveHomeSummaryUseCase.invoke()`:
   - `events = testingRepository.getAllEvents().map { it.sortedByDescending(TestingEvent::date) }`
   - `latest = events.map { it.firstOrNull { e -> e.groupId != null } }.distinctUntilChanged()
     .flatMapLatest { e -> e?.let(::observeProgress) ?: flowOf(null) }`
   - `observeProgress(e)` = `combine(getGroupFlow(gid), getIndividualsInGroup(gid),
     getTestsForEvent(e.id), getEventResults(e.id))` → `null` if group is null; otherwise members
     distinct by id, results restricted to member ids, `athletesTested` = distinct individualIds,
     `isComplete` = every member has a result for every distinct menu test id.
   - `combine(peopleRepository.getAllIndividuals(), events, latest)` → `HomeSummary`.
   - `@OptIn(ExperimentalCoroutinesApi::class)` for `flatMapLatest`.
3. **ViewModel.** Constructor: `ObserveHomeSummaryUseCase`, `ObserveCurrentUserUseCase`,
   `SignOutUseCase`, `ObserveGettingStartedDismissedUseCase`, `DismissGettingStartedUseCase`.
   One collector on the summary: `.catch { _uiState.update { errorMessage, isLoading = false } }
   .collect { _uiState.update { it.copy(groupEvents, latestEvent, activeAthletes,
   scheduledTestCount, isLoading = false) } }`. Nav-only actions fall to `else -> Unit`.
4. **Screen.**
   - `LazyVerticalGrid` → `LazyColumn` (every remaining item is full-span), same padding/spacing,
     stable `key`s per item.
   - `ContextHeaderCard(greeting…, latestEvent, isLoading, onContinue, onViewReport)`:
     date + greeting unchanged; below a thin white divider:
     - loading → nothing extra (no empty-state flash);
     - `latestEvent == null` → "No testing events yet" / "Start a group event below — its
       progress will show up here.";
     - otherwise label "LATEST EVENT", event name (titleMedium, white), "$groupName ·
       ${formatEventMetadata(date)}", "$tested of $total athletes tested" with a white
       `LinearProgressIndicator` on `White.copy(alpha = 0.25f)` track, "$n not yet tested" when
       n > 0, then a white pill button (`ElectricBlue` text) "Continue testing" / "View report",
       plus a white `TextButton` "View report" when Continue is primary and tested > 0.
   - Delete the "Quick Actions" header and all six `QuickActionCard`s; add `MoreToolsCard` — one
     outlined surface card, header "More tools", three rows (icon in `BlueIconBg` circle,
     `SportBlue` tint, label, one-line subtitle, chevron), ≥48dp tall, divider between rows:
     Leaderboard ("Rank a group's results"), Recommendations ("Suggested test batteries"),
     Quick Test ("Score without registering"). Remove `QuickActionCard`.
   - Leaderboard picker reads `uiState.groupEvents` directly; drop the `filter` and `!!`.
   - Previews: populated (in-progress), complete, empty.
5. **NavGraph.** Add `onNavigateToTestingGrid = { e, g -> navigate(Screen.TestingGrid.createRoute(e, g)) }`
   and `onNavigateToSessionReport = { e, g -> navigate(Screen.SessionReport.createRoute(g, e)) }`;
   remove `onNavigateToReports`.
6. **Tests & docs** as in §7; update `design.md` inventory rows.

## 6. Constraints for this change
- `DashboardViewModel` ends with **no `data.*` and no `domain.repository.*` imports**; no `stateIn`,
  only `_uiState.update { it.copy(...) }`.
- `SportOrange` appears in `DashboardScreen.kt` only in `HeroCard` (Start Group Testing Event) and
  the leaderboard picker is re-tinted `SportBlue`.
- Tested-count rule must match `observeSessionReport` (current members only) or Home and the
  Session Report will disagree about the same event.
- No filtering or `find` in composition — the picker list and progress numbers arrive computed.

## 7. Verification
- **Unit** `ObserveHomeSummaryUseCaseTest` (fake repos as in `DeleteGroupUseCaseTest`, but backed by
  `MutableStateFlow`s): no events → `latestEvent == null`, counts 0; only ungrouped events →
  `latestEvent == null`, `eventCount` counts them; newest group event chosen over older and over a
  newer ungrouped one; partial results → tested/total/notTested correct and `isComplete == false`;
  results from a non-member are ignored; every member × every test → `isComplete == true`;
  deleted group → `null`.
- **Instrumented (compile only)** `DashboardContentTest`: "Leaderboard", "Recommendations",
  "Quick Test" present; "Tests Library" and "Quick Actions" absent; empty hero copy shown.
- **Build** `.\gradlew.bat :app:testDebugUnitTest :app:installDebug` (one build at a time).
- **Manual / screenshots** (`D:\Android\Sdk\platform-tools\adb.exe`, `screencap` + `pull`):
  before (current tree) and after, light and dark: Home with data; empty state. Empty state needs a
  fresh install state — back up the debug app's `databases/` and `shared_prefs/` via `run-as`,
  `pm clear`, onboard a throwaway local coach, screenshot, then restore the files so the emulator's
  test data is unchanged. Leave the emulator in dark mode. Tap Continue → Testing Grid for that
  event; View report → that Session Report.
