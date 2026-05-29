package com.rbitton.calendae.ui.calendar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rbitton.calendae.data.CalendarInfo

/**
 * The scrollable list of calendars with color swatches and visibility toggles.
 * Shared by the popup ([CalendarsDialog]) and the unfolded pane
 * ([CalendarsPaneContent]).
 */
@Composable
fun CalendarsList(
    calendars: List<CalendarInfo>,
    enabledIds: Set<Long>,
    onToggle: (id: Long, enabled: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.verticalScroll(rememberScrollState())) {
        calendars.forEach { calendar ->
            val enabled = calendar.id in enabledIds
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onToggle(calendar.id, !enabled) }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = enabled, onCheckedChange = { onToggle(calendar.id, it) })
                Spacer(Modifier.width(4.dp))
                ColorSwatch(calendar.color)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        calendar.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (calendar.accountName.isNotBlank() &&
                        calendar.accountName != calendar.displayName
                    ) {
                        Text(
                            calendar.accountName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** Popup form, used on a folded phone. */
@Composable
fun CalendarsDialog(
    calendars: List<CalendarInfo>,
    enabledIds: Set<Long>,
    onToggle: (id: Long, enabled: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Calendars") },
        text = { CalendarsList(calendars, enabledIds, onToggle) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

/** Full-pane form, used as a book page when unfolded (never a popup). */
@Composable
fun CalendarsPaneContent(
    calendars: List<CalendarInfo>,
    enabledIds: Set<Long>,
    onToggle: (id: Long, enabled: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Calendars", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.width(0.dp))
        CalendarsList(calendars, enabledIds, onToggle, Modifier.weight(1f).padding(top = 12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(onClick = onDismiss) { Text("Done") }
        }
    }
}
