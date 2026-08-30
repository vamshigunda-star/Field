package com.vamshi.field.ui.groupoverview

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vamshi.field.domain.model.reports.Distribution
import com.vamshi.field.domain.model.reports.SessionRow
import com.vamshi.field.domain.model.reports.TestTrendStrip
import com.vamshi.field.ui.components.AppTopBar
import com.vamshi.field.ui.report.components.DistributionBar
import com.vamshi.field.ui.report.components.MiniSparkline
import androidx.compose.foundation.isSystemInDarkTheme
import com.vamshi.field.ui.theme.PerformanceRed
import com.vamshi.field.ui.theme.PerformanceRedDark
import com.vamshi.field.ui.theme.PerformanceRedText
import com.vamshi.field.ui.theme.PerformanceRedTextDark
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun GroupOverviewScreen(
    onNavigateBack: () -> Unit,
    onNavigateToSession: (String, String) -> Unit,   // (groupId, sessionId)
    onNavigateToCreateSession: () -> Unit,
    viewModel: GroupOverviewViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    GroupOverviewContent(
        uiState = uiState,
        onAction = { action ->
            when (action) {
                GroupOverviewAction.OnNavigateBack -> onNavigateBack()
                is GroupOverviewAction.OnNavigateToSession -> onNavigateToSession(viewModel.groupId, action.sessionId)
                GroupOverviewAction.OnNavigateToCreateSession -> onNavigateToCreateSession()
                else -> viewModel.onAction(action)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupOverviewContent(
    uiState: GroupOverviewUiState,
    onAction: (GroupOverviewAction) -> Unit
) {
    val data = uiState.data
    Scaffold(
        topBar = {
            AppTopBar(
                title = data?.group?.name ?: "Group",
                navigationIcon = {
                    IconButton(onClick = { onAction(GroupOverviewAction.OnNavigateBack) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        when {
            uiState.isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            data == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Group not found", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> GroupOverviewBody(uiState = uiState, padding = padding, onAction = onAction)
        }

        val selectedId = uiState.selectedTrendTestId
        val selectedTrend = if (selectedId != null) data?.trends?.firstOrNull { it.test.id == selectedId } else null
        if (selectedTrend != null) {
            ModalBottomSheet(onDismissRequest = { onAction(GroupOverviewAction.OnDismissTestTrend) }) {
                TestTrendChartSheet(trend = selectedTrend)
            }
        }
    }
}

@Composable
private fun GroupOverviewBody(
    uiState: GroupOverviewUiState,
    padding: PaddingValues,
    onAction: (GroupOverviewAction) -> Unit
) {
    val data = uiState.data!!
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { GroupHealthSnapshot(data.athletes.size, data.distribution) }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Sessions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }

        if (data.sessions.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No sessions for this group yet.", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { onAction(GroupOverviewAction.OnNavigateToCreateSession) }) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.size(6.dp))
                            Text("Start a session")
                        }
                    }
                }
            }
        } else {
            items(data.sessions, key = { it.event.id }) { row ->
                SessionListRow(row = row, onClick = { onAction(GroupOverviewAction.OnNavigateToSession(row.event.id)) })
            }
        }

        item { Text("Per-test trends", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }

        if (data.trends.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("No data yet", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        } else {
            items(data.trends, key = { it.test.id }) { trend ->
                TestTrendRow(
                    trend = trend,
                    onClick = { onAction(GroupOverviewAction.OnSelectTestTrend(trend.test.id)) }
                )
            }
        }
    }
}

@Composable
private fun GroupHealthSnapshot(memberCount: Int, distribution: Distribution) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Group health", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("$memberCount athletes", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DistributionBar(distribution = distribution)
            val isDark = isSystemInDarkTheme()
            val supColor = if (isDark) Color(0xFF4ADE80) else Color(0xFF1B5E20)
            val hlthColor = if (isDark) Color(0xFFFDE047) else Color(0xFFF57F17)
            val needsColor = if (isDark) Color(0xFFF87171) else Color(0xFFB71C1C)
            val noDataColor = MaterialTheme.colorScheme.outlineVariant
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CountLabel("Superior", distribution.superior, supColor)
                CountLabel("Healthy", distribution.healthy, hlthColor)
                CountLabel("Needs Imp.", distribution.needsImprovement, needsColor)
                if (distribution.noData > 0) CountLabel("No Data", distribution.noData, noDataColor)
            }
        }
    }
}

@Composable
private fun CountLabel(label: String, count: Int, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(modifier = Modifier.size(8.dp).background(color, RoundedCornerShape(2.dp)))
        Text("$count $label", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun SessionListRow(row: SessionRow, onClick: () -> Unit) {
    val df = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
    val isDark = isSystemInDarkTheme()
    val flagBg = if (isDark) PerformanceRedDark else PerformanceRed
    val flagText = if (isDark) PerformanceRedTextDark else PerformanceRedText
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(row.event.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(df.format(Date(row.event.date)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Text(
                    "${row.testCount} test${if (row.testCount == 1) "" else "s"} · ${row.athletesTested}/${row.totalAthletes} tested",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (row.flagCount > 0) {
                Box(modifier = Modifier.background(flagBg, RoundedCornerShape(999.dp)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                    Text("${row.flagCount} flag${if (row.flagCount == 1) "" else "s"}", style = MaterialTheme.typography.labelSmall, color = flagText, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun TestTrendChartSheet(trend: TestTrendStrip) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(trend.test.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            "Group avg percentile across ${trend.points.size} session${if (trend.points.size == 1) "" else "s"}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        if (trend.points.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                Text("No data yet", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        } else {
            TrendLineChart(points = trend.points, modifier = Modifier.fillMaxWidth().height(240.dp))
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun TrendLineChart(points: List<Pair<Long, Float>>, modifier: Modifier = Modifier) {
    val isDark = isSystemInDarkTheme()
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val df = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }

    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val healthyColor = if (isDark) Color(0x33FDE047) else Color(0x1AF57F17)
    val superiorColor = if (isDark) Color(0x334ADE80) else Color(0x1A1B5E20)

    Canvas(modifier = modifier) {
        val totalWidth = size.width
        val totalHeight = size.height

        val leftPadding = 36.dp.toPx()
        val bottomPadding = 24.dp.toPx()
        val topPadding = 8.dp.toPx()
        val rightPadding = 8.dp.toPx()

        val w = totalWidth - leftPadding - rightPadding
        val h = totalHeight - bottomPadding - topPadding

        // Background bands: NeedsImp <35, Healthy 35-69, Superior >=70
        // Percentile 100 at top (topPadding), 0 at bottom (totalHeight - bottomPadding)
        val yAt = { p: Float -> topPadding + h * (1f - (p.coerceIn(0f, 100f) / 100f)) }

        drawRect(superiorColor, topLeft = Offset(leftPadding, yAt(100f)), size = Size(w, yAt(70f) - yAt(100f)))
        drawRect(healthyColor, topLeft = Offset(leftPadding, yAt(70f)), size = Size(w, yAt(35f) - yAt(70f)))

        // Gridlines and Y labels at 0, 35, 70, 100
        listOf(0f, 35f, 70f, 100f).forEach { p ->
            val y = yAt(p)
            drawLine(gridColor, start = Offset(leftPadding, y), end = Offset(totalWidth - rightPadding, y), strokeWidth = 1f)

            val textLayoutResult = textMeasurer.measure(p.toInt().toString(), style = labelStyle)
            drawText(
                textLayoutResult,
                topLeft = Offset(leftPadding - textLayoutResult.size.width - 8.dp.toPx(), y - textLayoutResult.size.height / 2f)
            )
        }

        if (points.isEmpty()) return@Canvas

        val minDate = points.first().first
        val maxDate = points.last().first
        val span = (maxDate - minDate).coerceAtLeast(1L)

        // X labels (Dates)
        points.forEachIndexed { i, (date, _) ->
            // Only draw first, last, and middle if there are few points, otherwise just first and last
            val shouldDraw = i == 0 || i == points.size - 1 || (points.size in 3..5 && i == points.size / 2)
            if (shouldDraw) {
                val x = leftPadding + w * ((date - minDate).toFloat() / span.toFloat())
                val label = df.format(Date(date))
                val textLayoutResult = textMeasurer.measure(label, style = labelStyle)

                val xOffset = when (i) {
                    0 -> x
                    points.size - 1 -> x - textLayoutResult.size.width
                    else -> x - textLayoutResult.size.width / 2f
                }

                drawText(
                    textLayoutResult,
                    topLeft = Offset(
                        xOffset.coerceIn(leftPadding, totalWidth - textLayoutResult.size.width - rightPadding),
                        totalHeight - textLayoutResult.size.height
                    )
                )
            }
        }

        if (points.size == 1) {
            drawCircle(lineColor, radius = 4.dp.toPx(), center = Offset(leftPadding + w / 2f, yAt(points[0].second)))
            return@Canvas
        }

        val path = Path()
        points.forEachIndexed { i, (date, pct) ->
            val x = leftPadding + w * ((date - minDate).toFloat() / span.toFloat())
            val y = yAt(pct)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, lineColor, style = Stroke(width = 2.5.dp.toPx()))

        points.forEach { (date, pct) ->
            val x = leftPadding + w * ((date - minDate).toFloat() / span.toFloat())
            drawCircle(lineColor, radius = 3.dp.toPx(), center = Offset(x, yAt(pct)))
        }
    }
}

@Composable
private fun TestTrendRow(trend: TestTrendStrip, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(trend.test.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text("${trend.points.size} session${if (trend.points.size == 1) "" else "s"}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val normalized = remember(trend.points) {
                val pts = trend.points.map { it.second }
                if (pts.isEmpty()) emptyList() else pts.map { it / 100f }
            }
            MiniSparkline(points = normalized)
        }
    }
}
