package com.rbitton.calendae.ui.calendar

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
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
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val MonthTitleFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())

// One week's worth of day columns per arrow tap in the week timeline.
private const val WEEK_STEP_DAYS = 7

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
    val scope = rememberCoroutineScope()

    var editorOpen by remember { mutableStateOf(false) }
    var editingEvent by remember { mutableStateOf<CalendarEvent?>(null) }
    var calendarsOpen by remember { mutableStateOf(false) }
    // Month paging direction: +1 slides the new page in from the right, -1 from the left.
    var navDirection by remember { mutableIntStateOf(1) }

    val windowStart = state.weekWindowStart
    fun indexOf(date: LocalDate) =
        ChronoUnit.DAYS.between(windowStart, date).toInt().coerceIn(0, state.weekWindowDays - 1)
    val weekListState = rememberLazyListState(initialFirstVisibleItemIndex = indexOf(state.selectedDate))
    val weekVisibleDate by remember {
        derivedStateOf { windowStart.plusDays(weekListState.firstVisibleItemIndex.toLong()) }
    }

    // Scroll the timeline to the selected day whenever we (re)enter week view.
    LaunchedEffect(state.viewMode) {
        if (state.viewMode == ViewMode.WEEK) weekListState.scrollToItem(indexOf(state.selectedDate))
    }

    val isWeek = state.viewMode == ViewMode.WEEK
    val goPrevious: () -> Unit = {
        if (isWeek) {
            scope.launch {
                weekListState.animateScrollToItem(
                    (weekListState.firstVisibleItemIndex - WEEK_STEP_DAYS).coerceAtLeast(0),
                )
            }
        } else {
            navDirection = -1; viewModel.goPrevious()
        }
    }
    val goNext: () -> Unit = {
        if (isWeek) {
            scope.launch {
                weekListState.animateScrollToItem(
                    (weekListState.firstVisibleItemIndex + WEEK_STEP_DAYS)
                        .coerceAtMost(state.weekWindowDays - 1),
                )
            }
        } else {
            navDirection = 1; viewModel.goNext()
        }
    }
    val goToday: () -> Unit = {
        if (isWeek) {
            scope.launch { weekListState.animateScrollToItem(indexOf(state.today)) }
            viewModel.goToToday()
        } else {
            navDirection = if (YearMonth.now().isBefore(state.visibleMonth)) -1 else 1
            viewModel.goToToday()
        }
    }

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

    val title = if (isWeek) weekVisibleDate.format(MonthTitleFormat)
    else state.visibleMonth.format(MonthTitleFormat)

    Scaffold(
        topBar = {
            CalendarTopBar(
                title = title,
                isWeek = isWeek,
                showCalendars = state.calendars.size > 1,
                onPrevious = goPrevious,
                onNext = goNext,
                onToday = goToday,
                onToggleView = {
                    viewModel.setViewMode(if (isWeek) ViewMode.MONTH else ViewMode.WEEK)
                },
                onOpenCalendars = { calendarsOpen = true },
            )
        },
        floatingActionButton = {
            if (state.hasPermission) {
                FloatingActionButton(onClick = {
                    if (isWeek) viewModel.selectDate(weekVisibleDate)
                    editingEvent = null
                    editorOpen = true
                }) {
                    Icon(Icons.Filled.Add, "Add event")
                }
            }
        },
    ) { padding ->
        val onEventClick: (CalendarEvent) -> Unit = { editingEvent = it; editorOpen = true }
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                !state.hasPermission ->
                    PermissionGate(onGrant = { permissionLauncher.launch(CalendarPermissions) })

                isWeek -> WeekTimeline(
                    listState = weekListState,
                    windowStart = windowStart,
                    dayCount = state.weekWindowDays,
                    selectedDate = state.selectedDate,
                    today = state.today,
                    eventsByDate = state.eventsByDate,
                    onDayClick = viewModel::selectDate,
                    onEventClick = onEventClick,
                )

                else -> MonthPager(
                    state = state,
                    foldState = foldState,
                    navDirection = navDirection,
                    onPrevious = goPrevious,
                    onNext = goNext,
                    onDateClick = viewModel::selectDate,
                    onEventClick = onEventClick,
                )
            }
            if (state.hasPermission && state.loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
            }
        }
    }

    if (editorOpen) {
        EventEditorDialog(
            date = state.selectedDate,
            event = editingEvent,
            writableCalendars = state.calendars.filter { it.isWritable },
            onDismiss = { editorOpen = false },
            onSave = { eventTitle, start, end, calendarId ->
                val event = editingEvent
                if (event == null) viewModel.addEvent(eventTitle, state.selectedDate, start, end, calendarId)
                else viewModel.updateEvent(event.id, eventTitle, state.selectedDate, start, end)
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

/**
 * The month view, paged and swipeable: navigating (by swipe or the arrows) slides
 * the old month out and the new one in, keyed by the visible month.
 */
@Composable
private fun MonthPager(
    state: CalendarUiState,
    foldState: FoldState,
    navDirection: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onDateClick: (LocalDate) -> Unit,
    onEventClick: (CalendarEvent) -> Unit,
) {
    val latestPrev = rememberUpdatedState(onPrevious)
    val latestNext = rememberUpdatedState(onNext)

    AnimatedContent(
        targetState = state.visibleMonth,
        transitionSpec = {
            val dir = navDirection
            (slideInHorizontally(tween(280)) { w -> dir * w } + fadeIn(tween(280)))
                .togetherWith(slideOutHorizontally(tween(280)) { w -> -dir * w } + fadeOut(tween(280)))
                .using(SizeTransform(clip = false))
        },
        label = "month-page",
    ) { month ->
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    var total = 0f
                    val threshold = 64.dp.toPx()
                    detectHorizontalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = {
                            if (total > threshold) latestPrev.value()
                            else if (total < -threshold) latestNext.value()
                        },
                        onHorizontalDrag = { _, dragAmount -> total += dragAmount },
                    )
                },
        ) {
            MonthView(month, state, foldState, onDateClick, onEventClick)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarTopBar(
    title: String,
    isWeek: Boolean,
    showCalendars: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    onToggleView: () -> Unit,
    onOpenCalendars: () -> Unit,
) {
    androidx.compose.material3.TopAppBar(
        title = { Text(title) },
        actions = {
            TextButton(onClick = onToggleView) { Text(if (isWeek) "Month" else "Week") }
            if (showCalendars) TextButton(onClick = onOpenCalendars) { Text("Calendars") }
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

@Composable
private fun MonthView(
    month: YearMonth,
    state: CalendarUiState,
    foldState: FoldState,
    onDateClick: (LocalDate) -> Unit,
    onEventClick: (CalendarEvent) -> Unit,
) {
    val monthContent = @Composable {
        MonthGrid(
            month = month,
            selectedDate = state.selectedDate,
            today = state.today,
            daysWithEvents = state.eventsByDate.keys,
            onDateClick = onDateClick,
            modifier = Modifier.fillMaxSize().padding(vertical = 8.dp),
        )
    }
    val agenda = @Composable {
        DayAgenda(
            date = state.selectedDate,
            events = state.selectedDayEvents,
            onEventClick = onEventClick,
            today = state.today,
            modifier = Modifier.fillMaxSize().padding(top = 8.dp),
        )
    }

    when (foldState.posture) {
        Posture.BOOK, Posture.TABLETOP -> HingeSpread(foldState, monthContent, agenda)
        Posture.FLAT -> Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                MonthGrid(
                    month = month,
                    selectedDate = state.selectedDate,
                    today = state.today,
                    daysWithEvents = state.eventsByDate.keys,
                    onDateClick = onDateClick,
                )
            }
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            Box(Modifier.fillMaxWidth().weight(1f)) { agenda() }
        }
    }
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
