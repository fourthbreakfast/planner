package dev.fourthbreakfast.planner.desktop

import dev.fourthbreakfast.planner.agenda.timeText
import dev.fourthbreakfast.planner.core.Event
import dev.fourthbreakfast.planner.core.reminderAt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

// a reminder that fell due while the computer slept still shows when it wakes, within a few hours
private const val LATE_MILLIS = 6 * 60 * 60_000L

/**
 * Reminders on a computer: while the app runs, in its window or in the tray, each event's reminder shows as a Windows
 * notification at its time through [notify]. Nothing rings while the app is quit; the phone keeps doing that.
 */
class DesktopReminders(
    private val events: Flow<List<Event>>,
    private val scope: CoroutineScope,
    private val is24Hour: Boolean,
    private val notify: (title: String, text: String) -> Unit,
) {
    fun begin() {
        scope.launch {
            val shown = HashSet<String>()
            events.collectLatest { all ->
                while (true) {
                    val now = System.currentTimeMillis()
                    for (event in all) {
                        if (event.inBin) continue
                        val at = reminderAt(event, from = now - LATE_MILLIS) ?: continue
                        val key = "${event.id}@$at"
                        if (key in shown || at !in (now - LATE_MILLIS)..now) continue
                        shown += key
                        val time = event.minute?.let { timeText(it, is24Hour) }.orEmpty()
                        notify(event.title, listOf(time, event.location).filter { it.isNotBlank() }.joinToString(", "))
                    }
                    delay(15_000)
                }
            }
        }
    }
}
