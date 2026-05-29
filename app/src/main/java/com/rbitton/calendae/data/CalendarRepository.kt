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
     * individual occurrences. When [calendarIds] is non-null, only events from
     * those calendars are returned.
     */
    suspend fun eventsBetween(
        start: LocalDate,
        endExclusive: LocalDate,
        calendarIds: Set<Long>? = null,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<CalendarEvent> = withContext(Dispatchers.IO) {
        if (calendarIds != null && calendarIds.isEmpty()) return@withContext emptyList()

        val startMillis = start.atStartOfDay(zone).toInstant().toEpochMilli()
        val endMillis = endExclusive.atStartOfDay(zone).toInstant().toEpochMilli()

        // Instances.CONTENT_URI takes the window as path segments and expands recurrences.
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath(startMillis.toString())
            .appendPath(endMillis.toString())
            .build()

        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.DISPLAY_COLOR,
        )

        val (selection, args) = calendarIds?.let { ids ->
            val placeholders = ids.joinToString(",") { "?" }
            "${CalendarContract.Instances.CALENDAR_ID} IN ($placeholders)" to
                ids.map { it.toString() }.toTypedArray()
        } ?: (null to null)

        val events = mutableListOf<CalendarEvent>()
        resolver.query(uri, projection, selection, args, CalendarContract.Instances.BEGIN)
            ?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_ID)
                val calCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.CALENDAR_ID)
                val titleCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
                val beginCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
                val endCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.END)
                val allDayCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.ALL_DAY)
                val locCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_LOCATION)
                val colorCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.DISPLAY_COLOR)

                while (cursor.moveToNext()) {
                    events += CalendarEvent(
                        id = cursor.getLong(idCol),
                        calendarId = cursor.getLong(calCol),
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

    /** Lists every visible calendar on the device, primary ones first. */
    suspend fun calendars(): List<CalendarInfo> = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.CALENDAR_COLOR,
            CalendarContract.Calendars.IS_PRIMARY,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
        )
        val order = "${CalendarContract.Calendars.IS_PRIMARY} DESC, " +
            "${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME} ASC"

        val result = mutableListOf<CalendarInfo>()
        // List every calendar (not just system-VISIBLE ones); Calendae manages its
        // own per-calendar visibility, and events are shown regardless of the
        // system flag, so the list must match what actually appears.
        resolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            null,
            null,
            order,
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(CalendarContract.Calendars._ID)
            val nameCol = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
            val accountCol = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_NAME)
            val colorCol = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_COLOR)
            val primaryCol = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.IS_PRIMARY)
            val accessCol = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL)

            while (cursor.moveToNext()) {
                result += CalendarInfo(
                    id = cursor.getLong(idCol),
                    displayName = cursor.getString(nameCol)?.takeIf { it.isNotBlank() }
                        ?: "(Unnamed)",
                    accountName = cursor.getString(accountCol).orEmpty(),
                    color = cursor.getInt(colorCol),
                    isPrimary = cursor.getInt(primaryCol) == 1,
                    isWritable = cursor.getInt(accessCol) >=
                        CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR,
                )
            }
        }
        result
    }

    /**
     * Inserts a timed event and returns its new event id, or `null` if it could
     * not be created. Uses [calendarId] when given, otherwise the primary
     * writable calendar.
     */
    suspend fun addEvent(
        title: String,
        date: LocalDate,
        start: LocalTime,
        end: LocalTime,
        calendarId: Long? = null,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long? = withContext(Dispatchers.IO) {
        val targetCalendar = calendarId ?: primaryCalendarId() ?: return@withContext null
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, targetCalendar)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DTSTART, date.atTime(start).atZone(zone).toInstant().toEpochMilli())
            put(CalendarContract.Events.DTEND, date.atTime(end).atZone(zone).toInstant().toEpochMilli())
            put(CalendarContract.Events.EVENT_TIMEZONE, zone.id)
        }
        val uri = resolver.insert(CalendarContract.Events.CONTENT_URI, values)
        uri?.let { ContentUris.parseId(it) }
    }

    /** Updates an existing event's title and times. Returns true on success. */
    suspend fun updateEvent(
        eventId: Long,
        title: String,
        date: LocalDate,
        start: LocalTime,
        end: LocalTime,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Boolean = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DTSTART, date.atTime(start).atZone(zone).toInstant().toEpochMilli())
            put(CalendarContract.Events.DTEND, date.atTime(end).atZone(zone).toInstant().toEpochMilli())
            put(CalendarContract.Events.EVENT_TIMEZONE, zone.id)
        }
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        resolver.update(uri, values, null, null) > 0
    }

    /** Deletes an event by id. Returns true if a row was removed. */
    suspend fun deleteEvent(eventId: Long): Boolean = withContext(Dispatchers.IO) {
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        resolver.delete(uri, null, null) > 0
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
