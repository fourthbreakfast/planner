planner, android and windows apps
kotlin, jetpack compose, minsdk 26, targetsdk 37, package dev.fourthbreakfast.planner
built on windows with android studio, nothing here depends on nixos
tested 2026-11-01: builds from the command line and runs on a moto g84 5g (android 17, api 37)

STRUCTURE
core: everything the app knows, as plain kotlin with no android, tested on a computer with gradlew :core:test. tasks, events and notes each have a rules class (Tasks, Events, Notes) that is the only way to change them, kept behind one Store interface (TaskStore, EventStore and NoteStore are its names for each). also here: sync's merge (Sync) and the clock every change is stamped with (LogicalClock), the backup file (Backup, Backups) and calendar import (IcsImport), repeating events (Occurrences), the note text format and its looks (NoteBody), reminder times (Reminder), what the widget says (WidgetLines) and search (Search). its test fixtures hold the in-memory stores the tests of core, sync and shared all use
sync: plain kotlin with no android, shared with the windows app. Syncthing starts and stops the app's own syncthing and talks to its rest api, SyncthingConfig sets its options (own ports, nothing outside the local network unless sync anywhere), DeviceFiles writes this device's file into the shared folder and merges the others' through core's Sync, Lan finds a device by its short code when broadcasts do not get through. tests run two real syncthings on the computer when PLANNER_SYNCTHING names a syncthing binary
shared: kotlin multiplatform for the phone and the computer. PlannerData builds the stores, the clock, the rules, the backup and sync over the room database (PlannerDatabase, data), once for either device. the screens the phone uses and the parts the desktop reuses live in tasks, agenda, notes, search, settings and ui; sync/SyncRunner runs syncthing for both devices; updates looks for a newer release; ui/theme holds the colours, the type and the spacing scale (Space.kt), which the design system in ../docs/design-system mirrors; the words and fonts are in composeResources; what each device does its own way is in ui/Platform.kt, with an android and a desktop side
app: the android side. PlannerApp makes PlannerData and everything that runs in the background, MainActivity shows PlannerScreen and carries a shortcut, a notification or a share to the right screen (Requests); settings holds the phone's settings and its sync screen with the camera; sync/SyncController runs sync while the app is in use and every 15 minutes in the background; reminders sets the alarms and shows the notifications; widget is the home screen widget; backup writes the daily file and saves and opens backups through the phone's file picker. res/values/strings.xml only holds the words android shows outside the screens
desktop: the windows app. Main starts it with a tray and a window of its own drawing (TitleBar, NativeFrame), Dashboard holds the three columns (TaskColumn, AgendaColumn, NoteColumn) and the panel that slides in, DesktopSync and DesktopReminders run sync and reminders. packageSetup wraps the app, its temurin 21 runtime and syncthing.exe in an inno setup installer; packageMsi remains for older clients
buildSrc: SyncthingBinary, which builds the pinned syncthing for the phone and for windows

DATABASE
room, schema files in shared/schemas, a new version for every change with an auto migration so nothing a person has saved is ever lost. add the new version to the upgrade tests in app/src/androidTest

CHECKS
.\gradlew.bat ktlintFormat fixes the style, .\gradlew.bat ktlintCheck reports it
.\gradlew.bat :core:test :sync:test :shared:desktopTest :desktop:test testDebugUnitTest runs the tests that need no phone: the rules, sync, the database on the computer, the list layout and the note editor. real syncthing tests need PLANNER_SYNCTHING set to a host binary; on windows build it with .\gradlew.bat :desktop:syncthingWindows and use desktop\build\syncthing\resources\windows\syncthing.exe
.\gradlew.bat connectedDebugAndroidTest -Pdemo runs the database upgrade tests on a connected test device. it installs and then uninstalls the dev copy, including its data, and refuses to run without -Pdemo. use an emulator or a device whose dev-copy data is disposable
github runs the style check, the tests, android lint and debug/release builds on pushes to main and pull requests (../.github/workflows/build.yml), but not the phone tests, so run those before a release that changes the database

BUILD from the command line, no android studio window needed
clone with --recurse-submodules: syncthing is in ../third_party/syncthing, pinned to one release
install go; the build (buildSrc/SyncthingBinary.kt) uses the exact go version syncthing's go.mod asks for, which go fetches by itself, and builds it without c, so no ndk is needed
set JAVA_HOME to the jdk that ships with android studio (C:\Program Files\Android\Android Studio\jbr)
set ANDROID_HOME to the sdk folder (%LOCALAPPDATA%\Android\Sdk)
.\gradlew.bat assembleDebug -Pdemo
.\gradlew.bat installDebug -Pdemo, with a test phone connected and debugging on. the dev copy has its own data, and reinstalling it preserves that data. use -Pdemo in android studio run configurations too

commands above run from this android folder in powershell. on linux or macos use ./gradlew instead of .\gradlew.bat. the bundled android syncthing executable is ARM64, so native sync needs an ARM64 phone; an x86_64 emulator can still run UI and database migration checks

RELEASE
plannerVersion and plannerVersionCode in gradle.properties name both apps; increment the code for every android release. add the version's notes to ../CHANGELOG.md before tagging
pushing the matching v tag runs ../.github/workflows/release.yml: signed APK, Windows setup executable and legacy MSI. local Windows publishing uses ../tools/release.ps1 with the same version and artifact set, native GitHub CLI (gh) signed in, and inno setup 6. the helper requires a clean working tree and an existing tag on fourthbreakfast/planner pointing to the checkout's exact HEAD; it never creates or pushes a tag. the original signing keystore must exist at %USERPROFILE%\.planner\release.jks with alias planner; the helper refuses a missing key or a certificate that differs from signing-certificate.sha256. CI also verifies the built APK against that public pin before publishing. signing credentials stay outside the repo

TOOLS, pinned here and moved forward only on purpose
the scripts in ../tools do the steps below for you on a fresh windows install (install-android-studio.ps1, then install-android-sdk.ps1), the numbers are written in both places so change both together
android studio 2026.2.2.8
  winget install --id Google.AndroidStudio --exact --version 2026.2.2.8
android sdk command line tools 16111833 (revision 23.0)
  https://dl.google.com/android/repository/commandlinetools-win-16111833_latest.zip
  sha256 e5885e2e59038c0778a85fc19f01de7ce7c567c105553f7617b936b1e0e87b2b
  unzip into the sdk folder as cmdline-tools\latest
sdk packages, installed with the android cli from those tools, always with --no-metrics
  android --no-metrics sdk install platform-tools platforms/android-37.0@2.0.0 build-tools/36.0.0@36.0.0
  platform-tools (adb) floats on purpose, it does not affect what gets built
  do not use sdkmanager: it is deprecated and wraps the same cli without turning the metrics off, and the android cli sends usage data to google unless --no-metrics is given
  licenses are accepted by hand, once, the android studio first run does it
  that first run also installs an emulator and a system image, nothing here depends on them

PROJECT PINS, in the project files, this is the tested set
agp 9.5.0
gradle 9.6.0 (wrapper, with checksum)
kotlin 2.4.21
compose bom 2026.09.00
core-ktx 1.19.1, lifecycle (viewmodel-compose, and the multiplatform runtime-compose in shared) 2.11.0, activity-compose 1.13.0
room 2.8.5, ksp 2.3.13, kotlinx-coroutines 1.11.0
ktlint gradle plugin 14.2.0
syncthing v2.1.7 (submodule), built with go 1.26.2, kotlinx-serialization-json 1.8.1, work-runtime-ktx 2.12.0, zxing core 3.5.4, zxing-android-embedded 4.3.0
fonts bundled in shared/src/commonMain/composeResources/font: ibm plex sans (regular, bold, italic) and ibm plex mono (regular, medium), licence in ../docs/licenses
compilesdk 37, targetsdk 37, minsdk 26

MOVING FORWARD, only on purpose, one commit per round
1. android studio
2. gradle and agp together
3. compilesdk
4. targetsdk, only after trying the app on the phone
after each step build from the command line and run it on the phone
