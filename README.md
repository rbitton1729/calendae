# Calendae

A simple, **foldable-first** calendar for Android. On a book-style foldable it
lays out like a paper planner — a month grid on the left page, the selected
day's agenda on the right — and gracefully collapses to a single stacked pane
when folded or on a phone.

## Design

- **Book spread:** when the device reports a vertical fold, the UI splits into
  two facing pages with the gutter aligned to the physical hinge.
- **Month + Day:** left page is the month grid; tapping a date fills the right
  page with that day's events.
- **System calendar:** events are read from and written to the device's
  calendars via `CalendarContract` (requires `READ_/WRITE_CALENDAR`).

## Architecture

```
ui/calendar/   CalendarScreen (book spread + single-pane fallback + permission gate),
               CalendarViewModel, MonthGrid, DayAgenda, AddEventDialog
fold/          FoldState + rememberFoldState() — WindowInfoTracker hinge detection
data/          CalendarEvent, CalendarRepository (CalendarContract reads/writes)
```

Jetpack Compose + Material 3, MVVM, `androidx.window` for posture detection.
`minSdk 36`, so `java.time` and current adaptive APIs are used directly.

## Build & run

```sh
./gradlew :app:assembleDebug      # build the APK
./gradlew :app:installDebug       # install on a connected device/emulator
```

Best experienced on a foldable emulator (e.g. *Pixel Fold* / *7.6" Fold-in*) —
toggle the fold state to see the book spread engage. Grant calendar access on
first launch.
