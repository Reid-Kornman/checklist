---
name: windows-gradle-build
description: "Use when building this Android app with Gradle on Windows, especially for 'Unable to delete directory', 'Failed to delete some children', or generated output folders that are not empty. Prevent concurrent builds and recover with narrowly scoped cleanup."
---

# Windows Gradle Builds

Use the Gradle wrapper from the repository root. This project shares generated outputs under `app/build`, so concurrent Gradle invocations can contend for the same directories.

## Normal Build

1. Run one Gradle command at a time and wait for it to finish before starting another task, including tasks launched by Android Studio.
2. For ordinary incremental builds, invoke the requested task using the repository's Gradle wrapper. Do not run `clean`, stop daemons, or delete build outputs preemptively; these actions slow normal builds and do not prevent file locks.

## Directory Deletion Failure

When Gradle reports `Unable to delete directory` or `Failed to delete some children`:

1. Capture the exact failed path and wait for the failed Gradle process to finish. Do not immediately launch another build against the same outputs.
2. Check that no other Gradle build is active. Only then stop the Gradle daemons with the wrapper's `--stop` option from the repository root; stopping daemons can interrupt other builds.
3. Retry the original Gradle task once.
4. If the same failure recurs, remove only the exact failing generated directory when it is under this repository's `app/build` tree, using PowerShell `Remove-Item -LiteralPath '<exact path from the error>' -Recurse -Force`. Never delete a guessed path, source directory, or anything outside generated build outputs.
5. Retry the original task once more. If a different output directory fails, or the targeted cleanup cannot remove it, stop retrying and investigate another process holding files open. Do not repeat broad cleanup loops.
6. Removing the entire `app/build` directory is a last resort after targeted recovery fails and no build is active. Explain that this discards generated outputs before doing so.

After recovery, run the same task once more as a normal incremental build if the user reports that the issue recurs. If it succeeds, do not add cleanup to every build; keep builds serialized and use this recovery only for actual lock failures.