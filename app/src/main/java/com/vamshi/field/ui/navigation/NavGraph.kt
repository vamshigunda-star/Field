package com.vamshi.field.ui.navigation

import android.util.Log
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.vamshi.field.ui.aicoach.AiCoachScreen
import com.vamshi.field.ui.athlete.AthleteDashboardScreen
import com.vamshi.field.ui.athlete.AthleteTestDetailScreen
import com.vamshi.field.ui.auth.AuthGateState
import com.vamshi.field.ui.auth.AuthGateViewModel
import com.vamshi.field.ui.auth.onboarding.OnboardingScreen
import com.vamshi.field.ui.auth.restore.RestoreBackupScreen
import com.vamshi.field.ui.auth.unlock.UnlockScreen
import com.vamshi.field.ui.customtest.CustomTestScreen
import com.vamshi.field.ui.dashboard.DashboardScreen
import com.vamshi.field.ui.groupoverview.GroupOverviewScreen
import com.vamshi.field.ui.leaderboard.LeaderboardScreen
import com.vamshi.field.ui.quicktest.QuickTestScreen
import com.vamshi.field.ui.recommendations.RecommendationsScreen
import com.vamshi.field.ui.report.ReportScreen
import com.vamshi.field.ui.roster.RosterScreen
import com.vamshi.field.ui.session.SessionReportScreen
import com.vamshi.field.ui.settings.SettingsScreen
import com.vamshi.field.ui.testing.CreateEventScreen
import com.vamshi.field.ui.testing.TestingGridScreen
import com.vamshi.field.ui.testing.stopwatch.StopwatchScreen
import com.vamshi.field.ui.testlibrary.TestLibraryScreen

@Composable
fun ALearningNavGraph(navController: NavHostController, modifier: Modifier = Modifier) {
    val authGateViewModel: AuthGateViewModel = hiltViewModel()
    val authGateState by authGateViewModel.state.collectAsState()

    // While the auth gate is resolving, show a full-screen spinner so we never
    // briefly flash the wrong destination.
    if (authGateState == AuthGateState.Loading) {
        Log.d("ALearningNavGraph", "Auth gate is loading...")
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    Log.d("ALearningNavGraph", "Auth gate resolved: $authGateState")

    val startDestination = when (authGateState) {
        AuthGateState.Authenticated -> Screen.Dashboard.route
        AuthGateState.UnauthenticatedHasUsers -> Screen.Unlock.route
        AuthGateState.UnauthenticatedNoUsers -> Screen.Onboarding.route
        AuthGateState.Loading -> Screen.Unlock.route // unreachable — handled above
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        enterTransition = { slideInHorizontally(animationSpec = tween(220, easing = FastOutSlowInEasing)) { fullWidth -> fullWidth / 4 } + fadeIn(animationSpec = tween(220)) },
        exitTransition = { slideOutHorizontally(animationSpec = tween(220, easing = FastOutSlowInEasing)) { fullWidth -> -fullWidth / 4 } + fadeOut(animationSpec = tween(220)) },
        popEnterTransition = { slideInHorizontally(animationSpec = tween(220, easing = FastOutSlowInEasing)) { fullWidth -> -fullWidth / 4 } + fadeIn(animationSpec = tween(220)) },
        popExitTransition = { slideOutHorizontally(animationSpec = tween(220, easing = FastOutSlowInEasing)) { fullWidth -> fullWidth / 4 } + fadeOut(animationSpec = tween(220)) }
    ) {
        // ───── Auth screens ─────

        composable(Screen.Unlock.route) {

            UnlockScreen(
                onUnlockSuccess = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onNavigateToRestore = {
                    navController.navigate(Screen.RestoreBackup.route)
                }
            )
        }

        composable(Screen.Onboarding.route) {

            OnboardingScreen(
                onOnboardingSuccess = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onNavigateToRestore = {
                    navController.navigate(Screen.RestoreBackup.route)
                }
            )
        }

        composable(Screen.RestoreBackup.route) {

            RestoreBackupScreen(
                onNavigateBack = { navController.popBackStack() },
                onRestoreSuccess = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        // ───── Main app screens ─────

        composable(Screen.Dashboard.route) {

            DashboardScreen(
                onNavigateToRoster = { navController.navigate(BottomNavItem.Roster.route) },
                onNavigateToTestLibrary = { navController.navigate(Screen.TestLibrary.route) },
                onNavigateToCreateEvent = { navController.navigate(Screen.CreateEvent.createRoute()) },
                onNavigateToRecommendations = { navController.navigate(Screen.Recommendations.route) },
                onNavigateToNewTest = { navController.navigate(Screen.CustomTest.createRoute()) },
                onNavigateToQuickTest = { navController.navigate(Screen.QuickTest.createRoute()) },
                onNavigateToIndividualTest = { navController.navigate(Screen.QuickTest.createRoute(mode = "individual")) },
                onNavigateToLeaderboard = { eventId, groupId, mode ->
                    navController.navigate(Screen.Leaderboard.createRoute(eventId, groupId, mode))
                },
                onNavigateToReports = { navController.navigate(Screen.Report.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                onNavigateToAiCoach = { navController.navigate(Screen.AiCoach.route) },
                onNavigateToSignIn = {
                    navController.navigate(Screen.Unlock.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Roster.route) {

            RosterScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToAthleteReport = { athleteId ->
                    navController.navigate(Screen.AthleteDashboard.createRoute(athleteId))
                },
                onNavigateToTest = { aId, tId, cId ->
                    navController.navigate(Screen.AthleteTestDetail.createRoute(aId, tId, cId))
                }
            )
        }



        composable(Screen.TestLibrary.route) {

            TestLibraryScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToEditTest = { testId ->
                    navController.navigate(Screen.CustomTest.createRoute(testId))
                }
            )
        }

        composable(
            route = Screen.CustomTest.route,
            arguments = listOf(
                navArgument("testId") { type = NavType.StringType; nullable = true; defaultValue = null }
            )
        ) {
            CustomTestScreen(
                onNavigateBack = { navController.popBackStack() },
                // The new test is already in the catalog by now, so drop the coach into the
                // library to see it rather than back onto the dashboard with no feedback.
                onTestSaved = {
                    navController.navigate(Screen.TestLibrary.route) {
                        popUpTo(Screen.CustomTest.route) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(Screen.Recommendations.route) {

            RecommendationsScreen(
                onNavigateBack = { navController.popBackStack() },
                onApplyAndContinue = { recommendationId ->
                    navController.navigate(Screen.CreateEvent.createRoute(recommendationId)) {
                        // A rapid double-tap on "Apply & Continue" must not push two
                        // CreateEvent instances onto the back stack.
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(
            route = Screen.CreateEvent.route,
            arguments = listOf(
                navArgument("recommendationId") { type = NavType.StringType; nullable = true; defaultValue = null }
            ),
            enterTransition = { slideInHorizontally(animationSpec = tween(200, easing = FastOutSlowInEasing)) { fullWidth -> fullWidth / 4 } + fadeIn(animationSpec = tween(200)) },
            exitTransition = { slideOutHorizontally(animationSpec = tween(200, easing = FastOutSlowInEasing)) { fullWidth -> -fullWidth / 4 } + fadeOut(animationSpec = tween(200)) },
            popEnterTransition = { slideInHorizontally(animationSpec = tween(200, easing = FastOutSlowInEasing)) { fullWidth -> -fullWidth / 4 } + fadeIn(animationSpec = tween(200)) },
            popExitTransition = { slideOutHorizontally(animationSpec = tween(200, easing = FastOutSlowInEasing)) { fullWidth -> fullWidth / 4 } + fadeOut(animationSpec = tween(200)) }
        ) {

            CreateEventScreen(
                onNavigateBack = { navController.popBackStack() },
                onEventCreated = { eventId, groupId ->
                    navController.navigate(Screen.TestingGrid.createRoute(eventId, groupId)) {
                        popUpTo(Screen.Dashboard.route)
                    }
                }
            )
        }

        composable(
            route = Screen.QuickTest.route,
            arguments = listOf(
                navArgument("athleteId") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("testIds") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("eventId") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("mode") { type = NavType.StringType; nullable = true; defaultValue = null }
            )
        ) {
            QuickTestScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // ───── Reports & Results layer ─────

        composable(Screen.Report.route) {

            ReportScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToGroup = { groupId ->
                    navController.navigate(Screen.GroupOverview.createRoute(groupId))
                },
                onNavigateToSession = { groupId, sessionId ->
                    navController.navigate(Screen.SessionReport.createRoute(groupId, sessionId))
                },
                onNavigateToAthlete = { athleteId ->
                    navController.navigate(Screen.AthleteDashboard.createRoute(athleteId))
                },
                onNavigateToTest = { athleteId, testId ->
                    navController.navigate(Screen.AthleteTestDetail.createRoute(athleteId, testId, null))
                },
                onNavigateToAiCoach = { contextString -> 
                    navController.navigate(Screen.AiCoach.createRoute(contextString)) 
                },
                onStartQuickTest = { athleteId, testIds ->
                    navController.navigate(Screen.QuickTest.createRoute(athleteId = athleteId, testIds = testIds))
                },
                onResumeTesting = { eventId, groupId, athleteId, testIds ->
                    if (groupId != null) {
                        navController.navigate(Screen.TestingGrid.createRoute(eventId, groupId = groupId))
                    } else {
                        navController.navigate(Screen.QuickTest.createRoute(athleteId = athleteId, testIds = testIds ?: emptyList(), eventId = eventId))
                    }
                }
            )
        }

        composable(
            route = Screen.GroupOverview.route,
            arguments = listOf(navArgument("groupId") { type = NavType.StringType })
        ) {
            GroupOverviewScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToSession = { groupId, sessionId ->
                    navController.navigate(Screen.SessionReport.createRoute(groupId, sessionId))
                },
                onNavigateToCreateSession = { navController.navigate(Screen.CreateEvent.createRoute()) }
            )
        }

        composable(
            route = Screen.SessionReport.route,
            arguments = listOf(
                navArgument("groupId") { type = NavType.StringType },
                navArgument("sessionId") { type = NavType.StringType }
            )
        ) {
            SessionReportScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToAthlete = { individualId, sessionId ->
                    navController.navigate(Screen.AthleteDashboard.createRoute(individualId, sessionId))
                },
                onResumeTesting = { eventId, groupId, athleteId, testIds ->
                    if (groupId != null) {
                        navController.navigate(Screen.TestingGrid.createRoute(eventId, groupId = groupId))
                    } else {
                        navController.navigate(Screen.QuickTest.createRoute(athleteId = athleteId, testIds = testIds ?: emptyList(), eventId = eventId))
                    }
                },
                onNavigateToAiCoach = { contextString -> navController.navigate(Screen.AiCoach.createRoute(contextString)) }
            )
        }

        composable(
            route = Screen.AthleteDashboard.route,
            arguments = listOf(
                navArgument("athleteId") { type = NavType.StringType },
                navArgument("contextSessionId") { type = NavType.StringType; nullable = true; defaultValue = null }
            )
        ) { backStackEntry ->
            val athleteId = backStackEntry.arguments?.getString("athleteId") ?: ""
            val contextSessionId = backStackEntry.arguments?.getString("contextSessionId")
            AthleteDashboardScreen(
                athleteId = athleteId,
                contextSessionId = contextSessionId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToTest = { aId, tId, cId ->
                    navController.navigate(Screen.AthleteTestDetail.createRoute(aId, tId, cId))
                },
                onStartQuickTest = { aId, testIds ->
                    navController.navigate(Screen.QuickTest.createRoute(aId, testIds))
                },
                onNavigateToAiCoach = { contextData ->
                    if (contextData != null) {
                        navController.currentBackStackEntry?.savedStateHandle?.set("ai_coach_context", contextData)
                    }
                    navController.navigate(Screen.AiCoach.route)
                }
            )
        }

        composable(
            route = Screen.AthleteTestDetail.route,
            arguments = listOf(
                navArgument("athleteId") { type = NavType.StringType },
                navArgument("testId") { type = NavType.StringType },
                navArgument("contextSessionId") { type = NavType.StringType; nullable = true; defaultValue = null }
            )
        ) { backStackEntry ->
            val athleteId = backStackEntry.arguments?.getString("athleteId") ?: ""
            val testId = backStackEntry.arguments?.getString("testId") ?: ""
            val contextSessionId = backStackEntry.arguments?.getString("contextSessionId")
            AthleteTestDetailScreen(
                athleteId = athleteId,
                testId = testId,
                contextSessionId = contextSessionId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // ───── Live Testing layer ─────

        composable(
            route = Screen.TestingGrid.route,
            arguments = listOf(
                navArgument("eventId") { type = NavType.StringType },
                navArgument("groupId") { type = NavType.StringType }
            )
        ) {
            TestingGridScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToStopwatch = { eventId, testId, groupId, athleteId, timingMode ->
                    navController.navigate(Screen.Stopwatch.createRoute(eventId, testId, groupId, athleteId, timingMode))
                },
                onNavigateToLeaderboard = { eventId, groupId, mode ->
                    navController.navigate(Screen.Leaderboard.createRoute(eventId, groupId, mode))
                },
                onNavigateToAthleteReport = { athleteId ->
                    navController.navigate(Screen.AthleteDashboard.createRoute(athleteId))
                },
                onNavigateToGroupReport = { eventId, groupId ->
                    navController.navigate(Screen.SessionReport.createRoute(groupId, eventId))
                }
            )
        }

        composable(
            route = Screen.Stopwatch.route,
            arguments = listOf(
                navArgument("eventId") { type = NavType.StringType },
                navArgument("fitnessTestId") { type = NavType.StringType },
                navArgument("groupId") { type = NavType.StringType },
                navArgument("athleteId") { type = NavType.StringType; nullable = true },
                navArgument("timingMode") { type = NavType.StringType; nullable = true }
            )
        ) {
            StopwatchScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.Leaderboard.route,
            arguments = listOf(
                navArgument("eventId") { type = NavType.StringType },
                navArgument("groupId") { type = NavType.StringType },
                navArgument("mode") { type = NavType.StringType }
            )
        ) {
            LeaderboardScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.AiCoach.route,
            arguments = listOf(navArgument("context") { nullable = true; type = NavType.StringType })
        ) {
            AiCoachScreen(
                viewModel = hiltViewModel(),
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
