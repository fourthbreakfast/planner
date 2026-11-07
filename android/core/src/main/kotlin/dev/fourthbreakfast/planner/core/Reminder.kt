package dev.fourthbreakfast.planner.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The reminders a person can pick, in minutes before the start. Null is no reminder. */
val REMINDER_CHOICES: List<Int?> = listOf(null, 0, 10, 15, 30, 60, 120, 24 * 60, 7 * 24 * 60)

/** Future alarms, including the next occurrence after a repeating reminder has just fired. */
fun scheduledReminders(events: List<Event>, now: Long, zone: ZoneId = ZoneId.systemDefault()): List<Pair<Event, Long>> = events
    .filterNot { it.inBin }
    .mapNotNull { event -> reminderAt(event, zone, from = now + 1)?.takeIf { it > now }?.let { event to it } }

/**
 * The moment, in milliseconds since 1970, to remind about an event, or null when it has no reminder. The day and
 * time are read as written on the wall clock of [zone], so a reminder follows the phone to a new time zone.
 */
fun reminderAt(event: Event, zone: ZoneId = ZoneId.systemDefault(), from: Long = System.currentTimeMillis()): Long? {
    val start = event.minute ?: return null
    val before = event.reminder ?: return null
    val offset = before * 60_000L
    fun at(day: Long) = LocalDate.ofEpochDay(day).atTime(start / 60, start % 60).atZone(zone).toInstant().toEpochMilli() - offset
    if (event.repeat == Repeat.NONE) return at(event.day)
    // a repeating event reminds about its next time whose reminder is not past [from]
    // Search where the event starts after counting forward by the offset, even for reminders years ahead.
    val fromDay = Instant.ofEpochMilli(from + offset).atZone(zone).toLocalDate().toEpochDay() - 1
    return occurrences(event, fromDay, fromDay + 800).map { at(it.start) }.firstOrNull { it >= from }
}
