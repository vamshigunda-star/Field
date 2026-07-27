package com.vamshi.field.ui.recommendations

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vamshi.field.domain.model.standards.FitnessTest
import com.vamshi.field.ui.components.AppTopBar
import com.vamshi.field.ui.components.CategoryDescription
import com.vamshi.field.ui.components.testing.CategoryAccordionHeader
import com.vamshi.field.ui.components.video.TestVideoPreview

@Composable
fun RecommendationsScreen(
    onNavigateBack: () -> Unit,
    onApplyAndContinue: (recommendationId: String) -> Unit,
    viewModel: RecommendationsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    RecommendationsContent(
        uiState = uiState,
        onAction = { action ->
            when (action) {
        is RecommendationsAction.OnNavigateBack -> onNavigateBack()
                is RecommendationsAction.OnApplyAndContinue -> {
                    uiState.selectedCategory?.let { onApplyAndContinue(it.id) }
                }
                else -> viewModel.onAction(action)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecommendationsContent(
    uiState: RecommendationsUiState,
    onAction: (RecommendationsAction) -> Unit
) {
    Scaffold(
        topBar = {
            AppTopBar(
                title = "Recommendations",
                navigationIcon = {
                    IconButton(onClick = { onAction(RecommendationsAction.OnNavigateBack) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 8.dp,
                shadowElevation = 8.dp
            ) {
                Button(
                    onClick = { onAction(RecommendationsAction.OnApplyAndContinue) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .height(56.dp),
                    enabled = uiState.selectedCategory != null && uiState.recommendedTests.isNotEmpty(),
                    shape = RoundedCornerShape(28.dp)
                ) {
                    Text(
                        "Apply & Continue (${uiState.recommendedTests.size} tests)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    ) { padding ->
        when {
            uiState.isLoading -> LoadingState(Modifier.padding(padding))
            uiState.errorMessage != null -> ErrorState(
                message = uiState.errorMessage,
                modifier = Modifier.padding(padding),
                onDismiss = { onAction(RecommendationsAction.OnDismissError) }
            )
            uiState.categories.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No recommendation categories available")
                }
            }
            else -> {
                RecommendationsBody(uiState = uiState, onAction = onAction, padding = padding)
            }
        }
    }
}

@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 3.dp)
            Text(
                "Loading recommendations...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ErrorState(message: String, modifier: Modifier = Modifier, onDismiss: () -> Unit) {
    Box(modifier = modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
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

@Composable
private fun EmptyRecommendationsState(categoryName: String?, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            Icons.Default.Inbox,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "No recommended tests for ${categoryName ?: "this category"} yet.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun RecommendationsBody(
    uiState: RecommendationsUiState,
    onAction: (RecommendationsAction) -> Unit,
    padding: PaddingValues
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = padding.calculateTopPadding() + 16.dp,
            end = 16.dp,
            bottom = padding.calculateBottomPadding() + 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        uiState.categories.forEach { category ->
            val isExpanded = category.id == uiState.selectedCategory?.id

            item(key = "category_${category.id}") {
                CategoryAccordionHeader(
                    name = category.name,
                    totalCount = uiState.recommendedTests.size.takeIf { isExpanded },
                    isExpanded = isExpanded,
                    onClick = { onAction(RecommendationsAction.OnToggleCategory(category)) },
                    subtitle = "Tap to view recommended tests".takeIf { !isExpanded }
                )
            }

            if (isExpanded) {
                item(key = "description_${category.id}") {
                    CategoryDescription(description = category.description)
                }
                if (uiState.recommendedTests.isEmpty()) {
                    item(key = "empty_${category.id}") {
                        EmptyRecommendationsState(category.name)
                    }
                } else {
                    items(uiState.recommendedTests, key = { it.id }) { test ->
                        RecommendedTestPreviewCard(test = test, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun RecommendedTestPreviewCard(test: FitnessTest, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column {
            TestVideoPreview(
                youtubeId = test.youtubeId,
                testName = test.name,
                height = 200.dp
            )

            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    test.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = test.unit,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (test.isHigherBetter) Icons.AutoMirrored.Filled.TrendingUp
                        else Icons.AutoMirrored.Filled.TrendingDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        if (test.isHigherBetter) "Higher is better" else "Lower is better",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
