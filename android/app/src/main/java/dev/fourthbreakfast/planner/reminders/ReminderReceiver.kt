package dev.fourthbreakfast.planner.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dev.fourthbreakfast.planner.MainActivity
import dev.fourthbreakfast.planner.R
import dev.fourthbreakfast.planner.agenda.timeText

private const val CHANNEL = "reminders"

/** Rings when an alarm from [Reminders] goes off: puts the event up as a notification. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra(TITLE) ?: return
        val time = timeText(intent.getIntExtra(MINUTE, 0), DateFormat.is24HourFormat(context))
        val place = intent.getStringExtra(PLACE).orEmpty()
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.reminders_channel), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.reminders_channel_text)
            },
        )
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_planner)
            .setContentTitle(title)
            .setContentText(if (place.isEmpty()) time else "$time, $place")
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        manager.notify(intent.data.toString().hashCode(), notification)
    }

    companion object {
        const val TITLE = "title"
        const val MINUTE = "minute"
        const val PLACE = "place"
    }
}
