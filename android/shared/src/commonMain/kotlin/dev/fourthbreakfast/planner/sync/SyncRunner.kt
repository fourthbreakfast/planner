package dev.fourthbreakfast.planner.sync

import dev.fourthbreakfast.planner.core.Sync
import dev.fourthbreakfast.planner.ui.Prefs
import java.io.File
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val TICK_MILLIS = 2_000L

/** The names a device keeps its sync choices under. They differ between the phone and the computer, and stay as they were. */
class SyncPrefKeys(val anywhere: String, val name: String, val paired: String)

/**
 * Runs the app's own Syncthing, from [binary] with its home and shared folder in [root], and keeps this device's file
 * and the others' in step with the database. It runs while something is paired and the app is in use, or while the
 * sync screen is open. The phone and the computer each say when the app is in use, and add what their system needs
 * around a start and a stop.
 */
abstract class SyncRunner(
    private val binary: File?,
    root: File,
    private val sync: Sync,
    private val changes: Flow<Any>,
    private val prefs: Prefs,
    private val keys: SyncPrefKeys,
    private val defaultName: String,
    protected val scope: CoroutineScope,
    private val log: (String) -> Unit,
) : SyncControls {
    private val syncthing = binary?.let { Syncthing(it, File(root, "home"), File(root, "share"), log = log) }
    private val lock = Mutex()
    private var files: DeviceFiles? = null
    private var loop: Job? = null
    private val current = MutableStateFlow(SyncState())

    /** Whether the sync screen is open. */
    protected var pairing = false
        private set

    override val state: StateFlow<SyncState> = current

    override val available: Boolean get() = binary?.exists() == true

    override val anywhere: Boolean get() = prefs.getBoolean(keys.anywhere, false)

    override val deviceName: String get() = prefs.getString(keys.name) ?: defaultName

    /** Whether any device is paired, as last seen. Written only when it changes, not on every look at the devices. */
    protected var paired: Boolean
        get() = prefs.getBoolean(keys.paired, false)
        private set(value) {
            if (value == paired) return
            prefs.putBoolean(keys.paired, value)
            onPairedChanged(value)
        }

    /** Called when the first device is paired or the last one goes. */
    protected open fun onPairedChanged(paired: Boolean) {}

    /** Called when sync anywhere is switched, before Syncthing starts again with it. */
    protected open fun onAnywhereChanged() {}

    /** Called just before Syncthing starts. */
    protected open fun beforeStart() {}

    /** Called once Syncthing has started. */
    protected open fun afterStart() {}

    /** Called once Syncthing has stopped. */
    protected open fun afterStop() {}

    /** Starts syncing when a device is paired, as the app comes into use. */
    fun startIfPaired() {
        if (paired) scope.launch { ensureRunning() }
    }

    override fun renameThisDevice(name: String) {
        val clean = name.trim().take(40)
        if (clean.isEmpty()) return
        prefs.putString(keys.name, clean)
        scope.launch {
            val syncthing = syncthing ?: return@launch
            quietly { if (syncthing.running) syncthing.rename(syncthing.myId, clean) }
        }
    }

    /** The sync screen is open: it runs, and any device asking to pair is shown. */
    override fun openPairing() {
        pairing = true
        scope.launch { ensureRunning() }
    }

    override fun closePairing() {
        pairing = false
        if (!paired) scope.launch { stop() }
    }

    override fun chooseAnywhere(on: Boolean) {
        prefs.putBoolean(keys.anywhere, on)
        onAnywhereChanged()
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
        val parsed = parsePairCode(code)
        val found = if (parsed.id.replace("-", "").length >= 52) {
            parsed.id to parsed.addresses
        } else {
            val short = shortId(parsed.id)
            // seen on the network already, or asked for door to door when the broadcasts do not get through
            syncthing.nearby().firstOrNull { shortId(it) == short }?.let { it to emptyList() }
                ?: findOnLan(short, syncthing.listenPort, log)?.let { (id, address) -> id to listOf(address) }
                ?: return false
        }
        return try {
            syncthing.pair(found.first, introducer = true, addresses = found.second)
            paired = true
            refresh()
            true
        } catch (_: IOException) {
            false
        }
    }

    override suspend fun syncNow() {
        ensureRunning()
        val syncthing = syncthing ?: return
        val files = files ?: return
        quietly {
            files.publish()
            syncthing.rescan()
            files.takeIn()
            files.carryNames(syncthing, deviceName)
            refresh()
        }
    }

    // a device that asked is let in only on a yes: never just because the sync screen was open
    override fun letIn(id: String) = act { it.pair(id, introducer = false) }

    override fun refuse(id: String) = act { it.refuse(id) }

    override fun unpair(id: String) = act { it.unpair(id) }

    /** Changes something about the paired devices, then shows the devices as they are now. */
    private fun act(change: suspend (Syncthing) -> Unit) {
        scope.launch {
            val syncthing = syncthing ?: return@launch
            try {
                change(syncthing)
                refresh()
            } catch (_: IOException) {
            }
        }
    }

    /** Starts Syncthing unless it runs already, and begins taking turns at publishing and taking in. */
    protected suspend fun ensureRunning() {
        val syncthing = syncthing ?: return
        if (!available) return
        lock.withLock {
            if (syncthing.running) return
            beforeStart()
            try {
                syncthing.start(anywhere, deviceName)
            } catch (e: Exception) {
                // a program of its own that would not start is no reason for the app to stop
                log("could not start: $e")
                syncthing.stop()
                current.value = current.value.copy(failed = true)
                return
            }
            afterStart()
            val files = DeviceFiles(syncthing.folder, syncthing.myId, sync).also { this.files = it }
            current.value = SyncState(running = true, myId = syncthing.myId, addresses = syncthing.addresses())
            loop = scope.launch { run(syncthing, files) }
        }
    }

    /** Publishes this device's file a second after each change, and takes in the others' every turn. */
    @OptIn(FlowPreview::class)
    private suspend fun run(syncthing: Syncthing, files: DeviceFiles) = coroutineScope {
        launch {
            quietly { files.publish() }
            changes.debounce(1_000).collect { quietly { files.publish() } }
        }
        while (isActive) {
            quietly {
                files.takeIn()
                files.carryNames(syncthing, deviceName)
                refresh()
            }
            delay(TICK_MILLIS)
        }
    }

    /** Shows the devices as they are now: the paired ones, and while pairing, the ones nearby and the ones asking. */
    protected suspend fun refresh() {
        val syncthing = syncthing ?: return
        val peers = syncthing.peers()
        paired = peers.isNotEmpty()
        current.value = current.value.copy(
            peers = peers,
            nearby = if (pairing) syncthing.nearby() else emptyList(),
            asking = if (pairing) syncthing.asking() else emptyList(),
        )
    }

    /** Stops Syncthing, letting it close its database first. */
    suspend fun stop() {
        lock.withLock {
            loop?.cancel()
            loop = null
            files = null
            syncthing?.stop()
            current.value = current.value.copy(running = false)
            afterStop()
        }
    }

    /** Whether everything has been sent to and taken from every connected device, for a short run to end on. */
    protected suspend fun settled(): Boolean = state.value.peers.any { it.connected } && syncthing?.settled() == true
}

/**
 * Runs [block], letting a failure pass: Syncthing busy or gone, a file half written. It is tried again on the next turn,
 * so nothing is lost by it, but a cancel still ends what was cancelled.
 */
private suspend fun quietly(block: suspend () -> Unit) {
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
    }
}
