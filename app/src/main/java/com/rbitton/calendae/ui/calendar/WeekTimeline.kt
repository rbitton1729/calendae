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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rbitton.calendae.data.CalendarEvent
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

private val HourHeight = 56.dp
private val AxisWidth = 44.dp
private val DayWidth = 84.dp
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
    // Open near 07:00 rather than at midnight.
    LaunchedEffect(Unit) { vScroll.scrollTo(with(density) { (HourHeight * 7).roundToPx() }) }

    Row(modifier.fillMaxSize()) {
        Column(Modifier.width(AxisWidth).fillMaxHeight()) {
            Spacer(Modifier.height(HeaderHeight + AllDayHeight))
            HorizontalDivider()
            Box(Modifier.weight(1f).verticalScroll(vScroll)) { HourAxis() }
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
                    vScroll = vScroll,
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
    vScroll: androidx.compose.foundation.ScrollState,
    onDayClick: (LocalDate) -> Unit,
    onEventClick: (CalendarEvent) -> Unit,
    zone: ZoneId,
) {
    Row {
        Column(Modifier.width(DayWidth).fillMaxHeight()) {
            DayHeader(date, isToday, isSelected) { onDayClick(date) }
            AllDayBand(events.filter { it.allDay }, onEventClick)
            HorizontalDivider()
            Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(vScroll)) {
                DayBody(date, events.filterNot { it.allDay }, onEventClick, zone)
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
            Surface(
                onClick = { onEventClick(event) },
                color = swatchColor(event.color),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (events.size > 1) "${event.title} +${events.size - 1}" else event.title,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
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
    onEventClick: (CalendarEvent) -> Unit,
    zone: ZoneId,
) {
    Box(Modifier.fillMaxWidth().height(HourHeight * Hours)) {
        HourLines()
        events.forEach { event ->
            val (startMin, endMin) = event.minuteRangeOn(date, zone)
            val top = HourHeight * (startMin / 60f)
            val blockHeight = (HourHeight * ((endMin - startMin) / 60f)).coerceAtLeast(20.dp)
            Surface(
                onClick = { onEventClick(event) },
                color = swatchColor(event.color),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 1.dp)
                    .offset(y = top).height(blockHeight),
            ) {
                Text(
                    event.title,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp),
                )
            }
        }
    }
}

@Composable
private fun HourAxis() {
    Column(Modifier.width(AxisWidth)) {
        for (hour in 0 until Hours) {
            Box(Modifier.height(HourHeight).fillMaxWidth(), contentAlignment = Alignment.TopEnd) {
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
private fun HourLines() {
    Column(Modifier.fillMaxSize()) {
        repeat(Hours) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(Modifier.height(HourHeight))
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
