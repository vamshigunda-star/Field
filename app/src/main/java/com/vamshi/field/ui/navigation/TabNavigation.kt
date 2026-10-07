package com.vamshi.field.ui.navigation

import androidx.navigation.NavController

/**
 * The one way to switch to a top-level tab (Home, Roster, Tests, Reports), whether from the
 * bottom bar, the navigation rail, or a shortcut tile on Home.
 *
 * Home is handled separately on purpose. The standard tab recipe —
 * `popUpTo(start) { saveState = true }` + `restoreState = true` — saves the screens above Home
 * *under Home's id*, then restores Home's saved state in the same call. If those screens were
 * pushed by a plain `navigate()` (as the Home tiles used to do), Home's "saved state" is the tab
 * that was just popped, so tapping Home put the coach straight back on it. Going Home is simply
 * "clear everything above it": the popped tab's state is still saved, so its own tab button
 * restores it later.
 */
fun NavController.navigateToTab(route: String) {
    // Anchored on Home's route, not graph.findStartDestination(): on a first run the graph's start
    // is Onboarding, which `popUpTo(0)` has already removed, so popping to it would do nothing.
    val home = Screen.Dashboard.route
    if (route == home) {
        // Saves the tab being left (so its button restores it) and restores nothing.
        popBackStack(home, inclusive = false, saveState = true)
        return
    }
    navigate(route) {
        popUpTo(home) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
