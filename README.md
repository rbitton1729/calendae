# Calendae

A simple, **foldable-first** calendar for Android. On a book-style foldable it
lays out like a paper planner — a month grid on the left page, the selected
day's agenda on the right — and gracefully collapses to a single stacked pane
when folded or on a phone.

## Design

- **Book spread:** a vertical fold splits the UI into two facing pages with the
  gutter aligned to the physical hinge. A horizontal fold (tabletop) splits
  top/bottom instead. No fold → single pane / even split.
- **Month + Day:** left page is the month grid; tapping a date fills the right
  page with that day's events. Tap an event to edit or delete it; the FAB adds.
- **Week spread:** a second view mode — an hour grid with day columns, split
  across the hinge in book posture. Toggle Month/Week from the top bar.
- **System calendar:** events are read from and written to the device's
  calendars via `CalendarContract` (requires `READ_/WRITE_CALENDAR`). The
  Calendars dialog toggles per-calendar visibility and acts as a color legend.

## Architecture

```
ui/calendar/   CalendarScreen (posture- + view-mode-aware host, permission gate),
               CalendarViewModel, MonthGrid, DayAgenda, WeekGrid,
               EventEditorDialog (add/edit/delete), CalendarsDialog
fold/          FoldState + rememberFoldState() — WindowInfoTracker hinge detection
data/          CalendarEvent, CalendarInfo, CalendarRepository (CalendarContract)
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
