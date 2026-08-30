package com.vamshi.field.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Groups
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String) {
    data object Dashboard : Screen("dashboard")
    data object Roster : Screen("roster")
    data object Athletes : Screen("athletes")
    data object Insights : Screen("insights")
    data object TestLibrary : Screen("test_library")
    data object Recommendations : Screen("recommendations")
    data object CreateEvent : Screen("create_event?recommendationId={recommendationId}") {
        fun createRoute(recommendationId: String? = null): String =
            "create_event" + if (recommendationId != null) "?recommendationId=$recommendationId" else ""
    }
    data object QuickTest : Screen("quick_test?athleteId={athleteId}&testIds={testIds}&eventId={eventId}&mode={mode}") {
        fun createRoute(athleteId: String? = null, testIds: List<String> = emptyList(), eventId: String? = null, mode: String? = null): String {
            val params = buildList {
                if (athleteId != null) add("athleteId=$athleteId")
                if (testIds.isNotEmpty()) add("testIds=${testIds.joinToString(",")}")
                if (eventId != null) add("eventId=$eventId")
                if (mode != null) add("mode=$mode")
            }
            return "quick_test" + if (params.isEmpty()) "" else "?" + params.joinToString("&")
        }
    }
    data object Report : Screen("reports")
    data object Leaderboard : Screen("leaderboard/{eventId}/{groupId}/{mode}") {
        fun createRoute(eventId: String, groupId: String, mode: String) =
            "leaderboard/$eventId/$groupId/$mode"
    }

    data object TestingGrid : Screen("testing_grid/{eventId}/{groupId}") {
        fun createRoute(eventId: String, groupId: String) = "testing_grid/$eventId/$groupId"
    }

    data object Stopwatch : Screen("stopwatch/{eventId}/{fitnessTestId}/{groupId}?athleteId={athleteId}&timingMode={timingMode}") {
        fun createRoute(
            eventId: String,
            fitnessTestId: String,
            groupId: String,
            athleteId: String? = null,
            timingMode: String? = null
        ): String {
            val params = mutableListOf<String>()
            if (athleteId != null) params.add("athleteId=$athleteId")
            if (timingMode != null) params.add("timingMode=$timingMode")
            val query = if (params.isNotEmpty()) "?" + params.joinToString("&") else ""
            return "stopwatch/$eventId/$fitnessTestId/$groupId$query"
        }
    }

    data object TestsHub : Screen("tests_hub")

    /** Custom Test Builder. No `testId` = create; with one = edit that coach-authored test. */
    data object CustomTest : Screen("custom_test?testId={testId}") {
        fun createRoute(testId: String? = null): String =
            "custom_test" + if (testId != null) "?testId=$testId" else ""
    }
    
    // Reports & Results layer
    data object GroupOverview : Screen("group/{groupId}") {
        fun createRoute(groupId: String) = "group/$groupId"
    }

    data object SessionReport : Screen("group/{groupId}/session/{sessionId}") {
        fun createRoute(groupId: String, sessionId: String) = "group/$groupId/session/$sessionId"
    }

    data object AthleteDashboard : Screen("athlete/{athleteId}?contextSessionId={contextSessionId}") {
        fun createRoute(athleteId: String, contextSessionId: String? = null) =
            "athlete/${android.net.Uri.encode(athleteId)}" + if (contextSessionId != null) "?contextSessionId=${android.net.Uri.encode(contextSessionId)}" else ""
    }

    data object AthleteTestDetail : Screen("athlete/{athleteId}/test/{testId}?contextSessionId={contextSessionId}") {
        fun createRoute(athleteId: String, testId: String, contextSessionId: String? = null) =
            "athlete/${android.net.Uri.encode(athleteId)}/test/${android.net.Uri.encode(testId)}" + if (contextSessionId != null) "?contextSessionId=${android.net.Uri.encode(contextSessionId)}" else ""
    }

    // ----- Auth screens -----
    /** First-launch account creation: Coach Name + Password (+ optional Email). Replaces SignUp. */
    data object Onboarding : Screen("onboarding")
    /** Returning-coach "Welcome back" password unlock. Replaces SignIn. */
    data object Unlock : Screen("unlock")
    /** Pre-auth Google Drive restore, reachable from both Onboarding and Unlock. Replaces ResetPassword. */
    data object RestoreBackup : Screen("restore_backup")
    
    data object AiCoach : Screen("ai_coach?context={context}") {
        fun createRoute(contextString: String?) = if (contextString != null) "ai_coach?context=${android.net.Uri.encode(contextString)}" else "ai_coach"
    }

    data object Settings : Screen("settings")
}

sealed class BottomNavItem(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    data object Home : BottomNavItem(
        route = Screen.Dashboard.route,
        title = "Home",
        icon = Icons.Default.Home
    )

    data object Roster : BottomNavItem(
        route = Screen.Roster.route,
        title = "Roster",
        icon = Icons.Default.Groups
    )

    data object Tests : BottomNavItem(
        route = Screen.TestLibrary.route,
        title = "Tests",
        icon = Icons.AutoMirrored.Filled.LibraryBooks
    )

    data object Reports : BottomNavItem(
        route = Screen.Report.route,
        title = "Reports",
        icon = Icons.AutoMirrored.Filled.TrendingUp
    )
}
