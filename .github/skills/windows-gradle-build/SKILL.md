---
name: windows-gradle-build
description: "Use when building this Android app with Gradle on Windows, especially recurring 'Unable to delete directory' or 'Failed to delete some children' errors in app/build. Prevent concurrent builds and use the shortest verified recovery path."
---

# Windows Gradle Builds

Use the Gradle wrapper from the repository root. Generated outputs are shared under `app/build`; overlapping Gradle invocations or an IDE build can contend for them.

## Normal Build

1. Run one Gradle command at a time and wait for it to finish before starting another task, including tasks launched by Android Studio.
2. For ordinary incremental builds, invoke the requested task using the repository's Gradle wrapper. Do not run `clean`, stop daemons, or delete build outputs preemptively.

## Repeated Output Deletion Failure

When Gradle reports `Unable to delete directory` or `Failed to delete some children`:

1. Capture the failing task and paths, and wait for Gradle to exit. Do not start another build yet.
2. Check `gradlew.bat --status` and running Gradle processes. Do not stop a busy daemon. If Android Studio is open on this project, wait for its build/sync to finish; if needed, ask the user to save and close the project. Never force-close the IDE.
3. Stop idle daemons with `gradlew.bat --stop`, then retry the original task once.
4. If the retry still fails, especially if the error moves between generated directories, stop chasing individual paths. Confirm no build or IDE process is active, stop idle daemons, and remove the generated `app/build` tree once:

	```powershell
	Remove-Item -LiteralPath app/build -Recurse -Force
	```

	This is safe only because `app/build` contains generated outputs. Never remove `app`, source directories, or paths outside the repository.

5. Retry the requested task once with file watching and parallel workers disabled. For this project's unit tests:

	```powershell
	.\gradlew.bat :app:testDebugUnitTest --no-watch-fs --no-parallel --max-workers=1
	```

	Substitute the user's original task when it is not the unit-test task. This exact sequence recovered the recurring resource-output deletion failure in this repository.
6. If a clean `app/build` still fails, stop cleanup attempts. Capture `--stacktrace`, inspect the exact paths and active processes, and use an available Windows handle-inspection tool to identify the owner. Do not repeatedly delete outputs, alter Gradle settings, or disable antivirus without evidence.

Windows commonly marks directories with the `ReadOnly` attribute; that alone is not evidence of a lock or a permissions problem. Check file attributes and ACLs only when they differ from normal workspace paths. If a Restart Manager query is used, register actual files; empty directory paths may return no useful owner information.

Do not add cleanup to every build or permanently change Gradle configuration just to mask a stale-output incident. Keep builds serialized and use this recovery only for actual output-deletion failures.