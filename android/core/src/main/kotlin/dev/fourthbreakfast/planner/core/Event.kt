package dev.fourthbreakfast.planner.core

import java.util.UUID

/**
 * Something on the agenda. The day is the number of days since 1970-01-01 in the person's own calendar
 * and the time is minutes since midnight, both as written, so an event never moves when the phone changes
 * time zone. No time means it takes the whole day, and an end time only means something after a start.
 */
data class Event(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val day: Long,
    val minute: Int? = null,
    val endMinute: Int? = null,
    val location: String = "",
    val notes: String = "",
    val createdAt: Long,
    val updatedAt: Long = createdAt,
    /** Set while the event is in the bin. */
    val deletedAt: Long? = null,
) {
    val inBin: Boolean get() = deletedAt != null
}

/** Soonest first. Within a day, the whole-day events come before the timed ones. Ties keep the order they were made in. */
fun sortedEvents(events: List<Event>): List<Event> =
    events.sortedWith(compareBy<Event>({ it.day }, { it.minute ?: -1 }, { it.createdAt }))
