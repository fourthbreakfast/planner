package dev.fourthbreakfast.planner.desktop

import dev.fourthbreakfast.planner.core.Sync
import dev.fourthbreakfast.planner.sync.DeviceFiles
import dev.fourthbreakfast.planner.sync.SyncControls
import dev.fourthbreakfast.planner.sync.SyncState
import dev.fourthbreakfast.planner.sync.Syncthing
import dev.fourthbreakfast.planner.sync.shortId
import dev.fourthbreakfast.planner.ui.Prefs
import java.io.File
import java.io.IOException
import java.net.InetAddress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val PREF_ANYWHERE = "sync_anywhere"
private const val PREF_PAIRED = "sync_paired"
private const val TICK_MILLIS = 2_000L

/**
 * The computer's sync: the app's own Syncthing runs for as long as the window is open and something is paired, or
 * while the sync screen is open, and stops with the app.
 */
class DesktopSync(
    private val binary: File?,
    root: File,
    private val sync: Sync,
    private val changes: Flow<Any>,
    private val prefs: Prefs,
    private val scope: CoroutineScope,
) : SyncControls {
    private val syncthing = binary?.let { Syncthing(it, File(root, "home"), File(root, "share")) }
    private val leftBehind = LeftBehind(File(root, "home"))
    private val lock = Mutex()
    private var loop: Job? = null
    private var pairing = false
    private val current = MutableStateFlow(SyncState())

    override val state: StateFlow<SyncState> = current

    override val available: Boolean get() = binary?.exists() == true

    override val anywhere: Boolean get() = prefs.getBoolean(PREF_ANYWHERE, false)

    private var paired: Boolean
        get() = prefs.getBoolean(PREF_PAIRED, false)
        set(value) = prefs.putBoolean(PREF_PAIRED, value)

    /** Called once the window is up. */
    fun begin() {
        if (paired) scope.launch { ensureRunning() }
    }

    override fun openPairing() {
        pairing = true
        scope.launch { ensureRunning() }
    }

    override fun closePairing() {
        pairing = false
        if (!paired) scope.launch { stop() }
    }

    override fun chooseAnywhere(on: Boolean) {
        prefs.putBoolean(PREF_ANYWHERE, on)
        scope.launch {
            if (syncthing?.running == true) {
                stop()
                ensureRunning()
            }
        }
    }

    override suspend fun pairWith(code: String): Boolean {
        val syncthing = syncthing ?: return false
        ensureRunning()
        val typed = code.trim().uppercase()
        val id = if (typed.replace("-", "").length >= 52) {
            typed
        } else {
            syncthing.nearby().firstOrNull { shortId(it) == typed.replace("-", "").take(7) } ?: return false
        }
        return try {
            syncthing.pair(id, introducer = true)
            refresh()
            true
        } catch (_: IOException) {
            false
        }
    }

    // a device that asked is let in only on a yes: never just because the sync screen was open
    override fun letIn(id: String) {
        scope.launch {
            try {
                syncthing?.pair(id, introducer = false)
                refresh()
            } catch (_: IOException) {
            }
        }
    }

    override fun refuse(id: String) {
        scope.launch {
            try {
                syncthing?.refuse(id)
                refresh()
            } catch (_: IOException) {
            }
        }
    }

    override fun unpair(id: String) {
        scope.launch {
            try {
                syncthing?.unpair(id)
                refresh()
            } catch (_: IOException) {
            }
        }
    }

    private suspend fun ensureRunning() {
        val syncthing = syncthing ?: return
        if (!available) return
        lock.withLock {
            if (syncthing.running) return
            leftBehind.stop()
            try {
                syncthing.start(anywhere, InetAddress.getLocalHost().hostName.lowercase())
            } catch (_: Exception) {
                // a program of its own that would not start is no reason for the app to stop
                syncthing.stop()
                current.value = current.value.copy(failed = true)
                return
            }
            val files = DeviceFiles(syncthing.folder, syncthing.myId, sync)
            current.value = SyncState(running = true, myId = syncthing.myId)
            loop = scope.launch { run(syncthing, files) }
        }
    }

    @OptIn(FlowPreview::class)
    private suspend fun run(syncthing: Syncthing, files: DeviceFiles) {
        val publishing = scope.launch {
            runCatching { files.publish() }
            changes.debounce(1_000).collect { runCatching { files.publish() } }
        }
        try {
            while (scope.isActive) {
                runCatching {
                    files.takeIn()
                    refresh()
                }
                delay(TICK_MILLIS)
            }
        } finally {
            publishing.cancel()
        }
    }

    private suspend fun refresh() {
        val syncthing = syncthing ?: return
        val peers = syncthing.peers()
        paired = peers.isNotEmpty()
        current.value = current.value.copy(
            peers = peers,
            nearby = if (pairing) syncthing.nearby() else emptyList(),
            asking = if (pairing) syncthing.asking() else emptyList(),
        )
    }

    /** Stops Syncthing, letting it close its database. */
    suspend fun stop() {
        lock.withLock {
            loop?.cancel()
            loop = null
            syncthing?.stop()
            current.value = current.value.copy(running = false)
        }
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
                .waitFor(15, java.util.concurrent.TimeUnit.SECONDS)
        }
    }
}
