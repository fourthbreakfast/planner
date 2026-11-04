package dev.fourthbreakfast.planner.sync

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.fourthbreakfast.planner.PlannerApp
import dev.fourthbreakfast.planner.core.Sync
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
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
import kotlinx.coroutines.withTimeoutOrNull

private const val PREFS = "sync"
private const val PREF_ANYWHERE = "anywhere"
private const val PREF_PAIRED = "paired"
private const val WORK = "sync"
private const val TICK_MILLIS = 2_000L
private const val LINGER_MILLIS = 60_000L

/**
 * Runs the app's Syncthing while the app is in use and someone is paired, or while the sync screen is open, and keeps
 * this device's file and the others' in step with the database. It stops a minute after the app goes out of sight.
 * Between uses, [SyncWorker] gives it a short run about every fifteen minutes.
 */
class SyncController(private val context: Context, private val sync: Sync, private val changes: Flow<Any>, private val scope: CoroutineScope) : SyncControls {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val binary = File(context.applicationInfo.nativeLibraryDir, "libsyncthing.so")
    private val root = File(context.filesDir, "syncthing")
    private val syncthing = Syncthing(binary, File(root, "home"), File(root, "share")) { Log.i("planner", it) }
    private val lock = Mutex()
    private val multicast = (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
        .createMulticastLock("planner sync")
        .apply { setReferenceCounted(false) }
    private var files: DeviceFiles? = null
    private var loop: Job? = null
    private var linger: Job? = null
    private var inSight = false
    private var pairing = false

    private val current = MutableStateFlow(SyncState())
    override val state: StateFlow<SyncState> = current

    /** False on a phone the bundled Syncthing was not built for. */
    override val available: Boolean get() = binary.exists()

    override val anywhere: Boolean get() = prefs.getBoolean(PREF_ANYWHERE, false)

    private var paired: Boolean
        get() = prefs.getBoolean(PREF_PAIRED, false)
        set(value) {
            if (value == paired) return
            prefs.edit().putBoolean(PREF_PAIRED, value).apply()
            schedule(value)
        }

    fun onInSight() {
        inSight = true
        linger?.cancel()
        if (paired) scope.launch { ensureRunning() }
    }

    fun onOutOfSight() {
        inSight = false
        linger?.cancel()
        linger = scope.launch {
            delay(LINGER_MILLIS)
            if (!inSight && !pairing) stop()
        }
    }

    /** The sync screen is open: it runs, and any device asking to pair is let in. */
    override fun openPairing() {
        pairing = true
        scope.launch { ensureRunning() }
    }

    override fun closePairing() {
        pairing = false
        if (!paired) scope.launch { stop() }
    }

    override fun chooseAnywhere(on: Boolean) {
        prefs.edit().putBoolean(PREF_ANYWHERE, on).apply()
        if (paired) schedule(true, replace = true)
        scope.launch {
            if (syncthing.running) {
                stop()
                ensureRunning()
            }
        }
    }

    /** Pairs with a device by its whole id, or by the short code it shows when it is on the same network. */
    override suspend fun pairWith(code: String): Boolean {
        ensureRunning()
        val code = parsePairCode(code)
        val found = if (code.id.replace("-", "").length >= 52) {
            code.id to code.addresses
        } else {
            val short = code.id.replace("-", "").take(7)
            // seen on the network already, or asked for door to door when the broadcasts do not get through
            syncthing.nearby().firstOrNull { shortId(it) == short }?.let { it to emptyList() }
                ?: findOnLan(short, syncthing.listenPort)?.let { (id, address) -> id to listOf(address) }
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

    // a device that asked is let in only on a yes: never just because the sync screen was open
    override fun letIn(id: String) {
        scope.launch {
            try {
                syncthing.pair(id, introducer = false)
                refresh()
            } catch (_: IOException) {
            }
        }
    }

    override fun refuse(id: String) {
        scope.launch {
            try {
                syncthing.refuse(id)
                refresh()
            } catch (_: IOException) {
            }
        }
    }

    override fun unpair(id: String) {
        scope.launch {
            try {
                syncthing.unpair(id)
                refresh()
            } catch (_: IOException) {
            }
        }
    }

    /** A short run for background work: trade with whoever is around, wait until everything is through, stop. */
    suspend fun runOnce() {
        if (!paired || !available) return
        ensureRunning()
        withTimeoutOrNull(120_000) {
            while (isActive) {
                delay(TICK_MILLIS)
                if (state.value.peers.any { it.connected } && syncthing.settled()) break
            }
        }
        if (!inSight && !pairing) stop()
    }

    private suspend fun ensureRunning() {
        if (!available) return
        lock.withLock {
            if (syncthing.running) return
            try {
                syncthing.start(anywhere, deviceName())
            } catch (e: Exception) {
                // a program of its own that would not start is no reason for the app to stop
                Log.w("planner", "syncthing did not start", e)
                syncthing.stop()
                current.value = current.value.copy(failed = true)
                return
            }
            // android drops the packets devices find each other with unless an app asks for them
            if (!multicast.isHeld) multicast.acquire()
            val files = DeviceFiles(syncthing.folder, syncthing.myId, sync).also { this.files = it }
            current.value = SyncState(running = true, myId = syncthing.myId, addresses = syncthing.addresses())
            loop = scope.launch { run(files) }
        }
    }

    @OptIn(FlowPreview::class)
    private suspend fun run(files: DeviceFiles) {
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
        val peers = syncthing.peers()
        paired = peers.isNotEmpty()
        current.value = current.value.copy(
            peers = peers,
            nearby = if (pairing) syncthing.nearby() else emptyList(),
            asking = if (pairing) syncthing.asking() else emptyList(),
        )
    }

    private suspend fun stop() {
        lock.withLock {
            loop?.cancel()
            loop = null
            files = null
            syncthing.stop()
            current.value = current.value.copy(running = false)
            if (multicast.isHeld) multicast.release()
        }
    }

    private fun schedule(on: Boolean, replace: Boolean = false) {
        val work = WorkManager.getInstance(context)
        if (!on) {
            work.cancelUniqueWork(WORK)
            return
        }
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            // on the local network only, the run waits for wifi and spends no mobile data; sync anywhere may use either
            .setConstraints(Constraints.Builder().setRequiredNetworkType(if (anywhere) NetworkType.CONNECTED else NetworkType.UNMETERED).build())
            .build()
        work.enqueueUniquePeriodicWork(WORK, if (replace) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP, request)
    }

    private fun deviceName(): String = Build.MODEL.orEmpty().ifBlank { "phone" }.lowercase()
}

/** The run between uses, whenever Android allows it. */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        (applicationContext as PlannerApp).syncController.runOnce()
        return Result.success()
    }
}
