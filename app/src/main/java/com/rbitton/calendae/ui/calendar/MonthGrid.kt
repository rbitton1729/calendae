package com.rbitton.calendae.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

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

/**
 * A month calendar grid. Days from adjacent months that fall in the leading or
 * trailing week are shown dimmed. A dot under a day marks that it has events.
 */
@Composable
fun MonthGrid(
    month: YearMonth,
    selectedDate: LocalDate,
    today: LocalDate,
    daysWithEvents: Set<LocalDate>,
    onDateClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val firstDayOfWeek = DayOfWeek.MONDAY
    val weeks = remember(month, firstDayOfWeek) { month.toWeeks(firstDayOfWeek) }

    Column(modifier = modifier.padding(horizontal = 8.dp)) {
        Row(Modifier.fillMaxWidth()) {
            for (i in 0..6) {
                val day = firstDayOfWeek.plus(i.toLong())
                Text(
                    text = day.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                )
            }
        }
        for (week in weeks) {
            Row(Modifier.fillMaxWidth()) {
                for (date in week) {
                    DayCell(
                        date = date,
                        inMonth = YearMonth.from(date) == month,
                        isToday = date == today,
                        isSelected = date == selectedDate,
                        hasEvents = date in daysWithEvents,
                        onClick = { onDateClick(date) },
                        modifier = Modifier.weight(1f),
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
    hasEvents: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val numberColor = when {
        isSelected -> scheme.onPrimary
        !inMonth -> scheme.onSurfaceVariant.copy(alpha = 0.4f)
        isToday -> scheme.primary
        else -> scheme.onSurface
    }

    Box(modifier = modifier.aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
        val cellModifier = when {
            isSelected -> Modifier.clip(CircleShape).size(40.dp)
            else -> Modifier.size(40.dp)
        }
        Surface(
            onClick = onClick,
            color = if (isSelected) scheme.primary else scheme.surface,
            shape = CircleShape,
            modifier = cellModifier,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = numberColor,
                    textAlign = TextAlign.Center,
                )
                Box(
                    Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .let { m ->
                            if (hasEvents) m.background(
                                if (isSelected) scheme.onPrimary else scheme.primary,
                            ) else m
                        },
                )
            }
        }
    }
}
