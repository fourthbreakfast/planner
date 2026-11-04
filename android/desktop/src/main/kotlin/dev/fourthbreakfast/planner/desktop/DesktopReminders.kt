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
            val due = DueReminders()
            events.collectLatest { all ->
                while (true) {
                    for (event in due.at(all, System.currentTimeMillis())) {
                        val time = event.minute?.let { timeText(it, is24Hour) }.orEmpty()
                        notify(event.title, listOf(time, event.location).filter { it.isNotBlank() }.joinToString(", "))
                    }
                    delay(15_000)
                }
            }
        }
    }
}

/**
 * Which reminders are due, each shown once: a reminder whose time has come within the last [late] milliseconds and was
 * not shown yet. It remembers what it showed only while that could still come due, so it does not grow as the app runs.
 */
class DueReminders(private val late: Long = LATE_MILLIS) {
    // each shown reminder, by event and time, with that time
    private val shown = HashMap<String, Long>()

    /** The events out of [events] whose reminder is due at [now], and marks them shown. */
    fun at(events: List<Event>, now: Long): List<Event> {
        shown.values.removeAll { it < now - late }
        return events.filter { event ->
            if (event.inBin) return@filter false
            val at = reminderAt(event, from = now - late) ?: return@filter false
            at in (now - late)..now && shown.putIfAbsent("${event.id}@$at", at) == null
        }
    }

    /** How many shown reminders it remembers, for the tests. */
    internal val remembered: Int get() = shown.size
}
