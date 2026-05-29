package com.rbitton.calendae.ui.calendar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rbitton.calendae.data.CalendarEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Unzoomed height of one hour row; the live height is this times the pinch zoom. */
private val BaseHourHeight = 56.dp
private const val MinZoom = 0.5f
private const val MaxZoom = 3f
private val AxisWidth = 44.dp
/** Width of one day column; referenced by the screen to center a day. */
internal val WeekDayWidth = 84.dp
private val DayWidth = WeekDayWidth
private val HeaderHeight = 50.dp
private val AllDayHeight = 28.dp
private const val Hours = 24
private const val MinutesPerDay = 24 * 60

/**
 * A continuously horizontally-scrolling week timeline: a pinned hour axis on the
 * left and an endless row of day columns. Days scroll freely (no page jumps);
 * the hour grid scrolls vertically, shared across all visible columns.
 */
@Composable
fun WeekTimeline(
    listState: LazyListState,
    windowStart: LocalDate,
    dayCount: Int,
    selectedDate: LocalDate,
    today: LocalDate,
    eventsByDate: Map<LocalDate, List<CalendarEvent>>,
    onDayClick: (LocalDate) -> Unit,
    onEventClick: (CalendarEvent) -> Unit,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    val vScroll = rememberScrollState()
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    // Pinch zoom over the hour grid. Persisted across config changes; 1f == BaseHourHeight.
    var zoom by rememberSaveable { mutableFloatStateOf(1f) }
    val hourHeight = BaseHourHeight * zoom

    // Open near 07:00 rather than at midnight.
    LaunchedEffect(Unit) { vScroll.scrollTo(with(density) { (hourHeight * 7).roundToPx() }) }

    // Drives the "now" indicator; refreshes about once a minute.
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalTime.now()
            delay(60_000L)
        }
    }

    // Two-finger pinch zooms the hour grid. Handled on the Initial pass so it wins
    // over the column/row scrollers; single-finger drags fall through untouched.
    val pinch = Modifier.pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.size >= 2) {
                    val factor = event.calculateZoom()
                    if (factor != 1f) {
                        val previous = zoom
                        zoom = (zoom * factor).coerceIn(MinZoom, MaxZoom)
                        val applied = zoom / previous
                        // Keep the time at the top edge fixed as the grid grows/shrinks.
                        scope.launch { vScroll.scrollTo((vScroll.value * applied).roundToInt()) }
                        event.changes.forEach { it.consume() }
                    }
                }
            } while (event.changes.any { it.pressed })
        }
    }

    Row(modifier.fillMaxSize().then(pinch)) {
        Column(Modifier.width(AxisWidth).fillMaxHeight()) {
            Spacer(Modifier.height(HeaderHeight + AllDayHeight))
            HorizontalDivider()
            Box(Modifier.weight(1f).verticalScroll(vScroll)) { HourAxis(hourHeight) }
        }
        VerticalDivider()
        LazyRow(state = listState, modifier = Modifier.weight(1f).fillMaxHeight()) {
            items(dayCount) { index ->
                val date = windowStart.plusDays(index.toLong())
                DayColumn(
                    date = date,
                    isToday = date == today,
                    isSelected = date == selectedDate,
                    events = eventsByDate[date].orEmpty(),
                    now = if (date == today) now else null,
                    vScroll = vScroll,
                    hourHeight = hourHeight,
                    onDayClick = onDayClick,
                    onEventClick = onEventClick,
                    zone = zone,
                )
            }
        }
    }
}

@Composable
private fun DayColumn(
    date: LocalDate,
    isToday: Boolean,
    isSelected: Boolean,
    events: List<CalendarEvent>,
    now: LocalTime?,
    vScroll: androidx.compose.foundation.ScrollState,
    hourHeight: androidx.compose.ui.unit.Dp,
    onDayClick: (LocalDate) -> Unit,
    onEventClick: (CalendarEvent) -> Unit,
    zone: ZoneId,
) {
    val todayTint = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
    Row {
        Column(Modifier.width(DayWidth).fillMaxHeight()) {
            DayHeader(date, isToday, isSelected) { onDayClick(date) }
            AllDayBand(events.filter { it.allDay }, onEventClick)
            HorizontalDivider()
            Box(
                Modifier.weight(1f).fillMaxWidth()
                    .background(if (isToday) todayTint else Color.Transparent)
                    .verticalScroll(vScroll),
            ) {
                DayBody(date, events.filterNot { it.allDay }, now, hourHeight, onEventClick, zone)
            }
        }
        VerticalDivider()
    }
}

@Composable
private fun DayHeader(date: LocalDate, isToday: Boolean, isSelected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().height(HeaderHeight).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(4.dp))
        Text(
            date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
        )
        Box(
            Modifier.size(28.dp).clip(CircleShape)
                .background(if (isSelected) scheme.primary else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    isSelected -> scheme.onPrimary
                    isToday -> scheme.primary
                    else -> scheme.onSurface
                },
            )
        }
    }
}

@Composable
private fun AllDayBand(events: List<CalendarEvent>, onEventClick: (CalendarEvent) -> Unit) {
    Box(Modifier.fillMaxWidth().height(AllDayHeight).padding(horizontal = 1.dp, vertical = 1.dp)) {
        events.firstOrNull()?.let { event ->
            val background = swatchColor(event.color)
            Surface(
                onClick = { onEventClick(event) },
                color = background,
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (events.size > 1) "${event.title} +${events.size - 1}" else event.title,
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColorOn(background),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun DayBody(
    date: LocalDate,
    events: List<CalendarEvent>,
    now: LocalTime?,
    hourHeight: androidx.compose.ui.unit.Dp,
    onEventClick: (CalendarEvent) -> Unit,
    zone: ZoneId,
) {
    Box(Modifier.fillMaxWidth().height(hourHeight * Hours)) {
        HourLines(hourHeight)
        events.forEach { event ->
            val (startMin, endMin) = event.minuteRangeOn(date, zone)
            val top = hourHeight * (startMin / 60f)
            val blockHeight = (hourHeight * ((endMin - startMin) / 60f)).coerceAtLeast(20.dp)
            val background = swatchColor(event.color)
            Surface(
                onClick = { onEventClick(event) },
                color = background,
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 1.dp)
                    .offset(y = top).height(blockHeight),
            ) {
                Text(
                    event.title,
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColorOn(background),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp),
                )
            }
        }
        if (now != null) NowLine(now, hourHeight)
    }
}

/** A horizontal marker at the current time, drawn over today's column. */
@Composable
private fun NowLine(now: LocalTime, hourHeight: androidx.compose.ui.unit.Dp) {
    val minutes = now.hour * 60 + now.minute
    val y = hourHeight * (minutes / 60f)
    val color = MaterialTheme.colorScheme.error
    Box(
        Modifier.fillMaxWidth().offset(y = y),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        HorizontalDivider(thickness = 2.dp, color = color)
    }
}

@Composable
private fun HourAxis(hourHeight: androidx.compose.ui.unit.Dp) {
    Column(Modifier.width(AxisWidth)) {
        for (hour in 0 until Hours) {
            Box(Modifier.height(hourHeight).fillMaxWidth(), contentAlignment = Alignment.TopEnd) {
                if (hour > 0) {
                    Text(
                        "%02d:00".format(hour),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.offset(y = (-7).dp).padding(end = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun HourLines(hourHeight: androidx.compose.ui.unit.Dp) {
    val color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    Column(Modifier.fillMaxSize()) {
        // Each row is exactly hourHeight; the divider is overlaid at its top so it
        // adds no height (otherwise lines drift below the labels by 1dp per hour).
        repeat(Hours) {
            Box(Modifier.fillMaxWidth().height(hourHeight)) {
                HorizontalDivider(color = color)
            }
        }
    }
}

/** Minute offsets [start, end] of [event] within [date], clamped to the day. */
private fun CalendarEvent.minuteRangeOn(date: LocalDate, zone: ZoneId): Pair<Int, Int> {
    val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
    val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val s = max(startMillis, dayStart)
    val e = min(endMillis, dayEnd)
    val startMin = ((s - dayStart) / 60_000L).toInt().coerceIn(0, MinutesPerDay)
    val endMin = ((e - dayStart) / 60_000L).toInt().coerceIn(0, MinutesPerDay)
    return startMin to max(endMin, startMin + 1)
}
