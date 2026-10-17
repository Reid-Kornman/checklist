# Checklist Android App

A native Android checklist for organizing tasks into routines.

## Features

- Create, edit, and delete routines, each with a name and start/end times.
- Add tasks directly to the selected routine and edit or delete them inline.
- Reorder tasks by dragging them into position and saving the new order.
- Mark tasks complete and reset completion status.
- Switch between light and dark mode; the selection is saved between launches.
- Store routines and tasks locally on the device.

The task list shows the selected routine's tasks in their configured order. Routine times are displayed but do not automatically filter tasks by the current time.

## Screenshots

| Light mode | Dark mode |
|---|---|
| <img src="docs/screenshots/Screenshot_20261016_225440.png" width="280" alt="Checklist with completed and incomplete tasks in light mode"> | <img src="docs/screenshots/Screenshot_20261016_225503.png" width="280" alt="Checklist with completed and incomplete tasks in dark mode"> |

| Add a task | Add a routine |
|---|---|
| <img src="docs/screenshots/Screenshot_20261016_225529.png" width="280" alt="Entering a new task inline with the keyboard open"> | <img src="docs/screenshots/Screenshot_20261016_225542.png" width="280" alt="Add Routine dialog with routine name and start and end times"> |

## Task Workflow

- Select a routine from the list at the top of the screen.
- Choose **Add Task** to create a task. Its name is entered inline; save it with the check button. Canceling a new task discards it.
- Use the edit button beside a task to change its name. Save with the check button or cancel to keep the existing name. Use the delete button and confirm to remove a task.
- Choose **Reorder Tasks**, drag tasks using their handles, then choose **Save Order** to persist the new sequence.
- Finish editing or save the order before starting another task edit or adding a task.

## Requirements

- Android 11 (API 30) or higher for a device or emulator.
- Android SDK Platform 37.
- JDK 21 for the Gradle build. The Gradle wrapper is configured to provision its JDK toolchain.

## Build and Install

From the project root in Windows PowerShell, build the debug APK:

```powershell
.\gradlew.bat :app:assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

With a device or emulator connected, install the debug build with:

```powershell
.\gradlew.bat :app:installDebug
```

To build, install, and launch on a running emulator using the npm script (requires Node.js/npm and Android `adb` on `PATH`):

```powershell
npm run deploy:emulator
```

This script removes `app/build` before building and installing the app.

You can also open the project in Android Studio, sync Gradle, and run the `app` configuration on a device or emulator.

## Data

Create and manage routines and tasks in the app. They are saved locally using Android SharedPreferences; there is no `tasks.json` configuration file.