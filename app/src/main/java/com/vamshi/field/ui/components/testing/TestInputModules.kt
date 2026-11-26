package com.vamshi.field.ui.components.testing

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vamshi.field.domain.model.standards.InputParadigm
import com.vamshi.field.domain.model.standards.StageLimits
import com.vamshi.field.domain.model.standards.StageScore

/**
 * The Switcher that chooses the right module based on test paradigm
 */
@Composable
fun TestInputSwitcher(
    paradigm: InputParadigm,
    currentValue: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    // Reach tests (back scratch, toe touch, trunk flexion) record a gap as a negative score.
    allowNegative: Boolean = false,
    // Beep and Yo-Yo cap the stage picker; custom Levels tests pass null.
    stageLimits: StageLimits? = null
) {
    when (paradigm) {
        InputParadigm.NUMERIC -> NumericInputModule(currentValue, onValueChange, onSubmit, allowNegative)
        InputParadigm.INCREMENTAL -> IncrementalInputModule(currentValue, onValueChange, onSubmit)
        InputParadigm.MULTI_STAGE -> StageInputModule(currentValue, onValueChange, onSubmit, stageLimits)
        else -> NumericInputModule(currentValue, onValueChange, onSubmit, allowNegative)
    }
}

@Composable
private fun NumericInputModule(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    allowNegative: Boolean
) {
    val keys = listOf(
        listOf("7", "8", "9"),
        listOf("4", "5", "6"),
        listOf("1", "2", "3"),
        if (allowNegative) listOf("±", ".", "0", "BACKSPACE") else listOf(".", "0", "BACKSPACE")
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
                                "±" -> onValueChange(if (value.startsWith("-")) value.drop(1) else "-$value")
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

/**
 * Rep / count entry. Coaches use it two ways: tapping +1 while the athlete performs, or typing
 * the total afterwards. +1 alone made a 60-rep squat test cost 60 taps, so the keypad is the
 * primary path and the ±1 row stays for live counting and fine correction.
 */
@Composable
private fun IncrementalInputModule(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    // A saved score re-opens as "48.0" (scoreInputText); edit the whole-number form so typing a
    // digit gives 485, not "48.05".
    val digits = value.toDoubleOrNull()?.toInt()?.toString() ?: ""
    val count = digits.toIntOrNull() ?: 0
    LaunchedEffect(value) {
        if (value != digits && value.toDoubleOrNull() != null) onValueChange(digits)
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalButton(
                onClick = { if (count > 0) onValueChange((count - 1).toString()) },
                enabled = count > 0,
                modifier = Modifier.weight(1f).height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Icon(Icons.Default.Remove, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("1", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            FilledTonalButton(
                onClick = { if (count < MAX_COUNT) onValueChange((count + 1).toString()) },
                modifier = Modifier.weight(2f).height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("1", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        }

        val keys = listOf(
            listOf("7", "8", "9"),
            listOf("4", "5", "6"),
            listOf("1", "2", "3"),
            listOf("C", "0", "BACKSPACE")
        )
        keys.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { key ->
                    InputButton(
                        text = key,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            when (key) {
                                "BACKSPACE" -> onValueChange(digits.dropLast(1))
                                "C" -> onValueChange("")
                                else -> {
                                    // "0" then "5" reads as 5, not 05.
                                    val next = if (digits == "0") key else digits + key
                                    if (next.length <= MAX_COUNT.toString().length) onValueChange(next)
                                }
                            }
                        },
                        isDelete = key == "BACKSPACE"
                    )
                }
            }
        }

        // 0 is a real result: no pull-ups, or a clean Flamingo/BESS trial with zero falls/errors.
        val isValid = digits.isNotEmpty()
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

/** Four digits covers any rep or catch count a field test produces and keeps the display on one line. */
private const val MAX_COUNT = 9999

@Composable
private fun StageInputModule(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    limits: StageLimits?
) {
    val maxLevel = limits?.maxLevel ?: Int.MAX_VALUE
    // Two digits of shuttle fit the stored encoding (see StageScore); a known test caps lower.
    val maxShuttle = limits?.maxShuttle ?: StageScore.MAX_SHUTTLE

    // An unrecorded cell arrives as "", and "".toInt() threw — opening the Beep or Yo-Yo
    // entry crashed the app.
    var level by remember { mutableIntStateOf(value.split(".").firstOrNull()?.toIntOrNull() ?: 1) }
    var shuttle by remember { mutableIntStateOf(value.split(".").getOrNull(1)?.toIntOrNull() ?: 1) }

    // SAVE is enabled from the start, so publish the 1.1 default; otherwise saving an untouched
    // picker submits "" and silently records nothing.
    LaunchedEffect(Unit) {
        if (value.isEmpty()) onValueChange("$level.$shuttle")
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            PickerColumn(
                label = "LEVEL",
                currentValue = level,
                onIncrement = { if (level < maxLevel) level++; onValueChange("$level.$shuttle") },
                onDecrement = { if (level > 1) level--; onValueChange("$level.$shuttle") },
                modifier = Modifier.weight(1f)
            )

            PickerColumn(
                label = "SHUTTLE",
                currentValue = shuttle,
                onIncrement = { if (shuttle < maxShuttle) shuttle++; onValueChange("$level.$shuttle") },
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
