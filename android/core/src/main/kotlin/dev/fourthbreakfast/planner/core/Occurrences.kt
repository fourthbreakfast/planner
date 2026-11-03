package dev.fourthbreakfast.planner.core

import java.time.LocalDate

/** One time an event happens: from [start] to [end], both days, the same day for most events. */
data class Occurrence(val event: Event, val start: Long) {
    val end: Long get() = start + event.span

    val key: String get() = "${event.id}@$start"
}

/** How many days after its first day an event ends: 0 for an event of one day. */
val Event.span: Long get() = ((endDay ?: day) - day).coerceAtLeast(0)

/** Whether a time of [event] starts on [day]: the first day, or a day its repeat lands on, not left out, not past its end. */
fun startsOn(event: Event, day: Long): Boolean {
    if (day < event.day || day in event.skipDays) return false
    if (event.repeatUntil != null && day > event.repeatUntil) return false
    if (day == event.day) return true
    val first = LocalDate.ofEpochDay(event.day)
    val date = LocalDate.ofEpochDay(day)
    return when (event.repeat) {
        Repeat.NONE -> false
        Repeat.DAILY -> true
        Repeat.WEEKLY -> (day - event.day) % 7 == 0L
        // a month without the day, the 31st in april, is left out, as calendars do
        Repeat.MONTHLY -> date.dayOfMonth == first.dayOfMonth
        // the 29th of february comes back only in leap years
        Repeat.YEARLY -> date.dayOfMonth == first.dayOfMonth && date.month == first.month
    }
}

/** Every time [event] happens that touches the days from [from] to [to], soonest first. */
fun occurrences(event: Event, from: Long, to: Long): List<Occurrence> {
    if (event.repeat == Repeat.NONE) {
        return if (event.day <= to && event.day + event.span >= from) listOf(Occurrence(event, event.day)) else emptyList()
    }
    val first = maxOf(event.day, from - event.span)
    val last = minOf(to, event.repeatUntil ?: to)
    return (first..last).filter { startsOn(event, it) }.map { Occurrence(event, it) }
}

/** Every time any of [events] happens that touches the days from [from] to [to], soonest first, whole days before times. */
fun occurrences(events: List<Event>, from: Long, to: Long): List<Occurrence> = events
    .flatMap { occurrences(it, from, to) }
    .sortedWith(compareBy<Occurrence>({ it.start }, { it.event.minute ?: -1 }, { it.event.createdAt }))

/** The next time [event] starts on [day] or after, looking no further than [within] days, or null. */
fun nextStart(event: Event, day: Long, within: Long = 800): Long? = occurrences(event, day, day + within).firstOrNull { it.start >= day }?.start
