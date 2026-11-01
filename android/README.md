planner, android app
kotlin, jetpack compose, minsdk 26, targetsdk 37, package dev.fourthbreakfast.planner
built on windows with android studio, nothing here depends on nixos
tested 2026-11-01: builds from the command line and runs on a moto g84 5g (android 17, api 37)

STRUCTURE
core: everything the app knows, as plain kotlin with no android, tested on a computer with gradlew :core:test. tasks, events and notes each have a rules class (Tasks, Events, Notes) that is the only way to change them, a store interface they are kept behind, and tests with an in-memory store. also here: the note text format and its looks (NoteBody), the backup file (Backup, Backups), reminder times (Reminder), what the widget says (WidgetLines) and search (Search)
app: the android side. screens in tasks, agenda, notes, search and settings, one folder each; data holds the room tables behind the core stores; reminders sets the alarms and shows the notifications; widget is the home screen widget; backup writes the daily file; ui/theme holds the colours, the type and the spacing scale (Space.kt), which the design system in ../docs/design-system mirrors; Requests carries a shortcut, a share or a notification to the right screen

DATABASE
room, schema files in app/schemas, a new version for every change with an auto migration so nothing a person has saved is ever lost. a change to the database needs an upgrade test on a phone with data in it before a release

BUILD from the command line, no android studio window needed
set JAVA_HOME to the jdk that ships with android studio (C:\Program Files\Android\Android Studio\jbr)
set ANDROID_HOME to the sdk folder (%LOCALAPPDATA%\Android\Sdk)
gradlew assembleDebug
gradlew installDebug, with the phone connected and usb debugging on

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
core-ktx 1.19.1, lifecycle (runtime-ktx, runtime-compose, viewmodel-compose) 2.11.0, activity-compose 1.13.0
room 2.8.5, ksp 2.3.13, kotlinx-coroutines 1.11.0
fonts bundled in app/src/main/res/font: ibm plex sans (regular, bold, italic) and ibm plex mono (regular, medium), licence in ../docs/licenses
compilesdk 37, targetsdk 37, minsdk 26

MOVING FORWARD, only on purpose, one commit per round
1. android studio
2. gradle and agp together
3. compilesdk
4. targetsdk, only after trying the app on the phone
after each step build from the command line and run it on the phone
