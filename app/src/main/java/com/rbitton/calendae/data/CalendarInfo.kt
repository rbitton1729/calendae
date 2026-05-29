package com.rbitton.calendae.data

/**
 * A calendar available on the device, as listed in [android.provider.CalendarContract.Calendars].
 *
 * @property isWritable true when events may be inserted into this calendar
 *   (contributor access or higher).
 */
data class CalendarInfo(
    val id: Long,
    val displayName: String,
    val accountName: String,
    val color: Int,
    val isPrimary: Boolean,
    val isWritable: Boolean,
)
