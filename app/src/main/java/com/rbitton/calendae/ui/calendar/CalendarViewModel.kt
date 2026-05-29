package com.rbitton.calendae.ui.calendar

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rbitton.calendae.data.CalendarEvent
import com.rbitton.calendae.data.CalendarRepository
import com.rbitton.calendae.data.startDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

/** Immutable snapshot of everything the calendar screen renders. */
data class CalendarUiState(
    val visibleMonth: YearMonth,
    val selectedDate: LocalDate,
    val eventsByDate: Map<LocalDate, List<CalendarEvent>> = emptyMap(),
    val hasPermission: Boolean = false,
    val loading: Boolean = false,
) {
    val selectedDayEvents: List<CalendarEvent>
        get() = eventsByDate[selectedDate].orEmpty()
}

class CalendarViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = CalendarRepository(app)
    private val zone: ZoneId = ZoneId.systemDefault()

    private val today: LocalDate = LocalDate.now()

    private val _state = MutableStateFlow(
        CalendarUiState(visibleMonth = YearMonth.from(today), selectedDate = today),
    )
    val state: StateFlow<CalendarUiState> = _state.asStateFlow()

    fun onPermissionResult(granted: Boolean) {
        _state.update { it.copy(hasPermission = granted) }
        if (granted) refresh()
    }

    fun selectDate(date: LocalDate) {
        _state.update {
            val month = YearMonth.from(date)
            if (month == it.visibleMonth) it.copy(selectedDate = date)
            else it.copy(selectedDate = date, visibleMonth = month)
        }
        if (_state.value.hasPermission && YearMonth.from(date) != _state.value.visibleMonth) {
            refresh()
        }
    }

    fun showMonth(month: YearMonth) {
        _state.update { it.copy(visibleMonth = month) }
        if (_state.value.hasPermission) refresh()
    }

    fun previousMonth() = showMonth(_state.value.visibleMonth.minusMonths(1))
    fun nextMonth() = showMonth(_state.value.visibleMonth.plusMonths(1))

    fun goToToday() {
        _state.update { it.copy(visibleMonth = YearMonth.from(today), selectedDate = today) }
        if (_state.value.hasPermission) refresh()
    }

    fun addEvent(title: String, date: LocalDate, start: LocalTime, end: LocalTime) {
        viewModelScope.launch {
            repository.addEvent(title, date, start, end, zone)
            refresh()
        }
    }

    /** Loads every event in the visible month plus its leading/trailing edge days. */
    fun refresh() {
        if (!_state.value.hasPermission) return
        val month = _state.value.visibleMonth
        // Pad by a week each side so events on grid cells from adjacent months show too.
        val rangeStart = month.atDay(1).minusDays(7)
        val rangeEnd = month.atEndOfMonth().plusDays(8)

        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val events = repository.eventsBetween(rangeStart, rangeEnd, zone)
            val byDate = events.groupBy { it.startDate(zone) }
                .mapValues { (_, list) -> list.sortedBy { it.sortKey } }
            _state.update { it.copy(eventsByDate = byDate, loading = false) }
        }
    }
}
