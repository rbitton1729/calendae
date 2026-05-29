package com.rbitton.calendae.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rbitton.calendae.data.CalendarEvent
import com.rbitton.calendae.data.CalendarInfo
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val EditorDateFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())

/**
 * Create or edit an event. When [event] is null this is a new event on [date];
 * otherwise the fields are pre-filled and a Delete action is offered. New events
 * may choose a target calendar from [writableCalendars].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventEditorDialog(
    date: LocalDate,
    event: CalendarEvent?,
    writableCalendars: List<CalendarInfo>,
    onDismiss: () -> Unit,
    onSave: (title: String, start: LocalTime, end: LocalTime, calendarId: Long?) -> Unit,
    onDelete: () -> Unit,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    val isEditing = event != null
    val initialStart = remember { event?.startTime(zone) ?: LocalTime.now().withMinute(0) }
    val initialEnd = remember { event?.endTime(zone) ?: initialStart.plusHours(1) }

    var title by remember { mutableStateOf(event?.title.orEmpty()) }
    var calendarId by remember {
        mutableStateOf(event?.calendarId ?: writableCalendars.firstOrNull()?.id)
    }
    val startState = rememberTimePickerState(initialStart.hour, initialStart.minute, true)
    val endState = rememberTimePickerState(initialEnd.hour, initialEnd.minute, true)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditing) "Edit event" else "New event") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = date.format(EditorDateFormat),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 12.dp),
                )
                if (!isEditing && writableCalendars.size > 1) {
                    CalendarSelector(
                        calendars = writableCalendars,
                        selectedId = calendarId,
                        onSelect = { calendarId = it },
                    )
                    Spacer(Modifier.size(12.dp))
                }
                LabeledTimeField("Starts", startState)
                Spacer(Modifier.size(8.dp))
                LabeledTimeField("Ends", endState)
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = {
                    val start = LocalTime.of(startState.hour, startState.minute)
                    var end = LocalTime.of(endState.hour, endState.minute)
                    if (!end.isAfter(start)) end = start.plusHours(1)
                    onSave(title.trim(), start, end, calendarId)
                },
            ) { Text(if (isEditing) "Save" else "Add") }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isEditing) {
                    TextButton(onClick = onDelete) { Text("Delete") }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LabeledTimeField(label: String, state: androidx.compose.material3.TimePickerState) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = 4.dp))
        TimeInput(state = state)
    }
}

@Composable
private fun CalendarSelector(
    calendars: List<CalendarInfo>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = calendars.firstOrNull { it.id == selectedId } ?: calendars.first()

    Column {
        Text("Calendar", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = 4.dp))
        Box {
            Row(
                Modifier.fillMaxWidth().clickable { expanded = true }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ColorSwatch(selected.color)
                Spacer(Modifier.width(8.dp))
                Text(selected.displayName, fontWeight = FontWeight.Medium)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                calendars.forEach { calendar ->
                    DropdownMenuItem(
                        leadingIcon = { ColorSwatch(calendar.color) },
                        text = { Text(calendar.displayName) },
                        onClick = {
                            onSelect(calendar.id)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
internal fun ColorSwatch(argb: Int, size: Int = 14) {
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(swatchColor(argb)),
    )
}

internal fun swatchColor(argb: Int): Color =
    if (argb != 0) Color(0xFF000000 or (argb.toLong() and 0xFFFFFF)) else Color(0xFF6650A4)
