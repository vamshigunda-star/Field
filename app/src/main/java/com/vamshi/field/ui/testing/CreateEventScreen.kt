package com.vamshi.field.ui.testing

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vamshi.field.domain.model.standards.TestPreset
import com.vamshi.field.ui.components.AppTopBar
import com.vamshi.field.ui.components.testing.CategoryAccordionHeader
import com.vamshi.field.ui.components.testing.TestSelectionCard
import com.vamshi.field.ui.components.testing.TestSelectionRow
import com.vamshi.field.ui.components.tour.CoachMarkBanner
import com.vamshi.field.ui.components.tour.TestingTourDialog
import com.vamshi.field.ui.theme.*

@Composable
fun CreateEventScreen(
    onNavigateBack: () -> Unit,
    onEventCreated: (eventId: String, groupId: String) -> Unit,
    viewModel: CreateEventViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showTestingTour by remember { mutableStateOf(false) }

    // Handle navigation event
    LaunchedEffect(uiState.eventCreated) {
        uiState.eventCreated?.let { (eventId, groupId) ->
            onEventCreated(eventId, groupId)
            viewModel.onAction(CreateEventAction.NavigationConsumed)
        }
    }

    CreateEventContent(
        uiState = uiState,
        onOpenTestingTour = { showTestingTour = true },
        onAction = { action ->
            when (action) {
                is CreateEventAction.NavigateBack -> onNavigateBack()
                else -> viewModel.onAction(action)
            }
        }
    )

    if (showTestingTour) {
        TestingTourDialog(
            onDismiss = { showTestingTour = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateEventContent(
    uiState: CreateEventUiState,
    onOpenTestingTour: () -> Unit = {},
    onAction: (CreateEventAction) -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(
                title = "Create Testing Event",
                navigationIcon = {
                    IconButton(onClick = { onAction(CreateEventAction.NavigateBack) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenTestingTour) {
                        Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = "Testing Guide")
                    }
                }
            )
        },
        bottomBar = {
            val isReady = uiState.eventName.isNotBlank() &&
                    uiState.selectedGroupId != null &&
                    uiState.selectedTestIds.isNotEmpty()

            val gradientBrush = if (isReady && !uiState.isCreating) {
                Brush.horizontalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                    )
                )
            } else {
                SolidColor(MaterialTheme.colorScheme.surfaceVariant)
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .shadow(
                                elevation = if (isReady && !uiState.isCreating) 6.dp else 0.dp,
                                shape = RoundedCornerShape(16.dp),
                                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                            ),
                        shape = RoundedCornerShape(16.dp),
                        color = Color.Transparent
                    ) {
                        Button(
                            onClick = { onAction(CreateEventAction.CreateEvent) },
                            modifier = Modifier
                                .fillMaxSize()
                                .background(gradientBrush),
                            enabled = isReady && !uiState.isCreating,
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                contentColor = Color.White,
                                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            if (uiState.isCreating) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = Color.White,
                                    strokeWidth = 2.5.dp
                                )
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        "Start Group Testing",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isReady) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (uiState.selectedTestIds.isNotEmpty()) {
                                        Spacer(Modifier.width(8.dp))
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = if (isReady) Color.White.copy(alpha = 0.25f) else MaterialTheme.colorScheme.outlineVariant
                                        ) {
                                            Text(
                                                "${uiState.selectedTestIds.size} Selected",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isReady) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
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
    ) { padding ->
        when {
            uiState.isLoading -> LoadingState()
            uiState.errorMessage != null && uiState.groups.isEmpty() -> InternalErrorState(
                message = uiState.errorMessage,
                onDismiss = { onAction(CreateEventAction.ClearError) }
            )
            else -> {
                CreateEventBody(uiState = uiState, onAction = onAction, padding = padding)
            }
        }
    }

    // Inline error snackbar for validation errors
    if (uiState.errorMessage != null && uiState.groups.isNotEmpty()) {
        Snackbar(
            modifier = Modifier.padding(16.dp),
            action = {
                TextButton(onClick = { onAction(CreateEventAction.ClearError) }) {
                    Text("Dismiss")
                }
            }
        ) {
            Text(uiState.errorMessage)
        }
    }

    if (uiState.isSavePresetDialogOpen) {
        SavePresetDialog(
            name = uiState.pendingPresetName,
            selectedCount = uiState.selectedTestIds.size,
            onNameChange = { onAction(CreateEventAction.SetPendingPresetName(it)) },
            onConfirm = { onAction(CreateEventAction.ConfirmSavePreset) },
            onDismiss = { onAction(CreateEventAction.DismissSavePresetDialog) }
        )
    }
}

@Composable
private fun SavePresetDialog(
    name: String,
    selectedCount: Int,
    onNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save as custom list") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Name this selection of $selectedCount tests so you can reuse it for future testing events.",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    label = { Text("List name") },
                    placeholder = { Text("Combine Day 1") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = name.isNotBlank()) {
                Text("Save list")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
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
private fun InternalErrorState(
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateEventBody(
    uiState: CreateEventUiState,
    onAction: (CreateEventAction) -> Unit,
    padding: PaddingValues
) {
    var groupDropdownExpanded by remember { mutableStateOf(false) }
    val selectedGroup = uiState.groups.find { it.id == uiState.selectedGroupId }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = padding,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ExposedDropdownMenuBox(
                    expanded = groupDropdownExpanded,
                    onExpandedChange = { groupDropdownExpanded = !groupDropdownExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val groupText = selectedGroup?.let {
                        val count = uiState.groupAthleteCounts[it.id] ?: 0
                        "${it.name} • $count athletes"
                    } ?: ""
                    
                    OutlinedTextField(
                        value = groupText,
                        onValueChange = {},
                        label = { Text("Athlete Group") },
                        singleLine = true,
                        leadingIcon = { 
                            Icon(
                                Icons.Default.Groups,
                                contentDescription = "Group",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        textStyle = androidx.compose.ui.text.TextStyle(fontWeight = FontWeight.SemiBold),
                        placeholder = { Text("Select athlete group") },
                        readOnly = true,
                        trailingIcon = { 
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = groupDropdownExpanded)
                        },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )

                    ExposedDropdownMenu(
                        expanded = groupDropdownExpanded,
                        onDismissRequest = { groupDropdownExpanded = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                    ) {
                        uiState.groups.forEach { group ->
                            val count = uiState.groupAthleteCounts[group.id] ?: 0
                            DropdownMenuItem(
                                text = { 
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = group.name,
                                            modifier = Modifier.weight(1f),
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Icon(
                                            Icons.Default.Person,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            "$count",
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                onClick = {
                                    onAction(CreateEventAction.SelectGroup(group.id))
                                    groupDropdownExpanded = false
                                },
                                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = uiState.eventName,
                    onValueChange = { onAction(CreateEventAction.SetEventName(it)) },
                    label = { Text("Event Name") },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Event,
                            contentDescription = "Event Name",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    textStyle = androidx.compose.ui.text.TextStyle(fontWeight = FontWeight.SemiBold),
                    placeholder = { Text("e.g. Spring Fitness Assessment") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedContainerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        }

        if (uiState.categories.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(2.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)),
                    shadowElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Select Tests",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = "${uiState.allTests.size} Tests Available",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        text = "${uiState.categories.size} Categories",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = "Choose one or more tests from the categories below. Tap any category to expand tests.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            uiState.categories.forEach { category ->
                val isExpanded = category.id == uiState.expandedCategoryId
                val categoryTests = uiState.allTests.filter { it.categoryId == category.id }
                val selectedCount = categoryTests.count { it.id in uiState.selectedTestIds }
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
                                selectedCount = selectedCount,
                                totalCount = categoryTests.size,
                                isExpanded = isExpanded,
                                onClick = { onAction(CreateEventAction.ToggleCategoryExpanded(category.id)) },
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
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    categoryTests.forEach { test ->
                                        TestSelectionCard(
                                            test = test,
                                            isSelected = test.id in uiState.selectedTestIds,
                                            onToggle = { onAction(CreateEventAction.ToggleTest(test.id)) },
                                            accentColor = visual.accentColor
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "↓ Scroll to explore additional test categories",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (uiState.presets.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "My custom lists",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.presets, key = { it.id }) { preset ->
                            val isApplied = preset.testIds.isNotEmpty() && uiState.selectedTestIds == preset.testIds.toSet()
                            CustomListChip(
                                preset = preset,
                                isApplied = isApplied,
                                onApply = { onAction(CreateEventAction.ApplyPreset(preset.id)) },
                                onDelete = if (!preset.isBuiltIn) {
                                    { onAction(CreateEventAction.DeletePreset(preset.id)) }
                                } else null
                            )
                        }
                    }
                }
            }
        }

        item {
            Button(
                onClick = { onAction(CreateEventAction.OpenSavePresetDialog) },
                enabled = uiState.selectedTestIds.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Save as Custom List",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        item { Spacer(modifier = Modifier.height(4.dp)) }
    }
}

@Composable
private fun CustomListChip(
    preset: TestPreset,
    isApplied: Boolean,
    onApply: () -> Unit,
    onDelete: (() -> Unit)?
) {
    Surface(
        onClick = onApply,
        shape = RoundedCornerShape(24.dp),
        color = if (isApplied) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (isApplied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = if (onDelete != null) 4.dp else 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                "${preset.name} · ${preset.testIds.size}",
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                color = if (isApplied) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
            )
            if (onDelete != null) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Delete list",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
