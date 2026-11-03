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
    /** Minutes before the start to remind, or null for no reminder. Only an event with a start time can have one. */
    val reminder: Int? = null,
    val location: String = "",
    val notes: String = "",
    val createdAt: Long,
    val updatedAt: Long = createdAt,
    /** Set while the event is in the bin. */
    val deletedAt: Long? = null,
    /** The last day of an event that goes on for days, or null when it is one day. */
    val endDay: Long? = null,
    val repeat: Repeat = Repeat.NONE,
    /** The last day it may repeat on, or null to repeat for good. */
    val repeatUntil: Long? = null,
    /** Days a repeating event leaves out: one time of it that was deleted, or changed into an event of its own. */
    val skipDays: Set<Long> = emptySet(),
) {
    val inBin: Boolean get() = deletedAt != null
}

/** How an event comes back. */
enum class Repeat { NONE, DAILY, WEEKLY, MONTHLY, YEARLY }

/** Soonest first. Within a day, the whole-day events come before the timed ones. Ties keep the order they were made in. */
fun sortedEvents(events: List<Event>): List<Event> = events.sortedWith(compareBy<Event>({ it.day }, { it.minute ?: -1 }, { it.createdAt }))
