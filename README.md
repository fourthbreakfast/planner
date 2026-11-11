# Planner

Tasks, agenda, notes and habits in one workspace for Android, Windows and Linux. Planner works offline, keeps its database on your device and offers optional sync between paired devices. No account is required.

[Downloads](https://github.com/fourthbreakfast/planner/releases) · [Changelog](CHANGELOG.md) · [Build from source](android/README.md)

![Planner desktop workspace](docs/readme/windows.png)

## Install

Download the files attached to a [published release](https://github.com/fourthbreakfast/planner/releases). The release pipeline produces the packages below; check the selected release for its available assets.

| Platform | Requirements | Package |
| --- | --- | --- |
| Android | Android 8 or newer; ARM64 for device sync | [APK](https://github.com/fourthbreakfast/planner/releases/latest) |
| Windows | Windows 10 or 11, 64-bit | [Setup executable](https://github.com/fourthbreakfast/planner/releases/latest) |
| Linux | x86-64 Debian/Ubuntu desktop, X11 or XWayland | [Debian package or portable archive](https://github.com/fourthbreakfast/planner/releases/latest) |

On Android, open the APK and allow installation from the app you used to download it. On Windows, run the per-user installer; administrator access is not required. The Windows installer is unsigned, so Windows may display a security prompt. Allow Planner through the firewall on the networks you use for device sync.

On Linux, install the `.deb` with your software installer or `sudo apt install ./planner-<version>-linux-x64.deb`. For the portable archive, extract it and run `./planner/bin/planner`; install `libnotify-bin` and `xdg-utils` for notifications and opening update installers. Desktop packages include Java and Syncthing. Native Wayland has not been verified. If the desktop has no supported system tray, closing the window exits Planner.

Updates preserve your database. Planner can check GitHub once a day, download an update and verify its checksum before offering installation. Installing remains an explicit action; Android and Linux may also ask for platform confirmation. Automatic checks can be disabled in settings.

## What you can do

| Area | Capabilities |
| --- | --- |
| [Today](docs/readme/today.png) | A compact task document with an **Up next** agenda summary. Complete tasks, select a **Do now** shortlist, add subtasks or schedule a task without duplicating it. |
| [Tasks](docs/readme/tasks.png) | Nested tasks, priorities, drag to reorder or nest, completion and a recoverable bin. Save a **Do now** shortlist and show it on its own. Schedule an existing task on the agenda while keeping one completion state. |
| [Habits](docs/readme/habits.png) | Weekday schedules, daily completion, history, current and best streaks, and an archive. Unscheduled days do not break a streak. |
| [Agenda](docs/readme/agenda.png) | List and month views, Monday-first weeks, optional times, multi-day and repeating events, and reminders. Import a local `.ics` file or subscribe to a read-only HTTPS/webcal feed. |
| [Notes](docs/readme/notes.png) | Titles, pinned notes, search, bulleted and numbered lists, checkboxes, indentation, bold, italic, underline, undo and redo. Formatting can apply across selected lines. |

Android also includes a home-screen widget, launcher shortcuts, reminder notifications and text sharing into notes. All apps support file export/import; Android can write a daily backup to a folder you choose. Desktop supports keyboard navigation, context menus, saved window size and tray operation where available. The in-app guide lists the controls for your platform.

## Sync and privacy

Pair devices in settings using a QR code or a full pairing code. Short codes work on the same local network. Planner runs its own private [Syncthing](https://syncthing.net) process; a separate Syncthing installation is not required.

Sync runs continuously while Planner is open. Android schedules background runs about every 15 minutes, subject to battery and network restrictions; background delivery is not immediate. **Sync now** starts a manual run. The status view shows each paired device's connection and completion.

Public discovery and relay services are enabled by default for new setups to help devices connect across networks. Traffic between devices is encrypted; those services can observe connection metadata. You can turn them off for local-network-only sync. Firewalls, router restrictions and mobile power management can still prevent or delay a connection.

The local database and exported backup files are not encrypted by Planner. Keep backups somewhere you trust. Permanent deletion records prevent old copies from reappearing through sync. If a note is changed independently on two devices, the losing version is kept in the bin. Sync does not replace a backup.

Calendar subscriptions keep the last valid feed when a refresh fails. Private subscription URLs and cached responses stay out of Planner sync and exported backups; subscribed events do sync. Feeds are read-only and have explicit recurrence and size limits. Restoring their events from a backup creates editable snapshots; subscriptions are not restored. See [calendar subscriptions](docs/calendar-subscriptions.md) for supported formats, refresh timing and time-zone behavior.

Update paired devices together when adopting habits, task shortlists or subscriptions; older versions do not understand all of their fields.

## Verify an Android download

Release APKs use the certificate recorded in [android/signing-certificate.sha256](android/signing-certificate.sha256). Its SHA-256 digest is:

```text
619dc72c00a929a10ec1da3e81ecec0062437f8a28e4263610655e4190d670db
```

With Android build tools installed, check an APK with:

```text
apksigner verify --print-certs planner-<version>.apk
```

## Development

Planner is written in Kotlin with Compose Multiplatform and Room. Android Studio is optional: command-line builds need a JDK and Go, plus the Android SDK for the phone app. Syncthing is built from the pinned source submodule.

The [development guide](android/README.md) covers setup, isolated development installs, platform packaging, tests, migrations and release signing. Source build and release automation live in [GitHub Actions](.github/workflows).

## Credits and license

Device sync uses [Syncthing](third_party/syncthing) under the [Mozilla Public License 2.0](docs/licenses/Syncthing-MPL-2.0.txt). Calendar recurrence uses [dmfs lib-recur](https://github.com/dmfs/lib-recur) under the [Apache License 2.0](docs/licenses/lib-recur-Apache-2.0.txt). QR codes use [ZXing](https://github.com/zxing/zxing) and [ZXing Android Embedded](https://github.com/journeyapps/zxing-android-embedded), also under [Apache 2.0](docs/licenses/Apache-2.0.txt). Bundled font licenses cover [IBM Plex](docs/licenses/IBMPlex-OFL.txt) and [Fira Code](docs/licenses/FiraCode-OFL.txt).

Planner is licensed under the [GNU Affero General Public License v3.0](LICENSE).
