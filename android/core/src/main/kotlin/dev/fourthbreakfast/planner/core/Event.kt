package dev.fourthbreakfast.planner.core

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
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
    /** A scheduled task. Completion belongs to the task, not to a second copy of it. */
    val taskId: String? = null,
    /** Subscription provenance is synced; the private feed URL is kept only on the refreshing device. */
    val calendarSourceId: String? = null,
    val calendarName: String? = null,
    val calendarGeneration: Long = 0,
    /** Absolute subscribed times; storage/sync wall fields are canonical UTC. Floating/all-day dates leave these null. */
    val calendarStartInstant: Long? = null,
    val calendarEndInstant: Long? = null,
) {
    val inBin: Boolean get() = deletedAt != null
    val readOnly: Boolean get() = calendarSourceId != null
}

/** Display projection never mutates the canonical event sent through sync. Manual/floating times stay as written. */
fun Event.forDisplay(zone: ZoneId = ZoneId.systemDefault()): Event {
    val instant = calendarStartInstant?.takeIf { readOnly } ?: return this
    val start = Instant.ofEpochMilli(instant).atZone(zone).toLocalDateTime()
    val end = calendarEndInstant?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDateTime() }
    val firstDay = start.toLocalDate().toEpochDay()
    val lastDay = end?.let { it.toLocalDate().toEpochDay() - if (it.toLocalTime() == LocalTime.MIDNIGHT) 1 else 0 }
    return copy(
        day = firstDay,
        minute = start.hour * 60 + start.minute,
        endDay = lastDay?.takeIf { it > firstDay },
        endMinute = end?.let { it.hour * 60 + it.minute },
    )
}

/** How an event comes back. */
enum class Repeat { NONE, DAILY, WEEKLY, MONTHLY, YEARLY }

/** Soonest first. Within a day, the whole-day events come before the timed ones. Ties keep the order they were made in. */
fun sortedEvents(events: List<Event>): List<Event> = events.sortedWith(compareBy<Event>({ it.day }, { it.minute ?: -1 }, { it.createdAt }))
