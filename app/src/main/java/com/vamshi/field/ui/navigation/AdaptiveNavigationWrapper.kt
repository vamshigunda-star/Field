package com.vamshi.field.ui.navigation

import android.app.Activity
import android.util.Log
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (isMainTab && !useNavRail) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .widthIn(max = 560.dp)
                            .height(68.dp),
                        shape = RoundedCornerShape(34.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 6.dp,
                        tonalElevation = 2.dp,
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            items.forEach { screen ->
                                val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                                FloatingBottomNavItem(
                                    screen = screen,
                                    selected = selected,
                                    onClick = {
                                        if (currentRoute != screen.route) {
                                            navController.navigate(screen.route) {
                                                popUpTo(navController.graph.findStartDestination().id) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        if (useNavRail && isMainTab) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding)
            ) {
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
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
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = SportOrange,
                                selectedTextColor = SportOrange,
                                indicatorColor = SportOrange.copy(alpha = 0.14f),
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
                content(Modifier)
            }
        } else {
            content(
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding)
            )
        }
    }
}

@Composable
private fun FloatingBottomNavItem(
    screen: BottomNavItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val motionSpec = tween<Color>(durationMillis = 250, easing = FastOutSlowInEasing)

    val iconColor by animateColorAsState(
        targetValue = if (selected) {
            SportOrange
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
        },
        animationSpec = motionSpec,
        label = "iconColor"
    )

    val indicatorColor by animateColorAsState(
        targetValue = if (selected) {
            SportOrange.copy(alpha = 0.14f)
        } else {
            Color.Transparent
        },
        animationSpec = motionSpec,
        label = "indicatorColor"
    )

    val textColor by animateColorAsState(
        targetValue = if (selected) {
            SportOrange
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
        },
        animationSpec = motionSpec,
        label = "textColor"
    )

    Box(
        modifier = modifier
            .fillMaxHeight()
            .padding(vertical = 5.dp, horizontal = 3.dp)
            .background(color = indicatorColor, shape = RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(
                    bounded = true,
                    color = SportOrange
                ),
                role = Role.Tab,
                onClick = onClick
            )
            .semantics {
                this.role = Role.Tab
                this.selected = selected
                this.stateDescription = if (selected) "Selected" else "Not selected"
                this.contentDescription = "${screen.title} tab${if (selected) ", selected" else ""}"
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Icon(
                imageVector = screen.icon,
                contentDescription = null, // Handled by parent tab semantics
                modifier = Modifier.size(22.dp),
                tint = iconColor
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = screen.title,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 11.5.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                ),
                color = textColor,
                maxLines = 1
            )
        }
    }
}

