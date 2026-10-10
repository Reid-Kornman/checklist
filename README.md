# Checklist Android App

A native Android checklist for organizing tasks into routines.

## Features

- Create, edit, and delete routines, each with a name and start/end times.
- Add, edit, delete, and order tasks in a routine.
- Mark tasks complete and reset completion status.
- Switch between light and dark mode; the selection is saved between launches.
- Store routines and tasks locally on the device.

The task list shows the selected routine's tasks in their configured order. Routine times are displayed but do not automatically filter tasks by the current time.

## Requirements

- Android 11 (API 30) or higher for a device or emulator.
- Android SDK Platform 36.
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