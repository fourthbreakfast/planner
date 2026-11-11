# Build and develop Planner

This directory contains the Android, Windows and Linux applications, their shared interface and their data rules.

[Product overview](../README.md) · [Design system](../docs/design-system/README.md) · [Changelog](../CHANGELOG.md) · [Build CI](../.github/workflows/build.yml)

## Setup

Clone the repository with its pinned Syncthing submodule:

```text
git clone --recurse-submodules https://github.com/fourthbreakfast/planner.git
cd planner/android
```

For an existing checkout, run `git submodule update --init --recursive` from the repository root.

Install a JDK and Go. CI uses Java 21, and desktop packaging requests a Temurin 21 toolchain through Gradle. Syncthing's `go.mod` pins its Go toolchain; Go can fetch that version when building. Android additionally requires the SDK packages listed below. No Android NDK is needed: the Syncthing build disables C dependencies.

Android Studio is optional. Set `JAVA_HOME` to your JDK and `ANDROID_HOME` to your Android SDK. A typical Windows installation uses:

```text
JAVA_HOME     C:\Program Files\Android\Android Studio\jbr
ANDROID_HOME  %LOCALAPPDATA%\Android\Sdk
```

The Windows setup helpers are [install-android-studio.ps1](../tools/install-android-studio.ps1) and [install-android-sdk.ps1](../tools/install-android-sdk.ps1). Run the commands below from this `android` directory. On Linux or macOS, replace `.\gradlew.bat` with `./gradlew`.

## Run Android

```powershell
.\gradlew.bat assembleDebug -Pdemo
.\gradlew.bat installDebug -Pdemo
```

Installation requires a connected device with USB debugging enabled. `-Pdemo` creates **Planner dev**, a separate app and database; updating it preserves its data. Use the same flag in Android Studio run configurations.

If an existing development copy has a different signing key, leave it installed and use `-Pdemo -Pqa` for **Planner QA** (`dev.fourthbreakfast.planner.demo.qa`). This requires `-Pdemo` and assigns separate native sync ports. Build the isolated app and instrumentation APK with:

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest -Pdemo -Pqa
```

Selected physical-phone tests can then be invoked directly with `adb`, without uninstalling existing apps. The [phone sync guide](../docs/phone-sync-qa.md) documents device and network scenarios.

The bundled Android Syncthing binary is ARM64. An ARM64 phone is required to exercise native sync; an x86-64 emulator can still cover the interface and database migrations.

## Run and package desktop

### Windows

```powershell
.\gradlew.bat :desktop:run
.\gradlew.bat :desktop:packageSetup --no-configuration-cache
```

Packaging requires [Inno Setup 6](https://jrsoftware.org/isinfo.php). Compose packaging tasks need `--no-configuration-cache`; ordinary builds and tests can keep the cache enabled. The per-user setup includes Java and Syncthing and retains migration support for older MSI installations. New MSI packages are not generated.

### Linux

On an x86-64 Linux host:

```bash
bash ./gradlew :desktop:run
bash ../tools/package-linux.sh
```

The packaging script requires `fakeroot`, `dpkg-deb` and Python 3. It writes a `.deb` to `desktop/build/linux` and a portable `.tar.gz` to `desktop/build/portable`; both include Java and Linux Syncthing. The Debian package identifier is `fourthbreakfast-planner`, avoiding a collision with GNOME Planner.

Packages target Debian/Ubuntu desktops using X11 or XWayland. Native Wayland has not been verified. The `.deb` declares `libnotify-bin` and `xdg-utils` dependencies for reminders and opening update installers; install them separately for a portable copy. If a desktop has no supported system tray, closing the window exits the application.

### Isolate development data

Set `PLANNER_DATA` to a separate directory and `PLANNER_PREFS` to a separate settings name before running a desktop development build. This keeps trial data apart from an existing installation.

Linux normally uses `$XDG_DATA_HOME/planner`, or `~/.local/share/planner` when unset. An existing database from an earlier source build at `~/.config/planner` continues to be used.

## Project structure

| Module | Responsibility |
| --- | --- |
| `core` | Platform-independent task, event, note and habit rules; stores; sync merging; logical clock; backups; calendar import/subscriptions; recurrence; formatting; reminders; widget content and search. Test fixtures provide in-memory stores. |
| `sync` | Private Syncthing process, discovery and pairing, device snapshot files and incoming merges. `Lan` resolves local short codes when broadcast discovery is unavailable. |
| `shared` | Compose Multiplatform screens, Room persistence, `PlannerData` service wiring, settings, updates and sync coordination. Platform hooks live in `ui/Platform.kt`; resources live in `composeResources`. |
| `app` | Android lifecycle, intents, pairing camera, background jobs, reminders, widget and file/folder access. Android-owned copy lives in `res/values/strings.xml`. |
| `desktop` | Windows/Linux shell, tray, desktop input, native integration, reminders, sync and packaging. |
| `buildSrc` | `SyncthingBinary`, which builds the pinned source for each target platform. |

Follow the [design system](../docs/design-system/README.md) for interface, visual and product-copy work. It distinguishes approved rules from proposals and historical references. Font and third-party licenses live in [docs/licenses](../docs/licenses).

## Verification

```powershell
.\gradlew.bat ktlintFormat
.\gradlew.bat ktlintCheck
.\gradlew.bat :core:test :sync:test :shared:desktopTest :desktop:test testDebugUnitTest
```

These tests cover data rules, merge behavior, the desktop database, layout and editing. Real Syncthing integration tests additionally need `PLANNER_SYNCTHING` pointing to a host binary. On Windows:

```powershell
.\gradlew.bat :desktop:syncthingWindows
$env:PLANNER_SYNCTHING = (Resolve-Path 'desktop\build\syncthing\resources\windows\syncthing.exe').Path
```

Then rerun the relevant tests with that environment variable set. Optional Compose screenshot tests use `PLANNER_RENDER` as their output directory.

### Database migrations

Room schemas are checked in under `shared/schemas`. A released schema change requires a new version, an appropriate migration and upgrade tests under `app/src/androidTest`.

```powershell
.\gradlew.bat connectedDebugAndroidTest -Pdemo
```

This task installs and uninstalls the development app, including its data. Run it on an emulator or a device with disposable development data. The task refuses to run without `-Pdemo`; use the isolated QA package and direct `adb` invocation when existing device data must be preserved.

[Build CI](../.github/workflows/build.yml) runs style checks, tests, Android lint and debug/release builds for pushes to `main` and pull requests. It does not run device tests. Exercise migrations on a device or emulator before releasing a database change.

## Releases

`plannerVersion` and `plannerVersionCode` in [gradle.properties](gradle.properties) name the platform builds. Increment the Android version code for every release and update the [changelog](../CHANGELOG.md) before tagging.

A matching `v` tag triggers [release CI](../.github/workflows/release.yml). Windows/Android and Linux jobs run checks, build the signed APK, Windows setup, Linux `.deb` and portable archive, and smoke-test the packaged desktop runtimes. A single publishing job requires both jobs to succeed, creates SHA-256 checksums, verifies uploaded draft assets, then makes the complete release public. A manual workflow run performs the same signed preflight without publication.

After publication, [tools/winget.ps1](../tools/winget.ps1) generates manifests from the published Windows installer. Validate those manifests before catalog submission; generation and Microsoft catalog acceptance are separate steps.

The legacy local Windows helper, [tools/release.ps1](../tools/release.ps1), publishes only APK and Windows setup and assumes an Android Studio installation. It is not the complete 2.0 release path. Use the workflow for production releases across all platforms.

Use the original release keystore at `%USERPROFILE%\.planner\release.jks`, alias `planner`. The helper refuses a missing key or a certificate that differs from [signing-certificate.sha256](signing-certificate.sha256); CI verifies the APK against that public pin as well. Signing credentials remain outside the repository.

## Toolchain reference

The checked-in build files and setup scripts define the pins. Update the scripts and this reference together when changing the setup.

| Setup component | Pin |
| --- | --- |
| Android Studio, optional | `2026.2.2.8` |
| Android SDK command-line tools | `16111833`, revision `23.0` |
| Android platform | `android-37.0@2.0.0` |
| Android build tools | `36.0.0@36.0.0` |
| Platform-tools / adb | Follows the current release; not a build-output dependency |
| CI JDK / packaged desktop runtime | Java 21 / Temurin 21 |

For manual Windows setup, install the pinned Android Studio version with `winget install --id Google.AndroidStudio --exact --version 2026.2.2.8`, or use a standalone JDK and SDK. Unpack [command-line tools 16111833](https://dl.google.com/android/repository/commandlinetools-win-16111833_latest.zip) as `cmdline-tools\latest` within the SDK. The archive SHA-256 is:

```text
e5885e2e59038c0778a85fc19f01de7ce7c567c105553f7617b936b1e0e87b2b
```

Install the packages with metrics disabled:

```powershell
android --no-metrics sdk install platform-tools platforms/android-37.0@2.0.0 build-tools/36.0.0@36.0.0
```

Use `android` rather than the deprecated `sdkmanager` wrapper to pass `--no-metrics` explicitly. Accept SDK licenses interactively during setup. An emulator and system image are useful for testing but are not build dependencies.

| Build dependency | Pin |
| --- | --- |
| Gradle / Android Gradle Plugin | `9.6.0` / `9.5.0` |
| Kotlin / KSP | `2.4.21` / `2.3.13` |
| Compose Multiplatform / Material 3 | `1.12.2` / `1.9.0` |
| Android Compose BOM | `2026.09.00` |
| Core KTX / Lifecycle / Activity Compose | `1.19.1` / `2.11.0` / `1.13.0` |
| Room / bundled SQLite | `2.8.5` / `2.7.1` |
| Coroutines / serialization JSON | `1.11.0` / `1.8.1` |
| WorkManager | `2.12.0` |
| ktlint Gradle plugin | `14.2.0` |
| Syncthing / Go | `v2.1.7` / `1.26.2` |
| ZXing / Android Embedded | `3.5.4` / `4.3.0` |
| lib-recur | `0.17.1` |
| Compile / target / minimum SDK | `37` / `37` / `26` |

Move the toolchain forward in small steps: update one layer, build from the command line and test the affected platforms before moving to the next. Check the final release's verification record rather than treating an older device run as evidence for a new build.
