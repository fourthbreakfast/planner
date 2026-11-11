# App improvement acceptance record

Recorded 9 November 2026. Desktop interaction fixes led this pass, followed by sync reliability and the agreed features. This file distinguishes implemented behavior from checks that still need a real device or operating system.

## Agreed scope

- A saved “do now” shortlist and a view showing only that shortlist and its subtasks. A separate single-task focus view was superseded by this decision.
- Schedule existing tasks on the agenda with one completion state.
- A separate habits section with weekday schedules, history and streaks.
- Read-only HTTPS/webcal calendar subscriptions with manual and periodic refresh, alongside editable local `.ics` imports. ICSx5 is another client for these feed URLs, not a separate file format.
- Fira Code throughout ordinary interface text, with programming ligatures enabled. Stored and copied text stays as typed.
- Continuous sync while the phone app is open; scheduled background work. Android WorkManager permits a minimum periodic interval of 15 minutes and may delay runs, so an exact ten-minute interval cannot be promised.

## Implemented behavior

| Request | Result |
| --- | --- |
| Desktop add-task target | The parent has a marker, its name appears beside the input, and Clear returns to root. |
| Enter saves agenda | Enter in name/location saves; notes retain new lines and Ctrl+Enter saves. Empty names and recurring-event confirmation use the same validation path. |
| No-time events | The picker says “no time”; the agenda omits the repeated “all day” label. |
| Pin | A restrained outline pin with a filled pinned state, shared by both apps. |
| Multiline bullets | Toolbar captures the selection before mouse focus changes, applies formatting to all selected lines, and preserves the selection. |
| Task dragging | Middle placement nests; edge placement plus horizontal position chooses sibling/ancestor depth. The line shows the actual priority-ranked destination. Hysteresis, a drag ghost and edge scrolling clarify movement. |
| Typing shortcuts | Dashboard commands pause while an editor, rename field, panel or drag owns input. |
| Monday first | Both month grid and date picker start Monday, including with a US locale. |
| Ligatures | Bundled regular/medium/bold Fira Code with `liga` and `calt`, plus license. |
| Device rename | Clicking into the name selects its full text for replacement. |
| Quick sync | Sync now directly in settings; desktop title also has refresh and status controls. |
| Sync status | Green requires all paired peers connected and caught up; disconnected, working and error states remain distinct. Per-device connection details are visible. |
| Public sync servers | Discovery/relays enabled for new preferences, with disclosure. Existing explicit choices are preserved. |
| Network recovery | Pending publication retries, local addresses refresh, full pairing codes preserve address metadata, and an explicitly attempted short-code pair retries once when its unique nearby device appears. |
| Phone background sync | Fifteen-minute WorkManager scheduling, continuous foreground operation, and a one-minute background linger. Retained pairing screens stop in the background and restart on return; a worker can finish its bounded run before shutdown. Failed scheduled runs can retry. |
| Task shortlist | Stars are independent of priority; both apps can show only chosen open tasks and their descendants. New tasks added in that view join the shortlist. |
| Task agenda scheduling | One deterministic schedule per task; agenda completion updates the original task, and reopening it restores its saved schedule. |
| Habits | A fourth phone tab and desktop panel, weekday schedules, independent per-day synced checks, current/best streaks, recent history and archive/restore. |
| Calendar feeds | Local private URLs, bounded HTTPS transport, recurrence expansion, atomic reconciliation, offline last-good data, source removal across sync and read-only UI/rules. See [calendar details](calendar-subscriptions.md). |
| Tray | Existing tray behavior retained; unsupported tray environments keep an accessible window and close exits. |
| Windows taskbar | Stable AppUserModelID on process and installer shortcut; secondary launches request foreground activation of the first instance. Older pinned shortcuts may require re-pinning. |
| Window size | Restored size saved independently of maximized state, clamped to available screen bounds and minimum dimensions. |
| MSI removal | New MSI generation/publication removed; setup still migrates an existing MSI installation. |
| WinGet | Native PowerShell manifest generation restored and validated against the published 1.2.1 setup. Release workflow produces manifests for review; catalog submission remains a separate publishing action. |
| Linux | Host-specific integrations, XDG data path, bundled Linux Syncthing, Debian package/portable archive tasks and Linux CI jobs. Actual packaging/runtime needs a Linux host. |
| Accessibility/touch | Explicit task actions, named/selected controls, minimum phone touch targets, keyboard access, reduced-motion support, and light/dark plus narrow/large-text render review. Further physical-device accessibility checks remain below. |

## Verification

Validation completed across 9–10 November 2026. Logs and rendered images are outside the repository under `../.tools`.

| Suite | Passed | Skipped | Failed |
| --- | ---: | ---: | ---: |
| Core rules | 173 | 0 | 0 |
| Sync, including real Syncthing processes | 23 | 2 | 0 |
| Shared UI/data | 143 | 0 | 0 |
| Desktop | 23 | 2 | 0 |
| Total JVM checks | 362 | 4 | 0 |

The default skips are two opt-in phone fixtures and the optional icon-export/trial-data tools. The new phone host fixture also passed in its explicitly configured physical-phone run, recorded separately below.

- Global `ktlintCheck` passes. Android lint reports no errors (23 warnings and one informational hint); the separate `-Pdemo` 1.3.0/code31 debug APK builds locally without Android Studio. The app unit-test task has no sources.
- The optimized Android release build also passes, including shrinking; its verification APK is unsigned.
- Actual mouse/key events in Compose scenes verify agenda submission, multiline note formatting, first-click device-name selection, nesting/outdenting and the shortlist drag guard. The tests check final persisted task placement as well as visible destinations.
- Light/dark screenshots were inspected, including 320px/large text habits and navigation, every weekday in the habit editor, parent-target feedback, the pin, no-time labels and dark drag contrast. README images now show the current implementation.
- Calendar checks cover Room/wire roundtrips, read-only controls, UTC/device-zone/DST projection, detached backup restore, removal during an in-flight fetch, and fair resumption after an interrupted refresh pass.
- Android emulator migration checks pass 7/7 on the disposable `emulator-5554`, covering every historical schema upgrade and preservation/defaults for the new fields. Only the separate demo app was installed/uninstalled; the task-created emulator was then stopped.
- The final Windows image/setup builds successfully. Its isolated packaged startup and second-instance IPC smoke checks pass: a database is created, the running JVM's PID sidecar remains readable while the lock is held, the foreground target matches that process, and the second launch exits after its window request is consumed. Test processes were stopped. Actual Windows shell focus/grouping remains the manual check below.

Local deliverables are saved outside Gradle's cleanable build folders in `../.tools/artifacts/1.3.0`: the Windows setup, separate dev and QA APKs, QA test APK, `SHA256SUMS.txt`, `BUILD-NOTES.md` and `PHONE-VERIFICATION.md`.

## Physical phone follow-up

A Motorola Moto g84 running Android 17 (SDK 37), ARM64, is connected for follow-up validation. A separate `planner qa` package avoids a signing-key conflict with the existing dev app. Its native TCP/QUIC and discovery ports are isolated from the regular app. The old dev app was backed up before the attempted upgrade; neither existing package was uninstalled.

- The actual Android 17 local-network permission prompt was exercised and accepted in the QA app. The bundled native engine starts, and the sync-anywhere default/disclosure is visible.
- A headless native LAN exchange passed: a PC task arrived in Room, phone completion returned to the PC, a starred phone task arrived at the PC, and sync reported current. No Activity was opened for that exchange.
- The fresh-task direct `runOnce` check initially failed on the phone. Restarted sync could use stale connection state and stop before incoming files reached Room. The corrected settlement path requires a fresh native connection, drains incoming files into the database, publishes the committed snapshot, and checks completion again. The native regression and repeated phone test now pass; background shutdown also passes with pairing retained.
- The production periodic request was verified and recreated through the production scheduling path for a controlled first-run check. Actual scheduled execution remained held by Android's Doze/power constraints. Battery Saver was enabled; temporarily exempting only QA and requesting its job did not establish an autonomous wake-up. Temporary QA exemptions were removed and the original screen/power state restored. No natural 15-minute wake-up or exact cadence is claimed.
- Cleanup passed: only the named PC fixture peer was removed, its periodic QA work was cancelled, existing QA tasks were preserved, and native processes were stopped. The old dev app's operational stopped state was cleared after its backup, without opening its UI.
- Interactive Home/return validation is pending an idle phone. No foreground lifecycle callback is simulated in that interactive test. The reproducible protocol is in [phone-sync-qa.md](phone-sync-qa.md).

## Device and release checks

These are distinct from passing source tests:

- Physical ARM64 Android phone: background wake/retry after idle, battery restrictions, full-code off-LAN pairing, later joining common Wi-Fi, incoming approval and multi-device status. The x86-64 emulator cannot run the bundled ARM64 Syncthing binary.
- Windows shell: pinned-button grouping/foreground behavior, tray menu and installer upgrade on a real desktop. Native UI automation currently cannot connect to its pipe, so Compose scene tests do not prove Windows shell behavior.
- Linux: actual Debian package installation, portable startup, X11/XWayland window identity, tray/no-tray behavior, notifications and update installer handoff. This workspace has no Linux runtime.
- TalkBack and touch on a phone, plus platform screen-reader and high-contrast checks, are not claimed complete by rendered screenshots.
- Local builds are versioned 1.3.0 (Android code 31), following published 1.2.1. Nothing has been pushed, published or submitted to WinGet.

## Local tool setup

The portable JDK, Go toolchain, Android SDK and emulator are in `../.tools`; Android Studio is optional. Use `-Pdemo` for Android validation and isolated `PLANNER_DATA` / `PLANNER_PREFS` for desktop trials. The demo app uses separate data from the regular planner app. One verifier runs Gradle at a time in this shared checkout to avoid incremental-cache conflicts.
