package com.rbitton.calendae.ui.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rbitton.calendae.data.CalendarEvent
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.floor

/** Compact (short-cell) indicators: up to this many dots, then a "+N" badge. */
private const val MaxDots = 3
private val DotSize = 5.dp
/** Reserved height under the number in compact mode, so day numbers stay aligned. */
private val IndicatorHeight = 15.dp
/** The day-number highlight never grows past this, however tall the cell is. */
private val MaxDayCircle = 40.dp

/** A cell at least this tall/wide gets event title chips instead of dots. */
private val RichMinCellWidth = 46.dp
private val RichDayCircle = 26.dp
private val RichNumberArea = 30.dp
private val ChipHeight = 17.dp
private val ChipSpacing = 2.dp

/**
 * Expands a month into whole weeks (each a list of 7 dates) starting on
 * [weekStart]. The first and last weeks may include days from adjacent months.
 */
private fun YearMonth.toWeeks(weekStart: DayOfWeek): List<List<LocalDate>> {
    val gridStart = atDay(1).with(TemporalAdjusters.previousOrSame(weekStart))
    val gridEndInclusive = atEndOfMonth().with(TemporalAdjusters.nextOrSame(weekStart.plus(6)))
    val weeks = mutableListOf<List<LocalDate>>()
    var cursor = gridStart
    while (!cursor.isAfter(gridEndInclusive)) {
        weeks += (0L..6L).map { cursor.plusDays(it) }
        cursor = cursor.plusDays(7)
    }
    return weeks
}

private fun DayOfWeek.isWeekend() = this == DayOfWeek.SATURDAY || this == DayOfWeek.SUNDAY

/**
 * A month calendar grid. The week rows share the available height, so the grid
 * fills its page rather than leaving a gap (or overflowing). Days from adjacent
 * months are dimmed. Each day adapts to its size: tall, wide cells (e.g. a book
 * page) show event title chips; short cells fall back to colored dots and a "+N".
 */
@Composable
fun MonthGrid(
    month: YearMonth,
    selectedDate: LocalDate,
    today: LocalDate,
    eventsByDate: Map<LocalDate, List<CalendarEvent>>,
    onDateClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val firstDayOfWeek = DayOfWeek.MONDAY
    val weeks = remember(month, firstDayOfWeek) { month.toWeeks(firstDayOfWeek) }
    val scheme = MaterialTheme.colorScheme

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        Row(Modifier.fillMaxWidth()) {
            for (i in 0..6) {
                val day = firstDayOfWeek.plus(i.toLong())
                val isTodayColumn = day == today.dayOfWeek
                Text(
                    text = day.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isTodayColumn) FontWeight.Bold else FontWeight.Normal,
                    color = when {
                        isTodayColumn -> scheme.primary
                        day.isWeekend() -> scheme.onSurfaceVariant.copy(alpha = 0.55f)
                        else -> scheme.onSurfaceVariant
                    },
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                )
            }
        }
        for (week in weeks) {
            Row(Modifier.fillMaxWidth().weight(1f)) {
                for (date in week) {
                    DayCell(
                        date = date,
                        inMonth = YearMonth.from(date) == month,
                        isToday = date == today,
                        isSelected = date == selectedDate,
                        events = eventsByDate[date].orEmpty(),
                        onClick = { onDateClick(date) },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    inMonth: Boolean,
    isToday: Boolean,
    isSelected: Boolean,
    events: List<CalendarEvent>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val numberColor = when {
        isSelected -> scheme.onPrimary
        !inMonth -> scheme.onSurfaceVariant.copy(alpha = 0.38f)
        isToday -> scheme.primary
        date.dayOfWeek.isWeekend() -> scheme.onSurfaceVariant
        else -> scheme.onSurface
    }

    // The whole cell selects the day. The pager owns horizontal drags, so this click
    // doesn't fight the swipe (taps select, drags page — like rows in a LazyColumn).
    BoxWithConstraints(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(2.dp),
        contentAlignment = Alignment.Center,
    ) {
        // How many title chips fit under the number; <2 (or a narrow/unbounded cell)
        // means there isn't room for a useful preview, so we show dots instead.
        val chipCapacity =
            if (maxHeight == Dp.Infinity) 0
            else floor((maxHeight - RichNumberArea).value / (ChipHeight + ChipSpacing).value).toInt()
        val rich = maxWidth >= RichMinCellWidth && chipCapacity >= 2

        if (rich) {
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.fillMaxWidth().height(RichNumberArea), contentAlignment = Alignment.Center) {
                    DayNumber(date, isToday, isSelected, numberColor, RichDayCircle)
                }
                EventChips(events, capacity = chipCapacity, dimmed = !inMonth)
            }
        } else {
            val circle = minOf(maxWidth, maxHeight - IndicatorHeight).coerceIn(0.dp, MaxDayCircle)
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                DayNumber(date, isToday, isSelected, numberColor, circle)
                EventDots(events, dimmed = !inMonth)
            }
        }
    }
}

/** The day number in its today/selected highlight. */
@Composable
private fun DayNumber(
    date: LocalDate,
    isToday: Boolean,
    isSelected: Boolean,
    numberColor: Color,
    diameter: Dp,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        color = if (isSelected) scheme.primary else Color.Transparent,
        shape = CircleShape,
        border = if (isToday && !isSelected) BorderStroke(1.5.dp, scheme.primary) else null,
        modifier = Modifier.size(diameter),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                color = numberColor,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Title chips for tall cells: as many as fit, then a "+N more" line. */
@Composable
private fun EventChips(events: List<CalendarEvent>, capacity: Int, dimmed: Boolean) {
    if (events.isEmpty()) return
    val shownCount = if (events.size <= capacity) events.size else capacity - 1
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 1.dp),
        verticalArrangement = Arrangement.spacedBy(ChipSpacing),
    ) {
        events.take(shownCount).forEach { event ->
            val bg = swatchColor(event.color)
            Surface(
                color = bg,
                shape = RoundedCornerShape(3.dp),
                modifier = Modifier.fillMaxWidth().height(ChipHeight).alpha(if (dimmed) 0.5f else 1f),
            ) {
                Box(contentAlignment = Alignment.CenterStart) {
                    Text(
                        text = event.title,
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColorOn(bg),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
        }
        val overflow = events.size - shownCount
        if (overflow > 0) {
            Text(
                text = "+$overflow more",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (dimmed) 0.5f else 1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

/**
 * Compact indicators for short cells: up to [MaxDots] dots in the events' calendar
 * colors, then a "+N". Always occupies [IndicatorHeight] so numbers stay aligned.
 */
@Composable
private fun EventDots(events: List<CalendarEvent>, dimmed: Boolean) {
    Box(
        Modifier.fillMaxWidth().height(IndicatorHeight),
        contentAlignment = Alignment.Center,
    ) {
        if (events.isEmpty()) return@Box
        val shown = events.take(MaxDots)
        val overflow = events.size - shown.size
        val alpha = if (dimmed) 0.4f else 1f
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            shown.forEach { event ->
                Box(
                    Modifier
                        .size(DotSize)
                        .clip(CircleShape)
                        .background(swatchColor(event.color).copy(alpha = alpha)),
                )
            }
            if (overflow > 0) {
                Text(
                    text = "+$overflow",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                    fontSize = 9.sp,
                    lineHeight = 10.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}
