package dev.fourthbreakfast.planner.core

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

/**
 * Reads the events out of an iCalendar file (.ics), as exported by other calendars. What the agenda has a place for
 * comes across: the title, the day and time, where it ends, the place, the notes, a reminder, and repeating by day,
 * week, month or year with its last day and the days left out. A repeat with anything more to it, every other week or
 * the first monday of a month, comes across as the single event it starts with.
 *
 * Every event gets the same id each time the same file is read, so reading a file twice adds nothing the second time.
 * Times are turned into the person's own time zone, [zone], as written there from then on.
 *
 * @throws BackupException when the text is not a calendar at all.
 */
fun parseIcs(text: String, now: Long, zone: ZoneId = ZoneId.systemDefault()): List<Event> {
    if (!text.contains("BEGIN:VCALENDAR", ignoreCase = true)) throw BackupException("not a calendar")
    val blocks = eventBlocks(unfold(text))
    val parsed = blocks.mapNotNull { readEvent(it, now, zone) }
    // a changed or cancelled time of a repeating event is left out of the series; a changed one is an event of its own
    val overridden = parsed.filter { it.recurrenceDay != null }.groupBy({ it.uid }, { it.recurrenceDay!! })
    return parsed.mapNotNull { entry ->
        val event = entry.event ?: return@mapNotNull null
        val skips = if (entry.recurrenceDay == null) overridden[entry.uid].orEmpty() else emptyList()
        if (skips.isEmpty()) event else event.copy(skipDays = event.skipDays + skips)
    }
}

internal class Line(val name: String, val params: Map<String, String>, val value: String)

/** One VEVENT: the series it belongs to, the day of the series it stands in for, and the event, or null when cancelled. */
internal class Entry(val uid: String, val recurrenceDay: Long?, val event: Event?)

/** Lines carried on from the line before, which starts with a space or a tab, are put back together. */
internal fun unfold(text: String): List<String> {
    val lines = ArrayList<String>()
    for (raw in text.split("\r\n", "\n", "\r")) {
        if ((raw.startsWith(" ") || raw.startsWith("\t")) && lines.isNotEmpty()) {
            lines[lines.lastIndex] = lines.last() + raw.substring(1)
        } else {
            lines.add(raw)
        }
    }
    return lines
}

internal fun readLine(raw: String): Line? {
    val colon = quoteAwareIndex(raw, ':')
    if (colon <= 0) return null
    val head = raw.substring(0, colon).split(';')
    val params = head.drop(1).mapNotNull { part ->
        val eq = part.indexOf('=')
        if (eq <= 0) null else part.substring(0, eq).uppercase() to part.substring(eq + 1).trim('"')
    }.toMap()
    return Line(head[0].uppercase(), params, raw.substring(colon + 1))
}

/** The first [c] outside double quotes, which is where a parameter's value may hold a colon. */
private fun quoteAwareIndex(text: String, c: Char): Int {
    var quoted = false
    for ((i, ch) in text.withIndex()) {
        if (ch == '"') {
            quoted = !quoted
        } else if (ch == c && !quoted) {
            return i
        }
    }
    return -1
}

/** The lines of every VEVENT, the lines inside an alarm marked apart with the name `ALARM:` before them. */
internal fun eventBlocks(lines: List<String>): List<List<Line>> {
    val blocks = ArrayList<List<Line>>()
    var current: ArrayList<Line>? = null
    var alarm = false
    for (raw in lines) {
        val line = readLine(raw) ?: continue
        when {
            line.name == "BEGIN" && line.value.equals("VEVENT", true) -> current = ArrayList()
            line.name == "END" && line.value.equals("VEVENT", true) -> {
                current?.let(blocks::add)
                current = null
            }
            current == null -> Unit
            line.name == "BEGIN" && line.value.equals("VALARM", true) -> alarm = true
            line.name == "END" && line.value.equals("VALARM", true) -> alarm = false
            alarm -> current.add(Line("ALARM:" + line.name, line.params, line.value))
            else -> current.add(line)
        }
    }
    return blocks
}

private class When(val day: Long, val minute: Int?)

/** A date, or a date and time, as the calendar writes them, in the person's own time zone. */
private fun readWhen(line: Line, zone: ZoneId): When? {
    val v = line.value.trim()
    return try {
        if (line.params["VALUE"] == "DATE" || (v.length == 8 && !v.contains('T'))) {
            When(LocalDate.of(v.substring(0, 4).toInt(), v.substring(4, 6).toInt(), v.substring(6, 8).toInt()).toEpochDay(), null)
        } else {
            val local = LocalDateTime.of(
                v.substring(0, 4).toInt(),
                v.substring(4, 6).toInt(),
                v.substring(6, 8).toInt(),
                v.substring(9, 11).toInt(),
                v.substring(11, 13).toInt(),
            )
            val from = when {
                v.endsWith("Z") -> ZoneOffset.UTC
                line.params["TZID"] != null -> runCatching { ZoneId.of(line.params["TZID"]!!) }.getOrDefault(zone)
                else -> zone
            }
            val there = local.atZone(from).withZoneSameInstant(zone).toLocalDateTime()
            When(there.toLocalDate().toEpochDay(), there.hour * 60 + there.minute)
        }
    } catch (_: RuntimeException) {
        null
    }
}

private fun unescape(text: String): String {
    val out = StringBuilder()
    var i = 0
    while (i < text.length) {
        val c = text[i]
        if (c == '\\' && i + 1 < text.length) {
            out.append(if (text[i + 1] == 'n' || text[i + 1] == 'N') '\n' else text[i + 1])
            i += 2
        } else {
            out.append(c)
            i++
        }
    }
    return out.toString()
}

/** "-PT15M", "-PT1H30M", "-P1D" before the start, as minutes, or null for anything else. */
private fun readTrigger(value: String): Int? {
    val m = Regex("^-P(?:(\\d+)W)?(?:(\\d+)D)?(?:T(?:(\\d+)H)?(?:(\\d+)M)?(?:\\d+S)?)?$").matchEntire(value.trim()) ?: return null
    val (w, d, h, min) = m.destructured
    return (w.toIntOrNull() ?: 0) * 7 * 1440 + (d.toIntOrNull() ?: 0) * 1440 + (h.toIntOrNull() ?: 0) * 60 + (min.toIntOrNull() ?: 0)
}

internal fun readEvent(lines: List<Line>, now: Long, zone: ZoneId): Entry? {
    fun first(name: String) = lines.firstOrNull { it.name == name }
    val uid = first("UID")?.value?.trim().orEmpty()
    if (first("STATUS")?.value.equals("CANCELLED", true)) {
        // one cancelled time of a repeating event takes that day out of the series; a cancelled event is left out
        val day = first("RECURRENCE-ID")?.let { readWhen(it, zone) }?.day ?: return null
        return if (uid.isEmpty()) null else Entry(uid, day, null)
    }
    val title = unescape(first("SUMMARY")?.value.orEmpty()).trim()
    if (title.isEmpty()) return null
    val start = first("DTSTART")?.let { readWhen(it, zone) } ?: return null
    val end = first("DTEND")?.let { readWhen(it, zone) }

    var endDay: Long? = null
    var endMinute: Int? = null
    if (end != null) {
        if (start.minute == null) {
            // a whole-day end is the day after the last one
            endDay = (end.day - 1).takeIf { it > start.day }
        } else if (end.minute != null) {
            if (end.day == start.day) {
                endMinute = end.minute.takeIf { it > start.minute }
            } else {
                endDay = (if (end.minute == 0) end.day - 1 else end.day).takeIf { it > start.day }
            }
        }
    }

    var repeat = Repeat.NONE
    var until: Long? = null
    first("RRULE")?.let { rule ->
        val parts = rule.value.split(';').mapNotNull { p -> p.split('=', limit = 2).takeIf { it.size == 2 } }.associate { it[0].uppercase() to it[1] }
        val frequency = when (parts["FREQ"]?.uppercase()) {
            "DAILY" -> Repeat.DAILY
            "WEEKLY" -> Repeat.WEEKLY
            "MONTHLY" -> Repeat.MONTHLY
            "YEARLY" -> Repeat.YEARLY
            else -> null
        }
        val plain = (parts["INTERVAL"]?.toIntOrNull() ?: 1) == 1 &&
            parts.keys.none { it in setOf("BYSETPOS", "BYMONTHDAY", "BYYEARDAY", "BYWEEKNO", "BYHOUR", "BYMINUTE") } &&
            sameDayRule(parts, frequency, start.day)
        if (frequency != null && plain) {
            repeat = frequency
            val count = parts["COUNT"]?.toIntOrNull()
            until = when {
                parts["UNTIL"] != null -> readWhen(Line("UNTIL", emptyMap(), parts["UNTIL"]!!), zone)?.day
                count != null -> lastOfCount(start.day, frequency, count)
                else -> null
            }
            if (until != null && until!! < start.day) until = null
        }
    }

    val skips = lines.filter { it.name == "EXDATE" }
        .flatMap { line -> line.value.split(',').mapNotNull { readWhen(Line("EXDATE", line.params, it), zone)?.day } }
        .toSet()

    val trigger = lines.firstOrNull { it.name == "ALARM:TRIGGER" && it.params["RELATED"]?.uppercase() != "END" }
    val reminder = trigger?.let { readTrigger(it.value) }?.takeIf { start.minute != null }

    val recurrence = first("RECURRENCE-ID")?.let { readWhen(it, zone) }
    val key = when {
        uid.isNotEmpty() -> "$uid|${first("RECURRENCE-ID")?.value.orEmpty()}"
        else -> "$title|${first("DTSTART")?.value.orEmpty()}"
    }
    val event = Event(
        id = UUID.nameUUIDFromBytes("ics:$key".toByteArray()).toString(),
        title = title,
        day = start.day,
        minute = start.minute,
        endMinute = endMinute,
        reminder = reminder,
        location = unescape(first("LOCATION")?.value.orEmpty()).trim(),
        notes = unescape(first("DESCRIPTION")?.value.orEmpty()).trim(),
        createdAt = now,
        endDay = endDay,
        repeat = repeat,
        repeatUntil = until,
        skipDays = skips,
    )
    return Entry(uid.ifEmpty { key }, recurrence?.day, event)
}

/** A weekly rule on the weekday it starts on, or a rule with no days named, is the plain repeat the agenda has. */
private fun sameDayRule(parts: Map<String, String>, frequency: Repeat?, startDay: Long): Boolean {
    val days = parts["BYDAY"]?.split(',') ?: return true
    if (frequency != Repeat.WEEKLY || days.size != 1) return false
    val names = listOf("MO", "TU", "WE", "TH", "FR", "SA", "SU")
    return days[0].uppercase() == names[LocalDate.ofEpochDay(startDay).dayOfWeek.ordinal]
}

/** The day of the last time, for a repeat that stops after [count] times. */
private fun lastOfCount(day: Long, frequency: Repeat, count: Int): Long? {
    if (count < 1) return null
    val first = LocalDate.ofEpochDay(day)
    val n = (count - 1).toLong()
    return when (frequency) {
        Repeat.DAILY -> day + n
        Repeat.WEEKLY -> day + 7 * n
        // the same date each month or year; a month without that date leaves its time out, which a longer run makes up for
        Repeat.MONTHLY -> generateSequence(0L) {
            it + 1
        }.map { first.plusMonths(it) }.filter { it.dayOfMonth == first.dayOfMonth }.drop(n.toInt()).first().toEpochDay()
        Repeat.YEARLY -> generateSequence(0L) {
            it + 1
        }.map { first.plusYears(it) }.filter { it.dayOfMonth == first.dayOfMonth }.drop(n.toInt()).first().toEpochDay()
        Repeat.NONE -> null
    }
}
