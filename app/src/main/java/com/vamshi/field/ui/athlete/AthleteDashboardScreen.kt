package com.vamshi.field.ui.athlete

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vamshi.field.domain.model.people.Individual
import com.vamshi.field.domain.model.reports.AthleteDashboardData
import com.vamshi.field.domain.model.reports.AthleteFlag
import com.vamshi.field.domain.model.reports.AthleteTestTile
import com.vamshi.field.domain.model.reports.Classification
import com.vamshi.field.domain.model.reports.FlagType
import com.vamshi.field.domain.model.standards.FitnessTest
import com.vamshi.field.domain.repository.AiCoachStatus
import com.vamshi.field.domain.usecase.testing.AthleteRadarData
import com.vamshi.field.ui.aicoach.AiCoachViewModel
import com.vamshi.field.ui.components.AppTopBar
import com.vamshi.field.ui.components.AppTopBarSubtitleColor
import com.vamshi.field.ui.components.charts.RadarChart
import com.vamshi.field.ui.report.components.DeltaArrow
import com.vamshi.field.ui.report.components.PercentileChip
import com.vamshi.field.ui.report.components.ZoneChip
import com.vamshi.field.ui.report.components.zoneColors
import com.vamshi.field.ui.report.components.zoneLabel
import com.vamshi.field.ui.theme.PerformanceRed
import com.vamshi.field.ui.theme.PerformanceRedText
import com.vamshi.field.ui.theme.SportOrange
import com.vamshi.field.ui.theme.SportOrangeContainer
import com.vamshi.field.ui.theme.SportOrangeVariant
import com.vamshi.field.util.CsvExporter
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun AthleteDashboardScreen(
    athleteId: String,
    contextSessionId: String? = null,
    onNavigateBack: () -> Unit,
    onNavigateToTest: (String, String, String?) -> Unit,
    onStartQuickTest: (String, List<String>) -> Unit, // (athleteId, testIds)
    onNavigateToAiCoach: (String?) -> Unit,
    viewModel: AthleteDashboardViewModel = hiltViewModel(),
    aiCoachViewModel: AiCoachViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(athleteId, contextSessionId) {
        viewModel.loadDashboard(athleteId, contextSessionId)
    }

    LaunchedEffect(viewModel.exportEvent) {
        viewModel.exportEvent.collect { request ->
            when (request) {
                is AthleteDashboardViewModel.ExportRequest.Athlete -> {
                    CsvExporter.exportAthleteResults(context, request.athlete, request.results, request.tests)
                }
            }
        }
    }

    AthleteDashboardContent(
        uiState = uiState,
        onAction = { action ->
            when (action) {
                AthleteDashboardAction.OnNavigateBack -> onNavigateBack()
                is AthleteDashboardAction.OnNavigateToTest ->
                    onNavigateToTest(viewModel.athleteId, action.testId, viewModel.contextSessionId)
                is AthleteDashboardAction.OnStartQuickTest ->
                    onStartQuickTest(viewModel.athleteId, action.testIds)
                else -> viewModel.onAction(action)
            }
        },
        aiCoachViewModel = aiCoachViewModel,
        onNavigateToAiCoach = onNavigateToAiCoach
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AthleteDashboardContent(
    uiState: AthleteDashboardUiState,
    aiCoachViewModel: AiCoachViewModel,
    onNavigateToAiCoach: (String?) -> Unit = {},
    onAction: (AthleteDashboardAction) -> Unit
) {
    val aiCoachState by aiCoachViewModel.uiState.collectAsState()
    val isAiCoachVisible = aiCoachState.status != AiCoachStatus.UNSUPPORTED
    val data = uiState.data
    Scaffold(
        topBar = {
            AppTopBar(
                title = {
                    Column {
                        Text(data?.athlete?.fullName ?: "Athlete", style = MaterialTheme.typography.titleLarge)
                        if (data != null) {
                            val ind = data.athlete
                            val grp = data.groups.firstOrNull()?.name?.let { " � $it" } ?: ""
                            
                            val avg = data.athleteSessionAvgPctile
                            val cls = when {
                                avg == null -> Classification.NO_DATA
                                avg >= 60 -> Classification.SUPERIOR
                                avg >= 30 -> Classification.HEALTHY
                                else -> Classification.NEEDS_IMPROVEMENT
                            }
                            val healthText = zoneLabel(cls)
                            val testCountText = "${data.sessionTestCount} Test${if (data.sessionTestCount == 1) "" else "s"}"

                            Text(
                                "${ind.currentAge}y � ${ind.sex.name.lowercase().replaceFirstChar { it.uppercase() }}$grp � $healthText � $testCountText",
                                style = MaterialTheme.typography.labelSmall, color = AppTopBarSubtitleColor
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { onAction(AthleteDashboardAction.OnNavigateBack) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (data != null) {
                        if (uiState.isExporting) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = Color.White)
                        } else {
                            IconButton(onClick = { onAction(AthleteDashboardAction.OnExportCsv) }) {
                                Icon(Icons.Default.Download, contentDescription = "Export CSV")
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when {
                uiState.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                data == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Athlete not found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> AthleteBody(uiState = uiState, padding = PaddingValues(0.dp), onAction = onAction)
            }

            com.vamshi.field.ui.aicoach.components.DraggableAiFab(
                isVisible = isAiCoachVisible,
                onClick = {
                    val contextString = data?.let { d ->
                        "Athlete: ${d.athlete.fullName}\nAge: ${d.athlete.currentAge}\nAvg Percentile: ${d.athleteSessionAvgPctile}\nTest Results:\n" +
                        d.tiles.joinToString("\n") { t -> "${t.test.name}: ${t.latestResult?.rawScore} ${t.test.unit} (${t.latestResult?.percentile}th percentile)" }
                    }
                    onNavigateToAiCoach(contextString)
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun AthleteBody(
    uiState: AthleteDashboardUiState,
    padding: PaddingValues,
    onAction: (AthleteDashboardAction) -> Unit,
    headerContent: @Composable (() -> Unit)? = null
) {
    val data = uiState.data!!
    var perTestExpanded by rememberSaveable { mutableStateOf(false) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
    
    val context = LocalContext.current
    val activity = remember(context) {
        var c = context
        while (c is android.content.ContextWrapper) {
            if (c is Activity) break
            c = c.baseContext
        }
        c as? Activity
    }
    val collapsedCount = if (activity == null || calculateWindowSizeClass(activity).widthSizeClass == WindowWidthSizeClass.Compact) 1 else 2

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (headerContent != null) {
            item { headerContent() }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AthleteAlertCard(data.athlete)
                CategoryRadarCard(
                    radarData = uiState.radarData,
                    hasResults = data.tiles.isNotEmpty()
                )
            }
        }

        if (data.tiles.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No test results yet for ${data.athlete.fullName}.", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(6.dp))
                        Text("Add results from a session to populate this view.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            val togglePerTest: () -> Unit = {
                perTestExpanded = !perTestExpanded
                val targetIndex = if (headerContent != null) 2 else 1
                coroutineScope.launch {
                    if (perTestExpanded) {
                        listState.animateScrollToItem(targetIndex)
                    } else {
                        listState.animateScrollToItem(0)
                    }
                }
            }
            item {
                PerTestHeader(
                    testCount = data.tiles.size,
                    collapsedCount = collapsedCount,
                    expanded = perTestExpanded,
                    onToggle = togglePerTest
                )
            }
            val visibleTiles = if (perTestExpanded) data.tiles else data.tiles.take(collapsedCount)
            items(visibleTiles, key = { it.test.id }) { tile ->
                TestBreakdownRow(tile = tile, onClick = { onAction(AthleteDashboardAction.OnNavigateToTest(tile.test.id)) })
            }
            if (!perTestExpanded && data.tiles.size > collapsedCount) {
                item {
                    Text(
                        "Show all ${data.tiles.size}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = togglePerTest)
                            .padding(vertical = 8.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }

        if (data.flags.isNotEmpty()) {
            item { Text("Flags", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            items(data.flags, key = { "${it.type}-${it.message}" }) { f ->
                FlagListRow(
                    flag = f,
                    onClick = {
                        when {
                            f.testIds.isNotEmpty() ->
                                onAction(AthleteDashboardAction.OnStartQuickTest(f.testIds))
                            f.testId != null ->
                                onAction(AthleteDashboardAction.OnStartQuickTest(listOf(f.testId)))
                            f.type == FlagType.MISSING_DATA && data.outstandingTests.isNotEmpty() ->
                                onAction(AthleteDashboardAction.OnStartQuickTest(data.outstandingTests.map { it.id }))
                        }
                    }
                )
            }
        }

        if (data.outstandingTests.isNotEmpty()) {
            item {
                MissingTestsCard(
                    tests = data.outstandingTests,
                    onTestClick = { onAction(AthleteDashboardAction.OnStartQuickTest(listOf(it))) },
                    onStartQuickTest = { onAction(AthleteDashboardAction.OnStartQuickTest(data.outstandingTests.map { it.id })) }
                )
            }
        }
    }
}

@Composable
fun AthleteAlertCard(athlete: Individual) {
    if (athlete.medicalAlert == null && !athlete.isRestricted) return
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = PerformanceRed)) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = PerformanceRedText)
            Spacer(Modifier.width(8.dp))
            Column {
                if (athlete.isRestricted) Text("Restricted", color = PerformanceRedText, fontWeight = FontWeight.Bold)
                athlete.medicalAlert?.let { Text(it, color = PerformanceRedText, style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}


@Composable
fun TestBreakdownRow(tile: AthleteTestTile, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = tile.test.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val tileLabel = tile.latestResult?.classification?.takeIf { it.isNotBlank() }
                        ?: zoneLabel(tile.classification)
                    ZoneChip(classification = tile.classification, label = tileLabel)
                    PercentileChip(percentile = tile.latestResult?.percentile)
                }
                
                Text(
                    text = "${tile.rawSparkline.size} attempt${if (tile.rawSparkline.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = tile.latestResult?.let {
                        val s = if (it.rawScore % 1.0 == 0.0) it.rawScore.toInt().toString() else String.format("%.1f", it.rawScore)
                        "$s ${tile.test.unit}"
                    } ?: "�",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )
                DeltaArrow(deltaPercentile = tile.deltaPercentile)
            }
        }
    }
}

@Composable
fun FlagListRow(flag: AthleteFlag, onClick: () -> Unit) {
    val isActionable = flag.testId != null || flag.testIds.isNotEmpty() || flag.type == FlagType.MISSING_DATA
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SportOrangeContainer),
        border = BorderStroke(1.dp, SportOrange.copy(alpha = 0.12f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().then(if (isActionable) Modifier.clickable(onClick = onClick) else Modifier).padding(16.dp), 
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = SportOrangeVariant)
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(flag.type.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }, fontWeight = FontWeight.SemiBold)
                Text(flag.message, style = MaterialTheme.typography.bodySmall, color = SportOrangeVariant)
                if (flag.type == FlagType.MISSING_DATA && flag.testNames.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    flag.testNames.forEach { name ->
                        Text("� $name", style = MaterialTheme.typography.labelSmall, color = SportOrangeVariant)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Tap to complete in Quick Test",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = SportOrangeVariant
                    )
                }
            }
            if (isActionable) {
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = SportOrangeVariant)
            }
        }
    }
}

@Composable
fun MissingTestsCard(tests: List<FitnessTest>, onTestClick: (String) -> Unit, onStartQuickTest: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column {
                Text("Missing Tests", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("${tests.size} remaining", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                tests.forEach { test ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onTestClick(test.id) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(test.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text("Not attempted", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Button(onClick = onStartQuickTest, modifier = Modifier.fillMaxWidth()) {
                Text("Start Quick Test (${tests.size})")
            }
        }
    }
}

@Composable
fun CategoryRadarCard(radarData: AthleteRadarData?, hasResults: Boolean) {
    val hasAxes = radarData != null && radarData.axisScores.any { it.normalizedScore > 0f }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "Skill Matrix",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold
                )
                
                if (hasAxes) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        RadarChart(
                            data = radarData!!,
                            modifier = Modifier.height(280.dp),
                            color = SportOrange
                        )
                    }
                    
                    val scoredAxes = radarData.axisScores.filter { it.testCount > 0 }
                    if (scoredAxes.size >= 2) {
                        val strongest = scoredAxes.maxBy { it.normalizedScore }
                        val focus = scoredAxes.minBy { it.normalizedScore }
                        
                        if (strongest !== focus) {
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                InsightItem(
                                    label = "Strongest Area",
                                    value = strongest.label,
                                    classification = Classification.SUPERIOR
                                )
                                InsightItem(
                                    label = "Focus Area",
                                    value = focus.label,
                                    classification = Classification.NEEDS_IMPROVEMENT
                                )
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(160.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (hasResults) "Not enough percentile data to chart yet."
                            else "Record results to see a category profile.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
            
            if (hasAxes) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 24.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .padding(24.dp)
                ) {
                    Text(
                        "How to read: This chart shows average percentile per category. A larger area indicates a stronger overall athletic profile compared to norms.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun InsightItem(
    label: String,
    value: String,
    classification: Classification,
    modifier: Modifier = Modifier
) {
    val colors = zoneColors(classification)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(colors.bg, CircleShape)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun PerTestHeader(testCount: Int, collapsedCount: Int, expanded: Boolean, onToggle: () -> Unit) {
    val backgroundColor = if (expanded) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val contentColor = if (expanded) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { onToggle() }.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Individual Test Breakdown", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = contentColor)
                val subtitle = if (expanded) {
                    "Showing all $testCount tests recorded"
                } else {
                    "Showing $collapsedCount test${if (collapsedCount == 1) "" else "s"} preview � $testCount tests recorded"
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (expanded) contentColor.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = if (expanded) contentColor else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
