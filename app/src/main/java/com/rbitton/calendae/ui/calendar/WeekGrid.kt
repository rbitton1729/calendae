package com.rbitton.calendae.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rbitton.calendae.data.CalendarEvent
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

private val HourHeight = 52.dp
private val AxisWidth = 44.dp
private const val Hours = 24
private const val MinutesPerDay = 24 * 60

/**
 * A week laid out as a paper planner: a fixed header of day columns, an optional
 * all-day band, then a scrollable hour grid with events positioned by time.
 *
 * In book posture the week is split into two pages: [leftDays] columns on the
 * left, the rest on the right, with a [gutterWidth] gap aligned to the hinge.
 */
@Composable
fun WeekGrid(
    days: List<LocalDate>,
    eventsByDate: Map<LocalDate, List<CalendarEvent>>,
    selectedDate: LocalDate,
    today: LocalDate,
    onDayClick: (LocalDate) -> Unit,
    onEventClick: (CalendarEvent) -> Unit,
    modifier: Modifier = Modifier,
    leftDays: Int? = null,
    gutterWidth: Dp = 0.dp,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    val hasAllDay = days.any { date -> eventsByDate[date].orEmpty().any { it.allDay } }
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    // Open near the working day (07:00) rather than at midnight.
    LaunchedEffect(Unit) { scroll.scrollTo(with(density) { (HourHeight * 7).roundToPx() }) }

    Column(modifier.fillMaxSize()) {
        WeekRow(days, leftDays, gutterWidth, axisLeading = { Spacer(Modifier.width(AxisWidth)) }) { date ->
            DayHeader(date, isToday = date == today, isSelected = date == selectedDate) {
                onDayClick(date)
            }
        }
        if (hasAllDay) {
            HorizontalDivider()
            WeekRow(days, leftDays, gutterWidth, axisLeading = {
                Box(Modifier.width(AxisWidth)) {
                    Text(
                        "all-day",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(4.dp),
                    )
                }
            }) { date ->
                AllDayCell(eventsByDate[date].orEmpty().filter { it.allDay }, onEventClick)
            }
        }
        HorizontalDivider()
        Box(Modifier.fillMaxWidth().verticalScroll(scroll)) {
            HourLines()
            WeekRow(days, leftDays, gutterWidth, axisLeading = { HourAxis() }) { date ->
                DayColumn(
                    date = date,
                    events = eventsByDate[date].orEmpty().filterNot { it.allDay },
                    onEventClick = onEventClick,
                    zone = zone,
                )
            }
        }
    }
}

/**
 * Lays out a leading axis cell then one weighted cell per day, inserting a
 * [gutterWidth] gap after [leftDays] columns to align with the hinge.
 */
@Composable
private fun WeekRow(
    days: List<LocalDate>,
    leftDays: Int?,
    gutterWidth: Dp,
    axisLeading: @Composable () -> Unit,
    cell: @Composable (LocalDate) -> Unit,
) {
    Row(Modifier.fillMaxWidth()) {
        axisLeading()
        days.forEachIndexed { index, date ->
            Box(Modifier.weight(1f)) { cell(date) }
            if (leftDays != null && index == leftDays - 1) Spacer(Modifier.width(gutterWidth))
        }
    }
}

@Composable
private fun DayHeader(date: LocalDate, isToday: Boolean, isSelected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
        )
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(if (isSelected) scheme.primary else Color.Transparent)
                .clickable(onClick = onClick),
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
private fun AllDayCell(events: List<CalendarEvent>, onEventClick: (CalendarEvent) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 1.dp, vertical = 2.dp)) {
        events.forEach { event ->
            Surface(
                onClick = { onEventClick(event) },
                color = swatchColor(event.color),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
            ) {
                Text(
                    event.title,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
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

@Composable
private fun DayColumn(
    date: LocalDate,
    events: List<CalendarEvent>,
    onEventClick: (CalendarEvent) -> Unit,
    zone: ZoneId,
) {
    Box(Modifier.fillMaxWidth().height(HourHeight * Hours)) {
        events.forEach { event ->
            val (startMin, endMin) = event.minuteRangeOn(date, zone)
            val top = HourHeight * (startMin / 60f)
            val blockHeight = (HourHeight * ((endMin - startMin) / 60f)).coerceAtLeast(20.dp)
            Surface(
                onClick = { onEventClick(event) },
                color = swatchColor(event.color),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 1.dp)
                    .offset(y = top)
                    .height(blockHeight),
            ) {
                Text(
                    event.title,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp),
                )
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
