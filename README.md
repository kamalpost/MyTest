# Task Manager — Android

A native Android port of the HTML/JS Task Manager, built with **Kotlin** and
**Jetpack Compose (Material 3)**. It keeps the original's GitHub-dark visual
identity and every feature of the web version, adapted to phone-sized screens.

<img alt="architecture" src="https://img.shields.io/badge/Kotlin-2.0-blue" /> <img alt="compose" src="https://img.shields.io/badge/Jetpack%20Compose-M3-green" />

## Feature parity with the web app

| Web app (3-pane HTML) | Android app |
|---|---|
| Pane 1: Add Task (name / category / priority) | **Add** tab |
| Pane 2: Task list + search + filters | **Tasks** tab |
| Pane 3: Details (category / priority / notes / links) | **Details** tab (opens on task tap) |
| Category management with `#`/`!` prefix rule, dedupe | Same rules, category pills with × remove |
| Search across name **and** notes | Same |
| Filters: category, priority, "Not updated today", "Show completed" | Same, as dropdowns + filter chips |
| `updatedToday/total` count badge | Shown on the Tasks tab label |
| Double-click rename | **Long-press** rename dialog |
| Complete / reopen, delete with confirm | Same, with confirm dialog |
| Clickable links detected in notes | Same (opens browser), updates live while typing |
| Pomodoro timer with 25/10/5 presets, warning/urgent colors | Same, in the bar under the header |
| Live clock with red phase each half-hour | Same, in the header |
| Pulsing status dot + header shows selected task name | Same |
| Toasts (success / error / info / warning) | Colored snackbars |
| localStorage + Flask server save | Local JSON file, **saved on every change** |
| Upload / export JSON backup | Storage Access Framework file picker — **same JSON format**, backups from the web app import directly |

The Flask server layer was intentionally dropped: on Android the app persists
to app-private storage on every mutation, which is strictly more reliable than
the web app's 5-minute autosave + save-on-close. Backups replace the server as
the way to move data between devices.

## Project layout

```
app/src/main/java/com/kamalpost/taskmanager/
├── MainActivity.kt              # Scaffold, tabs, snackbars, SAF import/export
├── TaskViewModel.kt             # All state + logic (filters, timer, backup)
├── data/
│   ├── Models.kt                # Task / AppData (JSON-compatible with web app)
│   └── TaskRepository.kt        # Atomic JSON file persistence
└── ui/
    ├── Components.kt            # Shared dropdown, tags, labels, date/link utils
    ├── TimerBar.kt              # Pomodoro bar
    ├── screens/                 # AddTaskScreen, TasksScreen, DetailsScreen
    └── theme/                   # GitHub-dark palette ported from the CSS vars
```

## Building

Open the project in **Android Studio** (Ladybug or newer) and run, or:

```bash
./gradlew assembleDebug        # APK at app/build/outputs/apk/debug/
```

Requires JDK 17+ and the Android SDK (compileSdk 35). Min SDK is 26
(Android 8.0). CI builds a debug APK on every push via GitHub Actions.

## Verification status

This project was authored in a sandbox without access to `dl.google.com`
(no Android SDK / AndroidX artifacts), so the final `assembleDebug` could not
be run there. It was verified instead by:

- Compiling **all** Kotlin sources against Compose Desktop's identical
  `androidx.compose.*` Material 3 APIs + kotlinx-serialization with the
  serialization and Compose compiler plugins — zero errors.
- Running a 30-case behavioral test of the ported logic (repository
  round-trip, web-backup import compatibility, all filters, category rules,
  task lifecycle, timer) — all passing.

The GitHub Actions workflow performs the real SDK build on push.
