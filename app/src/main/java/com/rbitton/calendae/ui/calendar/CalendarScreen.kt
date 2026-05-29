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
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rbitton.calendae.data.CalendarEvent
import com.rbitton.calendae.data.startDate
import com.rbitton.calendae.fold.FoldState
import com.rbitton.calendae.fold.rememberFoldState
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
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

/**
 * A secondary surface that, on a folded phone, appears as a dialog — but when
 * unfolded must instead occupy a book page (never a popup).
 */
private sealed interface Panel {
    data class Editor(val event: CalendarEvent?) : Panel
    data object Calendars : Panel
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(viewModel: CalendarViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val foldState = rememberFoldState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val zone = ZoneId.systemDefault()

    var panel by remember { mutableStateOf<Panel?>(null) }
    // Month paging direction: +1 slides the new page in from the right, -1 from the left.
    var navDirection by remember { mutableIntStateOf(1) }

    // Unfolded (a real book/tabletop spread) => content splits at the hinge, never a popup.
    val splitMode = foldState.isBook || foldState.isTabletop
    val isWeek = state.viewMode == ViewMode.WEEK

    val windowStart = state.weekWindowStart
    fun indexOf(date: LocalDate) =
        ChronoUnit.DAYS.between(windowStart, date).toInt().coerceIn(0, state.weekWindowDays - 1)
    val weekListState = rememberLazyListState(initialFirstVisibleItemIndex = indexOf(state.selectedDate))
    val weekVisibleDate by remember {
        derivedStateOf { windowStart.plusDays(weekListState.firstVisibleItemIndex.toLong()) }
    }

    LaunchedEffect(state.viewMode) {
        if (state.viewMode == ViewMode.WEEK) weekListState.scrollToItem(indexOf(state.selectedDate))
    }

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

    // An existing event is edited on its own day; a new event uses the selected day.
    val editorDate = (panel as? Panel.Editor)?.event?.startDate(zone) ?: state.selectedDate

    // Panel actions.
    val dismissPanel = { panel = null }
    val saveEvent: (String, LocalTime, LocalTime, Long?) -> Unit = { title, start, end, calendarId ->
        (panel as? Panel.Editor)?.let { editor ->
            val event = editor.event
            if (event == null) viewModel.addEvent(title, state.selectedDate, start, end, calendarId)
            else viewModel.updateEvent(event.id, title, event.startDate(zone), start, end)
        }
        panel = null
    }
    val deleteEvent = {
        (panel as? Panel.Editor)?.event?.let { viewModel.deleteEvent(it.id) }
        panel = null
    }
    val onEventClick: (CalendarEvent) -> Unit = { event ->
        viewModel.selectDate(event.startDate(zone))
        panel = Panel.Editor(event)
    }

    // When an event opens the split editor in week view, center its day in the left page.
    val centeredEventId = (panel as? Panel.Editor)?.event?.id
    LaunchedEffect(centeredEventId, splitMode, isWeek) {
        val event = (panel as? Panel.Editor)?.event
        if (event != null && isWeek && splitMode) {
            val index = indexOf(event.startDate(zone))
            weekListState.scrollToItem(index)
            val info = weekListState.layoutInfo
            val itemPx = info.visibleItemsInfo.firstOrNull { it.index == index }?.size ?: 0
            val viewport = info.viewportEndOffset - info.viewportStartOffset
            weekListState.animateScrollToItem(index, -((viewport - itemPx) / 2))
        }
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
                onToggleView = { viewModel.setViewMode(if (isWeek) ViewMode.MONTH else ViewMode.WEEK) },
                onOpenCalendars = { panel = Panel.Calendars },
            )
        },
        floatingActionButton = {
            // Hidden while a panel is open — you're already editing/adding.
            if (state.hasPermission && panel == null) {
                FloatingActionButton(onClick = {
                    if (isWeek) viewModel.selectDate(weekVisibleDate)
                    panel = Panel.Editor(null)
                }) {
                    Icon(Icons.Filled.Add, "Add event")
                }
            }
        },
    ) { padding ->
        val monthGrid: @Composable (Modifier) -> Unit = { mod ->
            MonthGridPaged(state, navDirection, goPrevious, goNext, viewModel::selectDate, mod)
        }
        val weekTimeline: @Composable (Modifier) -> Unit = { mod ->
            WeekTimeline(
                listState = weekListState,
                windowStart = windowStart,
                dayCount = state.weekWindowDays,
                selectedDate = state.selectedDate,
                today = state.today,
                eventsByDate = state.eventsByDate,
                onDayClick = viewModel::selectDate,
                onEventClick = onEventClick,
                modifier = mod,
            )
        }
        val agenda: @Composable () -> Unit = {
            DayAgenda(
                date = state.selectedDate,
                events = state.selectedDayEvents,
                onEventClick = onEventClick,
                today = state.today,
                modifier = Modifier.fillMaxSize().padding(top = 8.dp),
            )
        }

        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                !state.hasPermission ->
                    PermissionGate(onGrant = { permissionLauncher.launch(CalendarPermissions) })

                // Unfolded with a panel open: calendar on one page, panel on the other.
                splitMode && panel != null -> SplitPane(
                    foldState,
                    first = { if (isWeek) weekTimeline(Modifier.fillMaxSize()) else monthGrid(Modifier.fillMaxSize()) },
                    second = {
                        PanelPane(panel!!, state, editorDate, dismissPanel, saveEvent, deleteEvent, viewModel::setCalendarEnabled)
                    },
                )

                isWeek -> weekTimeline(Modifier.fillMaxSize())

                // Unfolded month, no panel: month grid on one page, agenda on the other.
                splitMode -> SplitPane(foldState, { monthGrid(Modifier.fillMaxSize()) }, agenda)

                // Folded phone month: month grid stacked above the agenda.
                else -> Column(Modifier.fillMaxSize()) {
                    monthGrid(Modifier.fillMaxWidth())
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    Box(Modifier.fillMaxWidth().weight(1f)) { agenda() }
                }
            }

            if (state.hasPermission && state.loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
            }
        }
    }

    // Popups are only ever shown on a folded phone — never when unfolded.
    if (!splitMode) {
        when (val current = panel) {
            is Panel.Editor -> EventEditorDialog(
                date = editorDate,
                event = current.event,
                writableCalendars = state.calendars.filter { it.isWritable },
                onDismiss = dismissPanel,
                onSave = saveEvent,
                onDelete = deleteEvent,
            )
            Panel.Calendars -> CalendarsDialog(
                calendars = state.calendars,
                enabledIds = state.enabledCalendarIds,
                onToggle = viewModel::setCalendarEnabled,
                onDismiss = dismissPanel,
            )
            null -> Unit
        }
    }
}

/** The secondary panel rendered as a book page (a [Surface] filling the page). */
@Composable
private fun PanelPane(
    panel: Panel,
    state: CalendarUiState,
    editorDate: LocalDate,
    onDismiss: () -> Unit,
    onSave: (String, LocalTime, LocalTime, Long?) -> Unit,
    onDelete: () -> Unit,
    onToggleCalendar: (Long, Boolean) -> Unit,
) {
    Surface(Modifier.fillMaxSize(), tonalElevation = 1.dp) {
        when (panel) {
            is Panel.Editor -> EventEditorPaneContent(
                date = editorDate,
                event = panel.event,
                writableCalendars = state.calendars.filter { it.isWritable },
                onDismiss = onDismiss,
                onSave = onSave,
                onDelete = onDelete,
            )
            Panel.Calendars -> CalendarsPaneContent(
                calendars = state.calendars,
                enabledIds = state.enabledCalendarIds,
                onToggle = onToggleCalendar,
                onDismiss = onDismiss,
            )
        }
    }
}

/** The month grid, paged and swipeable, animating between months. Grid only. */
@Composable
private fun MonthGridPaged(
    state: CalendarUiState,
    navDirection: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onDateClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val latestPrev = rememberUpdatedState(onPrevious)
    val latestNext = rememberUpdatedState(onNext)

    AnimatedContent(
        targetState = state.visibleMonth,
        modifier = modifier,
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
                .fillMaxWidth()
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
            MonthGrid(
                month = month,
                selectedDate = state.selectedDate,
                today = state.today,
                daysWithEvents = state.eventsByDate.keys,
                onDateClick = onDateClick,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )
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

/**
 * Two pages split at the hinge: a Row for a book (vertical) fold, a Column for a
 * tabletop (horizontal) fold. The split starts aligned to the physical hinge (or
 * centered if none) and can be dragged via the pill handle in the gutter.
 */
@Composable
private fun SplitPane(
    foldState: FoldState,
    first: @Composable () -> Unit,
    second: @Composable () -> Unit,
) {
    val isTabletop = foldState.isTabletop
    val density = LocalDensity.current

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val total = if (isTabletop) maxHeight else maxWidth
        val hingeThickness = with(density) { foldState.hingeThicknessPx.toDp() }
        val gutter = maxOf(hingeThickness, 28.dp) // wide enough to grab
        val minPane = 140.dp
        val maxFirst = (total - gutter - minPane).coerceAtLeast(minPane)

        // Initial split: gutter centered on the hinge, else an even split. Reset on refold.
        val initialFirst = remember(foldState.hingePositionPx, isTabletop, total) {
            val hingeStart = foldState.hingePositionPx?.let { with(density) { it.toDp() } - gutter / 2 }
            (hingeStart ?: (total - gutter) / 2).coerceIn(minPane, maxFirst)
        }
        var firstSize by remember(foldState.hingePositionPx, isTabletop) { mutableStateOf(initialFirst) }
        val clamped = firstSize.coerceIn(minPane, maxFirst)

        // Accumulate onto the current size (not the captured `clamped`, which is stale
        // inside the long-lived gesture coroutine and makes the handle jump).
        val onDrag: (Float) -> Unit = { deltaPx ->
            val delta = with(density) { deltaPx.toDp() }
            firstSize = (firstSize + delta).coerceIn(minPane, maxFirst)
        }

        if (isTabletop) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().height(clamped)) { first() }
                Gutter(isRow = false, thickness = gutter, onDrag = onDrag)
                Box(Modifier.fillMaxWidth().weight(1f)) { second() }
            }
        } else {
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.width(clamped).fillMaxHeight()) { first() }
                Gutter(isRow = true, thickness = gutter, onDrag = onDrag)
                Box(Modifier.weight(1f).fillMaxHeight()) { second() }
            }
        }
    }
}

/** A draggable divider with a white-transparent pill handle in its center. */
@Composable
private fun Gutter(isRow: Boolean, thickness: Dp, onDrag: (Float) -> Unit) {
    val sizeModifier = if (isRow) Modifier.width(thickness).fillMaxHeight()
    else Modifier.height(thickness).fillMaxWidth()
    val pill = if (isRow) Modifier.size(width = 4.dp, height = 40.dp)
    else Modifier.size(width = 40.dp, height = 4.dp)

    Box(
        sizeModifier.pointerInput(isRow) {
            if (isRow) detectHorizontalDragGestures { _, dragAmount -> onDrag(dragAmount) }
            else detectVerticalDragGestures { _, dragAmount -> onDrag(dragAmount) }
        },
        contentAlignment = Alignment.Center,
    ) {
        Box(pill.clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.4f)))
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
