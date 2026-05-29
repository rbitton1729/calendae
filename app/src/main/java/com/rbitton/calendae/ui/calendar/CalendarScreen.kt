package com.rbitton.calendae.ui.calendar

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rbitton.calendae.fold.FoldState
import com.rbitton.calendae.fold.rememberFoldState
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val MonthTitleFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())

private val CalendarPermissions = arrayOf(
    Manifest.permission.READ_CALENDAR,
    Manifest.permission.WRITE_CALENDAR,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(viewModel: CalendarViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val foldState = rememberFoldState()
    val context = LocalContext.current
    var showAddDialog by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        viewModel.onPermissionResult(result[Manifest.permission.READ_CALENDAR] == true)
    }

    // Reflect any permission already granted on (re)entry, before prompting.
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_CALENDAR,
        ) == PackageManager.PERMISSION_GRANTED
        viewModel.onPermissionResult(granted)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.visibleMonth.format(MonthTitleFormat)) },
                actions = {
                    IconButton(onClick = viewModel::previousMonth) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous month")
                    }
                    TextButton(onClick = viewModel::goToToday) { Text("Today") }
                    IconButton(onClick = viewModel::nextMonth) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next month")
                    }
                },
            )
        },
        floatingActionButton = {
            if (state.hasPermission) {
                FloatingActionButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Filled.Add, "Add event")
                }
            }
        },
    ) { padding ->
        when {
            !state.hasPermission -> PermissionGate(
                onGrant = { permissionLauncher.launch(CalendarPermissions) },
                modifier = Modifier.padding(padding),
            )

            foldState.isBookSpread -> BookSpread(
                foldState = foldState,
                left = { MonthPage(state, viewModel) },
                right = { DayAgenda(state.selectedDate, state.selectedDayEvents) },
                modifier = Modifier.padding(padding),
            )

            else -> SinglePane(state, viewModel, Modifier.padding(padding))
        }
    }

    if (showAddDialog) {
        AddEventDialog(
            date = state.selectedDate,
            onDismiss = { showAddDialog = false },
            onConfirm = { title, start, end ->
                viewModel.addEvent(title, state.selectedDate, start, end)
                showAddDialog = false
            },
        )
    }
}

/** The month grid sized to fill its page, wrapping the [MonthGrid] with state plumbing. */
@Composable
private fun MonthPage(state: CalendarUiState, viewModel: CalendarViewModel) {
    MonthGrid(
        month = state.visibleMonth,
        selectedDate = state.selectedDate,
        today = LocalDate.now(),
        daysWithEvents = state.eventsByDate.keys,
        onDateClick = viewModel::selectDate,
        modifier = Modifier.fillMaxSize().padding(vertical = 8.dp),
    )
}

/**
 * Two facing pages with the gutter aligned to the physical hinge when one is
 * reported; otherwise an even split with a divider.
 */
@Composable
private fun BookSpread(
    foldState: FoldState,
    left: @Composable () -> Unit,
    right: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxSize()) {
        val hingeStart = foldState.hingeStartPx
        if (hingeStart == null) {
            Box(Modifier.weight(1f)) { left() }
            VerticalDivider()
            Box(Modifier.weight(1f)) { right() }
        } else {
            val density = LocalDensity.current
            val leftWidth = with(density) { hingeStart.toDp() }
            val gutterWidth = with(density) { foldState.hingeWidthPx.toDp() }
            Box(Modifier.width(leftWidth)) { left() }
            Spacer(Modifier.width(gutterWidth))
            Box(Modifier.weight(1f)) { right() }
        }
    }
}

/** Folded / phone layout: month grid stacked above the selected day's agenda. */
@Composable
private fun SinglePane(
    state: CalendarUiState,
    viewModel: CalendarViewModel,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        MonthGrid(
            month = state.visibleMonth,
            selectedDate = state.selectedDate,
            today = LocalDate.now(),
            daysWithEvents = state.eventsByDate.keys,
            onDateClick = viewModel::selectDate,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )
        androidx.compose.material3.HorizontalDivider(Modifier.padding(horizontal = 16.dp))
        DayAgenda(
            date = state.selectedDate,
            events = state.selectedDayEvents,
            modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp),
        )
    }
}

@Composable
private fun PermissionGate(onGrant: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Calendae needs access to your calendar to show and add events.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onGrant) { Text("Grant access") }
        }
    }
}
