package dev.fourthbreakfast.planner.calendar

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.fourthbreakfast.planner.PlannerApp
import dev.fourthbreakfast.planner.core.CalendarSubscriptions
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Feed work is independent of device sync and never starts Syncthing. Android may defer scheduled refreshes. */
class CalendarRefresh(private val context: Context, private val calendars: CalendarSubscriptions, private val scope: CoroutineScope) {
    private var foreground: Job? = null

    fun schedule(hasLocalSources: Boolean) {
        val manager = WorkManager.getInstance(context)
        if (!hasLocalSources) {
            manager.cancelUniqueWork(WORK)
            return
        }
        val request = PeriodicWorkRequestBuilder<CalendarWorker>(30, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        manager.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun onInSight() {
        if (foreground?.isActive == true) return
        foreground = scope.launch {
            while (true) {
                calendars.refreshDue()
                delay(CalendarSubscriptions.REFRESH_MILLIS)
            }
        }
    }

    fun onOutOfSight() {
        foreground?.cancel()
        foreground = null
    }

    private companion object {
        const val WORK = "planner-calendar-refresh"
    }
}

class CalendarWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val calendars = (applicationContext as PlannerApp).data.calendars
        // A large collection of slow feeds must not hold background work indefinitely.
        return if (withTimeoutOrNull(2 * 60_000L) {
                calendars.refreshDue()
                true
            } == true
        ) {
            Result.success()
        } else {
            Result.retry()
        }
    }
}
