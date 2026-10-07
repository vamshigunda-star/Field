# Spec: "How to use Field" help page

## 1. Objective & scope
- **As a coach**, I want a short guide to the three core workflows so that I can get going without being walked through the app.
- **In scope:** one static page with three expandable guide cards (numbered steps, one screenshot, a caption); a "New to Field? Start here" link on Home; a permanent "Help → How to use Field" row in Settings.
- **Out of scope:** coach marks, overlays, auto-navigation, first-launch forcing, new dependencies, any database access.

## 2. Verified context
- Steps checked against the real controls: Roster tab → Groups → **+** → *Create New Group* / **Create Group**; Athletes → **+** → *Register Athlete*; group card **Add Athlete** → *Manage Group Members* → **Done**; Home **Start Group Testing Event** → *Athlete Group*, *Event Name*, *Select Tests* → **Start Group Testing**; grid cell → *Individual Timer / Group / Heat Timer / Manual Keypad Entry*; **Save Results**; Reports → *Event Report* → **Resume testing**; Reports → *Athlete Profile* → test → *Latest result / History / Peers*.
- The app has no Help area. Settings is the only menu, so the permanent entry goes there.
- Zone thresholds are read from `PerformanceThresholds`; the legend reuses `report/components/ZoneChip`.
- **Drift noticed:** "Select or create a testing event" → there is no way to pick an existing event before testing. You create a new one from Home, and reopen an unfinished one through Reports → Event Report → Resume testing. The guide says exactly that.

## 3. Files
| Action | Path | Purpose |
|---|---|---|
| NEW | `ui/help/HowToUseGuides.kt` | All copy + screenshot slots (`Guide`, `GuideStep`, `GuideScreenshot`) |
| NEW | `ui/help/HowToUseScreen.kt` | Stateless page, no ViewModel (nothing to load, nothing to write) |
| NEW | `test/.../ui/help/HowToUseGuidesTest.kt` | Content invariants |
| MODIFY | `ui/navigation/Screen.kt`, `NavGraph.kt` | `Screen.HowToUse` ("how_to_use") |
| MODIFY | `ui/dashboard/DashboardScreen.kt`, `DashboardViewModel.kt` | `HowToUseEntry` + `OnHowToUseClick` (nav-only) |
| MODIFY | `ui/settings/SettingsScreen.kt`, `SettingsContract.kt`, `SettingsViewModel.kt` | Help row + `OpenHowToUse` (nav-only); column made scrollable |

No schema change. The database stays at v17.

## 4. Screenshots
Captured on the `Medium_Phone` emulator in light theme, cropped to remove the status and gesture bars, then saved as 720 px WebP files in `app/src/main/res/drawable-nodpi/` (about 130 KB in total):
- `guide_add_athletes.webp`: Roster › Groups tab with the Varsity Football card open
- `guide_record_test.webp`: the testing screen for the "cvx" event (test tabs, Enter Result buttons)
- `guide_understand_results.webp`: Reports › Athlete Profile with the Skill Matrix radar

On a phone the testing screen shows one tab per test with an Enter Result button per athlete,
not an athletes × tests grid, so step 3 of "Record a test" describes the tabs.
