# Field Design System & UI Architecture

This document serves as the single source of truth for the UI/UX design and frontend architecture of the Field application. Future UI changes, component modifications, and flow optimizations should be driven by the rules and structures defined here.

## 1. Design System Overview

- **Architecture Strategy**: The UI is built using **Jetpack Compose** and **Material 3**, strictly adhering to **Clean Architecture**. The Presentation layer is completely decoupled from the Data layer.
- **State Management**: Implements Unidirectional Data Flow (UDF). ViewModels expose a single immutable `StateFlow<UiState>` and receive user intents through a sealed `ScreenAction` interface.
- **Design Philosophy**: 
  - **Performance-Driven Visuals**: The app relies heavily on color-coded zones to instantly convey fitness performance percentiles (Green ≥60, Yellow 30-59, Red <30).
  - **Offline-First & Durable**: UI interactions are designed to persist to local Room SQLite databases efficiently, ensuring no data loss during live physical testing.
  - **Action Isolation**: Critical data entry is currently isolated in modal dialogs to prevent accidental input during active sports scenarios.

## 2. Information Architecture

### Core Entities
- **Individual (Athlete)**: The base unit of measurement. Includes profile data and critical medical alert flags.
- **Group (Roster)**: A collection of Individuals.
- **FitnessTest**: Defines what is being measured, its unit, valid bounds, and input paradigm (e.g., manual vs stopwatch).
- **TestingEvent**: A live session mapping specific Tests to a Group.
- **TestResult**: The recorded outcome (raw score and percentile) of an Individual in a Test.

### Data Flow
`UI Interaction` → `ViewModel.onAction(intent)` → `UseCase execution` → `Room DB (Persistence)` → `StateFlow Update` → `UI Recomposition`.

## 3. Screen Inventory

| Screen Name | Route / Path | Purpose | Key Components | Entry/Exit Points |
|---|---|---|---|---|
| **Dashboard** | `dashboard` | Home hub and statistics | `AppTopBar` | Entry: App Launch / Exit: To any module |
| **Roster** | `roster` | Group and athlete management | `ListDetailPaneScaffold` | Entry: BottomNav |
| **Athletes** | `athletes` | Read-only global athlete list | `LazyColumn` | Entry: BottomNav |
| **TestLibrary** | `test_library` | Categorized list of all fitness tests | `ListDetailPaneScaffold` | Entry: Dashboard |
| **CreateEvent** | `create_event` | Setup new testing session | Form Inputs | Entry: Dashboard / Exit: TestingGrid |
| **TestingGrid** | `testing_grid/{eventId}/{groupId}` | Live data entry interface | `AthleteRow`, `ScoreCell`, `ScrollableTabRow` | Entry: CreateEvent / Exit: Leaderboard, Report |
| **Stopwatch** | `stopwatch/...` | Live timing capture | Stopwatch Timer UI | Entry: TestingGrid |
| **AthleteDashboard**| `athlete/{athleteId}` | Individual profile & history | `ListDetailPaneScaffold`, Charts | Entry: Athletes, TestingGrid |
| **Auth Screens** | `sign_in`, `sign_up` | Authentication gates | Forms | Entry: Cold Start |

## 4. User Flows (Derived from Code)

### Live Testing Flow
1. **Context**: User is on `TestingGridScreen`.
2. **Test Selection**: User swipes or taps a test category via `ScrollableTabRow`.
3. **Target Selection**: User taps an empty `ScoreCell` on an `AthleteRow`.
4. **Method Choice**: If the test supports timing, `TimingChoiceDialog` asks: "Stopwatch" or "Manual".
5. **Data Entry**: `ScoreEntryDialog` opens (modal).
6. **Persistence**: User inputs data and taps "Save" or "Save & Next".
7. **Resolution**: Dialog dismisses (or cycles to next athlete), UI recomposes with the colored `ScoreCell`.

*Friction Points*: The requirement to open a modal dialog for every single manual entry adds significant interaction cost. While "Save & Next" helps, the workflow is interrupted compared to inline spreadsheet-style data entry.

## 5. Component Architecture

### Reusable UI Components
- **`AppTopBar`** 
  - *Purpose*: Standardized top app bar navigation.
  - *Props*: `title`, `navigationIcon`, `actions`.
- **`AthleteRow`**
  - *Purpose*: Renders an athlete's identity and their score for a specific test.
  - *Props*: `athlete`, `test`, `savedResult`, `onCellClick`, `onAthleteClick`.
  - *Variants*: Displays a warning icon if the athlete has a medical alert.
- **`ScoreCell`**
  - *Purpose*: Visual block rendering the raw score and applying the performance color zone background.
  - *Props*: `savedResult`, `onClick`, `onLongPress`.
- **`ScoreEntryDialog`**
  - *Purpose*: Modal for capturing and validating numeric/time data.
  - *Props*: `inputParadigm`, `currentResult`, `validMin`, `validMax`, `onSave`, `onSaveAndNext`.
- **`TestInputSwitcher`**
  - *Purpose*: Dynamically swaps the input keyboard/layout based on the test type (numeric vs time).

## 6. Layout & Interaction Patterns

- **Layout Strategies**: 
  - **Cards & Rows**: Core list elements (athletes) are wrapped in `Card` for elevation and grouping.
  - **Adaptive Panes**: Newer architectural screens (Roster, AthleteDashboard) utilize `ListDetailPaneScaffold` to adapt to tablets, but `TestingGrid` currently does not.
- **Navigation Behavior**: ViewModels are navigation-agnostic. They emit callback intents (e.g., `TestingGridAction.OnNavigateBack`), and the Compose layer handles the actual `NavController` routing.
- **State Handling**: A unified `UiState` data class encapsulates all possible screen states (`isLoading`, `errorMessage`, `editingCell`), avoiding multiple independent reactive streams.

## 7. Live Testing Screen Deep Analysis (Critical Section)

### Current Structure
The `TestingGridScreen` operates as a scaffold containing a `ScrollableTabRow` for selecting the active `FitnessTest` and a `LazyColumn` for iterating through `Individuals`.

### Data Entry Model
It utilizes a distinct modal entry model. Interaction on the grid does not permit direct typing; it acts as a trigger to summon `ScoreEntryDialog`.

### Scalability Limitations (30-50 Athletes)
The codebase exhibits a critical performance limitation:
1. **O(N) Lookups in Render Phase**: Inside `TestingGridComponents.kt`'s `LazyColumn`, the code executes `gridData.results.find { it.individualId == athlete.id }` for every single athlete row during rendering. For 50 athletes and large result sets, this causes severe recomposition lag.
2. **Missing Lazy Keys**: The `items(gridData.students)` lacks a `key` parameter. When a single score is saved, Compose is forced to recompose the entire list rather than just the updated row.

### UX Issues
The modal-heavy approach guarantees input safety but sacrifices speed. Evaluating 40 athletes requires a minimum of 80 discrete taps (Tap cell -> Tap Save -> Tap cell).

## 8. Data Persistence Behavior

- **Trigger**: Data is persisted immediately upon explicit user confirmation ("Save" button in `ScoreEntryDialog`).
- **Mechanism**: The ViewModel's `saveScore` function executes `RecordTestResultUseCase` inside `viewModelScope.launch`.
- **Discrepancy**: While architectural documents (`DEVELOPMENT_CONTEXT.md`) reference a pending staging table (`StagePendingEntryUseCase`) and a bulk "Submit All" flow, the current implementation in `TestingGridViewModel.kt` executes immediate writes directly to the finalized `TestResult` table.

## 9. Design Inconsistencies & UX Gaps

- **Adaptive Layout Mismatch**: While `RosterScreen` uses `ListDetailPaneScaffold`, the most complex screen (`TestingGrid`) is relegated to a simple `LazyColumn`, missing an opportunity to display athlete details alongside the grid on tablets.
- **Anti-Pattern (State Mapping in UI)**: The UI layer is calculating test completion progress and matching athletes to results. This logic belongs in the ViewModel or UseCase to emit a pre-processed `List<AthleteRowState>`.
- **Data Entry Friction**: The reliance on `AlertDialog` for rapid data entry creates a bottleneck.

## 10. Implicit Design Rules (Extracted)

1. **"ViewModels Do Not Navigate"**: Navigation is handled strictly via sealed action classes returned to the UI layer.
2. **"Colors Dictate Performance, Not Branding"**: Green, Yellow, and Red are reserved exclusively for physiological performance zones and medical alerts, never for generic primary buttons.
3. **"Medical Alerts supersede UI space"**: If an athlete has an alert, a warning icon must prefix their name across all list views.
4. **"Explicit Commits"**: Data is never auto-saved on text change; it always requires a button press.

## 11. Recommendations for Design System Evolution

- **Normalize Patterns**: Enforce standard `key = { it.id }` tracking in all `LazyColumn` components across the app to resolve scroll jank.
- **Improve Scalability**: Shift the `List.find()` result matching logic out of the Compose function. The `GetTestingGridDataUseCase` should construct a `Map<String, TestResult>` or a flattened UI model.
- **Reduce Complexity**: Redesign `TestingGridScreen` to support **inline cell editing** (spreadsheet style) to eliminate the modal dialogs for tests using the `MANUAL_ENTRY` paradigm.

## 12. Color Palette

The app reads this markdown table during the Gradle `preBuild` phase to auto-generate `Color.kt`. **Do not alter the format of this table.**
To change the app's colors, modify the Hex values here and rebuild the app.

| Name | Hex |
|---|---|
| ElectricBlue | #0052FF |
| DynamicOrange | #FFFF5E00 |
| AquaCyan | #00D1FF |
| NavyVariant | #1B263B |
| NavyPrimary | #0052FF |
| SportBlue | #0052FF |
| VibrantBlue | #0052FF |
| BlueAccent | #00D1FF |
| SportOrange | #FFFF5E00 |
| SportOrangeVariant | #E65100 |
| SportOrangeContainer | #FFF4EC |
| PeachIconBg | #F6D0C0 |
| BlueIconBg | #C7D7FF |
| SurfaceWhite | #FFFFFF |
| BackgroundLight | #F7F9FA |
| TextPrimary | #111827 |
| TextSecondary | #6B7280 |
| OutlineGrey | #E5E7EB |
| PerformanceGreen | #E8F5E9 |
| PerformanceGreenText | #1B5E20 |
| PerformanceGreenBorder | #A5D6A7 |
| PerformanceYellow | #FFFDE7 |
| PerformanceYellowText | #F57F17 |
| PerformanceYellowBorder | #FFF59D |
| PerformanceRed | #FFEBEE |
| PerformanceRedText | #B71C1C |
| PerformanceRedBorder | #EF9A9A |
| PerformanceGrey | #FAFAFA |
| PerformanceGreyText | #757575 |
| PerformanceGreyBorder | #EEEEEE |
| PerformanceGreenDark | #14532D |
| PerformanceGreenTextDark | #86EFAC |
| PerformanceGreenBorderDark | #166534 |
| PerformanceYellowDark | #713F12 |
| PerformanceYellowTextDark | #FDE047 |
| PerformanceYellowBorderDark | #854D0E |
| PerformanceRedDark | #7F1D1D |
| PerformanceRedTextDark | #FDA4AF |
| PerformanceRedBorderDark | #991B1B |
| PerformanceGreyDark | #374151 |
| PerformanceGreyTextDark | #D1D5DB |
| PerformanceGreyBorderDark | #4B5563 |

## 13. Design Control Layer (Rules for Agents/Engineers)

When modifying the UI, the following strict rules apply based on this document:

1. **Layout Paradigm Shifts**: 
   - If transforming `TestingGrid` to an adaptive layout, you MUST implement `ListDetailPaneScaffold` to match `RosterScreen.kt` and `TestLibraryScreen.kt`.
2. **Component Updates**:
   - If the data entry model shifts from Modal → Inline, you MUST update `AthleteRow.kt` and `ScoreCell.kt` to handle focus and keyboard IME actions, and immediately deprecate `ScoreEntryDialog`.
3. **Performance Color Updates**:
   - If threshold values change, they must be updated in `CalculatePercentileUseCase` and reflected visually in `ScoreCell`. The UI must never calculate its own percentiles.
4. **State Management**:
   - If addressing the O(N) lookup scalability limitation, you MUST modify `TestingGridViewModel` and `GetTestingGridDataUseCase`—do NOT attempt to fix it by writing complex memoization within `TestingGridComponents.kt`.

## 14. Traceability Requirement

- **Routes**: `ui/navigation/Screen.kt`
- **Testing UI Context**: `ui/testing/TestingGridScreen.kt`, `ui/testing/TestingGridComponents.kt`
- **Testing Logic**: `ui/testing/TestingGridViewModel.kt`
- **Global Context**: `DEVELOPMENT_CONTEXT.md`
