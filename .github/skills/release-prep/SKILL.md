---
name: release-prep
description: "Use when preparing a release for this Android checklist app: review changes since the current version was set, recommend and ask for a SemVer bump, update Android versions, rebuild and recapture README screenshots, update relevant documentation, and draft GitHub release notes."
---

# Release Preparation

Prepare a release candidate for this repository only. Do not commit, tag, publish, upload, or create a release. Do not write release notes to a file; print the final draft in the response for copy and paste into GitHub.

## Project Facts

- Android app module: `app`.
- Release version source: `app/build.gradle`, `android.defaultConfig.versionName` and `versionCode`.
- Screenshots live in `docs/screenshots/` and are embedded in `README.md`.
- Screenshot names: `checklist-light-mode.png`, `checklist-dark-mode.png`, `add-task-inline.png`, and `add-routine-dialog.png`.
- App package and launcher: `com.example.checklist/.MainActivity`.
- Windows build and install command: `.\gradlew.bat :app:installDebug`.

## Workflow

1. Read the current `versionName`, `versionCode`, README, and current screenshot set. Check `git status` first. Preserve unrelated user changes; do not reset, clean, stash, or overwrite unrelated work.
2. Establish the change-count baseline. Find the commit that last introduced the current `versionName` in `app/build.gradle`, for example with `git log -S 'versionName = "1.5.0"' --format='%H %s' -- app/build.gradle`. Count non-merge commits after that commit through `HEAD` with `git rev-list --no-merges --count <baseline>..HEAD`, then inspect those commit subjects and relevant diffs to identify distinct release-worthy changes. Report the commit count and the distinct change count separately; do not treat every changed file as a separate feature. Account for relevant uncommitted changes separately and never include unrelated work in the release summary. If the version-setting commit cannot be found, use the latest version tag as a clearly labeled fallback; if neither baseline exists, state that history is unavailable and base the recommendation on the visible working-tree changes.
3. Recommend a SemVer segment using this transparent heuristic, based on distinct release-worthy changes since the baseline: 0-3 changes suggests PATCH; 4-10 suggests MINOR; 11 or more is a MAJOR candidate. A MAJOR recommendation requires a confirmed backwards-incompatible change; without one, recommend MINOR even when the count is high. Consider the actual changes as well as the count, explain the recommendation briefly, then ask the user to choose exactly one: MAJOR, MINOR, or PATCH. Do not edit version values until the user answers. If the user already specified a segment in the current request, use it without asking again.
4. Increment the selected part of `versionName` by one and reset lower parts to zero: PATCH `1.5.0` -> `1.5.1`, MINOR -> `1.6.0`, MAJOR -> `2.0.0`. Always increment Android `versionCode` by exactly one, regardless of the selected segment. Keep the existing Gradle syntax and touch no other version source unless discovery shows another authoritative declaration.
5. Update the README only where the release changes make it inaccurate or incomplete. Keep feature descriptions factual, refresh screenshot embeds after the new captures, and preserve unrelated user edits. Do not add a version history or release notes to the README unless the project already maintains one.
6. Build and install the changed app on a running emulator with `.\gradlew.bat :app:installDebug`. Check `adb devices -l` first. If no emulator is connected, ask the user to start one; do not substitute old screenshots. Do not uninstall the app or clear its data. If Gradle fails with a Windows access-denied error in generated incremental output, stop Gradle daemons and retry; only clear `app/build` as a last resort, since it removes generated build output.
7. Launch `com.example.checklist/.MainActivity` and capture fresh screenshots from the newly installed build using the emulator and `adb`. Use UI hierarchy bounds or visual inspection to locate controls rather than assuming coordinates across emulator sizes. Save each image to a temporary device path with `adb shell screencap -p`, then pull it into `docs/screenshots/` under the established descriptive filename. Capture these states:
   - `checklist-light-mode.png`: checklist in light mode with completed and incomplete tasks visible.
   - `checklist-dark-mode.png`: same checklist in dark mode with completed and incomplete tasks visible.
   - `add-task-inline.png`: empty inline task entry focused with the keyboard open.
   - `add-routine-dialog.png`: Add Routine dialog open over the clean checklist.
   Preserve existing emulator data. Before changing task completion or theme preference, record the initial state; cancel unsaved task/routine forms and restore changed completion/theme state after capturing. Do not add sample tasks or routines to persisted user data. Inspect every captured image and recapture any blank, transient, obscured, or incorrectly staged image.
8. Verify all four screenshot files exist and that their README links resolve. Run the relevant focused build/test checks available in the repository and `git diff --check`. Review the final diff and confirm only intended release-prep files changed; never revert pre-existing work.
9. Prepare a concise GitHub release-note draft from verified user-visible changes, including the resulting version. Present it in the final response as copyable Markdown text. Do not write it to a file or pretend a release has been published. Clearly mention any failed or unavailable build, test, emulator, or screenshot checks.

## Version Recommendation Prompt

Use one concise question after presenting the current version, the change count and baseline, and the recommendation. Offer the fixed choices `PATCH`, `MINOR`, and `MAJOR`; the user may also provide a different answer in text. A recommendation is guidance only: use the user's selected segment.