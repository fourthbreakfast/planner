# building planner

the android and windows apps share their data and rules, with a layout of their own. this folder contains both apps and the modules between them.

kotlin · compose multiplatform · room · android 8+ · windows 10/11, 64 bit

[app overview](../README.md) · [design system](../docs/design-system) · [release notes](../CHANGELOG.md)

## start here

clone the repository with `--recurse-submodules`. [syncthing](../third_party/syncthing) is pinned as a submodule and built from source. install go; the build fetches the exact go toolchain requested by syncthing's `go.mod`. it builds without c, so no android ndk is needed.

on windows, the scripts in [tools](../tools) prepare a fresh installation: run `install-android-studio.ps1`, then `install-android-sdk.ps1`. for a manual setup, use the pinned versions below.

set `JAVA_HOME` to the jdk bundled with android studio and `ANDROID_HOME` to the sdk directory. the usual paths are:

```text
JAVA_HOME     C:\Program Files\Android\Android Studio\jbr
ANDROID_HOME  %LOCALAPPDATA%\Android\Sdk
```

run commands from this `android` folder in powershell. on linux or macos, replace `.\gradlew.bat` with `./gradlew`.

```powershell
.\gradlew.bat assembleDebug -Pdemo
.\gradlew.bat installDebug -Pdemo
```

the second command needs a connected phone with usb debugging enabled. `-Pdemo` installs a separate app called “planner dev”, with its own data. reinstalling preserves that data. use `-Pdemo` in android studio run configurations too.

the bundled android syncthing executable is ARM64. native sync needs an ARM64 phone; an x86_64 emulator can still run the interface and database migration checks.

for windows:

```powershell
.\gradlew.bat :desktop:run
.\gradlew.bat :desktop:packageSetup --no-configuration-cache
```

the pinned Compose packaging tasks need `--no-configuration-cache`; normal builds and tests can keep the cache enabled. packaging also needs [inno setup 6](https://jrsoftware.org/isinfo.php). set `PLANNER_DATA` to a separate folder and `PLANNER_PREFS` to a separate name when trying changes on disposable desktop data and settings.

## where things live

| module | responsibility |
| --- | --- |
| `core` | plain kotlin rules, independent of android. `Tasks`, `Events` and `Notes` change data through store interfaces. also holds sync merging, the logical clock, backup and calendar import, recurring events, note formatting, reminder times, widget content and search. test fixtures provide the in-memory stores used across modules. |
| `sync` | plain kotlin shared by both apps. starts the private syncthing process, configures its ports and discovery, writes device files and merges incoming changes through core. `Lan` resolves short pairing codes when broadcasts cannot get through. |
| `shared` | multiplatform data and shared interface. `PlannerData` wires room, stores, rules, backups and sync. contains tasks, agenda, notes, search, settings, the sync runner and updates. `ui/Platform.kt` provides platform hooks; `ui/theme` holds colour, type and spacing. words and fonts live in `composeResources`. |
| `app` | android entry point and platform features: navigation requests, pairing camera, background sync, reminders, widget and backup file picker. `res/values/strings.xml` holds words android displays outside the compose screens. |
| `desktop` | windows entry point, tray, custom window frame, three-column dashboard and side panel. also desktop sync, reminders and installers. `packageSetup` bundles temurin 21 and syncthing; `packageMsi` remains for older clients. |
| `buildSrc` | `SyncthingBinary`, which builds the pinned syncthing for android and windows. |

## checks

format or check kotlin style:

```powershell
.\gradlew.bat ktlintFormat
.\gradlew.bat ktlintCheck
```

run the checks that need no phone:

```powershell
.\gradlew.bat :core:test :sync:test :shared:desktopTest :desktop:test testDebugUnitTest
```

these cover rules, sync, the desktop database, list layout and note editing. real syncthing integration tests also need `PLANNER_SYNCTHING` set to a host binary. on windows, build it with:

```powershell
.\gradlew.bat :desktop:syncthingWindows
$env:PLANNER_SYNCTHING = (Resolve-Path 'desktop\build\syncthing\resources\windows\syncthing.exe').Path
```

then run the test command above.

### database changes

room schemas live in `shared/schemas`. each schema change needs a new version, an appropriate migration and coverage in the upgrade tests under `app/src/androidTest`.

```powershell
.\gradlew.bat connectedDebugAndroidTest -Pdemo
```

**this command installs and then uninstalls the dev app, including its data.** use an emulator or a device whose dev data is disposable. the task refuses to run without `-Pdemo`.

[build CI](../.github/workflows/build.yml) runs style checks, tests, android lint and debug/release builds on pushes to `main` and pull requests. it does not run device tests; run those before a release that changes the database.

## releases

`plannerVersion` and `plannerVersionCode` in [gradle.properties](gradle.properties) name both apps. increment the code for every android release and add its notes to the [changelog](../CHANGELOG.md) before tagging.

pushing the matching `v` tag runs [release CI](../.github/workflows/release.yml), producing a signed apk, windows setup executable and legacy msi.

local windows publishing uses [tools/release.ps1](../tools/release.ps1), with the same version and artifact set. it needs native github CLI (`gh`) signed in and inno setup 6. the helper requires a clean working tree and an existing tag on `fourthbreakfast/planner` pointing to the checkout's exact `HEAD`; it never creates or pushes a tag.

the original signing keystore must exist at `%USERPROFILE%\.planner\release.jks`, with alias `planner`. the helper refuses a missing key or a certificate that differs from [signing-certificate.sha256](signing-certificate.sha256). CI also verifies the built apk against this public pin before publishing. signing credentials stay outside the repository.

## pinned tools

these versions describe the tested setup. the installation scripts repeat the setup pins; update both together.

| tool | version |
| --- | --- |
| android studio | `2026.2.2.8` |
| android sdk command line tools | `16111833` · revision `23.0` |
| android platform | `android-37.0@2.0.0` |
| android build tools | `36.0.0@36.0.0` |
| platform-tools / adb | follows the current release deliberately; does not affect build output |

<details>
<summary>manual windows installation</summary>

```powershell
winget install --id Google.AndroidStudio --exact --version 2026.2.2.8
```

download [command line tools 16111833](https://dl.google.com/android/repository/commandlinetools-win-16111833_latest.zip) and unpack them as `cmdline-tools\latest` inside the sdk folder. verify the archive's SHA-256:

```text
e5885e2e59038c0778a85fc19f01de7ce7c567c105553f7617b936b1e0e87b2b
```

install sdk packages with the android CLI, always disabling metrics:

```powershell
android --no-metrics sdk install platform-tools platforms/android-37.0@2.0.0 build-tools/36.0.0@36.0.0
```

use `android`, rather than the deprecated `sdkmanager` wrapper, to pass `--no-metrics` explicitly. accept licenses by hand once during android studio's first run. that setup also installs an emulator and system image; the build does not depend on either.

</details>

### project versions

| dependency | pin |
| --- | --- |
| android gradle plugin | `9.5.0` |
| gradle wrapper | `9.6.0`, with checksum |
| kotlin | `2.4.21` |
| compose bom | `2026.09.00` |
| core-ktx | `1.19.1` |
| lifecycle | `2.11.0` |
| activity-compose | `1.13.0` |
| room | `2.8.5` |
| ksp | `2.3.13` |
| kotlinx-coroutines | `1.11.0` |
| ktlint gradle plugin | `14.2.0` |
| syncthing / go | `v2.1.7` / `1.26.2` |
| kotlinx-serialization-json | `1.8.1` |
| work-runtime-ktx | `2.12.0` |
| zxing core / android embedded | `3.5.4` / `4.3.0` |
| compile / target / minimum sdk | `37` / `37` / `26` |

ibm plex sans (regular, bold, italic) and ibm plex mono (regular, medium) are bundled in `shared/src/commonMain/composeResources/font`. licenses are kept in [docs/licenses](../docs/licenses). headings and large counts are drawn from digital segments; plex sans carries body text, with plex mono for times and small metadata.

### moving the toolchain forward

change one layer per commit: android studio first, then gradle and agp together, then compile sdk. change target sdk after trying the app on the phone. build from the command line and run on the phone after each step.

last recorded device check: 2026-11-06, moto g84 5g. the signed 1.1.0 update installed and opened successfully; six database migration checks passed on the emulator. built on windows with android studio; no nixos dependency.
