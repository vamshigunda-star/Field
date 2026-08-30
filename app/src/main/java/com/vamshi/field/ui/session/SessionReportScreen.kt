package com.vamshi.field.ui.session

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.vamshi.field.ui.theme.SportOrange
import com.vamshi.field.ui.components.tour.CoachMarkBanner
import com.vamshi.field.ui.components.tour.TestingTourDialog
import com.vamshi.field.ui.session.components.GroupTrendChart
import com.vamshi.field.ui.session.components.TestSelectorHeroCard
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
    aiCoachViewModel: AiCoachViewModel = hiltViewModel(),
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
        onNavigateToAiCoach = onNavigateToAiCoach,
    )

        if (uiState.showTestingTour) {
            TestingTourDialog(
                onDismiss = { viewModel.onAction(SessionReportAction.OnDismissTestingTour) },
            )
        }

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
    onAction: (SessionReportAction) -> Unit,
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
                        IconButton(onClick = { onAction(SessionReportAction.OnOpenTestingTour) }) {
                            Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = "Testing Guide")
                        }
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
        if ((uiState.isSwitcherOpen) && (data != null)) {
            SessionSwitcherSheet(
                sessions = data.groupSessions,
                currentId = data.event.id,
                onPick = { onAction(SessionReportAction.OnSwitchSession(it.id)) }
            ) { onAction(SessionReportAction.OnDismissSwitcher) }
        }
        if ((uiState.isInsightSheetOpen) && (data != null)) {
            val activeTestId = uiState.selectedTestId ?: data.tests.firstOrNull()?.id
            val activeTest = data.tests.find { it.id == activeTestId }
            val activeRows = activeTestId?.let { data.leaderboardByTest[it] }.orEmpty()
            CoachInsightSheet(
                test = activeTest,
                redZoneAthletes = activeRows.filter { (it.percentile != null) && (it.percentile < 30) }
            ) { onAction(SessionReportAction.OnDismissInsight) }
        }
    }
}

@Composable
fun SessionReportBody(
    uiState: SessionReportUiState,
    padding: PaddingValues,
    onAction: (SessionReportAction) -> Unit,
    headerContent: @Composable (() -> Unit)? = null,
) {
    val data = uiState.data!!
    val activeTestId = uiState.selectedTestId ?: data.tests.firstOrNull()?.id
    val activeRows = activeTestId?.let { data.leaderboardByTest[it] }.orEmpty()
    val absent = activeTestId?.let { data.absentByTest[it] }.orEmpty()
    val isDark = isSystemInDarkTheme()

    var isMetricsExpanded by remember { mutableStateOf(value = true) }
    var isLeaderboardExpanded by remember { mutableStateOf(value = true) }
    var isAbsentExpanded by remember { mutableStateOf(value = true) }
    var isMissingExpanded by remember { mutableStateOf(value = true) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (headerContent != null) {
            item { headerContent() }
        }

        if (!uiState.hasSeenCoachMark) {
            item {
                CoachMarkBanner(
                    title = "Session Analytics & Reports",
                    message = "Review group attendance and test distributions. Tap the Lightbulb for insights, Sparkles for AI Coach analysis, or Download to export CSV data.",
                    actionLabel = "View Tour",
                    onActionClick = { onAction(SessionReportAction.OnOpenTestingTour) }
                ) { onAction(SessionReportAction.OnDismissCoachMark) }
            }
        }

        if (data.tests.isNotEmpty()) {
            // Primary control: prominent test selector
            item {
                TestSelectorHeroCard(
                    tests = data.tests,
                    selectedTestId = activeTestId,
                    onSelectTest = { onAction(SessionReportAction.OnSelectTest(it)) }
                )
            }

            // Stats Summary Card (Collapsible)
            val validScores = activeRows.mapNotNull { it.rawScore }
            val maxVal = if (validScores.isNotEmpty()) validScores.maxOrNull() ?: 0.0 else 0.0
            val minVal = if (validScores.isNotEmpty()) validScores.minOrNull() ?: 0.0 else 0.0
            val avgVal = if (validScores.isNotEmpty()) validScores.average() else 0.0
            val unit = activeRows.firstOrNull()?.unit ?: ""

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.35f else 0.50f))
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        CollapsibleSectionHeader(
                            title = "Session Metrics",
                            isExpanded = isMetricsExpanded,
                            onToggle = { isMetricsExpanded = !isMetricsExpanded },
                            icon = Icons.Default.Analytics
                        )

                        AnimatedVisibility(
                            visible = isMetricsExpanded,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                StatSummaryItem("Max", maxVal, unit, modifier = Modifier.weight(1f))
                                StatSummaryItem("Avg", avgVal, unit, modifier = Modifier.weight(1f))
                                StatSummaryItem("Min", minVal, unit, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            // Leaderboard Card (Collapsible)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.35f else 0.50f))
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        CollapsibleSectionHeader(
                            title = "Leaderboard",
                            badgeText = if (activeRows.isNotEmpty()) "${activeRows.size} ranked" else null,
                            isExpanded = isLeaderboardExpanded,
                            onToggle = { isLeaderboardExpanded = !isLeaderboardExpanded },
                            icon = Icons.Default.Leaderboard
                        )

                        AnimatedVisibility(
                            visible = isLeaderboardExpanded,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (activeRows.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("No results recorded for this test yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                } else {
                                    activeRows.forEach { row ->
                                        AthleteLeaderRow(
                                            row = row,
                                            onClick = { onAction(SessionReportAction.OnNavigateToAthlete(row.individualId)) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Absent subsection Card (Collapsible)
            if (absent.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.35f else 0.50f))
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            CollapsibleSectionHeader(
                                title = "Absent",
                                badgeText = "${absent.size} athletes",
                                isExpanded = isAbsentExpanded,
                                onToggle = { isAbsentExpanded = !isAbsentExpanded },
                                icon = Icons.Default.PersonOff,
                                badgeColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                badgeTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            AnimatedVisibility(
                                visible = isAbsentExpanded,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    absent.forEach { row ->
                                        AbsentAthleteRow(row = row) { onAction(SessionReportAction.OnResumeTesting) }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Group trend progression (Collapsible Line Chart)
            item {
                val trend = activeTestId?.let { data.groupTrendByTest[it] }.orEmpty()
                GroupTrendChart(
                    points = trend,
                    currentSessionDate = data.event.date,
                    unit = unit
                )
            }

            // Missing-data Card (Collapsible)
            val missingNames = activeTestId?.let { data.missingByTest[it] }.orEmpty()
            if (missingNames.isNotEmpty()) {
                item {
                    MissingDataCard(
                        names = missingNames,
                        isExpanded = isMissingExpanded,
                        onToggle = { isMissingExpanded = !isMissingExpanded }
                    ) { onAction(SessionReportAction.OnResumeTesting) }
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

@Composable
fun AbsentAthleteRow(row: LeaderboardRow, onClick: () -> Unit) {
    val isDark = isSystemInDarkTheme()
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isDark) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.40f else 0.65f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = row.athleteName,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.5.sp),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            ZoneChip(classification = Classification.NO_DATA, label = "Absent")
        }
    }
}

@Composable
fun MissingDataCard(
    names: List<String>,
    isExpanded: Boolean = true,
    onToggle: () -> Unit = {},
    onResume: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "missingChevron"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.35f else 0.50f))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = SportOrange,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Missing Data",
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.5.sp),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = SportOrange.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = "${names.size} athlete${if (names.size == 1) "" else "s"}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = SportOrange,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Resume testing",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable(onClick = onResume)
                    )

                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ExpandMore,
                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(18.dp)
                                .rotate(chevronRotation)
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    names.forEach { n ->
                        Text(
                            text = "• $n",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
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
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                            Spacer(Modifier.width(8.dp))
                            Text("Remediation Required (<30%ile)", color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        }
                        Spacer(Modifier.height(8.dp))
                        val names = redZoneAthletes.joinToString(", ") { row ->
                            val valStr = if ((row.rawScore != null) && (row.rawScore % 1.0 == 0.0)) row.rawScore.toInt().toString() else String.format(Locale.getDefault(), "%.1f", row.rawScore)
                            "${row.athleteName} ($valStr ${row.unit})"
                        }
                        Text("Immediate focus needed for: $names.", color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall)
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
private fun CollapsibleSectionHeader(
    title: String,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    badgeText: String? = null,
    badgeColor: Color? = null,
    badgeTextColor: Color? = null
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "chevronRotation"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.5.sp),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (badgeText != null) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = badgeColor ?: MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = badgeTextColor ?: MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(18.dp)
                    .rotate(chevronRotation)
            )
        }
    }
}

@Composable
fun StatSummaryItem(
    label: String,
    value: Double,
    unit: String,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val valueStr = if ((value % 1.0) == 0.0) value.toInt().toString() else String.format(Locale.getDefault(), "%.1f", value)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = if (isDark) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.50f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.35f else 0.50f))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(3.dp))
            Text(valueStr, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
            if (unit.isNotBlank()) {
                Text(unit, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
            }
        }
    }
}

@Composable
private fun CenterSpinner() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
