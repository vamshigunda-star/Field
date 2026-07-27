package com.vamshi.field.ui.quicktest

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vamshi.field.domain.model.people.BiologicalSex
import com.vamshi.field.ui.components.AppTopBar
import com.vamshi.field.ui.components.testing.CategoryAccordionHeader
import com.vamshi.field.ui.components.testing.TestInputSwitcher
import com.vamshi.field.ui.components.testing.TestSelectionRow
import com.vamshi.field.ui.theme.NavyPrimary
import com.vamshi.field.ui.theme.PerformanceGreen
import com.vamshi.field.ui.theme.PerformanceGreenText
import com.vamshi.field.ui.theme.PerformanceGrey
import com.vamshi.field.ui.theme.PerformanceRed
import androidx.compose.material3.OutlinedCard
import com.vamshi.field.ui.theme.PerformanceGreyText
import com.vamshi.field.ui.theme.PerformanceRedText
import com.vamshi.field.ui.theme.PerformanceYellow
import com.vamshi.field.ui.theme.PerformanceYellowText
import java.util.Locale

@Composable
fun QuickTestScreen(
    onNavigateBack: () -> Unit,
    viewModel: QuickTestViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    BackHandler {
        if (uiState.step == QuickTestStep.ENTER_SCORES) {
            viewModel.onAction(QuickTestAction.OnNavigateBack)
        } else {
            onNavigateBack()
        }
    }

    QuickTestContent(
        uiState = uiState,
        onAction = { action ->
            when (action) {
                is QuickTestAction.OnNavigateBack -> {
                    if (uiState.step == QuickTestStep.ENTER_SCORES) {
                        viewModel.onAction(action)
                    } else {
                        onNavigateBack()
                    }
                }
                else -> viewModel.onAction(action)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickTestContent(
    uiState: QuickTestUiState,
    onAction: (QuickTestAction) -> Unit
) {
    Scaffold(
        topBar = {
            AppTopBar(
                title = when (uiState.step) {
                    QuickTestStep.SETUP -> if (uiState.requireRegisteredAthlete) "Individual Test Setup" else "Quick Test Setup"
                    QuickTestStep.ENTER_SCORES -> "Enter Scores"
                    QuickTestStep.COMPLETE -> "Complete"
                },
                navigationIcon = {
                    IconButton(onClick = { onAction(QuickTestAction.OnNavigateBack) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (uiState.isDeleting) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = Color.White)
                    }
                }
            )
        },
        bottomBar = {
            if (uiState.step == QuickTestStep.SETUP) {
                Surface(tonalElevation = 3.dp) {
                    val canStart = uiState.athleteSearchQuery.isNotBlank() &&
                                 (!uiState.requireRegisteredAthlete || uiState.selectedAthlete != null) &&
                                 uiState.selectedTestIds.isNotEmpty() &&
                                 (!uiState.isGuest || (uiState.guestAge.isNotBlank() && uiState.guestSex != BiologicalSex.UNSPECIFIED))

                    Column(modifier = Modifier.padding(16.dp)) {
                        if (!canStart) {
                            Text(
                                text = when {
                                    uiState.athleteSearchQuery.isBlank() -> "Enter an athlete name to continue"
                                    uiState.requireRegisteredAthlete && uiState.selectedAthlete == null -> "Select a registered athlete to continue"
                                    uiState.selectedTestIds.isEmpty() -> "Select at least one test"
                                    uiState.isGuest && uiState.guestAge.isBlank() -> "Enter guest age"
                                    uiState.isGuest && uiState.guestSex == BiologicalSex.UNSPECIFIED -> "Select guest sex"
                                    else -> ""
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        Button(
                            onClick = { onAction(QuickTestAction.OnConfirmSetup) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            enabled = canStart,
                            shape = MaterialTheme.shapes.large,
                            elevation = ButtonDefaults.buttonElevation(
                                defaultElevation = 4.dp,
                                pressedElevation = 8.dp,
                                disabledElevation = 0.dp
                            ),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            border = if (!canStart) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Start Testing",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        when {
            uiState.isLoading -> LoadingState()
            uiState.errorMessage != null && uiState.categories.isEmpty() -> ErrorState(
                message = uiState.errorMessage,
                onDismiss = { onAction(QuickTestAction.OnDismissError) }
            )
            else -> {
                Box(modifier = Modifier.padding(padding)) {
                    when (uiState.step) {
                        QuickTestStep.SETUP -> SetupStep(uiState, onAction)
                        QuickTestStep.ENTER_SCORES -> EnterScoresStep(uiState, onAction)
                        QuickTestStep.COMPLETE -> CompleteStep(uiState, onAction)
                    }

                    // Floating error message if any
                    if (uiState.errorMessage != null) {
                        Snackbar(
                            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                            action = {
                                TextButton(onClick = { onAction(QuickTestAction.OnDismissError) }) {
                                    Text("Dismiss")
                                }
                            }
                        ) {
                            Text(uiState.errorMessage)
                        }
                    }
                }
            }
        }
    }

    if (uiState.showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { onAction(QuickTestAction.OnDismissDelete) },
            title = { Text("Delete Recorded Scores?") },
            text = { Text("This will permanently remove all scores recorded in this quick test session. This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = { onAction(QuickTestAction.OnConfirmDelete) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete All")
                }
            },
            dismissButton = {
                TextButton(onClick = { onAction(QuickTestAction.OnDismissDelete) }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetupStep(
    uiState: QuickTestUiState,
    onAction: (QuickTestAction) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            Text(
                "Athlete Details",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = uiState.athleteSearchQuery,
                    onValueChange = { onAction(QuickTestAction.OnAthleteQueryChange(it)) },
                    label = { Text(if (uiState.requireRegisteredAthlete) "Registered Athlete" else "Athlete Name") },
                    placeholder = {
                        Text(
                            if (uiState.requireRegisteredAthlete) "Search registered athletes..." else "Enter name or search..."
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Person, null) },
                    trailingIcon = {
                        if (uiState.selectedAthlete != null) {
                            Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF50))
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )

                // Search Suggestions
                if (uiState.matchingAthletes.isNotEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        tonalElevation = 2.dp,
                        shadowElevation = 4.dp
                    ) {
                        Column {
                            uiState.matchingAthletes.take(3).forEach { athlete ->
                                ListItem(
                                    headlineContent = { Text(athlete.fullName) },
                                    supportingContent = { Text(athlete.sex.name) },
                                    leadingContent = { 
                                        Box(Modifier.size(32.dp).background(NavyPrimary, CircleShape), contentAlignment = Alignment.Center) {
                                            Text(athlete.firstName.first().toString(), color = Color.White)
                                        }
                                    },
                                    modifier = Modifier.clickable { onAction(QuickTestAction.OnSelectAthlete(athlete)) }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (uiState.isGuest && uiState.athleteSearchQuery.isNotBlank()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Guest Details (Required for Percentiles)", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = uiState.guestAge,
                                onValueChange = { if (it.all { char -> char.isDigit() }) onAction(QuickTestAction.OnSetGuestAge(it)) },
                                label = { Text("Age") },
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                shape = RoundedCornerShape(12.dp)
                            )
                            
                            var expanded by remember { mutableStateOf(false) }
                            Box(modifier = Modifier.weight(1f)) {
                                OutlinedTextField(
                                    value = uiState.guestSex.name,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Sex") },
                                    modifier = Modifier.fillMaxWidth(),
                                    trailingIcon = {ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent
                                    ),
                                    interactionSource = remember { MutableInteractionSource() }
                                        .also { interactionSource ->
                                            LaunchedEffect(interactionSource) {
                                                interactionSource.interactions.collect {
                                                    if (it is PressInteraction.Release) {
                                                        expanded = !expanded
                                                    }
                                                }
                                            }
                                        }
                                )
                                DropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false }
                                ) {
                                    BiologicalSex.entries.filter { it != BiologicalSex.UNSPECIFIED }.forEach { sex ->
                                        DropdownMenuItem(
                                            text = { Text(sex.name) },
                                            onClick = {
                                                onAction(QuickTestAction.OnSetGuestSex(sex))
                                                expanded = false
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

        item {
            HorizontalDivider()
        }

        item {
            OutlinedTextField(
                value = uiState.eventName,
                onValueChange = { onAction(QuickTestAction.OnSetEventName(it)) },
                label = { Text("Event Name (Optional)") },
                placeholder = { Text("e.g., Morning Session") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )
        }

        item {
            Text(
                "Select Tests",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        uiState.categories.forEach { category ->
            val isExpanded = category.id == uiState.expandedCategoryId
            val categoryTests = uiState.allTests.filter { it.categoryId == category.id }
            val selectedCount = categoryTests.count { it.id in uiState.selectedTestIds }

            item(key = "category_${category.id}") {
                CategoryAccordionHeader(
                    name = category.name,
                    selectedCount = selectedCount,
                    totalCount = categoryTests.size,
                    isExpanded = isExpanded,
                    onClick = { onAction(QuickTestAction.OnToggleCategoryExpanded(category.id)) }
                )
            }

            if (isExpanded) {
                items(categoryTests, key = { it.id }) { test ->
                    TestSelectionRow(
                        test = test,
                        isSelected = test.id in uiState.selectedTestIds,
                        onToggle = { onAction(QuickTestAction.OnToggleTest(test.id)) }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun EnterScoresStep(
    uiState: QuickTestUiState,
    onAction: (QuickTestAction) -> Unit
) {
    var editingTest by remember { mutableStateOf<com.vamshi.field.domain.model.standards.FitnessTest?>(null) }
    
    Column(modifier = Modifier.fillMaxSize()) {
        // Athlete Banner
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = if (uiState.isGuest) Color(0xFF546E7A) else NavyPrimary
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier.size(48.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                }
                Column {
                    Text(
                        if (uiState.isGuest) "${uiState.athleteSearchQuery} (Guest)" 
                        else uiState.selectedAthlete?.fullName ?: "Unknown",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        "${uiState.recordedResults.size}/${uiState.selectedTests.size} Tests Completed",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(uiState.selectedTests) { test ->
                val savedResult = uiState.recordedResults.find { it.testId == test.id }
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    shape = RoundedCornerShape(12.dp),
                    onClick = { editingTest = test }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                test.name, 
                                fontSize = 16.sp, 
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                test.unit,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        Spacer(modifier = Modifier.width(16.dp))
                        
                        Box(modifier = Modifier.width(100.dp)) {
                            QuickTestScoreCell(savedResult = savedResult)
                        }
                    }
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { onAction(QuickTestAction.OnCompleteTest) },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary)
                ) {
                    Text(
                        "Complete Quick Test",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    if (editingTest != null) {
        val test = editingTest!!
        val savedResult = uiState.recordedResults.find { it.testId == test.id }
        
        QuickScoreEntryDialog(
            testName = test.name,
            unit = test.unit,
            inputParadigm = test.inputParadigm,
            currentResult = savedResult,
            validMin = test.validMin,
            validMax = test.validMax,
            onDismiss = { editingTest = null },
            onSave = { score -> 
                onAction(QuickTestAction.OnSaveScore(test.id, score))
                editingTest = null 
            },
            onDeleteSaved = {
                onAction(QuickTestAction.OnDeleteScore(test.id))
                editingTest = null
            }
        )
    }
}

@Composable
fun QuickTestScoreCell(savedResult: RecordedTestResult?) {
    val (bgColor, textColor) = when {
        savedResult == null -> Color(0xFFF3F4F6) to Color.Gray
        savedResult.percentile == null -> PerformanceGrey to Color.Black
        savedResult.percentile >= 60 -> PerformanceGreen.copy(alpha = 0.7f) to PerformanceGreenText
        savedResult.percentile >= 30 -> PerformanceYellow.copy(alpha = 0.7f) to PerformanceYellowText
        else -> PerformanceRed.copy(alpha = 0.7f) to PerformanceRedText
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        if (savedResult != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(String.format(Locale.getDefault(), "%.1f", savedResult.rawScore), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = textColor)
                savedResult.percentile?.let { p -> Text("${p}%", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = textColor.copy(alpha = 0.8f)) }
            }
        } else {
            Text("--", color = Color.LightGray, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun QuickScoreEntryDialog(
    testName: String,
    unit: String,
    inputParadigm: com.vamshi.field.domain.model.standards.InputParadigm,
    currentResult: RecordedTestResult?,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit,
    onDeleteSaved: () -> Unit,
    validMin: Double? = null,
    validMax: Double? = null
) {
    var scoreText by remember(currentResult) { mutableStateOf(currentResult?.rawScore?.toString() ?: "") }
    val scrollState = rememberScrollState()

    val isInRange = if (scoreText.isEmpty()) false else {
        val score = scoreText.toDoubleOrNull()
        if (score != null) {
            val validMinCheck = validMin?.let { score >= it } ?: true
            val validMaxCheck = validMax?.let { score <= it } ?: true
            validMinCheck && validMaxCheck
        } else false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = null,
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .verticalScroll(scrollState),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(testName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(unit, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(modifier = Modifier.fillMaxWidth().height(72.dp), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(scoreText.ifEmpty { "—" }, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                    }
                }
                if (!isInRange && scoreText.isNotEmpty()) {
                    Text(
                        text = buildString {
                            append("Valid range: ")
                            if (validMin != null) append("≥ $validMin")
                            if (validMin != null && validMax != null) append(" and ")
                            if (validMax != null) append("≤ $validMax")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                TestInputSwitcher(
                    paradigm = inputParadigm,
                    currentValue = scoreText,
                    onValueChange = { scoreText = it },
                    onSubmit = {
                        if (isInRange) {
                            scoreText.toDoubleOrNull()?.let { onSave(it) }
                        }
                    }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (currentResult != null) {
                        TextButton(onClick = onDeleteSaved, modifier = Modifier.weight(1f)) {
                            Text("Delete saved", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun CompleteStep(
    uiState: QuickTestUiState,
    onAction: (QuickTestAction) -> Unit
) {
    val results = uiState.recordedResults
    val percentiles = results.mapNotNull { it.percentile }
    val avg = if (percentiles.isNotEmpty()) percentiles.average().toInt() else null

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(64.dp), tint = PerformanceGreen)
                Spacer(Modifier.height(12.dp))
                Text("Quick Test Complete", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    if (uiState.isGuest)
                        "${results.size} test${if (results.size == 1) "" else "s"} calculated for ${uiState.athleteSearchQuery} (Guest)."
                    else
                        "${results.size} test${if (results.size == 1) "" else "s"} recorded to ${uiState.selectedAthlete?.fullName ?: "the athlete"}'s history.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (avg != null) {
            item {
                OverallStandingCard(avgPercentile = avg)
            }
        }

        if (results.isNotEmpty()) {
            item {
                Text(
                    "Results",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            items(results) { r -> ResultCard(result = r) }
        }

        item {
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { onAction(QuickTestAction.OnNavigateBack) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = MaterialTheme.shapes.large,
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
            ) {
                Text("Back to Dashboard", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            if (!uiState.isGuest) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { onAction(QuickTestAction.OnRequestDelete) },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = MaterialTheme.shapes.large,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Delete These Results", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun zoneFor(percentile: Int?): Triple<Color, Color, String> = when {
    percentile == null -> Triple(PerformanceGrey, PerformanceGreyText, "No Norm")
    percentile >= 60 -> Triple(PerformanceGreen, PerformanceGreenText, "Superior")
    percentile >= 30 -> Triple(PerformanceYellow, PerformanceYellowText, "Healthy")
    else -> Triple(PerformanceRed, PerformanceRedText, "Needs Improvement")
}

@Composable
private fun OverallStandingCard(avgPercentile: Int) {
    val (bg, fg, label) = zoneFor(avgPercentile)
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = bg)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Overall Standing", style = MaterialTheme.typography.labelLarge, color = fg.copy(alpha = 0.85f))
                Spacer(Modifier.height(4.dp))
                Text(label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = fg)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${avgPercentile}%",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Black,
                    color = fg
                )
                Text("avg percentile", style = MaterialTheme.typography.labelSmall, color = fg.copy(alpha = 0.85f))
            }
        }
    }
}

@Composable
private fun ResultCard(result: RecordedTestResult) {
    val (bg, fg, label) = zoneFor(result.percentile)
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(result.testName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text(
                    "${result.rawScore} ${result.unit}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    result.classification ?: label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = bg,
                modifier = Modifier.size(width = 72.dp, height = 56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (result.percentile != null) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "${result.percentile}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black,
                                color = fg
                            )
                            Text("pctile", style = MaterialTheme.typography.labelSmall, color = fg.copy(alpha = 0.85f))
                        }
                    } else {
                        Text("—", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = fg)
                    }
                }
            }
        }
    }
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
            CircularProgressIndicator(strokeWidth = 3.dp)
            Text(message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ErrorState(message: String, onDismiss: () -> Unit) {
    EmptyState(
        icon = Icons.Default.ErrorOutline,
        title = "An error occurred",
        message = message,
        actionLabel = "Dismiss",
        onAction = onDismiss
    )
}

@Composable
private fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(24.dp))
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}
