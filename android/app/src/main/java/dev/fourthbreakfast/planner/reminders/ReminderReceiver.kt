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
import dev.fourthbreakfast.planner.R
import dev.fourthbreakfast.planner.agenda.timeText

private const val CHANNEL = "reminders"
private const val SNOOZE_MINUTES = 10

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
        const val DISMISS = "dismiss"
    }
}
