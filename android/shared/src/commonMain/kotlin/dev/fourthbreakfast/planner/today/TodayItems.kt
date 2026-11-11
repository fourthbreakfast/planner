package dev.fourthbreakfast.planner.today

import dev.fourthbreakfast.planner.core.Event
import dev.fourthbreakfast.planner.core.Occurrence
import dev.fourthbreakfast.planner.core.Task
import dev.fourthbreakfast.planner.core.forDisplay
import dev.fourthbreakfast.planner.core.occurrences

/** Up to four current/upcoming occurrences; completed linked tasks never reappear as appointments. */
fun upNext(events: List<Event>, tasks: List<Task>, day: Long, minute: Int): List<Occurrence> =
    occurrences(events.filterNot { it.inBin }.map { it.forDisplay() }, day, day + 7)
        .filter { it.start > day || it.end > day || it.event.minute == null || (it.event.endMinute ?: it.event.minute ?: 0) >= minute }
        .filter { occurrence -> occurrence.event.taskId?.let { id -> tasks.firstOrNull { it.id == id }?.done } != true }
        .take(4)
