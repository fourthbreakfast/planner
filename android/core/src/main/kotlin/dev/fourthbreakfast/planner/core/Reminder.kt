package dev.fourthbreakfast.planner.core

import java.time.LocalDate
import java.time.ZoneId

/** The reminders a person can pick, in minutes before the start. Null is no reminder. */
val REMINDER_CHOICES: List<Int?> = listOf(null, 0, 10, 60)

/**
 * The moment, in milliseconds since 1970, to remind about an event, or null when it has no reminder. The day and
 * time are read as written on the wall clock of [zone], so a reminder follows the phone to a new time zone.
 */
fun reminderAt(event: Event, zone: ZoneId = ZoneId.systemDefault()): Long? {
    val start = event.minute ?: return null
    val before = event.reminder ?: return null
    val local = LocalDate.ofEpochDay(event.day).atTime(start / 60, start % 60)
    return local.atZone(zone).toInstant().toEpochMilli() - before * 60_000L
}
