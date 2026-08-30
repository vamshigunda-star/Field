package com.vamshi.field.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.TopAppBarScrollBehavior

/**
 * Standard top bar for every screen in ALearning.
 *
 * Visual contract (do not override per-screen):
 *  - Left-aligned TopAppBar on a clean surface that dynamically tints when content scrolls under
 *  - Title: typography.titleLarge (22sp SemiBold)
 *  - Container: colorScheme.surface; scrolledContainer: colorScheme.surfaceContainer
 *  - Trailing actions use [AppTopBarActionButton] for the rounded,
 *    tinted icon-container treatment instead of a bare IconButton.
 *
 * Use the [navigationIcon] slot for a back button on detail screens.
 * Use the [actions] slot for trailing action buttons.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    AppTopBar(
        modifier = modifier,
        scrollBehavior = null,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        },
        navigationIcon = navigationIcon,
        actions = actions,
    )
}

/**
 * Overload of [AppTopBar] supporting [TopAppBarScrollBehavior] for collapsible / dynamic scroll tinting.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    scrollBehavior: TopAppBarScrollBehavior?,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    AppTopBar(
        modifier = modifier,
        scrollBehavior = scrollBehavior,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        },
        navigationIcon = navigationIcon,
        actions = actions,
    )
}

/**
 * Composable-title overload for screens that need a multi-line title
 * (e.g. screen heading + subtitle like a date or group name).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    AppTopBar(
        modifier = modifier,
        scrollBehavior = null,
        title = title,
        navigationIcon = navigationIcon,
        actions = actions,
    )
}

/**
 * Composable-title overload supporting [TopAppBarScrollBehavior].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    modifier: Modifier = Modifier,
    scrollBehavior: TopAppBarScrollBehavior?,
    title: @Composable () -> Unit,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        modifier = modifier,
        title = title,
        navigationIcon = navigationIcon,
        actions = actions,
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
            actionIconContentColor = MaterialTheme.colorScheme.onSurface,
        ),
    )
}

/** Subtitle text color to use beneath the main title when the content-slot overload is used. */
val AppTopBarSubtitleColor: Color
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.onSurfaceVariant

/**
 * Trailing top-bar action styled per the design system: an icon inside a
 * rounded, tinted container rather than a bare [IconButton].
 */
@Composable
fun AppTopBarActionButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .padding(4.dp)
            .background(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(12.dp),
            ),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}
