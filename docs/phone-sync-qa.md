# Real-phone sync QA

This opt-in protocol uses only `dev.fourthbreakfast.planner.demo.qa` and a new desktop fixture folder. The regular app and an existing `planner dev` installation keep their packages, signing keys, databases, settings and native ports. Do not uninstall either app, clear its data, or run `connectedDebugAndroidTest` on a personal phone.

Build the separate QA application and its instrumentation APK:

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :sync:testClasses -Pdemo -Pqa
```

The outputs are `app/build/outputs/apk/debug/app-debug.apk` and `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`. The QA flag requires `-Pdemo`; it changes the package suffix to `.demo.qa` and the label to `planner qa`. Install these two APKs with `adb install -r`, targeting the explicitly selected QA phone. On Android 17, open the QA application's sync settings and grant its local-network permission through the system dialog.

The QA native engine uses TCP/QUIC 22103 and discovery 21103. Production and ordinary demo builds retain 22101/21101. The PC fixture listens only on loopback, and initiates the actual Syncthing data connection to the QA phone over its LAN address; it never opens the desktop app's data or settings.

Start the host fixture from `android`, with a unique run token and a new empty folder:

```powershell
$env:PLANNER_SYNCTHING = (Resolve-Path 'desktop/build/syncthing/resources/windows/syncthing.exe').Path
$env:PLANNER_PHONE_QA_RUN = '20261110-native-example'
$env:PLANNER_PHONE_QA_ROOT = 'C:\path\to\.tools\phone-sync-example'
$env:PLANNER_PHONE_QA_ADDRESS = 'tcp://PHONE_LAN_IP:22103'
.\gradlew.bat :sync:test --tests '*PhoneSyncHostTest*' --max-workers=1 --console=plain
```

Wait for `host.json` in that fixture folder. It contains the host's device ID and `controlPort`. The host accepts commands only on loopback under `/qa/<run-token>/`. Forward the phone's loopback control port to this host control port using the selected phone's ADB connection:

```text
adb -s PHONE_SERIAL reverse tcp:37481 tcp:HOST_CONTROL_PORT
```

This reverse mapping carries test orchestration only. Native Syncthing file transfer uses the real LAN connection to phone port 22103.

Run one instrumentation method at a time. All four methods require the run token and control endpoint; none installs, uninstalls or clears an application:

```text
adb -s PHONE_SERIAL shell am instrument -w -r -e class dev.fourthbreakfast.planner.PhoneSyncTest#METHOD -e plannerQaRun RUN_TOKEN -e plannerQaControl http://127.0.0.1:37481/qa/RUN_TOKEN dev.fourthbreakfast.planner.demo.qa.test/androidx.test.runner.AndroidJUnitRunner
```

Foreground methods never launch an Activity automatically. At `open_sync_screen`, wait for permission to use the phone's screen, launch the QA app and open Settings > Sync. Then acknowledge that exact UI state with `adb -s PHONE_SERIAL shell run-as dev.fourthbreakfast.planner.demo.qa touch files/qa-ui-ready-RUN_TOKEN`. The helper removes only its unique marker before waiting, and also requires a real started MainActivity through the instrumentation lifecycle monitor. A later Home stage waits for the actual Activity to stop; the driver does not simulate the foreground lifecycle callbacks.

For background-only verification while the phone is in use, add `-e plannerQaHeadless true` to `foregroundExchangeAndPrepareBackground`. This branch skips the UI marker, never launches an Activity or calls `onInSight`, and explicitly stops native sync before publishing the fresh background task. It records `exchangeMode=headless` and makes no foreground/Home/linger claim. JobScheduler delivery and the separate Room-only verifier remain the same.

For a controlled first eligible periodic run, additionally pass `-e plannerQaRequeuePeriodic true`. After stopping native sync, the driver cancels and verifies only the QA application's old unique `sync` request, publishes the fresh host task, then reapplies the existing sync-anywhere choice through the production scheduling path. A new WorkSpec ID must be enqueued or running; `periodic-requeued` reports it. Refresh the QA JobScheduler job ID before forcing it. This mode records `periodicRequeued=true` and proves the eligible worker path rather than a natural 15-minute cadence. It does not alter production/demo packages or device-wide power settings.

1. Optionally run `retainedPairingScreenStopsAndRestartsAcrossHome` before pairing. At `plannerQaStage=background_wait`, press Home without navigating away from Sync. After the 60-second linger stops native sync, `foreground_return` asks you to bring the QA application forward. The test verifies restart without calling `onInSight` itself. This covers a retained, unpaired pairing screen.
2. Run `foregroundExchangeAndPrepareBackground`. It verifies native startup on the QA port, explicit pairing, a PC task arriving in Room, completion returning to the PC, a starred phone task arriving on the PC, and current sync status. At `background_wait`, press Home. The test waits for the real linger stop, verifies the periodic WorkManager request exists, and only then asks the host to publish a new background task. Existing QA tasks must remain unchanged. It returns at `background-ready`.
3. Run only the QA package's scheduled SyncWorker through Android's JobScheduler, or wait for its natural periodic run. Find the QA application's job and WorkSpec ID in `dumpsys jobscheduler`; do not run a job belonging to the regular or ordinary demo package. A forced job can use `cmd jobscheduler run -f dev.fourthbreakfast.planner.demo.qa JOB_ID`. Wait for that worker to finish before restarting instrumentation.
4. Run `verifyScheduledBackgroundDelivery`. It reads Room without starting sync, opening an Activity, or invoking `runOnce`. The newly published task must already be present. The host records `mode=scheduled-worker` and shuts down its private engine.

For a periodic request that already completed its first run, wait until the next 15-minute interval is eligible before forcing it. AndroidX also checks the WorkSpec's next run time inside [WorkerWrapper](https://github.com/androidx/androidx/blob/androidx-main/work/work-runtime/src/main/java/androidx/work/impl/WorkerWrapper.kt), so an early JobScheduler force can return without executing SyncWorker. Keep the fixture alive and observe the later eligible run; do not alter the phone clock or the WorkManager database to accelerate the test.

If OS job delivery cannot be exercised, `directRunOnceFallback` instead publishes another fresh task and calls the production `runOnce` while out of sight with pairing retained. Its result is explicitly `mode=direct-run-once`; it does not establish Android scheduling behavior.

The host preserves `host.json`, `stage.txt`, `result.json` or `failure.txt`, and its private native logs in the fixture folder. It asserts the phone task's starred value and the shared completion state rather than merely printing a connection. Outside an explicitly configured run, the real-phone host test and phone instrumentation methods are skipped. Remove only the test's ADB reverse mapping when finished; keep existing phone data intact.

Use a new run token and empty host folder for a new end-to-end attempt after data has been exchanged. The phone driver removes only old peer entries whose names begin `planner QA PC `; unknown peers and existing tasks remain untouched. To abort a waiting host without killing a Gradle daemon or another app, POST `{"message":"operator abort"}` to its `/failed` endpoint; its test finally block stops only its private Syncthing.
