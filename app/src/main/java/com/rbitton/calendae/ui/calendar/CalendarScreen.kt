package com.rbitton.calendae.ui.calendar

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
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
import kotlin.math.abs

private val MonthTitleFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())

// The month pager fakes an endless range: a huge page count anchored so that page
// [MonthPageCenter] is the month the app opened in.
private const val MonthPageCount = 4001
private const val MonthPageCenter = MonthPageCount / 2

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
    val density = LocalDensity.current
    val zone = ZoneId.systemDefault()

    var panel by remember { mutableStateOf<Panel?>(null) }
    // Bumped when the split pill is dragged closed, asking an open editor to commit.
    var commitSignal by remember { mutableIntStateOf(0) }
    // True only while a pull-to-refresh is in flight (so navigation loads don't show the spinner).
    var pullRefreshing by remember { mutableStateOf(false) }
    LaunchedEffect(state.loading) { if (!state.loading) pullRefreshing = false }

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

    // Month paging via a finger-tracking HorizontalPager. Page <-> month maps around a
    // fixed anchor (the month the app opened in) so the range is effectively endless.
    val monthAnchor = remember(state.today) { YearMonth.from(state.today) }
    fun monthForPage(page: Int): YearMonth = monthAnchor.plusMonths((page - MonthPageCenter).toLong())
    fun pageForMonth(month: YearMonth): Int =
        MonthPageCenter + ChronoUnit.MONTHS.between(monthAnchor, month).toInt()
    val monthPagerState = rememberPagerState(initialPage = pageForMonth(state.visibleMonth)) {
        MonthPageCount
    }

    // Whichever month the pager rests on becomes the loaded month.
    LaunchedEffect(monthPagerState) {
        snapshotFlow { monthPagerState.settledPage }
            .collect { page -> viewModel.showMonth(monthForPage(page)) }
    }
    // Month changes from elsewhere (Today, tapping a day, the week view) move the pager.
    LaunchedEffect(monthPagerState) {
        snapshotFlow { state.visibleMonth }.collect { month ->
            val target = pageForMonth(month)
            if (target != monthPagerState.currentPage && !monthPagerState.isScrollInProgress) {
                if (abs(target - monthPagerState.currentPage) <= 2) {
                    monthPagerState.animateScrollToPage(target)
                } else {
                    monthPagerState.scrollToPage(target)
                }
            }
        }
    }

    val goPrevious: () -> Unit = {
        if (isWeek) {
            scope.launch {
                weekListState.animateScrollToItem(
                    (weekListState.firstVisibleItemIndex - WEEK_STEP_DAYS).coerceAtLeast(0),
                )
            }
        } else {
            scope.launch {
                monthPagerState.animateScrollToPage((monthPagerState.currentPage - 1).coerceAtLeast(0))
            }
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
            scope.launch {
                monthPagerState.animateScrollToPage(
                    (monthPagerState.currentPage + 1).coerceAtMost(MonthPageCount - 1),
                )
            }
        }
    }
    val goToday: () -> Unit = {
        if (isWeek) {
            scope.launch { weekListState.animateScrollToItem(indexOf(state.today)) }
            viewModel.goToToday()
        } else {
            // Sets visibleMonth + selectedDate to today; the sync effect moves the pager.
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
    // Dragging the split pill shut: editors save (handled via the signal), the
    // calendars list just closes.
    val collapsePanel: () -> Unit = {
        when (panel) {
            is Panel.Editor -> commitSignal++
            Panel.Calendars -> panel = null
            null -> Unit
        }
    }

    // When an event opens the split editor in week view, center its DAY in the left
    // page. Keyed on the date (not the event) so switching events on the same day
    // doesn't move the timeline.
    val centeredDate = (panel as? Panel.Editor)?.event?.startDate(zone)
    LaunchedEffect(centeredDate, splitMode, isWeek) {
        if (centeredDate != null && isWeek && splitMode) {
            // Let the split layout settle so we read the half-width viewport, not the full one.
            withFrameNanos {}
            val index = indexOf(centeredDate)
            val info = weekListState.layoutInfo
            val viewport = info.viewportEndOffset - info.viewportStartOffset
            val itemPx = with(density) { WeekDayWidth.toPx() }.toInt()
            // Single smooth scroll (no instant pre-jump) to center the day column.
            weekListState.animateScrollToItem(index, -((viewport - itemPx) / 2))
        }
    }

    // In month view the title follows the pager so it updates mid-swipe.
    val title = if (isWeek) weekVisibleDate.format(MonthTitleFormat)
    else monthForPage(monthPagerState.currentPage).format(MonthTitleFormat)

    Scaffold(
        topBar = {
            CalendarTopBar(
                title = title,
                isWeek = isWeek,
                onPrevious = goPrevious,
                onNext = goNext,
                onToday = goToday,
                onToggleView = { viewModel.setViewMode(if (isWeek) ViewMode.MONTH else ViewMode.WEEK) },
                onOpenCalendars = { panel = Panel.Calendars },
                onRefresh = viewModel::refresh,
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
            MonthGridPaged(monthPagerState, ::monthForPage, state, viewModel::selectDate, mod)
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

        // The second page when unfolded: an open panel, else the day agenda (month
        // only). Null in week view with no panel => the timeline fills the screen.
        val secondPage: (@Composable () -> Unit)? = when {
            panel != null -> {
                {
                    PanelPane(
                        panel!!, state, editorDate, commitSignal,
                        dismissPanel, saveEvent, deleteEvent, viewModel::setCalendarEnabled,
                    )
                }
            }
            !isWeek -> agenda
            else -> null
        }

        PullToRefreshBox(
            isRefreshing = pullRefreshing,
            onRefresh = { pullRefreshing = true; viewModel.refresh() },
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            when {
                !state.hasPermission ->
                    PermissionGate(onGrant = { permissionLauncher.launch(CalendarPermissions) })

                // Unfolded: primary stays mounted; the second page is added/removed in place.
                splitMode -> SplitPane(
                    foldState,
                    first = { if (isWeek) weekTimeline(Modifier.fillMaxSize()) else monthGrid(Modifier.fillMaxSize()) },
                    second = secondPage,
                    // Drag-to-close only in week view; in month the agenda is always the second pane.
                    onSecondCollapsed = if (panel != null && isWeek) collapsePanel else null,
                )

                // Folded phone week: full timeline (panel shows as a popup).
                isWeek -> weekTimeline(Modifier.fillMaxSize())

                // Folded phone month: month grid stacked above the agenda, the two
                // sharing the screen height so the grid fills its half (not just a
                // band of squares).
                else -> Column(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxWidth().weight(1f)) { monthGrid(Modifier.fillMaxSize()) }
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
    commitSignal: Int,
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
                commitSignal = commitSignal,
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

/**
 * The month grid as a finger-tracking pager: one month per page, snapping like the
 * week view. [monthForPage] maps a page index to the month it shows.
 */
@Composable
private fun MonthGridPaged(
    pagerState: PagerState,
    monthForPage: (Int) -> YearMonth,
    state: CalendarUiState,
    onDateClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    HorizontalPager(
        state = pagerState,
        modifier = modifier.fillMaxSize(),
        beyondViewportPageCount = 1, // keep neighbours composed so swipes start clean
        // Commit to the next month after a short drag (default needs a half-page), so a
        // light flick changes months the way a native pager does.
        flingBehavior = PagerDefaults.flingBehavior(state = pagerState, snapPositionalThreshold = 0.25f),
        key = { it },
    ) { page ->
        MonthGrid(
            month = monthForPage(page),
            selectedDate = state.selectedDate,
            today = state.today,
            eventsByDate = state.eventsByDate,
            onDateClick = onDateClick,
            modifier = Modifier.fillMaxSize().padding(vertical = 8.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarTopBar(
    title: String,
    isWeek: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    onToggleView: () -> Unit,
    onOpenCalendars: () -> Unit,
    onRefresh: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    androidx.compose.material3.TopAppBar(
        title = { Text(title) },
        actions = {
            TextButton(onClick = onToggleView) { Text(if (isWeek) "Month" else "Week") }
            IconButton(onClick = onPrevious) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous")
            }
            TextButton(onClick = onToday) { Text("Today") }
            IconButton(onClick = onNext) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next")
            }
            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, "More") }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    modifier = Modifier.width(200.dp),
                ) {
                    DropdownMenuItem(
                        text = { Text("Refresh") },
                        leadingIcon = { Icon(Icons.Filled.Refresh, null) },
                        onClick = { menuOpen = false; onRefresh() },
                    )
                    DropdownMenuItem(
                        text = { Text("Calendars") },
                        onClick = { menuOpen = false; onOpenCalendars() },
                    )
                }
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
    second: (@Composable () -> Unit)?,
    onSecondCollapsed: (() -> Unit)? = null,
) {
    val isTabletop = foldState.isTabletop
    val density = LocalDensity.current

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val total = if (isTabletop) maxHeight else maxWidth
        val hingeThickness = with(density) { foldState.hingeThicknessPx.toDp() }
        val gutter = maxOf(hingeThickness, 28.dp) // wide enough to grab
        val minPane = 140.dp
        val maxFirst = (total - gutter - minPane).coerceAtLeast(minPane)
        // When collapsible, allow over-dragging the second pane below its minimum.
        val collapsible = onSecondCollapsed != null
        val collapseThreshold = 90.dp
        val maxDrag = if (collapsible) (total - gutter).coerceAtLeast(minPane) else maxFirst

        // Initial split: gutter centered on the hinge, else an even split. Reset on refold.
        val initialFirst = remember(foldState.hingePositionPx, isTabletop, total) {
            val hingeStart = foldState.hingePositionPx?.let { with(density) { it.toDp() } - gutter / 2 }
            (hingeStart ?: (total - gutter) / 2).coerceIn(minPane, maxFirst)
        }
        var firstSize by remember(foldState.hingePositionPx, isTabletop) { mutableStateOf(initialFirst) }
        val clamped = firstSize.coerceIn(minPane, maxDrag)

        // Reset the split once the second pane is gone (after a collapse-close), so it
        // never resizes while still visible (which looked like a halfway flash).
        val hasSecond = second != null
        LaunchedEffect(hasSecond) { if (!hasSecond) firstSize = initialFirst }

        // Accumulate onto the current size (not the captured `clamped`, which is stale
        // inside the long-lived gesture coroutine and makes the handle jump).
        val onDrag: (Float) -> Unit = { deltaPx ->
            val delta = with(density) { deltaPx.toDp() }
            firstSize = (firstSize + delta).coerceIn(minPane, maxDrag)
        }
        val onDragEnd: () -> Unit = {
            val secondSize = total - gutter - firstSize
            // Just close; the size resets invisibly once the second pane is gone.
            if (collapsible && secondSize < collapseThreshold) onSecondCollapsed!!()
            else firstSize = firstSize.coerceIn(minPane, maxFirst) // snap back from the collapse zone
        }

        // `first` is always the leading child (single call site) so it never re-mounts
        // when the second page is added/removed — only its size modifier changes.
        if (isTabletop) {
            Column(Modifier.fillMaxSize()) {
                val firstMod = if (second != null) Modifier.fillMaxWidth().height(clamped)
                else Modifier.fillMaxWidth().weight(1f)
                Box(firstMod) { first() }
                if (second != null) {
                    Gutter(isRow = false, thickness = gutter, onDrag = onDrag, onDragEnd = onDragEnd)
                    Box(Modifier.fillMaxWidth().weight(1f)) { second() }
                }
            }
        } else {
            Row(Modifier.fillMaxSize()) {
                val firstMod = if (second != null) Modifier.width(clamped).fillMaxHeight()
                else Modifier.weight(1f).fillMaxHeight()
                Box(firstMod) { first() }
                if (second != null) {
                    Gutter(isRow = true, thickness = gutter, onDrag = onDrag, onDragEnd = onDragEnd)
                    Box(Modifier.weight(1f).fillMaxHeight()) { second() }
                }
            }
        }
    }
}

/** A draggable divider with a white-transparent pill handle in its center. */
@Composable
private fun Gutter(isRow: Boolean, thickness: Dp, onDrag: (Float) -> Unit, onDragEnd: () -> Unit) {
    val sizeModifier = if (isRow) Modifier.width(thickness).fillMaxHeight()
    else Modifier.height(thickness).fillMaxWidth()
    val pill = if (isRow) Modifier.size(width = 4.dp, height = 40.dp)
    else Modifier.size(width = 40.dp, height = 4.dp)

    Box(
        sizeModifier.pointerInput(isRow) {
            if (isRow) detectHorizontalDragGestures(
                onDragEnd = onDragEnd,
                onHorizontalDrag = { _, dragAmount -> onDrag(dragAmount) },
            ) else detectVerticalDragGestures(
                onDragEnd = onDragEnd,
                onVerticalDrag = { _, dragAmount -> onDrag(dragAmount) },
            )
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
