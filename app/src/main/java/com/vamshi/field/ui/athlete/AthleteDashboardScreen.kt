package com.vamshi.field.ui.athlete

import android.app.Activity
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Download
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vamshi.field.domain.model.people.Individual
import com.vamshi.field.domain.model.reports.AthleteDashboardData
import com.vamshi.field.domain.model.reports.AthleteFlag
import com.vamshi.field.domain.model.reports.AthleteTestTile
import com.vamshi.field.domain.model.reports.Classification
import com.vamshi.field.domain.model.reports.FlagType
import com.vamshi.field.domain.model.standards.FitnessTest
import com.vamshi.field.ui.report.components.PercentileChip
import com.vamshi.field.ui.report.components.ZoneChip
import com.vamshi.field.ui.report.components.zoneLabel
import com.vamshi.field.ui.theme.AquaCyan
import com.vamshi.field.ui.theme.ElectricBlue
import com.vamshi.field.ui.theme.PerformanceGreen
import com.vamshi.field.ui.theme.PerformanceGreenDark
import com.vamshi.field.ui.theme.PerformanceGreenText
import com.vamshi.field.ui.theme.PerformanceGreenTextDark
import com.vamshi.field.ui.theme.PerformanceGreyText
import com.vamshi.field.ui.theme.PerformanceGreyTextDark
import com.vamshi.field.ui.theme.PerformanceRed
import com.vamshi.field.ui.theme.PerformanceRedDark
import com.vamshi.field.ui.theme.PerformanceRedText
import com.vamshi.field.ui.theme.PerformanceRedTextDark
import com.vamshi.field.ui.theme.PerformanceYellowText
import com.vamshi.field.ui.theme.PerformanceYellowTextDark
import com.vamshi.field.ui.theme.getCategoryVisual
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
import androidx.compose.foundation.isSystemInDarkTheme
import com.vamshi.field.ui.theme.*
import com.vamshi.field.util.CsvExporter
import kotlin.math.roundToInt
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(data?.athlete?.fullName ?: "Athlete", style = MaterialTheme.typography.titleLarge)
                            if (data != null && (data.athlete.medicalAlert != null || data.athlete.isRestricted)) {
                                Spacer(Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.errorContainer
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = data.athlete.medicalAlert ?: "Restricted",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                        if (data != null) {
                            val ind = data.athlete
                            val grp = data.groups.firstOrNull()?.name?.let { " • $it" } ?: ""
                            
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
                                "${ind.currentAge}y • ${ind.sex.name.lowercase().replaceFirstChar { it.uppercase() }}$grp • $healthText • $testCountText",
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
            CategoryRadarCard(
                radarData = uiState.radarData,
                hasResults = data.tiles.isNotEmpty()
            )
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
                IndividualTestBreakdownCard(
                    tiles = data.tiles,
                    collapsedCount = collapsedCount,
                    expanded = perTestExpanded,
                    onToggle = togglePerTest,
                    onNavigateToTest = { testId ->
                        onAction(AthleteDashboardAction.OnNavigateToTest(testId))
                    }
                )
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.width(8.dp))
            Column {
                if (athlete.isRestricted) Text("Restricted", color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Bold)
                athlete.medicalAlert?.let { Text(it, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}


@Composable
fun IndividualTestBreakdownCard(
    tiles: List<AthleteTestTile>,
    collapsedCount: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    onNavigateToTest: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val rotationAngle by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "chevronRotation"
    )
    val visibleTiles = if (expanded) tiles else tiles.take(collapsedCount)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 1. Primary Header Row (Same bright surface background as test cards)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() }
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(ElectricBlue)
                )
                Spacer(Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Individual Test Breakdown",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isDark) ElectricBlue.copy(alpha = 0.2f) else ElectricBlue.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = "${tiles.size}",
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (isDark) AquaCyan else ElectricBlue
                            )
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    val subtitle = if (expanded) {
                        "Showing all ${tiles.size} recorded metrics"
                    } else {
                        "Showing $collapsedCount preview • ${tiles.size} tests recorded"
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Interactive Animated Chevron Button
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDark) 0.5f else 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(20.dp)
                            .rotate(rotationAngle)
                    )
                }
            }

            // 2. Nested Test Items (Discrete Cards with Spaced Separation)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                visibleTiles.forEach { tile ->
                    TestBreakdownItemRow(
                        tile = tile,
                        onClick = { onNavigateToTest(tile.test.id) }
                    )
                }
            }

            // 3. Bottom Footer (Show all X tests button when collapsed)
            if (!expanded && tiles.size > collapsedCount) {
                HorizontalDivider(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                )
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggle() },
                    color = if (isDark) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f) else Color(0xFFF8FAFC)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Show all ${tiles.size} tests",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TestBreakdownItemRow(
    tile: AthleteTestTile,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isDark) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.40f else 0.65f)
        ),
        shadowElevation = 0.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. Left: Test Name & Classification
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = tile.test.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val tileLabel = tile.latestResult?.classification?.takeIf { it.isNotBlank() }
                    ?: zoneLabel(tile.classification)
                ZoneChip(classification = tile.classification, label = tileLabel)
            }

            Spacer(Modifier.width(12.dp))

            // 2. Right: Score + Unit & Subtle Chevron
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                tile.latestResult?.let { res ->
                    val s = if (res.rawScore % 1.0 == 0.0) res.rawScore.toInt().toString() else String.format(java.util.Locale.US, "%.1f", res.rawScore)
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = s,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            ),
                            color = if (isDark) Color(0xFF60A5FA) else MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = tile.test.unit,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }
                } ?: run {
                    Text(
                        text = "—",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "View test details",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.40f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun FlagListRow(flag: AthleteFlag, onClick: () -> Unit) {
    val isActionable = flag.testId != null || flag.testIds.isNotEmpty() || flag.type == FlagType.MISSING_DATA
    val textColor = MaterialTheme.colorScheme.onSecondaryContainer
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().then(if (isActionable) Modifier.clickable(onClick = onClick) else Modifier).padding(16.dp), 
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(flag.type.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }, fontWeight = FontWeight.SemiBold, color = textColor)
                Text(flag.message, style = MaterialTheme.typography.bodySmall, color = textColor)
                if (flag.type == FlagType.MISSING_DATA && flag.testNames.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    flag.testNames.forEach { name ->
                        Text("• $name", style = MaterialTheme.typography.labelSmall, color = textColor)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Tap to complete in Quick Test",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = textColor
                    )
                }
            }
            if (isActionable) {
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = textColor)
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
    val hasAxes = radarData != null && radarData.axisScores.size >= 3 && radarData.axisScores.any { it.testCount > 0 }
    
    if (hasAxes && radarData != null) {
        RadarChart(
            data = radarData,
            modifier = Modifier.fillMaxWidth()
        )
    } else {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "Skill Matrix",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        "10-dimension athletic percentile profile",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (hasResults) "Not enough percentile data to chart yet."
                        else "Record test results to generate the athlete's 10-dimension skill matrix.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }
        }
    }
}

/**
 * Modern Delta Trend Capsule Badge (e.g. +2 %ile, -3 %ile, or 0 / stable).
 */
@Composable
fun DeltaTrendBadge(deltaPercentile: Int?, modifier: Modifier = Modifier) {
    val isDark = isSystemInDarkTheme()
    val greenBg = if (isDark) PerformanceGreenDark.copy(alpha = 0.35f) else PerformanceGreen.copy(alpha = 0.7f)
    val greenFg = if (isDark) PerformanceGreenTextDark else PerformanceGreenText
    val redBg = if (isDark) PerformanceRedDark.copy(alpha = 0.35f) else PerformanceRed.copy(alpha = 0.7f)
    val redFg = if (isDark) PerformanceRedTextDark else PerformanceRedText
    val neutralBg = if (isDark) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else Color(0xFFF1F5F9)
    val neutralFg = MaterialTheme.colorScheme.onSurfaceVariant

    val (bg, fg, icon, text) = when {
        deltaPercentile == null -> Tuple4(neutralBg, neutralFg, Icons.AutoMirrored.Filled.TrendingFlat, "—")
        deltaPercentile > 0 -> Tuple4(greenBg, greenFg, Icons.AutoMirrored.Filled.TrendingUp, "+$deltaPercentile")
        deltaPercentile < 0 -> Tuple4(redBg, redFg, Icons.AutoMirrored.Filled.TrendingDown, "$deltaPercentile")
        else -> Tuple4(neutralBg, neutralFg, Icons.AutoMirrored.Filled.TrendingFlat, "0")
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = bg
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                color = fg
            )
        }
    }
}

/**
 * Micro Sparkline Curve displaying recent session performance trajectory.
 */
@Composable
fun MiniSparkline(
    points: List<Float>,
    color: Color,
    modifier: Modifier = Modifier
) {
    if (points.size < 2) return
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        val path = Path()
        val step = w / (points.size - 1)
        points.forEachIndexed { i, p ->
            val clampedP = p.coerceIn(0f, 1f)
            val x = i * step
            val y = h - (clampedP * (h - 3.dp.toPx()) + 1.5.dp.toPx())
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}
private data class Tuple4<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
