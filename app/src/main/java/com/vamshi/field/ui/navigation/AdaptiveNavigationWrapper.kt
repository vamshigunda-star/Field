package com.vamshi.field.ui.navigation

import android.app.Activity
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import com.vamshi.field.ui.theme.SportOrange

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun AdaptiveNavigationWrapper(
    navController: NavController,
    content: @Composable (Modifier) -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) {
        var c = context
        while (c is android.content.ContextWrapper) {
            if (c is Activity) break
            c = c.baseContext
        }
        c as? Activity
    }

    if (activity == null) {
        content(Modifier)
        return
    }

    val windowSizeClass = calculateWindowSizeClass(activity)
    val useNavRail = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact

    val items = listOf(
        BottomNavItem.Home,
        BottomNavItem.Roster,
        BottomNavItem.Tests,
        BottomNavItem.Reports
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentRoute = currentDestination?.route

    // Check if the current route is one of the main tabs
    val isMainTab = items.any { it.route == currentRoute }

    Log.d("AdaptiveNavigationWrapper", "isMainTab: $isMainTab, currentRoute: $currentRoute")

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (isMainTab && !useNavRail) {
                Surface(
                    modifier = Modifier
                        .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                        .navigationBarsPadding(),
                    shape = RoundedCornerShape(28.dp),
                    color = Color.White,
                    shadowElevation = 8.dp,
                    border = BorderStroke(1.dp, Color(0xFFE5E7EB))
                ) {
                    NavigationBar(
                        containerColor = Color.Transparent,
                        contentColor = Color.Gray,
                        tonalElevation = 0.dp
                    ) {
                        items.forEach { screen ->
                            val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                            NavigationBarItem(
                                icon = { Icon(screen.icon, contentDescription = screen.title) },
                                label = { Text(screen.title) },
                                selected = selected,
                                onClick = {
                                    if (currentRoute != screen.route) {
                                        navController.navigate(screen.route) {
                                            popUpTo(navController.graph.findStartDestination().id)
                                            launchSingleTop = true
                                        }
                                    }
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = SportOrange,
                                    selectedTextColor = SportOrange,
                                    unselectedIconColor = Color.Gray,
                                    unselectedTextColor = Color.Gray,
                                    indicatorColor = SportOrange.copy(alpha = 0.12f)
                                )
                            )
                        }
                    }
                }
            }
        }
    )
{ innerPadding ->
        if (useNavRail && isMainTab) {
            Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                NavigationRail(
                    containerColor = Color.White,
                    contentColor = Color.Gray
                ) {
                    items.forEach { screen ->
                        val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                        NavigationRailItem(
                            icon = { Icon(screen.icon, contentDescription = screen.title) },
                            label = { Text(screen.title) },
                            selected = selected,
                            onClick = {
                                if (currentRoute != screen.route) {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id)
                                        launchSingleTop = true
                                    }
                                }
                            },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = SportOrange,
                                selectedTextColor = SportOrange,
                                unselectedIconColor = Color.Gray,
                                unselectedTextColor = Color.Gray,
                                indicatorColor = Color.Transparent
                            )
                        )
                    }
                }
                content(Modifier)
            }
        } else {
            content(Modifier.padding(innerPadding))
        }
    }
}
