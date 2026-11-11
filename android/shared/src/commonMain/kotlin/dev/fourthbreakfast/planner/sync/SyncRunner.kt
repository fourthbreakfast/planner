package dev.fourthbreakfast.planner.sync

import dev.fourthbreakfast.planner.core.Sync
import dev.fourthbreakfast.planner.ui.Prefs
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
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
    ports: Ports = Ports(),
    private val log: (String) -> Unit,
) : SyncControls {
    private val syncthing = binary?.let { Syncthing(it, File(root, "home"), File(root, "share"), ports = ports, log = log) }
    private val lock = Mutex()
    private val manualSync = Mutex()
    private val generation = AtomicLong(0)
    private val published = AtomicLong(-1)
    private var files: DeviceFiles? = null
    private var loop: Job? = null
    private val current = MutableStateFlow(SyncState())

    private var lastFailure: String? = null

    /** Whether the sync screen is open. */
    protected var pairing = false
        private set

    override val state: StateFlow<SyncState> = current

    override val available: Boolean get() = binary?.exists() == true

    override val anywhere: Boolean get() = prefs.getBoolean(keys.anywhere, true)

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
    protected open suspend fun beforeStart() {}

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
            quietly("rename") { if (syncthing.running) syncthing.rename(syncthing.myId, clean) }
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
        return try {
            pair(syncthing, parsePairCode(code))
        } catch (e: IOException) {
            // syncthing would not start, or stopped answering: the code is not found, and can be tried again
            log("pairing: $e")
            false
        }
    }

    private suspend fun pair(syncthing: Syncthing, parsed: PairCode): Boolean {
        val found = if (parsed.id.replace("-", "").length >= 52) {
            parsed.id to parsed.addresses
        } else {
            val short = shortId(parsed.id)
            // seen on the network already, or asked for door to door when the broadcasts do not get through
            syncthing.nearby().firstOrNull { shortId(it) == short }?.let { it to emptyList() }
                ?: findOnLan(short, syncthing.listenPort, log)?.let { (id, address) -> id to listOf(address) }
                ?: return false
        }
        syncthing.pair(found.first, introducer = true, addresses = found.second)
        paired = true
        refresh()
        return true
    }

    override suspend fun syncNow() = manualSync.withLock {
        current.update { it.copy(syncing = true, upToDate = false) }
        try {
            ensureRunning()
            val syncthing = syncthing ?: return@withLock
            val files = files ?: return@withLock
            quietly("sync now") {
                publish(syncthing, files)
                files.takeIn()
                files.carryNames(syncthing, deviceName)
                refresh()
            }
        } finally {
            current.update { it.copy(syncing = false) }
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
            quietly("change of devices") {
                change(syncthing)
                refresh()
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
            } catch (e: IOException) {
                // a program of its own that would not start is no reason for the app to stop
                log("could not start: $e")
                syncthing.stop()
                current.update { it.copy(failed = true, upToDate = false) }
                return
            }
            afterStart()
            published.set(-1)
            val files = DeviceFiles(syncthing.folder, syncthing.myId, sync).also { this.files = it }
            current.update { it.copy(running = true, myId = syncthing.myId, addresses = syncthing.addresses(), failed = false, upToDate = false) }
            loop = scope.launch { run(syncthing, files) }
        }
    }

    /** Publishes this device's file a second after each change, and takes in the others' every turn. */
    @OptIn(FlowPreview::class)
    private suspend fun run(syncthing: Syncthing, files: DeviceFiles) = coroutineScope {
        launch {
            quietly("publish") { publish(syncthing, files) }
            changes.onEach {
                generation.incrementAndGet()
                current.update { it.copy(upToDate = false) }
            }.debounce(1_000).collect { quietly("publish") { publish(syncthing, files) } }
        }
        while (isActive) {
            quietly("turn") {
                if (published.get() != generation.get()) publish(syncthing, files)
                files.takeIn()
                files.carryNames(syncthing, deviceName)
                refresh()
            }
            delay(TICK_MILLIS)
        }
    }

    private suspend fun publish(syncthing: Syncthing, files: DeviceFiles) {
        val version = generation.get()
        files.publish()
        syncthing.rescan()
        published.set(version)
    }

    /** Shows the devices as they are now: the paired ones, and while pairing, the ones nearby and the ones asking. */
    protected suspend fun refresh() {
        val syncthing = syncthing ?: return
        val peers = syncthing.peers()
        paired = peers.isNotEmpty()
        val version = generation.get()
        val complete = peers.isNotEmpty() && peers.all { it.connected } && published.get() == version && syncthing.settled()
        val nearby = if (pairing) syncthing.nearby() else emptyList()
        val asking = if (pairing) syncthing.asking() else emptyList()
        val addresses = syncthing.addresses()
        current.update {
            it.copy(
                peers = peers,
                addresses = addresses,
                nearby = nearby,
                asking = asking,
                failed = false,
                upToDate = complete && generation.get() == version,
            )
        }
    }

    /** Stops Syncthing, letting it close its database first. */
    suspend fun stop() {
        lock.withLock {
            loop?.cancelAndJoin()
            loop = null
            files = null
            syncthing?.stop()
            current.update { it.copy(running = false, peers = it.peers.map { peer -> peer.copy(connected = false) }, upToDate = false) }
            afterStop()
        }
    }

    /** Wire completion precedes the app's next poll: commit received files before a short run can stop that poll. */
    protected suspend fun settled(): Boolean = manualSync.withLock {
        val syncthing = syncthing ?: return@withLock false
        val files = files ?: return@withLock false
        try {
            if (syncthing.peers().none { it.connected } || !syncthing.settled()) return@withLock false
            files.takeIn()
            // Publish the committed snapshot and its acknowledgements before declaring this background run finished.
            publish(syncthing, files)
            refresh()
            syncthing.peers().any { it.connected } && syncthing.settled() && published.get() == generation.get()
        } catch (failure: IOException) {
            current.update { it.copy(failed = true, upToDate = false) }
            throw failure
        }
    }

    /**
     * Runs [block], one part of sync named [what], letting a failure to reach Syncthing or the shared folder pass: Syncthing
     * busy, gone or answering oddly, a file half written. Sync tries again on its next turn, so nothing is lost by it, and
     * the failure is logged once until a different one comes. Anything else is a fault in the app, and is not caught here.
     */
    private suspend fun quietly(what: String, block: suspend () -> Unit) {
        try {
            block()
            lastFailure = null
        } catch (e: IOException) {
            current.update { it.copy(failed = true, upToDate = false) }
            val failure = "$what: $e"
            if (failure != lastFailure) log(failure)
            lastFailure = failure
        }
    }
}
