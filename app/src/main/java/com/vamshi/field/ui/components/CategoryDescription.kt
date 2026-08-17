package com.vamshi.field.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp

@Composable
fun CategoryDescription(
    description: String?,
    modifier: Modifier = Modifier,
    fallback: String = "No category description available.",
) {
    val realDescription = description?.trim()?.ifEmpty { null }
    val hasRealDescription = realDescription != null
    Text(
        text = realDescription ?: fallback,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (hasRealDescription) 1f else 0.7f),
        modifier = modifier.padding(horizontal = 16.dp, vertical = 6.dp)
    )
}
