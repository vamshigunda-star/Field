package com.vamshi.field.ui.testing

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vamshi.field.domain.model.people.Individual
import com.vamshi.field.domain.model.standards.FitnessTest
import com.vamshi.field.domain.model.standards.TimingMode
import com.vamshi.field.domain.model.testing.TestResult
import com.vamshi.field.ui.theme.*
import com.vamshi.field.ui.components.testing.TestInputSwitcher
import java.util.Locale

@Composable
fun TimingChoiceDialog(
    athleteName: String,
    testName: String,
    unit: String,
    suggestedMode: TimingMode? = null,
    onSelectIndividualStopwatch: () -> Unit,
    onSelectGroupStopwatch: () -> Unit,
    onEnterManually: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Select Timing Mode", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                Text(
                    "$athleteName • $testName ($unit)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TimingOptionCard(
                    title = "Individual Timer",
                    description = "Dedicated timer for one athlete",
                    icon = Icons.Default.Person,
                    isSuggested = suggestedMode != TimingMode.GROUP_START,
                    onClick = onSelectIndividualStopwatch
                )
                TimingOptionCard(
                    title = "Group / Heat Timer",
                    description = "Mass start with split tap capture",
                    icon = Icons.Default.Groups,
                    isSuggested = suggestedMode == TimingMode.GROUP_START,
                    onClick = onSelectGroupStopwatch
                )
                TimingOptionCard(
                    title = "Manual Keypad Entry",
                    description = "Type in pre-recorded score numbers",
                    icon = Icons.Default.Edit,
                    isSuggested = false,
                    onClick = onEnterManually
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun TimingOptionCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSuggested: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isSuggested) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = if (isSuggested) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (isSuggested) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isSuggested) Color.White else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (isSuggested) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = "Default",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 14.sp
                )
            }
        }
    }
}

@Composable
fun DeleteResultDialog(
    athleteName: String,
    testName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete result?") },
        text = {
            Text(
                "Permanently delete $athleteName's $testName result? " +
                    "This cannot be undone and will update reports immediately."
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun LiveEntryPhase(
    uiState: TestingGridUiState,
    eventId: String,
    groupId: String,
    onAction: (TestingGridAction) -> Unit,
    padding: PaddingValues
) {
    val gridData = uiState.gridData ?: return
    if (gridData.tests.isEmpty()) return

    val selectedTest = gridData.tests.getOrNull(uiState.selectedTestIndex) ?: gridData.tests.first()

    Column(modifier = Modifier.fillMaxSize().padding(padding)) {
        // Progress Banner Header
        val testedAthletesForCurrentTest = remember(gridData.students, gridData.results, selectedTest.id) {
            gridData.students.count { athlete ->
                gridData.results.any { it.individualId == athlete.id && it.testId == selectedTest.id }
            }
        }
        
        TestingProgressBanner(
            totalAthletes = gridData.students.size,
            testedAthletes = testedAthletesForCurrentTest,
            totalTestsCompleted = gridData.results.size
        )

        // Test-Centric Tabs
        ScrollableTabRow(
            selectedTabIndex = uiState.selectedTestIndex,
            edgePadding = 16.dp,
            containerColor = MaterialTheme.colorScheme.surface,
            indicator = {},
            divider = {}
        ) {
            gridData.tests.forEachIndexed { index, test ->
                val isSelected = index == uiState.selectedTestIndex
                Tab(
                    selected = isSelected,
                    onClick = { onAction(TestingGridAction.OnSelectTestTab(index)) },
                    text = {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shadowElevation = if (isSelected) 4.dp else 0.dp,
                            modifier = Modifier.padding(vertical = 6.dp)
                        ) {
                            Text(
                                text = test.name,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                            )
                        }
                    }
                )
            }
        }

        // Athlete List for the Selected Test
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(gridData.students.size) { index ->
                val athlete = gridData.students[index]
                val savedResult = gridData.results.find { it.individualId == athlete.id && it.testId == selectedTest.id }
                val isFailed = (uiState.failedAction as? FailedGridAction.Save)?.let {
                    it.athlete.id == athlete.id && it.test.id == selectedTest.id
                } == true

                AthleteRow(
                    athlete = athlete,
                    test = selectedTest,
                    savedResult = savedResult,
                    isFailed = isFailed,
                    onCellClick = { testToLog ->
                        Log.d("TestingGridComponents", "Cell clicked for athlete ${athlete.id}, test ${testToLog.id}")
                        if (savedResult != null) {
                            // Tapping a saved result directly opens the score editor
                            onAction(TestingGridAction.OnStartEditing(athlete, testToLog))
                        } else {
                            handleCellAction(testToLog, athlete, eventId, groupId, uiState, onAction)
                        }
                    },
                    onCellLongPress = { testToLog ->
                        if (savedResult != null) {
                            // Long press to edit logic
                            onAction(TestingGridAction.OnStartEditing(athlete, testToLog))
                        } else if (testToLog.canUseStopwatch) {
                            // Long press on an unsaved timed test allows explicitly choosing mode or manual entry
                            onAction(TestingGridAction.OnRequestTimingChoice(athlete, testToLog))
                        } else {
                            onAction(TestingGridAction.OnStartEditing(athlete, testToLog))
                        }
                    },
                    onAthleteClick = { onAction(TestingGridAction.OnNavigateToAthleteReport(athlete.id)) }
                )
            }
        }
    }
}

private fun handleCellAction(
    test: FitnessTest, 
    athlete: Individual, 
    eventId: String, 
    groupId: String, 
    uiState: TestingGridUiState, 
    onAction: (TestingGridAction) -> Unit
) {
    if (test.canUseStopwatch) {
        when (uiState.testCapturePreferences[test.id]) {
            CaptureMethodPreference.INDIVIDUAL_STOPWATCH -> {
                onAction(TestingGridAction.OnNavigateToStopwatch(eventId, test.id, groupId, athlete.id, TimingMode.INDIVIDUAL.name))
            }
            CaptureMethodPreference.GROUP_STOPWATCH -> {
                onAction(TestingGridAction.OnNavigateToStopwatch(eventId, test.id, groupId, athlete.id, TimingMode.GROUP_START.name))
            }
            CaptureMethodPreference.MANUAL -> {
                onAction(TestingGridAction.OnStartEditing(athlete, test))
            }
            null -> {
                // Automatically preselect and launch the most appropriate timing mode immediately
                val defaultMode = if (test.timingMode == TimingMode.GROUP_START) {
                    TimingMode.GROUP_START
                } else {
                    TimingMode.INDIVIDUAL
                }
                onAction(TestingGridAction.OnNavigateToStopwatch(eventId, test.id, groupId, athlete.id, defaultMode.name))
            }
        }
    } else {
        onAction(TestingGridAction.OnStartEditing(athlete, test))
    }
}

@OptIn(ExperimentalComposeUiApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun AthleteRow(
    athlete: Individual,
    test: FitnessTest,
    savedResult: TestResult?,
    modifier: Modifier = Modifier,
    isFailed: Boolean = false,
    onCellClick: (FitnessTest) -> Unit,
    onCellLongPress: (FitnessTest) -> Unit,
    onAthleteClick: () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Athlete Info
            Row(
                modifier = Modifier
                    .weight(1f)
                    .combinedClickable(onClick = onAthleteClick),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Initial Circle Avatar
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = athlete.firstName.firstOrNull()?.toString() ?: "",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (athlete.isRestricted || athlete.medicalAlert != null) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = "Restricted or Medical Alert",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp).padding(end = 2.dp)
                            )
                        }
                        Text(
                            text = athlete.fullName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = athlete.sex.name.lowercase().replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Score Input / Display
            Box(modifier = Modifier.width(130.dp)) {
                ScoreCell(
                    savedResult = savedResult,
                    onClick = { onCellClick(test) },
                    onLongPress = { onCellLongPress(test) },
                    isFailed = isFailed
                )
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ScoreCell(
    savedResult: TestResult?,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    isFailed: Boolean = false
) {
    val isDark = isSystemInDarkTheme()
    val (bgColor, textColor) = when {
        isFailed -> if (isDark) PerformanceRedDark to PerformanceRedTextDark else PerformanceRed.copy(alpha = 0.7f) to PerformanceRedText
        savedResult == null -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) to MaterialTheme.colorScheme.primary
        savedResult.percentile == null -> if (isDark) PerformanceGreyDark to PerformanceGreyTextDark else PerformanceGrey to MaterialTheme.colorScheme.onSurface
        savedResult.percentile >= 60 -> if (isDark) PerformanceGreenDark to PerformanceGreenTextDark else PerformanceGreen.copy(alpha = 0.7f) to PerformanceGreenText
        savedResult.percentile >= 30 -> if (isDark) PerformanceYellowDark to PerformanceYellowTextDark else PerformanceYellow.copy(alpha = 0.7f) to PerformanceYellowText
        else -> if (isDark) PerformanceRedDark to PerformanceRedTextDark else PerformanceRed.copy(alpha = 0.7f) to PerformanceRedText
    }

    val cellBorder = when {
        isFailed -> BorderStroke(2.dp, MaterialTheme.colorScheme.error)
        savedResult == null -> BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
        else -> null
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .acceleratorClick(onClick = onClick, onLongClick = onLongPress),
        shape = RoundedCornerShape(8.dp),
        color = bgColor,
        border = cellBorder,
        shadowElevation = if (savedResult != null) 1.dp else 0.dp
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (isFailed) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(
                        Icons.Default.ErrorOutline,
                        contentDescription = "Save failed",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                    Text("Failed", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                }
            } else if (savedResult != null) {
                Box(modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 4.dp)) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit score",
                        tint = textColor.copy(alpha = 0.45f),
                        modifier = Modifier.size(11.dp).align(Alignment.TopEnd)
                    )
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(String.format(Locale.getDefault(), "%.1f", savedResult.rawScore), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = textColor)
                        savedResult.percentile?.let { p -> Text("${p}%", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = textColor.copy(alpha = 0.8f)) }
                    }
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        "Enter Result",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
fun ScoreEntryDialog(
    athleteName: String,
    testName: String,
    unit: String,
    testDescription: String?,
    inputParadigm: com.vamshi.field.domain.model.standards.InputParadigm,
    currentResult: TestResult?,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit,
    onDeleteSaved: () -> Unit,
    validMin: Double? = null,
    validMax: Double? = null
) {
    var scoreText by remember(athleteName, currentResult) { mutableStateOf(currentResult?.rawScore?.toString() ?: "") }
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
                    Text(athleteName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("$testName � $unit", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                    Surface(modifier = Modifier.fillMaxWidth().height(72.dp), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(scoreText.ifEmpty { "�" }, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (!isInRange && scoreText.isNotEmpty()) {
                        Text(
                            text = buildString {
                                append("Valid range: ")
                                if (validMin != null) append("= $validMin")
                                if (validMin != null && validMax != null) append(" and ")
                                if (validMax != null) append("= $validMax")
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
        dismissButton = {}
    )
}

@Composable
fun TestingProgressBanner(totalAthletes: Int, testedAthletes: Int, totalTestsCompleted: Int) {
    val progress = if (totalAthletes > 0) testedAthletes.toFloat() / totalAthletes else 0f
    Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(), 
                verticalAlignment = Alignment.CenterVertically, 
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Test Progress", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    "$testedAthletes / $totalAthletes Athletes • $totalTestsCompleted Tests Saved", 
                    style = MaterialTheme.typography.labelMedium, 
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Tap a cell to record or edit a score. Long press a cell to enter data manually.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
fun LoadingState() {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun ErrorState(message: String, onDismiss: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(32.dp), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.ErrorOutline, null, Modifier.size(48.dp), MaterialTheme.colorScheme.error)
            Text(message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onDismiss) { Text("Dismiss") }
        }
    }
}

@Composable
fun TestingCompleteDialog(
    athleteCount: Int,
    testsRecordedCount: Int,
    onViewReport: () -> Unit,
    onBackToDashboard: () -> Unit,
    onContinueTesting: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    AlertDialog(
        onDismissRequest = onContinueTesting,
        icon = {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF16A34A)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Success",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }
        },
        title = {
            Text(
                "Testing Complete",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isDark) PerformanceGreenDark else Color(0xFFF0FDF4),
                    border = BorderStroke(1.dp, if (isDark) PerformanceGreenBorderDark else Color(0xFFBBF7D0)),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            "$athleteCount ${if (athleteCount == 1) "Athlete" else "Athletes"} Tested",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) PerformanceGreenTextDark else Color(0xFF166534)
                        )
                        Text(
                            "$testsRecordedCount ${if (testsRecordedCount == 1) "Test" else "Tests"} Recorded Successfully",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isDark) PerformanceGreenTextDark else Color(0xFF15803D)
                        )
                    }
                }
                Text(
                    "All recorded testing session scores have been saved to athlete records.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onViewReport,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("View Session Report")
            }
        },
        dismissButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = onContinueTesting) {
                    Text("Continue Editing")
                }
                TextButton(onClick = onBackToDashboard) {
                    Text("Dashboard")
                }
            }
        }
    )
}

