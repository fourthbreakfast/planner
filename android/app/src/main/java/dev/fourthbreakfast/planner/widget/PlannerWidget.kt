package dev.fourthbreakfast.planner.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.format.DateFormat
import android.view.View
import android.widget.RemoteViews
import dev.fourthbreakfast.planner.MainActivity
import dev.fourthbreakfast.planner.PlannerApp
import dev.fourthbreakfast.planner.R
import dev.fourthbreakfast.planner.agenda.dateText
import dev.fourthbreakfast.planner.agenda.timeText
import dev.fourthbreakfast.planner.core.Event
import dev.fourthbreakfast.planner.core.Task
import dev.fourthbreakfast.planner.core.widgetLines
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private val LINES = intArrayOf(R.id.line0, R.id.line1, R.id.line2, R.id.line3, R.id.line4, R.id.line5, R.id.line6, R.id.line7)

/** The home screen widget: the day, today's events and the open tasks, as plain console lines. A touch opens the app. */
class PlannerWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        refresh(context)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        refresh(context)
    }

    private fun refresh(context: Context) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                (context.applicationContext as PlannerApp).refreshWidgets().join()
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        /** Draws every widget on the home screen from what the app holds now. */
        fun update(context: Context, events: List<Event>, tasks: List<Task>) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, PlannerWidget::class.java))
            if (ids.isEmpty()) return
            val day = LocalDate.now().toEpochDay()
            val is24Hour = DateFormat.is24HourFormat(context)
            val open = tasks.count { !it.inBin && !it.done && it.parentId == null }
            val tap = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            ids.forEach { id ->
                // as many lines as the widget is tall: a header, then a line for each 18dp
                val height = manager.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110)
                val rows = ((height - 52) / 18).coerceIn(1, LINES.size)
                val lines = widgetLines(
                    events,
                    tasks,
                    day,
                    rows,
                    context.getString(R.string.all_day),
                    { context.getString(R.string.widget_more, it) },
                ) { timeText(it, is24Hour) }
                val views = RemoteViews(context.packageName, R.layout.widget)
                views.setTextViewText(R.id.date, dateText(day, day))
                views.setTextViewText(R.id.count, context.getString(R.string.widget_open, open))
                LINES.forEachIndexed { index, view ->
                    val line = lines.getOrNull(index) ?: if (index == 0) context.getString(R.string.widget_nothing) else null
                    views.setTextViewText(view, line.orEmpty())
                    views.setViewVisibility(view, if (line != null) View.VISIBLE else View.GONE)
                }
                views.setOnClickPendingIntent(R.id.widget_root, tap)
                manager.updateAppWidget(id, views)
            }
        }
    }
}
