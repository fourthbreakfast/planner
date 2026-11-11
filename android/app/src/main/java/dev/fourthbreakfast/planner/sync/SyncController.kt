package dev.fourthbreakfast.planner.sync

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Process
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
import dev.fourthbreakfast.planner.ui.AndroidPrefs
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private const val WORK = "sync"
private const val TICK_MILLIS = 2_000L
private const val LINGER_MILLIS = 60_000L

/**
 * Sync on the phone: Syncthing runs while the app is in sight and someone is paired, or while the sync screen is open,
 * and stops a minute after the app goes out of sight. Between uses, [SyncWorker] gives it a short run about every
 * fifteen minutes.
 */
class SyncController(private val context: Context, sync: Sync, changes: Flow<Any>, scope: CoroutineScope) :
    SyncRunner(
        binary = binaryOf(context),
        root = rootOf(context),
        sync = sync,
        changes = changes,
        prefs = AndroidPrefs(context, "sync"),
        keys = SyncPrefKeys(anywhere = "anywhere", name = "sync_name", paired = "paired"),
        defaultName = Build.MODEL.orEmpty().ifBlank { "phone" }.lowercase(),
        scope = scope,
        ports = if (context.packageName == "dev.fourthbreakfast.planner.demo.qa") Ports(22103, 21103) else Ports(),
        log = { Log.i("planner", it) },
    ) {
    // android drops the packets devices find each other with unless an app asks for them
    private val multicast = (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
        .createMulticastLock("planner sync")
        .apply { setReferenceCounted(false) }
    private var linger: Job? = null
    private val lifetime = Mutex()
    private var backgroundRuns = 0

    @Volatile private var inSight = false

    fun onInSight() {
        inSight = true
        linger?.cancel()
        if (pairing || paired) scope.launch { lifetime.withLock { if (inSight) ensureRunning() } }
    }

    fun onOutOfSight() {
        inSight = false
        linger?.cancel()
        linger = scope.launch {
            delay(LINGER_MILLIS)
            // A worker owns the process until its transfer finishes. Complete an already-started stop before resume.
            withContext(NonCancellable) { lifetime.withLock { if (!inSight && backgroundRuns == 0) stop() } }
        }
    }

    /** A short run for background work: trade with whoever is around, wait until everything is through, stop. */
    suspend fun runOnce() {
        if (!paired || !available) return
        lifetime.withLock { backgroundRuns++ }
        try {
            ensureRunning()
            withTimeoutOrNull(120_000) {
                while (isActive) {
                    delay(TICK_MILLIS)
                    if (settledOrUnsure()) break
                }
            }
        } finally {
            // however the run ends, a syncthing nobody is looking at does not stay behind
            withContext(NonCancellable) {
                lifetime.withLock {
                    backgroundRuns--
                    if (!inSight && backgroundRuns == 0) stop()
                }
            }
        }
    }

    /** Whether the run can end: everything is through, or syncthing stopped answering, which waiting will not mend. */
    private suspend fun settledOrUnsure(): Boolean = try {
        settled()
    } catch (e: IOException) {
        Log.w("planner", "sync run: $e")
        true
    }

    override fun onPairedChanged(paired: Boolean) = schedule(paired)

    override fun onAnywhereChanged() {
        if (paired) schedule(true, replace = true)
    }

    // a syncthing a killed run of the app left going still holds the database and the port, so it goes first. android
    // normally stops an app's programs with it, but not always, and then sync would not start again until a restart
    override suspend fun beforeStart() = withContext(Dispatchers.IO) {
        val left = leftBehindSyncthings(binaryOf(context), File(rootOf(context), "home"))
        for (pid in left) {
            Log.i("planner", "stopping a syncthing left behind: $pid")
            Process.killProcess(pid)
        }
        // it is gone once linux no longer lists it
        withTimeoutOrNull(5_000) { while (left.any { File("/proc/$it").exists() }) delay(100) }
        Unit
    }

    override fun afterStart() {
        if (!multicast.isHeld) multicast.acquire()
    }

    override fun afterStop() {
        if (multicast.isHeld) multicast.release()
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
}

// android only runs a program the app unpacked itself, as a library
private fun binaryOf(context: Context) = File(context.applicationInfo.nativeLibraryDir, "libsyncthing.so")

private fun rootOf(context: Context) = File(context.filesDir, "syncthing")

/** The run between uses, whenever Android allows it. */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val sync = (applicationContext as PlannerApp).syncController
        sync.runOnce()
        return if (sync.state.value.failed) Result.retry() else Result.success()
    }
}
