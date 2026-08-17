package com.vamshi.field.ui.testing

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.ErrorOutline
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
    onUseStopwatch: () -> Unit,
    onEnterManually: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("How are you recording this time?") },
        text = {
            Column {
                Text(
                    "$athleteName � $testName ($unit)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Use the in-app stopwatch for live timing, or enter the time manually if you used an external timer.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onUseStopwatch,
                colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary)
            ) {
                Text("Use Stopwatch", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onEnterManually) {
                Text("Enter Manually")
            }
        }
    )
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

        // Helper Text
        Text(
            "?? Press and hold any saved score to edit it",
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )

        // Test-Centric Tabs
        ScrollableTabRow(
            selectedTabIndex = uiState.selectedTestIndex,
            edgePadding = 16.dp,
            containerColor = MaterialTheme.colorScheme.surface,
            indicator = { tabPositions ->
                if (uiState.selectedTestIndex < tabPositions.size) {
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[uiState.selectedTestIndex]),
                        color = NavyPrimary
                    )
                }
            },
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
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            shadowElevation = if (isSelected) 2.dp else 0.dp,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = test.name,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else Color.Gray,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
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
                            // Do nothing on regular click if saved, prevents accidental opening.
                        } else {
                            handleCellAction(testToLog, athlete, eventId, groupId, uiState, onAction)
                        }
                    },
                    onCellLongPress = { testToLog ->
                        if (savedResult != null) {
                            // Long press to edit logic
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
            CaptureMethodPreference.STOPWATCH -> {
                onAction(TestingGridAction.OnNavigateToStopwatch(eventId, test.id, groupId, athlete.id))
            }
            CaptureMethodPreference.MANUAL -> {
                onAction(TestingGridAction.OnStartEditing(athlete, test))
            }
            null -> {
                onAction(TestingGridAction.OnRequestTimingChoice(athlete, test))
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
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(NavyPrimary),
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
                                contentDescription = "Medical alert",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(
                            "${athlete.firstName} ${athlete.lastName}", 
                            fontSize = 14.sp, 
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2, 
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Score Cell
            Box(modifier = Modifier.width(140.dp)) {
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
    val (bgColor, textColor) = when {
        isFailed -> PerformanceRed.copy(alpha = 0.7f) to PerformanceRedText
        savedResult == null -> Color(0xFFF3F4F6) to Color.Gray
        savedResult.percentile == null -> PerformanceGrey to Color.Black
        savedResult.percentile >= 60 -> PerformanceGreen.copy(alpha = 0.7f) to PerformanceGreenText
        savedResult.percentile >= 30 -> PerformanceYellow.copy(alpha = 0.7f) to PerformanceYellowText
        else -> PerformanceRed.copy(alpha = 0.7f) to PerformanceRedText
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .then(
                if (isFailed) Modifier.border(2.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(8.dp))
                else Modifier
            )
            .acceleratorClick(onClick = onClick, onLongClick = onLongPress),
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
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(String.format(Locale.getDefault(), "%.1f", savedResult.rawScore), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = textColor)
                savedResult.percentile?.let { p -> Text("${p}%", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = textColor.copy(alpha = 0.8f)) }
            }
        } else {
            Text("--", color = Color.LightGray, fontSize = 20.sp, fontWeight = FontWeight.Bold)
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
    Surface(modifier = Modifier.fillMaxWidth(), color = Color(0xFFF3F4F6)) {
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
                    "$testedAthletes / $totalAthletes Athletes � $totalTestsCompleted Tests Saved", 
                    style = MaterialTheme.typography.labelMedium, 
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape), color = NavyPrimary)
        }
    }
}

@Composable
fun LoadingState() { Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = NavyPrimary) } }

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
