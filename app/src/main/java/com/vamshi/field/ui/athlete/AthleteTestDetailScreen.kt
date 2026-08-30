package com.vamshi.field.ui.athlete

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vamshi.field.domain.model.reports.AttemptRow
import com.vamshi.field.domain.model.reports.Classification
import com.vamshi.field.domain.model.reports.LeaderboardRow
import com.vamshi.field.ui.components.AppTopBar
import com.vamshi.field.ui.components.AppTopBarSubtitleColor
import com.vamshi.field.ui.report.components.ChartPoint
import com.vamshi.field.ui.report.components.ChartRangeFilter
import com.vamshi.field.ui.report.components.NormBandLineChart
import com.vamshi.field.ui.report.components.PercentileChip
import com.vamshi.field.ui.report.components.ZoneChip
import com.vamshi.field.ui.report.components.zoneColors
import com.vamshi.field.ui.report.components.zoneLabel
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import com.vamshi.field.ui.theme.ElectricBlue
import com.vamshi.field.ui.theme.SportOrange
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AthleteTestDetailScreen(
    athleteId: String,
    testId: String,
    contextSessionId: String? = null,
    onNavigateBack: () -> Unit,
    viewModel: AthleteTestDetailViewModel = hiltViewModel()
) {
    LaunchedEffect(athleteId, testId, contextSessionId) {
        if (athleteId.isNotBlank() && testId.isNotBlank()) {
            viewModel.loadDetail(athleteId, testId, contextSessionId)
        }
    }

    val uiState by viewModel.uiState.collectAsState()
    AthleteTestDetailContent(
        uiState = uiState,
        onAction = { action ->
            when (action) {
                AthleteTestDetailAction.OnNavigateBack -> onNavigateBack()
                else -> viewModel.onAction(action)
            }
        }
    )
    
    uiState.deleteCandidate?.let { attempt ->
        val df = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
        AlertDialog(
            onDismissRequest = { viewModel.onAction(AthleteTestDetailAction.OnDismissDelete) },
            title = { Text("Delete Test Result?") },
            text = { 
                val scoreStr = if (attempt.rawScore % 1.0 == 0.0) attempt.rawScore.toInt().toString() else String.format(Locale.getDefault(), "%.1f", attempt.rawScore)
                Text("This will permanently remove the score of $scoreStr recorded on ${df.format(Date(attempt.date))}. This action cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.onAction(AthleteTestDetailAction.OnConfirmDelete) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onAction(AthleteTestDetailAction.OnDismissDelete) }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AthleteTestDetailContent(
    uiState: AthleteTestDetailUiState,
    onAction: (AthleteTestDetailAction) -> Unit
) {
    val data = uiState.data
    Scaffold(
        topBar = {
            AppTopBar(
                title = {
                    Column {
                        Text(data?.test?.name ?: "Test", style = MaterialTheme.typography.titleLarge)
                        data?.athlete?.fullName?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall, color = AppTopBarSubtitleColor)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { onAction(AthleteTestDetailAction.OnNavigateBack) }) {
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
                Text("No data", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> DetailBody(uiState = uiState, padding = padding, onAction = onAction)
        }

        if (uiState.showPeerSheet && data != null) {
            ModalBottomSheet(onDismissRequest = { onAction(AthleteTestDetailAction.OnDismissPeerSheet) }) {
                PeerSheet(
                    rows = data.peerLeaderboard.orEmpty(),
                    highlightId = data.athlete.id,
                    title = "${data.test.name} · peers"
                )
            }
        }
    }
}

@Composable
private fun DetailBody(
    uiState: AthleteTestDetailUiState,
    padding: PaddingValues,
    onAction: (AthleteTestDetailAction) -> Unit
) {
    val data = uiState.data!!
    val latest = data.attempts.lastOrNull()
    val df = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
    val isDark = isSystemInDarkTheme()
    var attemptsExpanded by remember { mutableStateOf(true) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Top: History / Progression Chart Card (85% Chart / 15% Header)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Header Area (Compact ~15% space)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "History",
                                style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            if (data.attempts.isNotEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isDark) ElectricBlue.copy(alpha = 0.20f) else ElectricBlue.copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        text = "${data.attempts.size} attempts",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = ElectricBlue,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }

                        if (data.attempts.size >= 10) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                ChartRangeFilter.entries.forEach { range ->
                                    val isSelected = uiState.selectedRange == range
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.clickable { onAction(AthleteTestDetailAction.OnSelectRange(range)) }
                                    ) {
                                        Text(
                                            text = range.label,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 10.5.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Chart Canvas Area (~85% space)
                    if (data.attempts.size < 2) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Record 2 or more attempts to chart progress over time.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                        }
                    } else {
                        NormBandLineChart(
                            points = data.attempts.map {
                                ChartPoint(
                                    date = it.date,
                                    rawScore = it.rawScore,
                                    percentile = it.percentile,
                                    classificationLabel = it.classificationLabel
                                )
                            },
                            bands = data.bandsByDate,
                            isHigherBetter = data.test.isHigherBetter,
                            unit = data.test.unit,
                            lineColor = SportOrange,
                            selectedRange = uiState.selectedRange,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(280.dp)
                        )
                    }
                }
            }
        }

        // 2. Middle: Latest Result Card (Below Chart, strictly 2 uniform rows)
        item {
            val cls = latest?.classification ?: Classification.NO_DATA
            val colors = zoneColors(cls, isDark)

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = colors.bg.copy(alpha = if (isDark) 0.30f else 0.85f)),
                border = BorderStroke(1.dp, colors.fg.copy(alpha = 0.25f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Row 1: Label & Date (Left) + Classification ZoneChip (Right)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (latest != null) "Latest result • ${df.format(Date(latest.date))}" else "Latest result",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.fg
                        )

                        val latestLabel = latest?.classificationLabel?.takeIf { it.isNotBlank() }
                            ?: zoneLabel(cls)
                        ZoneChip(classification = cls, label = latestLabel)
                    }

                    // Row 2: Hero Score + Unit (Left) + Percentile Chip (Right)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = latest?.let { formatScore(it.rawScore) } ?: "—",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 24.sp
                                ),
                                color = colors.fg
                            )
                            Text(
                                text = data.test.unit,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = colors.fg.copy(alpha = 0.85f),
                                modifier = Modifier.padding(bottom = 3.dp)
                            )
                        }

                        PercentileChip(percentile = latest?.percentile)
                    }
                }
            }
        }

        // 3. Bottom: Attempts Accordion with Dynamic Dot & Discrete Sub-Cards
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Accordion Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { attemptsExpanded = !attemptsExpanded }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Dynamic Indicator Dot
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (data.attempts.isNotEmpty()) ElectricBlue else MaterialTheme.colorScheme.outlineVariant)
                        )
                        Spacer(Modifier.width(10.dp))

                        Text(
                            text = "Attempts (${data.attempts.size})",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )

                        if (data.peerLeaderboard != null) {
                            OutlinedButton(
                                onClick = { onAction(AthleteTestDetailAction.OnOpenPeerSheet) },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(Icons.Default.Groups, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Peers", style = MaterialTheme.typography.labelSmall)
                            }
                            Spacer(Modifier.width(8.dp))
                        }

                        val chevronRotation by animateFloatAsState(
                            targetValue = if (attemptsExpanded) 180f else 0f,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            label = "attemptsChevron"
                        )
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ExpandMore,
                                contentDescription = if (attemptsExpanded) "Collapse" else "Expand",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .size(18.dp)
                                    .rotate(chevronRotation)
                            )
                        }
                    }

                    if (attemptsExpanded) {
                        HorizontalDivider(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                        )

                        if (data.attempts.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No attempts yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                data.attempts.reversed().forEach { row ->
                                    AttemptRowView(
                                        row = row,
                                        unit = data.test.unit,
                                        onDelete = { onAction(AthleteTestDetailAction.OnRequestDelete(row)) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AttemptRowView(row: AttemptRow, unit: String, onDelete: () -> Unit) {
    val df = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
    val isDark = isSystemInDarkTheme()

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isDark) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.40f else 0.65f)
        ),
        shadowElevation = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. Left: Date & Classification
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = df.format(Date(row.date)),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                val attemptLabel = row.classificationLabel?.takeIf { it.isNotBlank() }
                    ?: zoneLabel(row.classification)
                ZoneChip(classification = row.classification, label = attemptLabel)
            }

            Spacer(Modifier.width(10.dp))

            // 2. Right: Score + Unit & Delete Action
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = formatScore(row.rawScore),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        ),
                        color = if (isDark) Color(0xFF60A5FA) else MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = unit,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete attempt",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.50f),
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PeerSheet(rows: List<LeaderboardRow>, highlightId: String, title: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        if (rows.isEmpty()) {
            Text("No peer results for this session.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
        rows.forEach { r ->
            val highlight = r.individualId == highlightId
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (highlight) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, RoundedCornerShape(6.dp))
                .padding(vertical = 8.dp, horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${r.rank}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(28.dp))
                Text(r.athleteName, modifier = Modifier.weight(1f), fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal)
                Text(r.rawScore?.let { formatScore(it) } ?: "—", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.width(8.dp))
                val peerLabel = r.classificationLabel?.takeIf { it.isNotBlank() }
                    ?: zoneLabel(r.classification)
                ZoneChip(classification = r.classification, label = peerLabel)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

private fun formatScore(s: Double): String =
    if (s % 1.0 == 0.0) s.toInt().toString() else String.format(Locale.getDefault(), "%.1f", s)
