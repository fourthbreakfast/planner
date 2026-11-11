package dev.fourthbreakfast.planner.desktop

import dev.fourthbreakfast.planner.core.Sync
import dev.fourthbreakfast.planner.sync.SyncPrefKeys
import dev.fourthbreakfast.planner.sync.SyncRunner
import dev.fourthbreakfast.planner.ui.Prefs
import java.io.File
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

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
        defaultName = DesktopPlatform.deviceName(),
        scope = scope,
        log = logTo(File(root, "home/planner.log")),
    ) {
    private val leftBehind = LeftBehind(File(root, "home"))

    override suspend fun beforeStart() = withContext(Dispatchers.IO) { leftBehind.stop() }
}

/** What the app did with its Syncthing, and why it would not start, kept beside Syncthing's own log. */
private fun logTo(file: File): (String) -> Unit = { line ->
    runCatching {
        file.parentFile.mkdirs()
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
        ProcessHandle.allProcesses().use { processes ->
            processes.filter { it.pid() != ProcessHandle.current().pid() }.forEach { process ->
                val info = process.info()
                if (isPrivateSyncthing(info.command().orElse(null), info.arguments().orElse(null), home)) {
                    process.destroy()
                    try {
                        process.onExit().get(5, TimeUnit.SECONDS)
                    } catch (_: java.util.concurrent.TimeoutException) {
                        process.destroyForcibly()
                    } catch (_: java.util.concurrent.ExecutionException) {
                        // It exited while its status was being read.
                    }
                }
            }
        }
    }
}
