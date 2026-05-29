package com.rbitton.calendae.ui.calendar

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rbitton.calendae.data.CalendarEvent
import com.rbitton.calendae.data.CalendarInfo
import com.rbitton.calendae.data.CalendarRepository
import com.rbitton.calendae.data.endDateInclusive
import com.rbitton.calendae.data.startDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

enum class ViewMode { MONTH, WEEK }

/** Half-width, in days, of the event window loaded for the free-scrolling week view. */
const val WEEK_WINDOW_DAYS = 182L

/** Immutable snapshot of everything the calendar screen renders. */
data class CalendarUiState(
    val visibleMonth: YearMonth,
    val selectedDate: LocalDate,
    val today: LocalDate,
    val viewMode: ViewMode = ViewMode.MONTH,
    val eventsByDate: Map<LocalDate, List<CalendarEvent>> = emptyMap(),
    val calendars: List<CalendarInfo> = emptyList(),
    val enabledCalendarIds: Set<Long> = emptySet(),
    val hasPermission: Boolean = false,
    val loading: Boolean = false,
) {
    val selectedDayEvents: List<CalendarEvent>
        get() = eventsByDate[selectedDate].orEmpty()

    /** Monday-anchored week containing [selectedDate]. */
    val weekStart: LocalDate
        get() = selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    val weekDays: List<LocalDate>
        get() = (0L..6L).map { weekStart.plusDays(it) }

    /** Index-0 date of the free-scrolling week timeline. */
    val weekWindowStart: LocalDate get() = today.minusDays(WEEK_WINDOW_DAYS)

    /** Total number of day columns in the week timeline. */
    val weekWindowDays: Int get() = (WEEK_WINDOW_DAYS * 2 + 1).toInt()
}

class CalendarViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = CalendarRepository(app)
    private val zone: ZoneId = ZoneId.systemDefault()
    private val today: LocalDate = LocalDate.now()

    private val _state = MutableStateFlow(
        CalendarUiState(visibleMonth = YearMonth.from(today), selectedDate = today, today = today),
    )
    val state: StateFlow<CalendarUiState> = _state.asStateFlow()

    /** Guards against kicking off a second calendar load while one is already running. */
    private var loadingCalendars = false

    init {
        // On a returning launch access is usually already granted, so start the calendar
        // query here (as the VM is built) rather than waiting for the first frame's
        // permission check — the IO is then in flight before the UI finishes composing.
        val granted = ContextCompat.checkSelfPermission(
            app, Manifest.permission.READ_CALENDAR,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            _state.update { it.copy(hasPermission = true) }
            loadCalendars()
        }
    }

    fun onPermissionResult(granted: Boolean) {
        val changed = _state.value.hasPermission != granted
        _state.update { it.copy(hasPermission = granted) }
        if (granted && (changed || _state.value.calendars.isEmpty())) loadCalendars()
    }

    fun selectDate(date: LocalDate) {
        val monthChanged = YearMonth.from(date) != _state.value.visibleMonth
        _state.update { it.copy(selectedDate = date, visibleMonth = YearMonth.from(date)) }
        if (monthChanged) refresh()
    }

    fun setViewMode(mode: ViewMode) {
        if (mode == _state.value.viewMode) return
        _state.update { it.copy(viewMode = mode) }
        refresh()
    }

    /** Loads [month] into the grid (the month pager drives this). Keeps the selected day. */
    fun showMonth(month: YearMonth) {
        if (month == _state.value.visibleMonth) return
        _state.update { it.copy(visibleMonth = month) }
        refresh()
    }

    fun goToToday() {
        _state.update {
            it.copy(visibleMonth = YearMonth.from(today), selectedDate = today)
        }
        refresh()
    }

    fun setCalendarEnabled(id: Long, enabled: Boolean) {
        _state.update {
            val ids = if (enabled) it.enabledCalendarIds + id else it.enabledCalendarIds - id
            it.copy(enabledCalendarIds = ids)
        }
        refresh()
    }

    fun addEvent(title: String, date: LocalDate, start: LocalTime, end: LocalTime, calendarId: Long?) {
        viewModelScope.launch {
            repository.addEvent(title, date, start, end, calendarId, zone)
            refresh()
        }
    }

    fun updateEvent(eventId: Long, title: String, date: LocalDate, start: LocalTime, end: LocalTime) {
        viewModelScope.launch {
            repository.updateEvent(eventId, title, date, start, end, zone)
            refresh()
        }
    }

    fun deleteEvent(eventId: Long) {
        viewModelScope.launch {
            repository.deleteEvent(eventId)
            refresh()
        }
    }

    private fun loadCalendars() {
        if (loadingCalendars) return // init and the permission callback can both ask
        loadingCalendars = true
        viewModelScope.launch {
            val calendars = repository.calendars()
            _state.update {
                it.copy(calendars = calendars, enabledCalendarIds = calendars.map { c -> c.id }.toSet())
            }
            loadingCalendars = false
            refresh()
        }
    }

    /** Loads events for the range the active view needs, applying the calendar filter. */
    fun refresh() {
        val s = _state.value
        if (!s.hasPermission) return

        val (rangeStart, rangeEnd) = when (s.viewMode) {
            // Load the neighbouring months too (plus a week of grid padding), so the
            // pager shows an adjacent month's events as you swipe to it, not after.
            ViewMode.MONTH -> s.visibleMonth.minusMonths(1).atDay(1).minusDays(7) to
                s.visibleMonth.plusMonths(1).atEndOfMonth().plusDays(8)
            // Load the whole timeline window so free scrolling never hits empty days.
            ViewMode.WEEK -> s.weekWindowStart to s.weekWindowStart.plusDays(s.weekWindowDays + 1L)
        }
        // Filtering is only needed when a strict subset of calendars is enabled.
        val filter = when {
            s.calendars.isEmpty() -> null
            s.enabledCalendarIds.size == s.calendars.size -> null
            else -> s.enabledCalendarIds
        }

        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val events = repository.eventsBetween(rangeStart, rangeEnd, filter, zone)
            // Put each event on every day it spans, so overnight/multi-day events
            // appear on the following day(s), not just their start day.
            val byDate = HashMap<LocalDate, MutableList<CalendarEvent>>()
            events.forEach { event ->
                var day = event.startDate(zone)
                val last = event.endDateInclusive(zone)
                while (!day.isAfter(last)) {
                    byDate.getOrPut(day) { mutableListOf() }.add(event)
                    day = day.plusDays(1)
                }
            }
            val sorted = byDate.mapValues { (_, list) -> list.sortedBy { it.sortKey } }
            _state.update { it.copy(eventsByDate = sorted, loading = false) }
        }
    }
}
