package com.vamshi.field.ui.components.testing

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vamshi.field.domain.model.standards.InputParadigm

/**
 * A semi-circular gauge that represents performance percentile (0-100)
 */
@Composable
fun PercentileGauge(
    percentile: Int?,
    modifier: Modifier = Modifier
) {
    val targetValue = (percentile ?: 0).toFloat() / 100f
    val animatedValue by animateFloatAsState(
        targetValue = targetValue,
        animationSpec = tween(durationMillis = 800),
        label = "gauge_animation"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.weight(1f)) {
            val trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            Canvas(modifier = Modifier.size(160.dp, 90.dp)) {
                val strokeWidth = 8.dp.toPx()
                
                // Background Arc (Track)
                drawArc(
                    color = trackColor,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                // Foreground Arc (Progress)
                if (percentile != null) {
                    drawArc(
                        brush = Brush.horizontalGradient(
                            colors = listOf(Color(0xFFEF4444), Color(0xFFFACC15), Color(0xFF22C55E))
                        ),
                        startAngle = 180f,
                        sweepAngle = 180f * animatedValue,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }
            }

            // Value Display
            Column(
                modifier = Modifier.offset(y = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (percentile != null) "${percentile}%" else "--",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black,
                    color = if (percentile == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "PERCENTILE",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * The Switcher that chooses the right module based on test paradigm
 */
@Composable
fun TestInputSwitcher(
    paradigm: InputParadigm,
    currentValue: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    when (paradigm) {
        InputParadigm.NUMERIC -> NumericInputModule(currentValue, onValueChange, onSubmit)
        InputParadigm.INCREMENTAL -> IncrementalInputModule(currentValue, onValueChange, onSubmit)
        InputParadigm.MULTI_STAGE -> StageInputModule(currentValue, onValueChange, onSubmit)
        else -> NumericInputModule(currentValue, onValueChange, onSubmit)
    }
}

@Composable
private fun NumericInputModule(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    val keys = listOf(
        listOf("7", "8", "9"),
        listOf("4", "5", "6"),
        listOf("1", "2", "3"),
        listOf(".", "0", "BACKSPACE")
    )

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        keys.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { key ->
                    InputButton(
                        text = key,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            when (key) {
                                "BACKSPACE" -> if (value.isNotEmpty()) onValueChange(value.dropLast(1))
                                "." -> if (!value.contains(".")) onValueChange(value + ".")
                                else -> onValueChange(value + key)
                            }
                        },
                        isDelete = key == "BACKSPACE"
                    )
                }
            }
        }
        
        // LARGE SUBMIT BUTTON
        val isValid = value.toDoubleOrNull() != null
        Button(
            onClick = onSubmit,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            enabled = isValid,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF16A34A),
                contentColor = Color.White,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
            ),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 4.dp,
                pressedElevation = 8.dp,
                disabledElevation = 0.dp
            ),
            border = if (!isValid) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null
        ) {
            Icon(Icons.Default.Check, null)
            Spacer(Modifier.width(8.dp))
            Text("SAVE", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun IncrementalInputModule(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    val count = value.toDoubleOrNull()?.toInt() ?: 0

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalIconButton(
                onClick = { if (count > 0) onValueChange((count - 1).toString()) },
                modifier = Modifier.size(72.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Decrease", modifier = Modifier.size(32.dp))
            }

            Text(
                text = count.toString(),
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold
            )

            FilledIconButton(
                onClick = { onValueChange((count + 1).toString()) },
                modifier = Modifier.size(72.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Icon(Icons.Default.Add, contentDescription = "Increase", modifier = Modifier.size(32.dp))
            }
        }

        val isValid = count > 0
        Button(
            onClick = onSubmit,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            enabled = isValid,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF16A34A),
                contentColor = Color.White,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
            ),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 4.dp,
                pressedElevation = 8.dp,
                disabledElevation = 0.dp
            ),
            border = if (!isValid) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null
        ) {
            Icon(Icons.Default.Check, null)
            Spacer(Modifier.width(8.dp))
            Text("SAVE", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun StageInputModule(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    var level by remember { mutableIntStateOf(value.split(".").firstOrNull()?.toInt() ?: 1) }
    var shuttle by remember { mutableIntStateOf(value.split(".").getOrNull(1)?.toInt() ?: 1) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            PickerColumn(
                label = "LEVEL",
                currentValue = level,
                onIncrement = { level++; onValueChange("$level.$shuttle") },
                onDecrement = { if (level > 1) level--; onValueChange("$level.$shuttle") },
                modifier = Modifier.weight(1f)
            )

            PickerColumn(
                label = "SHUTTLE",
                currentValue = shuttle,
                onIncrement = { shuttle++; onValueChange("$level.$shuttle") },
                onDecrement = { if (shuttle > 1) shuttle--; onValueChange("$level.$shuttle") },
                modifier = Modifier.weight(1f)
            )
        }

        val isValid = level > 0 && shuttle > 0
        Button(
            onClick = onSubmit,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            enabled = isValid,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF16A34A),
                contentColor = Color.White,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
            ),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 4.dp,
                pressedElevation = 8.dp,
                disabledElevation = 0.dp
            ),
            border = if (!isValid) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null
        ) {
            Icon(Icons.Default.Check, null)
            Spacer(Modifier.width(8.dp))
            Text("SAVE", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PickerColumn(
    label: String,
    currentValue: Int,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Card(
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(8.dp)) {
                IconButton(onClick = onIncrement) { Icon(Icons.Default.Add, null) }
                Text(currentValue.toString(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                IconButton(onClick = onDecrement) { Icon(Icons.Default.Remove, null) }
            }
        }
    }
}

@Composable
private fun InputButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDelete: Boolean = false
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(50.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (isDelete) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
        tonalElevation = 1.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (isDelete) {
                Icon(Icons.AutoMirrored.Filled.Backspace, null)
            } else {
                Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        }
    }
}
