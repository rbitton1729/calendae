package com.rbitton.calendae.ui.calendar

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rbitton.calendae.data.CalendarEvent
import com.rbitton.calendae.fold.FoldState
import com.rbitton.calendae.fold.Posture
import com.rbitton.calendae.fold.rememberFoldState
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val MonthTitleFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
private val WeekTitleDay: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())

// In book posture the week is split 4 days / 3 days across the hinge.
private const val WEEK_LEFT_DAYS = 4

private val CalendarPermissions = arrayOf(
    Manifest.permission.READ_CALENDAR,
    Manifest.permission.WRITE_CALENDAR,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(viewModel: CalendarViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val foldState = rememberFoldState()
    val context = LocalContext.current

    var editorOpen by remember { mutableStateOf(false) }
    var editingEvent by remember { mutableStateOf<CalendarEvent?>(null) }
    var calendarsOpen by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        viewModel.onPermissionResult(result[Manifest.permission.READ_CALENDAR] == true)
    }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_CALENDAR,
        ) == PackageManager.PERMISSION_GRANTED
        viewModel.onPermissionResult(granted)
    }

    Scaffold(
        topBar = {
            CalendarTopBar(
                state = state,
                onPrevious = viewModel::goPrevious,
                onNext = viewModel::goNext,
                onToday = viewModel::goToToday,
                onToggleView = {
                    viewModel.setViewMode(
                        if (state.viewMode == ViewMode.MONTH) ViewMode.WEEK else ViewMode.MONTH,
                    )
                },
                onOpenCalendars = { calendarsOpen = true },
            )
        },
        floatingActionButton = {
            if (state.hasPermission) {
                FloatingActionButton(onClick = { editingEvent = null; editorOpen = true }) {
                    Icon(Icons.Filled.Add, "Add event")
                }
            }
        },
    ) { padding ->
        val onEventClick: (CalendarEvent) -> Unit = { editingEvent = it; editorOpen = true }
        Box(Modifier.padding(padding)) {
            when {
                !state.hasPermission -> PermissionGate(
                    onGrant = { permissionLauncher.launch(CalendarPermissions) },
                )
                state.viewMode == ViewMode.WEEK -> WeekView(state, foldState, viewModel, onEventClick)
                else -> MonthView(state, foldState, viewModel, onEventClick)
            }
        }
    }

    if (editorOpen) {
        EventEditorDialog(
            date = state.selectedDate,
            event = editingEvent,
            writableCalendars = state.calendars.filter { it.isWritable },
            onDismiss = { editorOpen = false },
            onSave = { title, start, end, calendarId ->
                val event = editingEvent
                if (event == null) viewModel.addEvent(title, state.selectedDate, start, end, calendarId)
                else viewModel.updateEvent(event.id, title, state.selectedDate, start, end)
                editorOpen = false
            },
            onDelete = {
                editingEvent?.let { viewModel.deleteEvent(it.id) }
                editorOpen = false
            },
        )
    }

    if (calendarsOpen) {
        CalendarsDialog(
            calendars = state.calendars,
            enabledIds = state.enabledCalendarIds,
            onToggle = viewModel::setCalendarEnabled,
            onDismiss = { calendarsOpen = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarTopBar(
    state: CalendarUiState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    onToggleView: () -> Unit,
    onOpenCalendars: () -> Unit,
) {
    androidx.compose.material3.TopAppBar(
        title = { Text(state.title()) },
        actions = {
            TextButton(onClick = onToggleView) {
                Text(if (state.viewMode == ViewMode.MONTH) "Week" else "Month")
            }
            if (state.calendars.size > 1) {
                TextButton(onClick = onOpenCalendars) { Text("Calendars") }
            }
            IconButton(onClick = onPrevious) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous")
            }
            TextButton(onClick = onToday) { Text("Today") }
            IconButton(onClick = onNext) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next")
            }
        },
    )
}

private fun CalendarUiState.title(): String = when (viewMode) {
    ViewMode.MONTH -> visibleMonth.format(MonthTitleFormat)
    ViewMode.WEEK -> {
        val start = weekStart
        val end = weekStart.plusDays(6)
        "${start.format(WeekTitleDay)} – ${end.format(WeekTitleDay)}"
    }
}

@Composable
private fun MonthView(
    state: CalendarUiState,
    foldState: FoldState,
    viewModel: CalendarViewModel,
    onEventClick: (CalendarEvent) -> Unit,
) {
    val month = @Composable {
        MonthGrid(
            month = state.visibleMonth,
            selectedDate = state.selectedDate,
            today = LocalDate.now(),
            daysWithEvents = state.eventsByDate.keys,
            onDateClick = viewModel::selectDate,
            modifier = Modifier.fillMaxSize().padding(vertical = 8.dp),
        )
    }
    val agenda = @Composable {
        DayAgenda(
            date = state.selectedDate,
            events = state.selectedDayEvents,
            onEventClick = onEventClick,
            modifier = Modifier.fillMaxSize().padding(top = 8.dp),
        )
    }

    when (foldState.posture) {
        Posture.BOOK, Posture.TABLETOP -> HingeSpread(foldState, month, agenda)
        Posture.FLAT -> Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                MonthGrid(
                    month = state.visibleMonth,
                    selectedDate = state.selectedDate,
                    today = LocalDate.now(),
                    daysWithEvents = state.eventsByDate.keys,
                    onDateClick = viewModel::selectDate,
                )
            }
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            Box(Modifier.fillMaxWidth().weight(1f)) { agenda() }
        }
    }
}

@Composable
private fun WeekView(
    state: CalendarUiState,
    foldState: FoldState,
    viewModel: CalendarViewModel,
    onEventClick: (CalendarEvent) -> Unit,
) {
    val density = LocalDensity.current
    val splitLeftDays = if (foldState.isBook) WEEK_LEFT_DAYS else null
    val gutter = if (foldState.isBook) {
        with(density) { foldState.hingeThicknessPx.toDp() }
    } else 0.dp

    WeekGrid(
        days = state.weekDays,
        eventsByDate = state.eventsByDate,
        selectedDate = state.selectedDate,
        today = LocalDate.now(),
        onDayClick = viewModel::selectDate,
        onEventClick = onEventClick,
        leftDays = splitLeftDays,
        gutterWidth = gutter,
        modifier = Modifier.fillMaxSize(),
    )
}

/**
 * Two pages with the gutter aligned to the physical hinge: a Row for a book
 * (vertical) fold, a Column for a tabletop (horizontal) fold. Falls back to an
 * even split with a divider when no hinge position is reported.
 */
@Composable
private fun HingeSpread(
    foldState: FoldState,
    first: @Composable () -> Unit,
    second: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val position = foldState.hingePositionPx
    val thickness = with(density) { foldState.hingeThicknessPx.toDp() }

    if (foldState.isTabletop) {
        Column(Modifier.fillMaxSize()) {
            if (position == null) {
                Box(Modifier.fillMaxWidth().weight(1f)) { first() }
                HorizontalDivider()
                Box(Modifier.fillMaxWidth().weight(1f)) { second() }
            } else {
                Box(Modifier.fillMaxWidth().height(with(density) { position.toDp() })) { first() }
                Spacer(Modifier.height(thickness))
                Box(Modifier.fillMaxWidth().weight(1f)) { second() }
            }
        }
    } else {
        Row(Modifier.fillMaxSize()) {
            if (position == null) {
                Box(Modifier.weight(1f)) { first() }
                VerticalDivider()
                Box(Modifier.weight(1f)) { second() }
            } else {
                Box(Modifier.width(with(density) { position.toDp() })) { first() }
                Spacer(Modifier.width(thickness))
                Box(Modifier.weight(1f)) { second() }
            }
        }
    }
}

@Composable
private fun PermissionGate(onGrant: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Calendae needs access to your calendar to show and add events.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onGrant) { Text("Grant access") }
        }
    }
}
