package dev.fourthbreakfast.planner.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.DateFormat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dev.fourthbreakfast.planner.MainActivity
import dev.fourthbreakfast.planner.PlannerApp
import dev.fourthbreakfast.planner.R
import dev.fourthbreakfast.planner.agenda.timeText
import dev.fourthbreakfast.planner.core.reminderAt
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val CHANNEL = "reminders"
private const val SNOOZE_MINUTES = 10
private const val ON_TIME_MILLIS = 30 * 60_000L

/**
 * Rings when an alarm from [Reminders] goes off: puts the event up as a notification. Touching it opens the event,
 * one button rings again in ten minutes and the other puts it away.
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(ID) ?: return
        val manager = NotificationManagerCompat.from(context)
        if (intent.action == DISMISS) {
            manager.cancel(id.hashCode())
            return
        }
        if (intent.action == SNOOZE) {
            manager.cancel(id.hashCode())
            Reminders(context).snooze(intent, System.currentTimeMillis() + SNOOZE_MINUTES * 60_000L)
            return
        }
        // sync may have moved or deleted the event since the alarm was set while the app was not running: it only rings
        // for an event that is still there, at the time it is still meant to
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val event = (context.applicationContext as PlannerApp).events.all.first().firstOrNull { it.id == id }
                val due = event?.takeIf { !it.inBin }?.let(::reminderAt)
                val snoozed = intent.getBooleanExtra(SNOOZED, false)
                if (due != null && (snoozed || abs(due - System.currentTimeMillis()) < ON_TIME_MILLIS)) show(context, intent, id, manager)
            } finally {
                pending.finish()
            }
        }
    }

    private fun show(context: Context, intent: Intent, id: String, manager: NotificationManagerCompat) {
        val title = intent.getStringExtra(TITLE) ?: return
        val time = timeText(intent.getIntExtra(MINUTE, 0), DateFormat.is24HourFormat(context))
        val place = intent.getStringExtra(PLACE).orEmpty()
        if (!manager.areNotificationsEnabled()) return
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.reminders_channel), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.reminders_channel_text)
            },
        )
        val open = PendingIntent.getActivity(
            context,
            id.hashCode(),
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(MainActivity.OPEN_EVENT, id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val snooze = PendingIntent.getBroadcast(
            context,
            id.hashCode(),
            Intent(context, ReminderReceiver::class.java)
                .setAction(SNOOZE)
                .setData(Uri.parse("planner://event/$id/snoozed"))
                .putExtras(intent.extras ?: return),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val dismiss = PendingIntent.getBroadcast(
            context,
            id.hashCode(),
            Intent(context, ReminderReceiver::class.java)
                .setAction(DISMISS)
                .setData(Uri.parse("planner://event/$id/dismissed"))
                .putExtra(ID, id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_planner)
            .setContentTitle(title)
            .setContentText(if (place.isEmpty()) time else "$time, $place")
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setAutoCancel(true)
            .setContentIntent(open)
            .addAction(0, context.getString(R.string.snooze), snooze)
            .addAction(0, context.getString(R.string.dismiss), dismiss)
            .build()
        manager.notify(id.hashCode(), notification)
    }

    companion object {
        const val ID = "id"
        const val TITLE = "title"
        const val MINUTE = "minute"
        const val PLACE = "place"
        const val SNOOZE = "snooze"
        const val SNOOZED = "snoozed"
        const val DISMISS = "dismiss"
    }
}
