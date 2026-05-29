package com.rbitton.calendae.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Reads and writes events through the system [CalendarContract] provider.
 *
 * All calls touch the [android.content.ContentResolver] and therefore run on
 * [Dispatchers.IO]. Callers are responsible for holding the relevant runtime
 * calendar permissions before invoking these methods.
 */
class CalendarRepository(private val context: Context) {

    private val resolver get() = context.contentResolver

    /**
     * Returns all event instances overlapping the half-open range
     * [[start], [endExclusive]), expanded so that recurring events appear as
     * individual occurrences.
     */
    suspend fun eventsBetween(
        start: LocalDate,
        endExclusive: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<CalendarEvent> = withContext(Dispatchers.IO) {
        val startMillis = start.atStartOfDay(zone).toInstant().toEpochMilli()
        val endMillis = endExclusive.atStartOfDay(zone).toInstant().toEpochMilli()

        // Instances.CONTENT_URI takes the window as path segments and expands recurrences.
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath(startMillis.toString())
            .appendPath(endMillis.toString())
            .build()

        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.DISPLAY_COLOR,
        )

        val events = mutableListOf<CalendarEvent>()
        resolver.query(uri, projection, null, null, CalendarContract.Instances.BEGIN)
            ?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_ID)
                val titleCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
                val beginCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
                val endCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.END)
                val allDayCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.ALL_DAY)
                val locCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_LOCATION)
                val colorCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.DISPLAY_COLOR)

                while (cursor.moveToNext()) {
                    events += CalendarEvent(
                        id = cursor.getLong(idCol),
                        title = cursor.getString(titleCol)?.takeIf { it.isNotBlank() }
                            ?: "(No title)",
                        startMillis = cursor.getLong(beginCol),
                        endMillis = cursor.getLong(endCol),
                        allDay = cursor.getInt(allDayCol) == 1,
                        location = cursor.getString(locCol)?.takeIf { it.isNotBlank() },
                        color = cursor.getInt(colorCol),
                    )
                }
            }
        events
    }

    /** Convenience wrapper for a single day. */
    suspend fun eventsOn(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<CalendarEvent> =
        eventsBetween(date, date.plusDays(1), zone)

    /**
     * Inserts a timed event into the user's primary writable calendar and returns
     * its new event id, or `null` if no writable calendar is available.
     */
    suspend fun addEvent(
        title: String,
        date: LocalDate,
        start: LocalTime,
        end: LocalTime,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long? = withContext(Dispatchers.IO) {
        val calendarId = primaryCalendarId() ?: return@withContext null
        val startMillis = date.atTime(start).atZone(zone).toInstant().toEpochMilli()
        val endMillis = date.atTime(end).atZone(zone).toInstant().toEpochMilli()

        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DTSTART, startMillis)
            put(CalendarContract.Events.DTEND, endMillis)
            put(CalendarContract.Events.EVENT_TIMEZONE, zone.id)
        }
        val uri = resolver.insert(CalendarContract.Events.CONTENT_URI, values)
        uri?.let { ContentUris.parseId(it) }
    }

    /** The id of a writable calendar, preferring the account's primary one. */
    private fun primaryCalendarId(): Long? {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.IS_PRIMARY,
        )
        // Contributor access (500) or higher is required to insert events.
        val selection = "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ? " +
            "AND ${CalendarContract.Calendars.VISIBLE} = 1"
        val args = arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString())
        val order = "${CalendarContract.Calendars.IS_PRIMARY} DESC"

        resolver.query(
            CalendarContract.Calendars.CONTENT_URI, projection, selection, args, order,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getLong(
                    cursor.getColumnIndexOrThrow(CalendarContract.Calendars._ID),
                )
            }
        }
        return null
    }
}
