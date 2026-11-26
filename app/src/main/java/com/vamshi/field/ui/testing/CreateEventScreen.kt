package com.vamshi.field.ui.testing

import com.vamshi.field.ui.util.countOf
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.filled.Edit
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.vamshi.field.domain.model.standards.TestPreset
import com.vamshi.field.ui.components.AppTopBar
import androidx.compose.ui.state.ToggleableState
import com.vamshi.field.ui.components.testing.CategoryAccordionHeader
import com.vamshi.field.ui.components.testing.TestSelectionCard
import com.vamshi.field.ui.theme.*
import com.vamshi.field.ui.components.FieldLoadingState

@Composable
fun CreateEventScreen(
    onNavigateBack: () -> Unit,
    onEventCreated: (eventId: String, groupId: String) -> Unit,
    viewModel: CreateEventViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Handle navigation event
    LaunchedEffect(uiState.eventCreated) {
        uiState.eventCreated?.let { (eventId, groupId) ->
            onEventCreated(eventId, groupId)
            viewModel.onAction(CreateEventAction.NavigationConsumed)
        }
    }

    CreateEventContent(
        uiState = uiState,
        onAction = { action ->
            when (action) {
                is CreateEventAction.NavigateBack -> onNavigateBack()
                else -> viewModel.onAction(action)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateEventContent(
    uiState: CreateEventUiState,
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
                }
            )
        },
        bottomBar = {
            val isReady = uiState.missingRequirement == null

            val gradientBrush = if (isReady && !uiState.isCreating) {
                Brush.horizontalGradient(
                    // Brand orange is reserved for start actions, and this commits the event.
                    colors = listOf(
                        SportOrange,
                        SportOrange.copy(alpha = 0.85f)
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
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp)
                ) {
                    // What still stops Start. The button used to sit greyed out with no reason, and the
                    // empty field was often scrolled far off screen above the categories.
                    uiState.missingRequirement?.let { missing ->
                        Text(
                            text = missing.hint,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .shadow(
                                elevation = if (isReady && !uiState.isCreating) 6.dp else 0.dp,
                                shape = RoundedCornerShape(16.dp),
                                spotColor = SportOrange.copy(alpha = 0.35f)
                            ),
                        shape = RoundedCornerShape(16.dp),
                        color = Color.Transparent
                    ) {
                        Button(
                            onClick = { onAction(CreateEventAction.CreateEvent) },
                            modifier = Modifier
                                .fillMaxSize()
                                .background(gradientBrush),
                            // Tappable even when something is missing: the ViewModel then sends the
                            // coach to that field. It still looks grey until everything is filled.
                            enabled = !uiState.isCreating,
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
            uiState.isLoading -> FieldLoadingState("Loading...")
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
    val listState = rememberLazyListState()
    val eventNameFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val detailsCollapsed = uiState.expandedCategoryId != null

    // Item positions as laid out: [event details (only while no category is open)],
    // [Select Tests header], categories. Computed in effects, never during composition.
    fun headerIndex() = if (detailsCollapsed) 0 else 1
    fun categoryIndex(id: String) = headerIndex() + 1 + uiState.categories.indexOfFirst { it.id == id }

    // An opened category scrolls up under the summary bar, so its tests start on screen even for a
    // category near the bottom. Closing every category returns to the top, where the fields are.
    LaunchedEffect(uiState.expandedCategoryId) {
        val id = uiState.expandedCategoryId
        if (id == null) {
            listState.animateScrollToItem(0)
        } else if (uiState.categories.any { it.id == id }) {
            listState.animateScrollToItem(categoryIndex(id))
        }
    }

    // Start tapped with something missing: go to it.
    LaunchedEffect(uiState.jumpTo) {
        when (uiState.jumpTo) {
            MissingRequirement.GROUP -> {
                listState.animateScrollToItem(0)
                groupDropdownExpanded = true
            }
            MissingRequirement.EVENT_NAME -> {
                listState.animateScrollToItem(0)
                runCatching { eventNameFocus.requestFocus() }
                keyboard?.show()
            }
            MissingRequirement.TESTS -> listState.animateScrollToItem(headerIndex())
            null -> return@LaunchedEffect
        }
        onAction(CreateEventAction.JumpConsumed)
    }

    Column(modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
        if (detailsCollapsed) {
            CompactEventDetailsBar(
                groupName = uiState.selectedGroupName,
                eventName = uiState.eventName,
                onClick = { onAction(CreateEventAction.CollapseCategories) }
            )
        }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = padding.calculateBottomPadding()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // The full fields only while every category is closed; an open category shows the pinned
        // summary bar instead, so the coach never scrolls back up past the tests to fix them.
        if (!detailsCollapsed) item(key = "event_details") {
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
                    val groupText = uiState.selectedGroupName?.let { name ->
                        val count = uiState.groupAthleteCounts[uiState.selectedGroupId] ?: 0
                        "$name • ${countOf(count, "athlete")}"
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
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
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
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    placeholder = { Text("e.g. Spring Fitness Assessment") },
                    modifier = Modifier.fillMaxWidth().focusRequester(eventNameFocus),
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
            item(key = "select_tests_header") {
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
                val categoryTests = uiState.testsByCategory[category.id].orEmpty()
                val selectedCount = categoryTests.count { it.id in uiState.selectedTestIds }
                val selectionState = when {
                    categoryTests.isEmpty() -> null
                    selectedCount == 0 -> ToggleableState.Off
                    selectedCount == categoryTests.size -> ToggleableState.On
                    else -> ToggleableState.Indeterminate
                }
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
                                isDocked = true,
                                selectionState = selectionState,
                                onToggleSelection = { onAction(CreateEventAction.ToggleCategorySelection(category.id)) }
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
}

/**
 * The athlete group and event name, collapsed to one pinned line while a category is open. A blank
 * value reads in the error colour so a missing name is obvious mid-selection. Tapping it closes the
 * category and brings the full fields back.
 */
@Composable
private fun CompactEventDetailsBar(groupName: String?, eventName: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .semantics { contentDescription = "Edit athlete group and event name" }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Groups,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(8.dp))
            val missing = MaterialTheme.colorScheme.error
            val filled = MaterialTheme.colorScheme.onSurface
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = if (groupName == null) missing else filled)) {
                        append(groupName ?: "No group")
                    }
                    append("  ·  ")
                    withStyle(SpanStyle(color = if (eventName.isBlank()) missing else filled)) {
                        append(eventName.ifBlank { "No event name" })
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(
                Icons.Default.Edit,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
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
