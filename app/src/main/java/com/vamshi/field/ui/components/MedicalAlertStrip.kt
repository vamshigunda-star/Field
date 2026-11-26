package com.vamshi.field.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Medical alert / restriction shown wherever an athlete is about to be tested.
 *
 * Safety-critical and deliberately always visible — no tooltip, no long-press. It used to appear only
 * as a 16 dp icon in the grid and nowhere on the stopwatch or Quick Test screens. Draws nothing when
 * the athlete has neither an alert nor a restriction.
 */
@Composable
fun MedicalAlertStrip(
    medicalAlert: String?,
    isRestricted: Boolean,
    modifier: Modifier = Modifier
) {
    val text = medicalAlertText(medicalAlert, isRestricted) ?: return
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Medical alert: $text" },
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

/** "Asthma", "Restricted · Asthma", "Restricted", or null when there is nothing to warn about. */
fun medicalAlertText(medicalAlert: String?, isRestricted: Boolean): String? {
    val alert = medicalAlert?.trim()?.takeIf { it.isNotEmpty() }
    return when {
        isRestricted && alert != null -> "Restricted · $alert"
        isRestricted -> "Restricted"
        else -> alert
    }
}
