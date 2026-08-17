package com.vamshi.field.ui.session

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vamshi.field.domain.model.reports.Classification
import com.vamshi.field.domain.model.reports.LeaderboardRow
import com.vamshi.field.domain.repository.AiCoachStatus
import com.vamshi.field.ui.aicoach.AiCoachViewModel
import com.vamshi.field.ui.components.AppTopBar
import com.vamshi.field.ui.components.AppTopBarActionButton
import com.vamshi.field.ui.components.AppTopBarSubtitleColor
import com.vamshi.field.ui.report.components.AthleteLeaderRow
import com.vamshi.field.ui.report.components.SessionSwitcherSheet
import com.vamshi.field.ui.report.components.ZoneChip
import com.vamshi.field.ui.theme.PerformanceRed
import com.vamshi.field.ui.theme.PerformanceRedText
import com.vamshi.field.ui.theme.SportOrangeContainer
import com.vamshi.field.util.CsvExporter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SessionReportScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAthlete: (String, String) -> Unit,
    onResumeTesting: (String, String?, String?, List<String>?) -> Unit,
    onNavigateToAiCoach: (String?) -> Unit,
    viewModel: SessionReportViewModel = hiltViewModel(),
    aiCoachViewModel: AiCoachViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val sessionId = uiState.data?.event?.id ?: ""
    val groupId = uiState.data?.group?.id ?: ""

    LaunchedEffect(uiState.isDeleted) {
        if (uiState.isDeleted) {
            onNavigateBack()
        }
    }

    LaunchedEffect(viewModel.exportEvent) {
        viewModel.exportEvent.collect { request ->
            when (request) {
                is SessionReportViewModel.ExportRequest.Event -> {
                    CsvExporter.exportEventResults(context, request.eventName, request.results, request.tests)
                }
            }
        }
    }

    SessionReportContent(
        uiState = uiState,
        onAction = { action ->
            when (action) {
                SessionReportAction.OnNavigateBack -> onNavigateBack()
                is SessionReportAction.OnNavigateToAthlete ->
                    onNavigateToAthlete(action.individualId, sessionId)
                SessionReportAction.OnResumeTesting -> {
                    if (sessionId.isNotEmpty()) {
                        val allRows = uiState.data?.leaderboardByTest?.values?.flatten().orEmpty() + uiState.data?.absentByTest?.values?.flatten().orEmpty()
                        val athleteId = allRows.firstOrNull()?.individualId
                        val testIds = uiState.data?.tests?.map { it.id }
                        onResumeTesting(sessionId, groupId.takeIf { it.isNotEmpty() }, athleteId, testIds)
                    }
                }
                else -> viewModel.onAction(action)
            }
        },
        aiCoachViewModel = aiCoachViewModel,
        onNavigateToAiCoach = onNavigateToAiCoach
    )

    if (uiState.showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.onAction(SessionReportAction.OnDismissDelete) },
            title = { Text("Delete Event?") },
            text = { Text("Are you sure you want to permanently delete this event and all associated test results? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { viewModel.onAction(SessionReportAction.OnConfirmDelete) }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onAction(SessionReportAction.OnDismissDelete) }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun SessionReportContent(
    uiState: SessionReportUiState,
    aiCoachViewModel: AiCoachViewModel,
    onNavigateToAiCoach: (String?) -> Unit = {},
    onAction: (SessionReportAction) -> Unit
) {
    val aiCoachState by aiCoachViewModel.uiState.collectAsState()
    val isAiCoachVisible = aiCoachState.status != AiCoachStatus.UNSUPPORTED
    val data = uiState.data
    val df = remember { SimpleDateFormat("EEEE, MMM d", Locale.getDefault()) }

    Scaffold(
        topBar = {
            AppTopBar(
                title = {
                    Column {
                        Text(
                            data?.event?.let { df.format(Date(it.date)) } ?: "Session",
                            style = MaterialTheme.typography.titleLarge
                        )
                        data?.group?.name?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall, color = AppTopBarSubtitleColor)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { onAction(SessionReportAction.OnNavigateBack) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (data != null) {
                        if (data.tests.isNotEmpty()) {
                            AppTopBarActionButton(
                                icon = Icons.Default.Lightbulb,
                                contentDescription = "Coach Insight",
                                onClick = { onAction(SessionReportAction.OnOpenInsight) }
                            )
                        }
                        if (isAiCoachVisible) {
                            AppTopBarActionButton(
                                icon = Icons.Default.AutoAwesome,
                                contentDescription = "AI Coach",
                                onClick = {
                                    val contextString = data.let { d ->
                                        "Session: ${d.event.name}\nTotal Athletes: ${d.totalAthletes}\n" +
                                        "Tests:\n" + d.tests.joinToString("\n") { it.name }
                                    }
                                    onNavigateToAiCoach(contextString)
                                }
                            )
                        }
                        if (uiState.isExporting) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = Color.White)
                        } else {
                            AppTopBarActionButton(
                                icon = Icons.Default.Download,
                                contentDescription = "Export CSV",
                                onClick = { onAction(SessionReportAction.OnExportCsv) }
                            )
                        }
                        IconButton(onClick = { onAction(SessionReportAction.OnRequestDelete) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Event", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        }
    ) { padding ->
        when {
            uiState.isLoading -> CenterSpinner()
            data == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Session not found", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> SessionReportBody(uiState = uiState, padding = padding, onAction = onAction)
        }
        if (uiState.isSwitcherOpen && data != null) {
            SessionSwitcherSheet(
                sessions = data.groupSessions,
                currentId = data.event.id,
                onPick = { onAction(SessionReportAction.OnSwitchSession(it.id)) },
                onDismiss = { onAction(SessionReportAction.OnDismissSwitcher) }
            )
        }
        if (uiState.isInsightSheetOpen && data != null) {
            val activeTestId = uiState.selectedTestId ?: data.tests.firstOrNull()?.id
            val activeTest = data.tests.find { it.id == activeTestId }
            val activeRows = activeTestId?.let { data.leaderboardByTest[it] }.orEmpty()
            CoachInsightSheet(
                test = activeTest,
                redZoneAthletes = activeRows.filter { it.percentile != null && it.percentile < 30 },
                onDismiss = { onAction(SessionReportAction.OnDismissInsight) }
            )
        }
    }
}

@Composable
fun SessionReportBody(
    uiState: SessionReportUiState,
    padding: PaddingValues,
    onAction: (SessionReportAction) -> Unit,
    headerContent: @Composable (() -> Unit)? = null
) {
    val data = uiState.data!!
    val activeTestId = uiState.selectedTestId ?: data.tests.firstOrNull()?.id
    val activeRows = activeTestId?.let { data.leaderboardByTest[it] }.orEmpty()
    val absent = activeTestId?.let { data.absentByTest[it] }.orEmpty()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (headerContent != null) {
            item { headerContent() }
        }

        if (data.tests.isNotEmpty()) {
            // Primary control: prominent test selector
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                        Text(
                            "TEST",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                        Spacer(Modifier.height(8.dp))
                        if (data.tests.size > 1) {
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                data.tests.forEach { t ->
                                    FilterChip(
                                        selected = t.id == activeTestId,
                                        onClick = { onAction(SessionReportAction.OnSelectTest(t.id)) },
                                        label = { Text(t.name, maxLines = 1, style = MaterialTheme.typography.labelMedium) },
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                }
                            }
                        } else {
                            Text(
                                data.tests.first().name,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            // Stats Summary
            val validScores = activeRows.mapNotNull { it.rawScore }
            val maxVal = if (validScores.isNotEmpty()) validScores.maxOrNull() ?: 0.0 else 0.0
            val minVal = if (validScores.isNotEmpty()) validScores.minOrNull() ?: 0.0 else 0.0
            val avgVal = if (validScores.isNotEmpty()) validScores.average() else 0.0
            val unit = activeRows.firstOrNull()?.unit ?: ""

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                        Text("Session Metrics", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                        Spacer(Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            StatSummaryItem("Max", maxVal, unit, modifier = Modifier.weight(1f))
                            StatSummaryItem("Avg", avgVal, unit, modifier = Modifier.weight(1f))
                            StatSummaryItem("Min", minVal, unit, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Leaderboard", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }

            if (activeRows.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        Text("No results recorded for this test yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                items(activeRows, key = { it.individualId }) { row ->
                    AthleteLeaderRow(
                        row = row,
                        onClick = { onAction(SessionReportAction.OnNavigateToAthlete(row.individualId)) }
                    )
                }
            }

            // Absent subsection
            if (absent.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Absent (${absent.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                items(absent, key = { "absent-${it.individualId}" }) { row ->
                    AbsentAthleteRow(row = row, onClick = { onAction(SessionReportAction.OnResumeTesting) })
                }
            }

            // Group trend
            item {
                Spacer(Modifier.height(4.dp))
                Text("Group trend", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            item {
                val trend = activeTestId?.let { data.groupTrendByTest[it] }.orEmpty()
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(24.dp),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    if (trend.size < 2) {
                        Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text("Need 2+ sessions for trend analysis", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxWidth().height(160.dp).padding(24.dp), contentAlignment = Alignment.Center) {
                            TrendBars(points = trend)
                        }
                    }
                }
            }

            // Missing-data card
            val missingNames = activeTestId?.let { data.missingByTest[it] }.orEmpty()
            if (missingNames.isNotEmpty()) {
                item {
                    MissingDataCard(
                        names = missingNames,
                        onResume = { onAction(SessionReportAction.OnResumeTesting) }
                    )
                }
            }
        } else {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("No tests in this session.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AbsentAthleteRow(row: LeaderboardRow, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(row.athleteName, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            ZoneChip(classification = Classification.NO_DATA, label = "Absent")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MissingDataCard(names: List<String>, onResume: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SportOrangeContainer),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onResume).padding(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Missing data: ${names.size} athlete${if (names.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "Resume testing",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(6.dp))
            names.forEach { n -> Text("• $n", style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
fun TrendBars(points: List<Pair<Long, Float>>) {
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        val max = points.maxOf { it.second }.coerceAtLeast(1f)
        points.forEach { (_, v) ->
            val frac = (v / max).coerceIn(0f, 1f)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .width(20.dp)
                        .height((110 * frac).dp.coerceAtLeast(2.dp))
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                )
                Spacer(Modifier.height(4.dp))
                Text("${v.toInt()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoachInsightSheet(
    test: com.vamshi.field.domain.model.standards.FitnessTest?,
    redZoneAthletes: List<LeaderboardRow>,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lightbulb, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.width(8.dp))
                Text("Coach's Insight", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                InsightSection(title = "MEASURES", content = getMeasuresText(test))
                InsightSection(title = "PRIMARY FOCUS", content = getFocusText(test))
                InsightSection(title = "RECOMMENDED IMPROVEMENT", content = getImprovementText(test))
            }

            if (redZoneAthletes.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = PerformanceRed.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, PerformanceRed)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = PerformanceRedText)
                            Spacer(Modifier.width(8.dp))
                            Text("Remediation Required (<30%ile)", color = PerformanceRedText, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        }
                        Spacer(Modifier.height(8.dp))
                        val names = redZoneAthletes.joinToString(", ") { row ->
                            val valStr = if (row.rawScore != null && row.rawScore % 1.0 == 0.0) row.rawScore.toInt().toString() else String.format("%.1f", row.rawScore)
                            "${row.athleteName} ($valStr ${row.unit})"
                        }
                        Text("Immediate focus needed for: $names.", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun InsightSection(title: String, content: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = content,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun getMeasuresText(test: com.vamshi.field.domain.model.standards.FitnessTest?): String = when {
    test == null -> "Select a test."
    test.name.contains("Jump", ignoreCase = true) -> "Lower-body explosive power and vertical displacement."
    test.name.contains("Sprint", ignoreCase = true) -> "Acceleration phase efficiency and maximal linear velocity."
    test.name.contains("Squat", ignoreCase = true) -> "Absolute lower-body muscular strength baseline."
    test.name.contains("Beep", ignoreCase = true) || test.name.contains("PACER", ignoreCase = true) -> "Aerobic capacity (VO2 max) and fatigue resistance."
    test.name.contains("Agility", ignoreCase = true) -> "Lateral acceleration, deceleration, and change-of-direction mechanics."
    else -> "Fitness performance relative to age and sex standards."
}

private fun getFocusText(test: com.vamshi.field.domain.model.standards.FitnessTest?): String = when {
    test == null -> "Select a test."
    test.name.contains("Jump", ignoreCase = true) -> "Plyometric training, triple extension, and landing mechanics."
    test.name.contains("Sprint", ignoreCase = true) -> "Drive phase body angle, arm drive, and hamstring conditioning."
    test.name.contains("Squat", ignoreCase = true) -> "Progressive overload, depth consistency, and core stability."
    test.name.contains("Beep", ignoreCase = true) || test.name.contains("PACER", ignoreCase = true) -> "High-intensity interval training (HIIT) and aerobic base."
    test.name.contains("Agility", ignoreCase = true) -> "Center of gravity control, footwork precision, and braking force."
    else -> "General athletic development and balanced conditioning."
}

private fun getImprovementText(test: com.vamshi.field.domain.model.standards.FitnessTest?): String = when {
    test == null -> "Select a test."
    test.name.contains("Jump", ignoreCase = true) -> "Box jumps, depth jumps, and power cleans for explosive development."
    test.name.contains("Sprint", ignoreCase = true) -> "Resisted sprinting, wall drills, and mobility work for stride length."
    test.name.contains("Squat", ignoreCase = true) -> "Goblet squats for technique, followed by back/front squat cycles."
    test.name.contains("Beep", ignoreCase = true) || test.name.contains("PACER", ignoreCase = true) -> "Interval runs (400m repeats) and long-duration steady-state cardio."
    test.name.contains("Agility", ignoreCase = true) -> "5-10-5 shuttle drills, ladder work, and deceleration stops."
    else -> "Follow standard age-appropriate fitness progression protocols."
}

@Composable
fun StatSummaryItem(
    label: String,
    value: Double,
    unit: String,
    modifier: Modifier = Modifier
) {
    val valueStr = if (value % 1.0 == 0.0) value.toInt().toString() else String.format("%.1f", value)
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(valueStr, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
            Text(unit, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CenterSpinner() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
