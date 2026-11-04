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
import dev.fourthbreakfast.planner.ui.AndroidPrefs
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
        // Android only runs a program the app unpacked itself, as a library
        binary = File(context.applicationInfo.nativeLibraryDir, "libsyncthing.so"),
        root = File(context.filesDir, "syncthing"),
        sync = sync,
        changes = changes,
        prefs = AndroidPrefs(context, "sync"),
        keys = SyncPrefKeys(anywhere = "anywhere", name = "sync_name", paired = "paired"),
        defaultName = Build.MODEL.orEmpty().ifBlank { "phone" }.lowercase(),
        scope = scope,
        log = { Log.i("planner", it) },
    ) {
    // android drops the packets devices find each other with unless an app asks for them
    private val multicast = (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
        .createMulticastLock("planner sync")
        .apply { setReferenceCounted(false) }
    private var linger: Job? = null
    private var inSight = false

    fun onInSight() {
        inSight = true
        linger?.cancel()
        startIfPaired()
    }

    fun onOutOfSight() {
        inSight = false
        linger?.cancel()
        linger = scope.launch {
            delay(LINGER_MILLIS)
            if (!inSight && !pairing) stop()
        }
    }

    /** A short run for background work: trade with whoever is around, wait until everything is through, stop. */
    suspend fun runOnce() {
        if (!paired || !available) return
        ensureRunning()
        withTimeoutOrNull(120_000) {
            while (isActive) {
                delay(TICK_MILLIS)
                if (settled()) break
            }
        }
        if (!inSight && !pairing) stop()
    }

    override fun onPairedChanged(paired: Boolean) = schedule(paired)

    override fun onAnywhereChanged() {
        if (paired) schedule(true, replace = true)
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

/** The run between uses, whenever Android allows it. */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        (applicationContext as PlannerApp).syncController.runOnce()
        return Result.success()
    }
}
