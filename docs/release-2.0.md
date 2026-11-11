# Planner 2.0

Release guide for Planner 2.0.0.

Planner 2.0 brings tasks, agenda, notes and habits into a more compact, text-first workspace. It also adds task shortlists, scheduled tasks, calendar subscriptions and Linux packaging.

## Workspace and interaction

- A restrained shared visual direction, with clearer hierarchy, quieter controls and consistent navigation between Today, Tasks, Habits, Agenda and Notes.
- A saved **Do now** shortlist, with a view that shows the selected tasks and their subtasks.
- Clearer task nesting and drag destinations, improved keyboard and touch controls, and a simpler note pin.
- Multiline note formatting that preserves the selected lines, including mixed list types.
- Enter saves agenda forms; notes keep normal newlines, with Ctrl+Enter available to submit.
- Monday-first calendar weeks and events without an obligatory “all day” label.

## Agenda and habits

- Schedule an existing task on the agenda. Completing it there completes the original task.
- Track habits separately, with scheduled weekdays, daily completion, history, current and best streaks, and an archive.
- Subscribe to read-only HTTPS/webcal feeds alongside local `.ics` import. Refresh manually or periodically, with the last valid feed retained when a refresh fails.
- Expand common recurrence rules, cancellations and moved occurrences. Absolute timed subscriptions retain their instants across device time zones and daylight-saving changes; floating and all-day events keep their written times/dates.
- Restore subscribed events from a backup as independent, editable snapshots. Private feed URLs are not exported or restored.

## Sync and desktop integration

- **Sync now** is directly accessible from settings. Device status distinguishes connection from completion.
- Public discovery and relays are enabled for new sync setups, with a local-network-only option. Full pairing codes work across networks when connectivity permits; short codes are local-network-only.
- Android sync remains continuous while open and scheduled about every 15 minutes in the background, subject to Android's battery and network restrictions.
- Better Windows taskbar identity, existing-window focus and window-size restoration.
- Linux x86-64 `.deb` and portable packaging for Debian/Ubuntu desktops with X11 or XWayland. New Windows releases use the setup executable; new MSI packages are no longer generated.
- WinGet manifest generation is available for review. Catalog submission and acceptance are separate from generating a manifest.

## Installing and upgrading

Use the assets attached to the [published release](https://github.com/fourthbreakfast/planner/releases). Android requires version 8 or newer, with an ARM64 phone for native device sync. Windows targets 64-bit Windows 10/11. Desktop packages include their runtime and Syncthing.

Update paired devices together: older versions do not understand every habit, shortlist or subscription field. Existing databases are migrated when the application opens. Keep a backup before updating devices you rely on; sync is not a substitute for a backup.

Android updates retain the existing signing certificate. Windows setup supports migration from earlier MSI installations. Linux packaging must run on Linux, and native Wayland has not been verified. On a desktop without a supported tray, closing the window exits Planner.

## Limits and details

Calendar subscriptions are read-only HTTPS feeds, including webcal links upgraded to HTTPS. CalDAV, OAuth and username/password authentication are not included; use a provider's private subscription URL. Recurring feeds cover a rolling window and reject unsupported rules instead of replacing the agenda with a partial result. See the [calendar guide](calendar-subscriptions.md) for exact support and limits.

Background jobs can be delayed by the operating system. Public sync discovery/relays can observe connection metadata, while device traffic is encrypted. Planner does not encrypt the database or exported backups on disk.

The [README](../README.md) covers installation and capabilities; the [development guide](../android/README.md) covers source builds, verification and release tooling. The [verification record](releases/2.0-verification.md) records platform checks and their limits.
