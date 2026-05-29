package com.rbitton.calendae.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
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
import androidx.compose.ui.graphics.luminance
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
private val EditorTimeFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())

/** Which time field a [TimePickerDialog] is currently editing, if any. */
private enum class TimeField { NONE, START, END }

/**
 * Create or edit an event. When [event] is null this is a new event on [date];
 * otherwise the fields are pre-filled and a Delete action is offered. Times are
 * chosen with the circular clock dial. New events may pick a target calendar.
 */
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

    // Keyed on the event so clicking a different event re-initializes the fields.
    var title by remember(event?.id) { mutableStateOf(event?.title.orEmpty()) }
    var calendarId by remember(event?.id) {
        mutableStateOf(event?.calendarId ?: writableCalendars.firstOrNull()?.id)
    }
    var start by remember(event?.id) { mutableStateOf(event?.startTime(zone) ?: LocalTime.now().withMinute(0)) }
    var end by remember(event?.id) { mutableStateOf(event?.endTime(zone) ?: start.plusHours(1)) }
    var editing by remember(event?.id) { mutableStateOf(TimeField.NONE) }

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
                TimeRow("Starts", start.format(EditorTimeFormat)) { editing = TimeField.START }
                TimeRow("Ends", end.format(EditorTimeFormat)) { editing = TimeField.END }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = {
                    val safeEnd = if (end.isAfter(start)) end else start.plusHours(1)
                    onSave(title.trim(), start, safeEnd, calendarId)
                },
            ) { Text(if (isEditing) "Save" else "Add") }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isEditing) TextButton(onClick = onDelete) { Text("Delete") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )

    if (editing != TimeField.NONE) {
        val initial = if (editing == TimeField.START) start else end
        TimePickerDialog(
            initial = initial,
            onDismiss = { editing = TimeField.NONE },
            onConfirm = { picked ->
                if (editing == TimeField.START) {
                    // Keep the original duration when shifting the start.
                    val duration = java.time.Duration.between(start, end)
                    start = picked
                    end = picked.plus(duration)
                } else {
                    end = picked
                }
                editing = TimeField.NONE
            },
        )
    }
}

/**
 * Full-pane event editor, used as a book page when unfolded — never a popup.
 * Picking a time swaps the pane to an inline clock dial (a sub-stage) rather
 * than opening a nested dialog.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventEditorPaneContent(
    date: LocalDate,
    event: CalendarEvent?,
    writableCalendars: List<CalendarInfo>,
    onDismiss: () -> Unit,
    onSave: (title: String, start: LocalTime, end: LocalTime, calendarId: Long?) -> Unit,
    onDelete: () -> Unit,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    val isEditing = event != null

    // Keyed on the event so clicking a different event re-initializes the fields.
    var title by remember(event?.id) { mutableStateOf(event?.title.orEmpty()) }
    var calendarId by remember(event?.id) {
        mutableStateOf(event?.calendarId ?: writableCalendars.firstOrNull()?.id)
    }
    var start by remember(event?.id) { mutableStateOf(event?.startTime(zone) ?: LocalTime.now().withMinute(0)) }
    var end by remember(event?.id) { mutableStateOf(event?.endTime(zone) ?: start.plusHours(1)) }
    var editing by remember(event?.id) { mutableStateOf(TimeField.NONE) }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text(
            if (isEditing) "Edit event" else "New event",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        if (editing == TimeField.NONE) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
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
                    CalendarSelector(writableCalendars, calendarId) { calendarId = it }
                    Spacer(Modifier.size(12.dp))
                }
                TimeRow("Starts", start.format(EditorTimeFormat)) { editing = TimeField.START }
                TimeRow("Ends", end.format(EditorTimeFormat)) { editing = TimeField.END }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (isEditing) {
                    TextButton(onClick = onDelete) { Text("Delete") }
                    Spacer(Modifier.weight(1f))
                } else {
                    Spacer(Modifier.weight(1f))
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                Button(
                    enabled = title.isNotBlank(),
                    onClick = {
                        val safeEnd = if (end.isAfter(start)) end else start.plusHours(1)
                        onSave(title.trim(), start, safeEnd, calendarId)
                    },
                ) { Text(if (isEditing) "Save" else "Add") }
            }
        } else {
            val isStart = editing == TimeField.START
            val timeState = rememberTimePickerState(
                (if (isStart) start else end).hour,
                (if (isStart) start else end).minute,
                is24Hour = true,
            )
            Text(
                if (isStart) "Start time" else "End time",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                TimePicker(state = timeState)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { editing = TimeField.NONE }) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                    val picked = LocalTime.of(timeState.hour, timeState.minute)
                    if (isStart) {
                        val duration = java.time.Duration.between(start, end)
                        start = picked
                        end = picked.plus(duration)
                    } else {
                        end = picked
                    }
                    editing = TimeField.NONE
                }) { Text("Set") }
            }
        }
    }
}

/** A labeled, tappable row showing a chosen time. */
@Composable
private fun TimeRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
        )
    }
}

/**
 * A time picker presented as the circular clock dial, with a toggle to the
 * keyboard text entry for those who prefer it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(
    initial: LocalTime,
    onDismiss: () -> Unit,
    onConfirm: (LocalTime) -> Unit,
) {
    val state = rememberTimePickerState(initial.hour, initial.minute, is24Hour = true)
    var dialMode by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select time") },
        text = {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (dialMode) TimePicker(state = state) else TimeInput(state = state)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) { Text("OK") }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { dialMode = !dialMode }) {
                    Text(if (dialMode) "Keyboard" else "Clock")
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
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

/** A legible text/icon color (near-black or white) for content placed on [background]. */
internal fun contentColorOn(background: Color): Color =
    if (background.luminance() > 0.5f) Color(0xFF1A1A1A) else Color.White
