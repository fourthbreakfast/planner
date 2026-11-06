package dev.fourthbreakfast.planner.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.fourthbreakfast.planner.PlannerApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Alarms are lost when the phone restarts, and move when the time or the time zone changes, so they are set again. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_DATE_CHANGED,
            -> Unit
            else -> return
        }
        val app = context.applicationContext as PlannerApp
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                app.resyncReminders().join()
                app.refreshWidgets().join()
            } finally {
                pending.finish()
            }
        }
    }
}
