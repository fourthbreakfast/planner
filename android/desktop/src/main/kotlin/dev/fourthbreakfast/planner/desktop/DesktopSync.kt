package dev.fourthbreakfast.planner.desktop

import dev.fourthbreakfast.planner.core.Sync
import dev.fourthbreakfast.planner.sync.SyncPrefKeys
import dev.fourthbreakfast.planner.sync.SyncRunner
import dev.fourthbreakfast.planner.ui.Prefs
import java.io.File
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

/**
 * Sync on the computer: the app's own Syncthing runs for as long as the app does and something is paired, or while the
 * sync screen is open, and stops with the app.
 */
class DesktopSync(binary: File?, root: File, sync: Sync, changes: Flow<Any>, prefs: Prefs, scope: CoroutineScope) :
    SyncRunner(
        binary = binary,
        root = root,
        sync = sync,
        changes = changes,
        prefs = prefs,
        keys = SyncPrefKeys(anywhere = "sync_anywhere", name = "sync_name", paired = "sync_paired"),
        // the name windows gives the computer, read without asking the network
        defaultName = (System.getenv("COMPUTERNAME") ?: "windows").lowercase(),
        scope = scope,
        log = logTo(File(root, "home/planner.log")),
    ) {
    private val leftBehind = LeftBehind(File(root, "home"))

    override fun beforeStart() = leftBehind.stop()
}

/** What the app did with its Syncthing, and why it would not start, kept beside Syncthing's own log. */
private fun logTo(file: File): (String) -> Unit = { line ->
    runCatching {
        if (file.length() > 200_000) file.delete()
        file.appendText("${LocalDateTime.now().withNano(0)} $line\n")
    }
}

/**
 * A Syncthing an earlier run of the app left going, when it was killed rather than quit: Windows does not stop the
 * children of a killed program. It holds the folder and the port, so it is stopped before a new one starts. Only a
 * Syncthing started with this app's own home folder is touched.
 */
private class LeftBehind(private val home: File) {
    fun stop() {
        val query = "Get-CimInstance Win32_Process -Filter \"Name='syncthing.exe'\" | " +
            "Where-Object { \$_.CommandLine -like '*${home.path.replace("'", "''")}*' } | " +
            "ForEach-Object { Stop-Process -Id \$_.ProcessId -Force }"
        runCatching {
            ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command", query)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start()
                .waitFor(15, TimeUnit.SECONDS)
        }
    }
}
