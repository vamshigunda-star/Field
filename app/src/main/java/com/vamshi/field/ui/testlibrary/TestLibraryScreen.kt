package com.vamshi.field.ui.testlibrary

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vamshi.field.domain.model.standards.FitnessTest
import com.vamshi.field.domain.model.standards.TestSource
import com.vamshi.field.ui.components.AppTopBar
import com.vamshi.field.ui.components.CategoryDescription
import com.vamshi.field.ui.components.testing.CategoryAccordionHeader
import com.vamshi.field.ui.components.video.TestVideoPreview

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
                    modifier = Modifier.fillMaxSize().padding(padding),
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
 * Copy states plainly what confirming does. With results attached the row can't be hard
 * deleted (test_results holds a RESTRICT foreign key), so it archives: history stays,
 * the test leaves the library and event setup.
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
        modifier = Modifier.fillMaxSize().padding(32.dp),
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
    // NavigableListDetailPaneScaffold requires a ThreePaneScaffoldNavigator<Any>; the
    // destination content is always a test id (String) — see the detail pane read below.
    val navigator = rememberListDetailPaneScaffoldNavigator<Any>()

    BackHandler(navigator.canNavigateBack()) {
        navigator.navigateBack()
    }

    NavigableListDetailPaneScaffold(
        modifier = Modifier.padding(padding),
        navigator = navigator,
        listPane = {
            AnimatedPane {
                Column(modifier = Modifier.fillMaxSize()) {
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = { onAction(TestLibraryAction.OnSearchQueryChanged(it)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        placeholder = { Text("Search tests...", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        shape = RoundedCornerShape(20.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedBorderColor = MaterialTheme.colorScheme.primary
                        ),
                        singleLine = true
                    )

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        uiState.categories.forEach { category ->
                            val isExpanded = category.id == uiState.expandedCategoryId
                            val categoryTests = uiState.allTests.filter { it.categoryId == category.id }
                            val filteredTests = if (uiState.searchQuery.isBlank()) {
                                categoryTests
                            } else {
                                categoryTests.filter { it.name.contains(uiState.searchQuery, ignoreCase = true) }
                            }

                            item(key = "category_${category.id}") {
                                CategoryAccordionHeader(
                                    name = category.name,
                                    totalCount = categoryTests.size,
                                    isExpanded = isExpanded,
                                    onClick = { onAction(TestLibraryAction.OnToggleCategoryExpanded(category.id)) }
                                )
                            }

                            if (isExpanded) {
                                item(key = "description_${category.id}") {
                                    CategoryDescription(description = category.description)
                                }
                                items(filteredTests, key = { it.id }) { test ->
                                    TestListCard(
                                        test = test,
                                        categoryName = category.name,
                                        onClick = { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, test.id) },
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
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
}

@Composable
private fun TestListCard(test: FitnessTest, categoryName: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column {
            // Netflix-style Top Video / Athletic Hero Thumbnail
            TestVideoPreview(
                youtubeId = test.youtubeId,
                testName = test.name,
                height = 135.dp,
                cornerShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
            )

            // Card Body (Clickable to open test detail)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
                    .padding(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        test.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (test.source == TestSource.USER) {
                        CustomBadge()
                    }
                }

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = "$categoryName • ${test.unit}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val badgeColor = if (test.isHigherBetter) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                    val textColor = if (test.isHigherBetter) Color(0xFF2E7D32) else Color(0xFFC62828)
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

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            "View Details",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = "View Details",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

/** Small tinted chip marking a coach-authored test in lists and the detail pane. */
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

            val badgeColor = if (test.isHigherBetter) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
            val textColor = if (test.isHigherBetter) Color(0xFF2E7D32) else Color(0xFFC62828)
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
