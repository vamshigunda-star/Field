package com.vamshi.field.ui.roster

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.isSystemInDarkTheme
import com.vamshi.field.domain.model.people.BiologicalSex
import com.vamshi.field.domain.model.people.Group
import com.vamshi.field.domain.model.people.Individual
import com.vamshi.field.ui.components.AppFilterChip
import com.vamshi.field.ui.theme.ElectricBlue
import java.util.*

@Composable
fun RosterTabRow(
    currentTab: RosterTab,
    onTabSelected: (RosterTab) -> Unit,
    athleteCount: Int,
    groupCount: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AppFilterChip(
            label = "Athletes ($athleteCount)",
            isSelected = currentTab == RosterTab.ATHLETES,
            onClick = { onTabSelected(RosterTab.ATHLETES) }
        )
        AppFilterChip(
            label = "Groups ($groupCount)",
            isSelected = currentTab == RosterTab.GROUPS,
            onClick = { onTabSelected(RosterTab.GROUPS) }
        )
    }
}

@Composable
fun AthleteFilterRow(
    selectedSexFilters: Set<BiologicalSex>,
    selectedAgeRange: AthleteAgeRange?,
    onSexToggled: (BiologicalSex) -> Unit,
    onAgeRangeSelected: (AthleteAgeRange?) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppFilterChip(
                label = "Male",
                isSelected = BiologicalSex.MALE in selectedSexFilters,
                onClick = { onSexToggled(BiologicalSex.MALE) }
            )
            AppFilterChip(
                label = "Female",
                isSelected = BiologicalSex.FEMALE in selectedSexFilters,
                onClick = { onSexToggled(BiologicalSex.FEMALE) }
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AthleteAgeRange.entries.forEach { range ->
                AppFilterChip(
                    label = range.label,
                    isSelected = selectedAgeRange == range,
                    onClick = { onAgeRangeSelected(if (selectedAgeRange == range) null else range) },
                    modifier = Modifier.weight(1f),
                    compact = true
                )
            }
        }
    }
}

@Composable
fun AthleteTabContent(
    uiState: RosterUiState,
    onAction: (RosterAction) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        SearchBar(
            query = uiState.athleteSearchQuery,
            onQueryChange = { onAction(RosterAction.OnAthleteSearchQueryChanged(it)) },
            placeholder = "Search athletes..."
        )

        AthleteFilterRow(
            selectedSexFilters = uiState.selectedSexFilters,
            selectedAgeRange = uiState.selectedAgeRange,
            onSexToggled = { onAction(RosterAction.OnSexFilterToggled(it)) },
            onAgeRangeSelected = { onAction(RosterAction.OnAgeRangeFilterSelected(it)) }
        )

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(uiState.filteredAthletes, key = { it.id }) { athlete ->
                val isSelected = uiState.selectedAthleteIds.contains(athlete.id)
                val groups = uiState.athleteGroups[athlete.id] ?: emptyList()

                SwipeableAthleteCard(
                    athlete = athlete,
                    groups = groups,
                    isSelected = isSelected,
                    onToggleSelection = { onAction(RosterAction.OnToggleAthleteSelection(athlete.id)) },
                    onDelete = { onAction(RosterAction.OnDeleteAthlete(athlete.id)) },
                    onClick = { onAction(RosterAction.OnNavigateToAthleteReport(athlete.id)) }
                )
            }
            item {
                Text(
                    "← Swipe left to delete",
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableAthleteCard(
    athlete: Individual,
    groups: List<Group>,
    isSelected: Boolean,
    onToggleSelection: () -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            if (it == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                false // Don't dismiss yet, wait for confirmation dialog
            } else false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            val color = MaterialTheme.colorScheme.errorContainer
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(14.dp))
                    .background(color)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
            }
        }
    ) {
        ModernAthleteCard(
            athlete = athlete,
            groups = groups,
            isSelected = isSelected,
            onToggleSelection = onToggleSelection,
            onClick = onClick
        )
    }
}

@Composable
fun ModernAthleteCard(
    athlete: Individual,
    groups: List<Group>,
    isSelected: Boolean,
    onToggleSelection: () -> Unit,
    onClick: () -> Unit
) {
    val initials = "${athlete.firstName.first()}${athlete.lastName.first()}".uppercase()
    val isRestricted = athlete.isRestricted || athlete.medicalAlert != null
    val isDark = isSystemInDarkTheme()
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        color = if (isDark) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface,
        shadowElevation = 0.dp,
        border = BorderStroke(
            width = 1.dp,
            color = if (isSelected) ElectricBlue else MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.40f else 0.65f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Custom Checkbox
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (isSelected) ElectricBlue 
                        else if (isDark) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) 
                        else MaterialTheme.colorScheme.surface
                    )
                    .border(
                        1.dp, 
                        if (isSelected) ElectricBlue else MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.60f else 0.80f), 
                        RoundedCornerShape(6.dp)
                    )
                    .clickable { onToggleSelection() },
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Squircle Avatar
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(10.dp),
                color = if (isRestricted) {
                    MaterialTheme.colorScheme.errorContainer
                } else if (isDark) {
                    ElectricBlue.copy(alpha = 0.20f)
                } else {
                    ElectricBlue.copy(alpha = 0.12f)
                }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        initials, 
                        fontWeight = FontWeight.Bold, 
                        fontSize = 14.sp,
                        color = if (isRestricted) MaterialTheme.colorScheme.error else ElectricBlue
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        athlete.fullName, 
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.5.sp), 
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (isRestricted) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(Icons.Default.Warning, contentDescription = "Medical Alert", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    }
                }
                Text(
                    "Age ${((System.currentTimeMillis() - athlete.dateOfBirth) / 31_557_600_000L).toInt()} • ${athlete.sex.name.lowercase().replaceFirstChar { it.uppercase() }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                if (groups.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        groups.take(3).forEach { group ->
                            Surface(
                                color = if (isDark) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.60f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.70f),
                                shape = RoundedCornerShape(5.dp)
                            ) {
                                Text(
                                    group.name,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 10.5.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GroupsTabContent(
    uiState: RosterUiState,
    onAction: (RosterAction) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        SearchBar(
            query = uiState.groupSearchQuery,
            onQueryChange = { onAction(RosterAction.OnGroupSearchQueryChanged(it)) },
            placeholder = "Search groups..."
        )

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(uiState.filteredGroups, key = { it.id }) { group ->
                val isExpanded = uiState.expandedGroupIds.contains(group.id)
                val members = uiState.groupMembers[group.id] ?: emptyList()
                
                ModernGroupCard(
                    group = group,
                    members = members,
                    isExpanded = isExpanded,
                    onToggleExpansion = { onAction(RosterAction.OnToggleGroupExpansion(group.id)) },
                    onRemoveMember = { athleteId -> onAction(RosterAction.OnRemoveAthleteFromGroup(group.id, athleteId)) },
                    onAddMember = { onAction(RosterAction.OnShowManageMembersDialog(group.id)) }
                )
            }
            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

@Composable
fun ModernGroupCard(
    group: Group,
    members: List<Individual>,
    isExpanded: Boolean,
    onToggleExpansion: () -> Unit,
    onRemoveMember: (String) -> Unit,
    onAddMember: () -> Unit
) {
    val rotation by animateFloatAsState(if (isExpanded) 90f else 0f)
    val isDark = isSystemInDarkTheme()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = if (isDark) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface,
        shadowElevation = 0.dp,
        border = BorderStroke(
            width = 1.dp,
            color = if (isExpanded) ElectricBlue.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.40f else 0.65f)
        )
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpansion() }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = if (isDark) ElectricBlue.copy(alpha = 0.20f) else ElectricBlue.copy(alpha = 0.12f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Groups,
                            contentDescription = null,
                            tint = ElectricBlue
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        group.name, 
                        fontWeight = FontWeight.Bold, 
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.5.sp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "${members.size} athletes", 
                        style = MaterialTheme.typography.bodySmall, 
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (!isExpanded) {
                    AvatarStack(members)
                }

                IconButton(onClick = onToggleExpansion) {
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.rotate(rotation),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    members.forEach { member ->
                        MemberRow(member, onRemove = { onRemoveMember(member.id) })
                    }
                    
                    // Add Athlete button at the bottom of the list
                    TextButton(
                        onClick = onAddMember,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add Athlete", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun MemberRow(member: Individual, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Menu, contentDescription = "Drag", tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Box(
            modifier = Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Text("${member.firstName.first()}${member.lastName.first()}".uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(member.fullName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text("Age ${((System.currentTimeMillis() - member.dateOfBirth) / 31_557_600_000L).toInt()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onRemove, modifier = Modifier.size(24.dp)) {
            Icon(Icons.Default.Close, contentDescription = "Remove", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun AvatarStack(members: List<Individual>) {
    val isDark = isSystemInDarkTheme()
    Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
        members.take(4).forEach { member ->
            Surface(
                modifier = Modifier.size(28.dp).border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
                shape = CircleShape,
                color = if (isDark) ElectricBlue.copy(alpha = 0.25f) else ElectricBlue.copy(alpha = 0.15f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        "${member.firstName.first()}${member.lastName.first()}".uppercase(),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = ElectricBlue
                    )
                }
            }
        }
        if (members.size > 4) {
            Surface(
                modifier = Modifier.size(28.dp).border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("+${members.size - 4}", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun SearchBar(query: String, onQueryChange: (String) -> Unit, placeholder: String) {
    val isDark = isSystemInDarkTheme()
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        placeholder = { Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = if (isDark) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface,
            focusedContainerColor = if (isDark) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.40f else 0.65f),
            focusedBorderColor = MaterialTheme.colorScheme.primary
        ),
        singleLine = true
    )
}

@Composable
fun ContextualActionBar(selectedCount: Int, onAddToGroup: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.primary,
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth().height(64.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add to Group", color = Color.White, fontWeight = FontWeight.Bold)
            }
            Surface(
                color = Color.White.copy(alpha = 0.25f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    "$selectedCount selected",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
            TextButton(onClick = onAddToGroup) {
                Text("CHOOSE", color = Color.White, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToGroupSelectionSheet(
    groups: List<Group>,
    onDismiss: () -> Unit,
    onGroupSelected: (String) -> Unit,
    onCreateGroup: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(24.dp).padding(bottom = 32.dp)) {
            Text("Select Group", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(groups) { group ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable { onGroupSelected(group.id) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isDark) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.40f else 0.65f))
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(group.name, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                item {
                    TextButton(onClick = onCreateGroup) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Text("Create New Group")
                    }
                }
            }
        }
    }
}

@Composable
fun ConfirmationDialog(
    title: String,
    message: String,
    confirmText: String,
    isDestructive: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = { Text(message) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            ) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun LoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun ErrorState(message: String, onDismiss: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            TextButton(onClick = onDismiss) { Text("Dismiss") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddGroupSheet(onDismiss: () -> Unit, onConfirm: (String, String?, String?) -> Unit) {
    var name by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var cycle by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.outline) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Create New Group", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Group Name *") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            
            OutlinedTextField(
                value = location,
                onValueChange = { location = it },
                label = { Text("Location (Optional)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            
            OutlinedTextField(
                value = cycle,
                onValueChange = { cycle = it },
                label = { Text("Cycle/Term (e.g. Fall 2025)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Button(
                onClick = { onConfirm(name, location.ifBlank { null }, cycle.ifBlank { null }) },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Create Group", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageGroupMembersSheet(
    allAthletes: List<Individual>,
    currentAthleteIds: Set<String>,
    onDismiss: () -> Unit,
    onAddAthlete: (String) -> Unit,
    onRemoveAthlete: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredAthletes = remember(searchQuery, allAthletes) {
        if (searchQuery.isBlank()) allAthletes else {
            allAthletes.filter { it.fullName.contains(searchQuery, ignoreCase = true) }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.outline) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f)
                .padding(horizontal = 24.dp)
        ) {
            Text("Manage Group Members", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search athletes...") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Search, null) },
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredAthletes) { athlete ->
                    val isInGroup = athlete.id in currentAthleteIds
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isInGroup) ElectricBlue.copy(alpha = 0.10f) else Color.Transparent)
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = if (isInGroup) ElectricBlue.copy(alpha = 0.20f) else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    athlete.firstName.first().toString().uppercase(),
                                    color = if (isInGroup) ElectricBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        
                        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(athlete.fullName, fontWeight = FontWeight.SemiBold)
                            Text(athlete.sex.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        IconButton(
                            onClick = { 
                                if (isInGroup) onRemoveAthlete(athlete.id) 
                                else onAddAthlete(athlete.id) 
                            }
                        ) {
                            Icon(
                                if (isInGroup) Icons.Default.RemoveCircleOutline else Icons.Default.AddCircleOutline,
                                contentDescription = null,
                                tint = if (isInGroup) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
            
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp).height(56.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Done", fontWeight = FontWeight.Bold)
            }
        }
    }
}
