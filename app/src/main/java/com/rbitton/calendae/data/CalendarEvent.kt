package com.rbitton.calendae.data

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * A single occurrence of a calendar event, as read from the system calendar.
 *
 * Times are stored as epoch milliseconds (UTC); [allDay] events are anchored to
 * UTC midnight by the platform, so they are interpreted accordingly.
 */
data class CalendarEvent(
    val id: Long,
    val calendarId: Long,
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    val allDay: Boolean,
    val location: String?,
    val color: Int,
) {
    fun startTime(zone: ZoneId): LocalTime =
        Instant.ofEpochMilli(startMillis).atZone(zone).toLocalTime()

    fun endTime(zone: ZoneId): LocalTime =
        Instant.ofEpochMilli(endMillis).atZone(zone).toLocalTime()

    /** Sort key: all-day events first, then by start time. */
    val sortKey: Long get() = if (allDay) Long.MIN_VALUE else startMillis
}

/** The local date an event begins on, in the given [zone]. */
fun CalendarEvent.startDate(zone: ZoneId): LocalDate =
    Instant.ofEpochMilli(startMillis).atZone(zone).toLocalDate()
