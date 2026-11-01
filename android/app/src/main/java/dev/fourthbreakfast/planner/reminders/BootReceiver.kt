package dev.fourthbreakfast.planner.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.fourthbreakfast.planner.PlannerApp

/** Alarms are lost when the phone restarts, and move when the time or the time zone changes, so they are set again. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        (context.applicationContext as PlannerApp).resyncReminders()
    }
}
