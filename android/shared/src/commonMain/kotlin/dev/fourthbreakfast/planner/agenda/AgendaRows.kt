package dev.fourthbreakfast.planner.agenda

import dev.fourthbreakfast.planner.core.Event
import dev.fourthbreakfast.planner.core.Repeat
import dev.fourthbreakfast.planner.core.occurrences
import dev.fourthbreakfast.planner.core.sortedEvents
import dev.fourthbreakfast.planner.core.span

sealed interface AgendaRow {
    val key: String
}

/** The heading above a day's events. */
data class DayRow(val day: Long, val earlier: Boolean) : AgendaRow {
    override val key get() = "day-$day-${if (earlier) "earlier" else "ahead"}"
}

/** One time of an event, listed on [day]. */
data class EventItem(val event: Event, val earlier: Boolean, val day: Long = event.day) : AgendaRow {
    override val key get() = "${event.id}@$day"
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
    // a repeating event is listed each time it comes in the next weeks; one that goes on for days and began before today
    // is listed under today while it lasts
    val furthest = events.filter { it.repeat == Repeat.NONE }.maxOfOrNull { it.day } ?: today
    val ahead = occurrences(events, today, maxOf(furthest, today + REPEAT_AHEAD_DAYS))
        .filter { it.event.repeat == Repeat.NONE || it.start <= today + REPEAT_AHEAD_DAYS }
        .groupBy { maxOf(it.start, today) }
    val behind = sortedEvents(events.filter { it.repeat == Repeat.NONE && it.day + it.span < today })
    val rows = ArrayList<AgendaRow>(events.size * 2 + 1)
    ahead.toSortedMap().forEach { (day, onThatDay) ->
        rows += DayRow(day, earlier = false)
        onThatDay.forEach { rows += EventItem(it.event, earlier = false, day = it.start) }
    }
    if (behind.isNotEmpty()) {
        rows += EarlierRow
        dayDescending(behind).groupBy { it.day }.forEach { (day, onThatDay) ->
            rows += DayRow(day, earlier = true)
            onThatDay.forEach { rows += EventItem(it, earlier = true) }
        }
    }
    return rows
}

/** Newest day first, but each day keeps its events in the order they happen. */
private fun dayDescending(events: List<Event>): List<Event> =
    events.groupBy { it.day }.toSortedMap(compareByDescending { it }).values.flatMap { sortedEvents(it) }

/** How many events are going on today or still to come, a repeating one counted once. */
fun upcomingCount(events: List<Event>, today: Long): Int = events.count { occurrences(it, today, today + 800).isNotEmpty() }

/** How far ahead the list shows each time of a repeating event. */
const val REPEAT_AHEAD_DAYS = 60L

/** The days of [days] that some time of an event covers, for the dots of the month. */
fun busyDays(events: List<Event>, from: Long, to: Long): Set<Long> = occurrences(events, from, to).flatMapTo(HashSet()) { (it.start..it.end).toList() }
