package com.vamshi.field.ui.testlibrary

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.vamshi.field.domain.model.standards.FitnessTest
import com.vamshi.field.domain.model.standards.TestSource
import com.vamshi.field.ui.components.AppTopBar
import com.vamshi.field.ui.components.video.TestVideoPreview
import com.vamshi.field.ui.components.video.VideoPlayerModal
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import com.vamshi.field.ui.components.testing.CategoryAccordionHeader
import com.vamshi.field.ui.theme.*
import com.vamshi.field.ui.theme.getCategoryVisual
import com.vamshi.field.ui.util.youtubeThumbnailUrl

@Composable
fun TestLibraryScreen(
    onNavigateBack: () -> Unit,
    onNavigateToEditTest: (testId: String) -> Unit = {},
    viewModel: TestLibraryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    TestLibraryContent(
        uiState = uiState,
        onAction = { action ->
            when (action) {
                is TestLibraryAction.OnNavigateBack -> onNavigateBack()
                is TestLibraryAction.OnEditTest -> onNavigateToEditTest(action.testId)
                else -> viewModel.onAction(action)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestLibraryContent(
    uiState: TestLibraryUiState,
    onAction: (TestLibraryAction) -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            AppTopBar(
                title = "Tests Library",
                navigationIcon = {
                    IconButton(onClick = { onAction(TestLibraryAction.OnNavigateBack) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        when {
            uiState.isLoading -> LoadingState()
            uiState.errorMessage != null -> ErrorState(
                message = uiState.errorMessage,
                onDismiss = { onAction(TestLibraryAction.OnDismissError) }
            )
            uiState.categories.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No fitness tests available")
                }
            }
            else -> {
                TestLibraryBody(uiState = uiState, onAction = onAction, padding = padding)
            }
        }
    }

    uiState.deleteDialog?.let { dialog ->
        DeleteTestDialog(dialog = dialog, onAction = onAction)
    }

    if (uiState.snackbarMessage != null) {
        Snackbar(
            modifier = Modifier.padding(16.dp),
            action = {
                TextButton(onClick = { onAction(TestLibraryAction.OnDismissSnackbar) }) {
                    Text("OK")
                }
            }
        ) {
            Text(uiState.snackbarMessage)
        }
    }
}

/**
 * Confirmation dialog for archiving or deleting a custom test.
 */
@Composable
private fun DeleteTestDialog(
    dialog: DeleteTestDialogState,
    onAction: (TestLibraryAction) -> Unit
) {
    val archiving = dialog.resultCount > 0
    AlertDialog(
        onDismissRequest = { onAction(TestLibraryAction.OnDismissDeleteDialog) },
        title = { Text(if (archiving) "Archive this test?" else "Delete this test?") },
        text = {
            Text(
                if (archiving) {
                    val results = "${dialog.resultCount} recorded " +
                        if (dialog.resultCount == 1) "result" else "results"
                    "\"${dialog.testName}\" has $results. Archiving keeps that history in " +
                        "reports but removes the test from the library and new events."
                } else {
                    "\"${dialog.testName}\" has no recorded results and will be removed " +
                        "permanently, along with its performance bands."
                }
            )
        },
        confirmButton = {
            TextButton(onClick = { onAction(TestLibraryAction.OnConfirmDeleteTest) }) {
                Text(
                    if (archiving) "Archive" else "Delete",
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(onClick = { onAction(TestLibraryAction.OnDismissDeleteDialog) }) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun LoadingState(message: String = "Loading...") {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.dp
            )
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ErrorState(
    message: String,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Default.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Text(
                message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
            OutlinedButton(onClick = onDismiss) {
                Text("Dismiss")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun TestLibraryBody(
    uiState: TestLibraryUiState,
    onAction: (TestLibraryAction) -> Unit,
    padding: PaddingValues
) {
    val navigator = rememberListDetailPaneScaffoldNavigator<Any>()
    var activeVideoModal by remember { mutableStateOf<Pair<String, String>?>(null) }

    BackHandler(navigator.canNavigateBack()) {
        navigator.navigateBack()
    }

    NavigableListDetailPaneScaffold(
        modifier = Modifier.padding(padding),
        navigator = navigator,
        listPane = {
            AnimatedPane {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    uiState.categories.forEach { category ->
                        val isExpanded = category.id == uiState.expandedCategoryId
                        val categoryTests = uiState.allTests.filter { it.categoryId == category.id }
                        val visual = getCategoryVisual(category.name, category.radarAxis)

                        item(key = "category_${category.id}") {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = if (isExpanded) {
                                        visual.accentColor.copy(alpha = 0.35f)
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.50f)
                                    }
                                ),
                                shadowElevation = if (isExpanded) 1.5.dp else 0.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    CategoryAccordionHeader(
                                        name = category.name,
                                        radarAxis = category.radarAxis,
                                        totalCount = categoryTests.size,
                                        isExpanded = isExpanded,
                                        onClick = { onAction(TestLibraryAction.OnToggleCategoryExpanded(category.id)) },
                                        isDocked = true
                                    )

                                    if (isExpanded) {
                                        HorizontalDivider(
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                            thickness = 1.dp
                                        )
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            categoryTests.forEach { test ->
                                                NetflixTestCard(
                                                    test = test,
                                                    onClick = {
                                                        navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, test.id)
                                                    },
                                                    onPlayVideo = { videoId, title ->
                                                        activeVideoModal = Pair(videoId, title)
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        detailPane = {
            AnimatedPane {
                val testId = navigator.currentDestination?.content as? String
                val selectedTest = remember(testId, uiState.allTests) {
                    uiState.allTests.find { it.id == testId }
                }
                if (selectedTest != null) {
                    TestDetailPane(test = selectedTest, onAction = onAction)
                } else {
                    EmptyDetailPane()
                }
            }
        }
    )

    activeVideoModal?.let { (videoId, videoTitle) ->
        VideoPlayerModal(
            youtubeId = videoId,
            videoTitle = videoTitle,
            onDismiss = { activeVideoModal = null }
        )
    }
}

/**
 * Netflix-style Full-Bleed Media Card.
 * Overlays all typography directly over a dark multi-stop gradient without a white footer.
 */
@Composable
private fun NetflixTestCard(
    test: FitnessTest,
    onClick: () -> Unit,
    onPlayVideo: (youtubeId: String, title: String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp, pressedElevation = 4.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(190.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 1. Full-bleed Image Thumbnail or Branded Gradient
            if (test.youtubeId != null) {
                AsyncImage(
                    model = youtubeThumbnailUrl(test.youtubeId),
                    contentDescription = test.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                                    MaterialTheme.colorScheme.surfaceVariant
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.FitnessCenter,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                        modifier = Modifier.size(48.dp)
                    )
                }
            }

            // 2. Dark Multi-Stop Gradient Scrim for Legibility
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to Color.Transparent,
                                0.35f to Color.Black.copy(alpha = 0.20f),
                                0.65f to Color.Black.copy(alpha = 0.65f),
                                1.0f to Color.Black.copy(alpha = 0.90f)
                            )
                        )
                    )
            )

            // 3. Top Row: Custom Badge & Video Guide Pill
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (test.source == TestSource.USER) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.90f)
                    ) {
                        Text(
                            text = "CUSTOM",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                if (test.youtubeId != null) {
                    Surface(
                        onClick = { onPlayVideo(test.youtubeId, test.name) },
                        shape = RoundedCornerShape(20.dp),
                        color = Color.Black.copy(alpha = 0.65f),
                        contentColor = Color.White
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = "Play video guide",
                                modifier = Modifier.size(13.dp),
                                tint = Color.White
                            )
                            Text(
                                text = "Video Guide",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            // 4. Subtle Center Play Button (for cards with video)
            if (test.youtubeId != null) {
                Surface(
                    onClick = { onPlayVideo(test.youtubeId, test.name) },
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.22f),
                    modifier = Modifier
                        .size(38.dp)
                        .align(Alignment.Center)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.92f),
                            modifier = Modifier.size(30.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = "Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 5. Bottom Overlay: Primary Test Title & Secondary Performance Indicator
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = test.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val indicatorText = if (test.isHigherBetter) "↑ Higher is Better" else "↓ Lower is Better"
                    val indicatorColor = if (test.isHigherBetter) Color(0xFF81C784) else Color(0xFFFF8A80)

                    Text(
                        text = indicatorText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = indicatorColor
                    )

                    Text(
                        text = "•",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.5f)
                    )

                    Text(
                        text = test.unit,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

/** Small tinted chip marking a coach-authored test in the detail pane. */
@Composable
private fun CustomBadge() {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    ) {
        Text(
            "Custom",
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun TestDetailPane(test: FitnessTest, onAction: (TestLibraryAction) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = test.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (test.source == TestSource.USER) {
                CustomBadge()
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    "Unit: ${test.unit}",
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val isDark = isSystemInDarkTheme()
            val badgeColor = if (test.isHigherBetter) {
                if (isDark) PerformanceGreenDark else PerformanceGreen
            } else {
                if (isDark) PerformanceRedDark else PerformanceRed
            }
            val textColor = if (test.isHigherBetter) {
                if (isDark) PerformanceGreenTextDark else PerformanceGreenText
            } else {
                if (isDark) PerformanceRedTextDark else PerformanceRedText
            }
            Surface(
                color = badgeColor,
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    if (test.isHigherBetter) "Higher is better" else "Lower is better",
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        // Seeded tests are read-only; only coach-authored tests can be edited or removed.
        if (test.source == TestSource.USER) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { onAction(TestLibraryAction.OnEditTest(test.id)) },
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Edit")
                }
                OutlinedButton(
                    onClick = { onAction(TestLibraryAction.OnRequestDeleteTest(test.id)) },
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Remove")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        test.description?.let { desc ->
            Text(
                text = desc,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        test.youtubeId?.let { youtubeId ->
            TestVideoPreview(
                youtubeId = youtubeId,
                testName = test.name,
                height = 200.dp,
                cornerShape = RoundedCornerShape(16.dp)
            )
        }
    }
}

@Composable
private fun EmptyDetailPane() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Select a test to view details", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
