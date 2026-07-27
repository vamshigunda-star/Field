package com.vamshi.field.ui.customtest

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vamshi.field.domain.model.people.BiologicalSex
import com.vamshi.field.domain.model.standards.BandLevel
import com.vamshi.field.domain.model.standards.CustomTestField
import com.vamshi.field.domain.model.standards.MeasurementMethod
import com.vamshi.field.domain.model.standards.ScoringBands
import com.vamshi.field.ui.components.AppTopBar
import com.vamshi.field.ui.theme.PerformanceGreenText
import com.vamshi.field.ui.theme.PerformanceRedText
import com.vamshi.field.ui.theme.PerformanceYellowText

/** Units a coach is most likely to want, offered as one-tap chips. Free text still wins. */
private val COMMON_UNITS = listOf("sec", "reps", "cm", "m", "kg", "laps", "level", "points")

@Composable
fun CustomTestScreen(
    onNavigateBack: () -> Unit,
    onTestSaved: (testId: String) -> Unit,
    viewModel: CustomTestViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.savedTestId) {
        uiState.savedTestId?.let { testId ->
            onTestSaved(testId)
            viewModel.onAction(CustomTestAction.NavigationConsumed)
        }
    }

    CustomTestContent(
        uiState = uiState,
        onAction = { action ->
            when (action) {
                CustomTestAction.OnNavigateBack -> onNavigateBack()
                else -> viewModel.onAction(action)
            }
        }
    )
}

@Composable
fun CustomTestContent(
    uiState: CustomTestUiState,
    onAction: (CustomTestAction) -> Unit
) {
    Scaffold(
        topBar = {
            AppTopBar(
                title = if (uiState.isEditMode) "Edit Test" else "New Test",
                navigationIcon = {
                    IconButton(onClick = { onAction(CustomTestAction.OnNavigateBack) }) {
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
                    onClick = { onAction(CustomTestAction.OnSave) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .height(56.dp),
                    enabled = uiState.canSave,
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            if (uiState.isEditMode) "Save changes" else "Save test",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 3.dp)
            }
        } else {
            CustomTestBody(uiState = uiState, onAction = onAction, padding = padding)
        }
    }

    if (uiState.errorMessage != null) {
        Snackbar(
            modifier = Modifier.padding(16.dp),
            action = {
                TextButton(onClick = { onAction(CustomTestAction.OnDismissError) }) { Text("Dismiss") }
            }
        ) {
            Text(uiState.errorMessage)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun CustomTestBody(
    uiState: CustomTestUiState,
    onAction: (CustomTestAction) -> Unit,
    padding: PaddingValues
) {
    var categoryExpanded by remember { mutableStateOf(false) }
    val draft = uiState.draft
    val selectedCategory = uiState.categories.find { it.id == draft.categoryId }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = padding,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Spacer(Modifier.height(8.dp))
            SectionLabel("Basics")
        }

        item {
            OutlinedTextField(
                value = draft.name,
                onValueChange = { onAction(CustomTestAction.OnNameChange(it)) },
                label = { Text("Test name") },
                placeholder = { Text("e.g. Sled Push 20m") },
                singleLine = true,
                isError = CustomTestField.NAME in uiState.errors,
                supportingText = uiState.errors[CustomTestField.NAME]?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
        }

        item {
            ExposedDropdownMenuBox(
                expanded = categoryExpanded,
                onExpandedChange = { categoryExpanded = !categoryExpanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = selectedCategory?.name.orEmpty(),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Category") },
                    placeholder = { Text("Select") },
                    isError = CustomTestField.CATEGORY in uiState.errors,
                    supportingText = uiState.errors[CustomTestField.CATEGORY]?.let { { Text(it) } },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                ExposedDropdownMenu(
                    expanded = categoryExpanded,
                    onDismissRequest = { categoryExpanded = false },
                    modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                ) {
                    uiState.categories.forEach { category ->
                        DropdownMenuItem(
                            text = { Text(category.name) },
                            onClick = {
                                onAction(CustomTestAction.OnCategorySelect(category.id))
                                categoryExpanded = false
                            },
                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                        )
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = draft.unit,
                    onValueChange = { onAction(CustomTestAction.OnUnitChange(it)) },
                    label = { Text("Unit") },
                    placeholder = { Text("e.g. sec") },
                    singleLine = true,
                    isError = CustomTestField.UNIT in uiState.errors,
                    supportingText = uiState.errors[CustomTestField.UNIT]?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    COMMON_UNITS.forEach { unit ->
                        FilterChip(
                            selected = draft.unit == unit,
                            onClick = { onAction(CustomTestAction.OnUnitChange(unit)) },
                            label = { Text(unit) },
                            colors = brandChipColors(),
                            border = brandChipBorder(selected = draft.unit == unit)
                        )
                    }
                }
            }
        }

        item { DirectionSelector(draft.isHigherBetter, onAction) }

        item {
            OutlinedTextField(
                value = draft.description.orEmpty(),
                onValueChange = { onAction(CustomTestAction.OnDescriptionChange(it)) },
                label = { Text("How to run it (optional)") },
                placeholder = { Text("Instructions the coach sees during testing") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
        }

        item {
            AdvancedSection(uiState = uiState, onAction = onAction)
        }

        item { ScoringSection(uiState = uiState, onAction = onAction) }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground
    )
}

@Composable
private fun DirectionSelector(
    isHigherBetter: Boolean,
    onAction: (CustomTestAction) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Which direction is better?",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        // The trend arrow goes in SegmentedButton's own `icon` slot, not inside the label.
        // Putting it in the label draws it on top of the text, because the icon slot is
        // laid out in the same place whether or not you supply one.
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = isHigherBetter,
                onClick = { onAction(CustomTestAction.OnDirectionChange(true)) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                colors = directionSegmentColors(),
                icon = {
                    Icon(
                        Icons.AutoMirrored.Filled.TrendingUp,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                label = { Text("Higher") }
            )
            SegmentedButton(
                selected = !isHigherBetter,
                onClick = { onAction(CustomTestAction.OnDirectionChange(false)) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                colors = directionSegmentColors(),
                icon = {
                    Icon(
                        Icons.AutoMirrored.Filled.TrendingDown,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                label = { Text("Lower") }
            )
        }
        Text(
            if (isHigherBetter) "A bigger number is a better result — reps, distance, weight."
            else "A smaller number is a better result — sprint and run times.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Chip selection in the app's primary — the M3 default renders lavender here. */
@Composable
private fun brandChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
    selectedLabelColor = MaterialTheme.colorScheme.primary,
    containerColor = MaterialTheme.colorScheme.surface,
    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
)

@Composable
private fun brandChipBorder(selected: Boolean) = BorderStroke(
    if (selected) 2.dp else 1.dp,
    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
)

/**
 * Segmented-button colours pinned to the app's primary.
 *
 * The M3 default selected container is `secondaryContainer`, which renders lavender in
 * this theme and reads as off-brand next to the blue/orange palette everywhere else.
 */
@Composable
private fun directionSegmentColors() = SegmentedButtonDefaults.colors(
    activeContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
    activeContentColor = MaterialTheme.colorScheme.primary,
    activeBorderColor = MaterialTheme.colorScheme.primary,
    inactiveContainerColor = MaterialTheme.colorScheme.surface,
    inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    inactiveBorderColor = MaterialTheme.colorScheme.outline
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdvancedSection(
    uiState: CustomTestUiState,
    onAction: (CustomTestAction) -> Unit
) {
    val rotation by animateFloatAsState(
        if (uiState.isAdvancedExpanded) 180f else 0f,
        label = "advancedChevron"
    )
    val draft = uiState.draft

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            onClick = { onAction(CustomTestAction.OnToggleAdvanced) },
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("How it's measured", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        advancedSummary(uiState),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    Icons.Default.ExpandMore,
                    contentDescription = if (uiState.isAdvancedExpanded) "Collapse" else "Expand",
                    modifier = Modifier.rotate(rotation),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        AnimatedVisibility(visible = uiState.isAdvancedExpanded) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Score entry",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MeasurementMethod.entries.forEach { method ->
                            val selected = draft.measurementMethod == method
                            FilterChip(
                                selected = selected,
                                onClick = { onAction(CustomTestAction.OnMeasurementMethodChange(method)) },
                                label = { Text(method.label()) },
                                colors = brandChipColors(),
                                border = brandChipBorder(selected = selected)
                            )
                        }
                    }
                    Text(
                        draft.measurementMethod.hint(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                TrialsStepper(
                    trials = draft.trialsPerAthlete,
                    error = uiState.errors[CustomTestField.TRIALS],
                    onChange = { onAction(CustomTestAction.OnTrialsChange(it)) }
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Valid score range (optional)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = uiState.validMinText,
                            onValueChange = { onAction(CustomTestAction.OnValidMinChange(it)) },
                            label = { Text("Lowest") },
                            singleLine = true,
                            isError = CustomTestField.VALID_RANGE in uiState.errors,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        OutlinedTextField(
                            value = uiState.validMaxText,
                            onValueChange = { onAction(CustomTestAction.OnValidMaxChange(it)) },
                            label = { Text("Highest") },
                            singleLine = true,
                            isError = CustomTestField.VALID_RANGE in uiState.errors,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                    val rangeError = uiState.errors[CustomTestField.VALID_RANGE]
                    Text(
                        rangeError ?: "Scores outside this range are rejected during testing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (rangeError != null) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun TrialsStepper(
    trials: Int,
    error: String?,
    onChange: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Attempts per athlete",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { onChange(trials - 1) }, enabled = trials > 1) {
                Icon(Icons.Default.Remove, contentDescription = "One fewer attempt")
            }
            Text(
                "$trials",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.widthIn(min = 24.dp),
                textAlign = TextAlign.Center
            )
            IconButton(onClick = { onChange(trials + 1) }, enabled = trials < 5) {
                Icon(Icons.Default.Add, contentDescription = "One more attempt")
            }
        }
        if (error != null) {
            Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

/**
 * Raw-scores-only vs. performance bands.
 *
 * Raw is the default and deliberately framed as a legitimate choice rather than a
 * degraded one — most of the seeded catalog has no norms either. The band path asks for
 * three numbers, which is all the app's norm model actually resolves.
 */
@Composable
private fun ScoringSection(
    uiState: CustomTestUiState,
    onAction: (CustomTestAction) -> Unit
) {
    val scoring = uiState.draft.scoring

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionLabel("Scoring")

        ScoringChoice(
            selected = scoring == null,
            title = "Record raw scores only",
            subtitle = "Reports show the numbers and whether an athlete is improving, " +
                "but no green/yellow/red zone.",
            onClick = { onAction(CustomTestAction.OnScoringEnabledChange(false)) }
        )
        ScoringChoice(
            selected = scoring != null,
            title = "Add performance bands",
            subtitle = "Three numbers turn scores into the same zones the built-in tests use.",
            onClick = { onAction(CustomTestAction.OnScoringEnabledChange(true)) }
        )

        AnimatedVisibility(visible = scoring != null) {
            if (scoring != null) {
                BandEditor(uiState = uiState, scoring = scoring, onAction = onAction)
            }
        }
    }
}

@Composable
private fun ScoringChoice(
    selected: Boolean,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Column(
                modifier = Modifier.padding(top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun BandEditor(
    uiState: CustomTestUiState,
    scoring: ScoringBands,
    onAction: (CustomTestAction) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.weight(1f)) {
                Text("Same standard for all athletes", style = MaterialTheme.typography.bodyMedium)
                Text(
                    if (scoring.sameForAllSexes) "One set of bands for everyone."
                    // Worth saying plainly: separate ladders leave athletes recorded as
                    // unspecified with no matching standard at all.
                    else "Athletes with no sex recorded won't get a zone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = scoring.sameForAllSexes,
                onCheckedChange = { onAction(CustomTestAction.OnSameForAllSexesChange(it)) }
            )
        }

        if (scoring.sameForAllSexes) {
            BandLadder(
                sex = null,
                cutPoints = scoring.shared,
                uiState = uiState,
                onAction = onAction
            )
        } else {
            Text("Male", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            BandLadder(BiologicalSex.MALE, scoring.male, uiState, onAction)
            Spacer(Modifier.height(4.dp))
            Text("Female", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            BandLadder(BiologicalSex.FEMALE, scoring.female, uiState, onAction)
        }

        uiState.errors[CustomTestField.BANDS]?.let { error ->
            Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

/**
 * The four bands worst→best, with an editable boundary between each pair.
 *
 * Reading top to bottom always goes worst to best regardless of direction, so the numbers
 * ascend for a higher-is-better test and descend for a lower-is-better one. The outer
 * rows state their open end ("over 8", "under 5.5") so the ladder is unambiguous.
 */
@Composable
private fun BandLadder(
    sex: BiologicalSex?,
    cutPoints: List<Double?>,
    uiState: CustomTestUiState,
    onAction: (CustomTestAction) -> Unit
) {
    val unit = uiState.draft.unit.ifBlank { "" }
    val higher = uiState.draft.isHigherBetter
    val levels = BandLevel.entries

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        levels.forEachIndexed { index, level ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(level.zoneColor(), shape = CircleShape)
                    )
                    Text(level.label, style = MaterialTheme.typography.bodyMedium)
                }

                when (index) {
                    // Worst band: open at the bad end.
                    0 -> BoundaryField(
                        prefix = if (higher) "under" else "over",
                        sex = sex,
                        index = 0,
                        cutPoints = cutPoints,
                        uiState = uiState,
                        onAction = onAction,
                        unit = unit
                    )
                    // Best band: open at the good end, boundary owned by the row above.
                    levels.lastIndex -> Text(
                        text = buildString {
                            append(if (higher) "over " else "under ")
                            append(cutPoints.lastOrNull()?.display() ?: "—")
                            if (unit.isNotBlank()) append(" $unit")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    else -> BoundaryField(
                        prefix = "to",
                        sex = sex,
                        index = index,
                        cutPoints = cutPoints,
                        uiState = uiState,
                        onAction = onAction,
                        unit = unit
                    )
                }
            }
        }
    }
}

@Composable
private fun BoundaryField(
    prefix: String,
    sex: BiologicalSex?,
    index: Int,
    cutPoints: List<Double?>,
    uiState: CustomTestUiState,
    onAction: (CustomTestAction) -> Unit,
    unit: String
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            prefix,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = uiState.cutPointText[CutPointKey(sex, index)].orEmpty(),
            onValueChange = { onAction(CustomTestAction.OnCutPointChange(sex, index, it)) },
            singleLine = true,
            isError = CustomTestField.BANDS in uiState.errors,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.width(96.dp),
            shape = RoundedCornerShape(12.dp)
        )
        if (unit.isNotBlank()) {
            Text(
                unit,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Dot colour matching the zone the band's percentile lands in (60/30 thresholds). */
private fun BandLevel.zoneColor(): Color = when (this) {
    BandLevel.NEEDS_WORK -> PerformanceRedText
    BandLevel.FAIR -> PerformanceYellowText
    BandLevel.GOOD, BandLevel.EXCELLENT -> PerformanceGreenText
}

private fun Double.display(): String =
    if (this % 1.0 == 0.0) toLong().toString() else toString()

private fun advancedSummary(uiState: CustomTestUiState): String {
    val draft = uiState.draft
    val attempts = if (draft.trialsPerAthlete == 1) "1 attempt" else "${draft.trialsPerAthlete} attempts"
    return "${draft.measurementMethod.label()} · $attempts"
}

private fun MeasurementMethod.label(): String = when (this) {
    MeasurementMethod.KEYPAD -> "Keypad"
    MeasurementMethod.COUNTER -> "Counter"
    MeasurementMethod.STOPWATCH -> "Stopwatch"
    MeasurementMethod.LEVELS -> "Levels"
}

private fun MeasurementMethod.hint(): String = when (this) {
    MeasurementMethod.KEYPAD -> "Type the score on a number pad — distances, weights, heights."
    MeasurementMethod.COUNTER -> "Tap to count up — push-ups, sit-ups."
    MeasurementMethod.STOPWATCH -> "Time it in the app, or type a time in instead."
    MeasurementMethod.LEVELS -> "Record the level or stage reached — beep-test style."
}
