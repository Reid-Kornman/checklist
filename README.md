# Checklist Android App

This is an Android native app that displays a list of tasks based on the current time of day. The tasks are configured in a JSON file and the app refreshes every hour to show the relevant tasks.

## Features

- Displays tasks from `tasks.json` that match the current hour.
- Refreshes the list every hour automatically.
- Tasks are sorted by order.

## Requirements

- Android 11 (API 30) or higher
- Android Studio or VS Code with Android extensions for building

## Building and Running

1. Open the project in Android Studio.
2. Build and run on an emulator or device.

Alternatively, if using VS Code:

- Install Android extensions.
- Ensure Android SDK is set up.
- Use the gradle wrapper: `./gradlew build`

## Configuration

Edit `app/src/main/assets/tasks.json` to add or modify tasks.

Each task has:
- `name`: The display name
- `order`: The order in the list
- `startTime`: Start time in HH:mm format
- `endTime`: End time in HH:mm format

The app shows tasks where the current hour is >= start hour and < end hour.