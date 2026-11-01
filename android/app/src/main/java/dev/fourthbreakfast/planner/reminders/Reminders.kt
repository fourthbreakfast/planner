package dev.fourthbreakfast.planner.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import dev.fourthbreakfast.planner.core.Event
import dev.fourthbreakfast.planner.core.reminderAt

private const val PREFS = "reminders"
private const val SCHEDULED = "scheduled"

/** Keeps the phone's alarms in step with the agenda: one alarm for each coming event that has a reminder. */
class Reminders(private val context: Context) {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Sets the alarm of every event that should have one and takes away the others that were set before. */
    fun sync(events: List<Event>, now: Long = System.currentTimeMillis()) {
        val coming = events.filter { !it.inBin }.mapNotNull { event ->
            reminderAt(event)?.takeIf { it > now }?.let { event to it }
        }
        val keep = coming.map { (event, at) ->
            set(event, at)
            event.id
        }.toSet()
        prefs.getStringSet(SCHEDULED, emptySet()).orEmpty().filter { it !in keep }.forEach(::cancel)
        prefs.edit().putStringSet(SCHEDULED, keep).apply()
    }

    private fun intentFor(id: String) = Intent(context, ReminderReceiver::class.java).setData(Uri.parse("planner://event/$id"))

    private fun pending(id: String, intent: Intent = intentFor(id), flags: Int = 0) =
        PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or flags)

    private fun set(event: Event, at: Long) {
        val intent = intentFor(event.id)
            .putExtra(ReminderReceiver.ID, event.id)
            .putExtra(ReminderReceiver.TITLE, event.title)
            .putExtra(ReminderReceiver.MINUTE, event.minute ?: 0)
            .putExtra(ReminderReceiver.PLACE, event.location)
        val pending = pending(event.id, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        // exact when the phone allows it, otherwise as close as it will go
        if (alarms.canScheduleExactAlarms()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        }
    }

    /** Rings the same reminder again later, from the alarm that just went off. */
    fun snooze(source: Intent, at: Long) {
        val id = source.getStringExtra(ReminderReceiver.ID) ?: return
        val intent = Intent(context, ReminderReceiver::class.java)
            .setData(Uri.parse("planner://event/$id/snooze"))
            .putExtras(source.extras ?: return)
        val pending = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        if (alarms.canScheduleExactAlarms()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        }
    }

    private fun cancel(id: String) {
        alarms.cancel(pending(id, flags = PendingIntent.FLAG_NO_CREATE).let { it ?: return })
    }
}
