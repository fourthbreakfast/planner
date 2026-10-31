package dev.fourthbreakfast.planner.agenda

import dev.fourthbreakfast.planner.core.Event
import dev.fourthbreakfast.planner.core.sortedEvents

sealed interface AgendaRow {
    val key: String
}

/** The heading above a day's events. */
data class DayRow(val day: Long, val earlier: Boolean) : AgendaRow {
    override val key get() = "day-$day-${if (earlier) "earlier" else "ahead"}"
}

data class EventItem(val event: Event, val earlier: Boolean) : AgendaRow {
    override val key get() = event.id
}

/** The line between what is ahead and what already happened. */
data object EarlierRow : AgendaRow {
    override val key get() = "earlier"
}

/**
 * Today and what is ahead, soonest first and grouped by day. What already happened follows under a divider,
 * most recent first, so it never pushes the next event down the screen.
 */
fun agendaRows(events: List<Event>, today: Long): List<AgendaRow> {
    val (ahead, behind) = sortedEvents(events).partition { it.day >= today }
    val rows = ArrayList<AgendaRow>(events.size * 2 + 1)
    fun addDays(list: List<Event>, earlier: Boolean) {
        list.groupBy { it.day }.forEach { (day, onThatDay) ->
            rows += DayRow(day, earlier)
            onThatDay.forEach { rows += EventItem(it, earlier) }
        }
    }
    addDays(ahead, earlier = false)
    if (behind.isNotEmpty()) {
        rows += EarlierRow
        addDays(dayDescending(behind), earlier = true)
    }
    return rows
}

/** Newest day first, but each day keeps its events in the order they happen. */
private fun dayDescending(events: List<Event>): List<Event> =
    events.groupBy { it.day }.toSortedMap(compareByDescending { it }).values.flatMap { sortedEvents(it) }

/** How many events are today or still ahead. */
fun upcomingCount(events: List<Event>, today: Long): Int = events.count { it.day >= today }
