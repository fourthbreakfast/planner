package dev.fourthbreakfast.planner.core

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.TimeZone
import java.util.UUID
import kotlinx.coroutines.CancellationException
import org.dmfs.rfc5545.DateTime
import org.dmfs.rfc5545.recur.RecurrenceRule

class CalendarFeedException(val reason: CalendarFailure) : Exception()

/** Repeating subscriptions are materialized in this disclosed rolling window; ordinary finite events are retained. */
data class CalendarFeed(val events: List<Event>, val fromDay: Long, val throughDay: Long)

/** RFC 5545 recurrence expansion kept separate from the older, deliberately simple one-time importer. */
fun parseCalendarFeed(text: String, now: Long, zone: ZoneId = ZoneId.systemDefault(), checkCancelled: () -> Unit = {}): CalendarFeed {
    val lines = unfold(text.trimStart('\uFEFF'))
    if (!lines.firstOrNull().equals("BEGIN:VCALENDAR", true) || !lines.lastOrNull { it.isNotBlank() }.equals("END:VCALENDAR", true)) {
        throw CalendarFeedException(CalendarFailure.INVALID_FEED)
    }
    val starts = lines.count { it.equals("BEGIN:VEVENT", true) }
    if (starts != lines.count { it.equals("END:VEVENT", true) } || starts > 2_000) throw CalendarFeedException(CalendarFailure.INVALID_FEED)
    if (starts == 0 && lines.any { it.equals("BEGIN:VTODO", true) || it.equals("BEGIN:VJOURNAL", true) }) {
        throw CalendarFeedException(CalendarFailure.UNSUPPORTED_FEED)
    }
    val blocks = eventBlocks(lines)
    if (starts != blocks.size) throw CalendarFeedException(CalendarFailure.INVALID_FEED)
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val from = today.minusYears(1)
    val through = today.plusYears(2)
    val deadline = System.nanoTime() + 3_000_000_000L
    val aliases = timezoneAliases(lines)
    fun checkBudget() {
        checkCancelled()
        if (System.nanoTime() > deadline) throw CalendarFeedException(CalendarFailure.TOO_LARGE)
    }
    fun first(block: List<Line>, name: String) = block.firstOrNull { it.name == name }
    fun uid(block: List<Line>): String = first(block, "UID")?.value?.trim()?.takeIf { it.isNotEmpty() }
        ?: throw CalendarFeedException(CalendarFailure.INVALID_FEED)
    fun date(line: Line): DateTime = feedDate(line, aliases)
    fun cancelled(block: List<Line>) = first(block, "STATUS")?.value.equals("CANCELLED", true)
    fun template(block: List<Line>): Event {
        first(block, "DTSTART")?.let(::date) ?: throw CalendarFeedException(CalendarFailure.INVALID_FEED)
        first(block, "DTEND")?.let(::date)
        val source = if (first(block, "SUMMARY")?.value.isNullOrBlank()) block + Line("SUMMARY", emptyMap(), "untitled event") else block
        val normalized = source.map { line ->
            val tz = line.params["TZID"]
            if (tz != null && aliases[tz] != null) Line(line.name, line.params + ("TZID" to aliases.getValue(tz)), line.value) else line
        }.filterNot { it.name in setOf("RRULE", "RDATE", "EXDATE", "EXRULE") }
        return readEvent(normalized, now, zone)?.event ?: throw CalendarFeedException(CalendarFailure.INVALID_FEED)
    }
    val byUid = blocks.groupBy(::uid)
    val result = LinkedHashMap<String, Event>()
    fun add(event: Event, key: String) {
        checkBudget()
        if (result.size >= 10_000) throw CalendarFeedException(CalendarFailure.TOO_LARGE)
        val id = UUID.nameUUIDFromBytes("ics-feed:$key".toByteArray(Charsets.UTF_8)).toString()
        result[id] = event.copy(id = id, repeat = Repeat.NONE, repeatUntil = null, skipDays = emptySet())
    }
    for ((uid, entries) in byUid) {
        checkBudget()
        if (entries.any { first(it, "EXRULE") != null || it.any { line -> line.params["RANGE"] != null } }) {
            throw CalendarFeedException(CalendarFailure.UNSUPPORTED_FEED)
        }
        val master = entries.filter { first(it, "RECURRENCE-ID") == null }.maxWithOrNull(
            compareBy<List<Line>>({ first(it, "SEQUENCE")?.value?.toLongOrNull() ?: 0 }, { first(it, "LAST-MODIFIED")?.value.orEmpty() }),
        )
        if (master != null && cancelled(master)) continue
        val overrides = entries.filter { first(it, "RECURRENCE-ID") != null }.groupBy {
            instanceKey(date(first(it, "RECURRENCE-ID")!!))
        }.mapValues { (_, versions) ->
            versions.maxWith(
                compareBy<List<Line>>(
                    { first(it, "SEQUENCE")?.value?.toLongOrNull() ?: 0 },
                    { first(it, "LAST-MODIFIED")?.value.orEmpty() },
                ),
            )
        }
        if (master != null) {
            val base = template(master)
            val start = date(first(master, "DTSTART")!!)
            val rules = master.filter { it.name == "RRULE" }
            if (rules.size > 1) throw CalendarFeedException(CalendarFailure.UNSUPPORTED_FEED)
            val exclusions = master.filter { it.name == "EXDATE" }.flatMap { line ->
                line.value.split(',').map { instanceKey(date(Line(line.name, line.params, it))) }
            }.toSet() + overrides.keys
            val dates = LinkedHashMap<String, DateTime>()
            dates[instanceKey(start)] = start
            val duration = eventDuration(master, start, aliases)
            if (rules.isNotEmpty()) {
                try {
                    val rule = RecurrenceRule(rules.single().value)
                    val iterator = rule.iterator(start)
                    // Include a multi-day event which begins just before the window.
                    val margin = minOf(366, maxOf(1, duration.duration.toDays().toInt() + 1))
                    iterator.fastForward(boundary(start, from.minusDays(margin.toLong()), zone))
                    checkBudget()
                    var count = 0
                    while (iterator.hasNext()) {
                        checkBudget()
                        if (++count > 10_000) throw CalendarFeedException(CalendarFailure.TOO_LARGE)
                        val occurrence = iterator.nextDateTime()
                        val local = occurrenceLocal(occurrence, zone)
                        if (local.toLocalDate() > through) break
                        dates[instanceKey(occurrence)] = occurrence
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: CalendarFeedException) {
                    throw e
                } catch (_: Exception) {
                    throw CalendarFeedException(CalendarFailure.UNSUPPORTED_FEED)
                }
            }
            for (line in master.filter { it.name == "RDATE" }) {
                for (value in line.value.split(',')) {
                    if ('/' in value) throw CalendarFeedException(CalendarFailure.UNSUPPORTED_FEED)
                    val occurrence = date(Line(line.name, line.params, value))
                    dates[instanceKey(occurrence)] = occurrence
                }
            }
            val recurring = rules.isNotEmpty() || master.any { it.name == "RDATE" }
            for ((key, occurrence) in dates) {
                if (key in exclusions) continue
                val event = materialize(base, occurrence, duration, zone)
                if (!recurring || (event.day <= through.toEpochDay() && (event.endDay ?: event.day) >= from.toEpochDay())) {
                    add(event, if (recurring) "$uid|$key" else "$uid|single")
                }
            }
        }
        for ((key, override) in overrides) {
            if (!cancelled(override)) {
                val inherited = master.orEmpty().filter { line ->
                    line.name in setOf("SUMMARY", "DESCRIPTION", "LOCATION") && override.none { it.name == line.name }
                }
                val effective = override + inherited
                val start = first(effective, "DTSTART")?.let(::date) ?: throw CalendarFeedException(CalendarFailure.INVALID_FEED)
                val duration = if (first(effective, "DTEND") == null && first(effective, "DURATION") == null && master != null) {
                    eventDuration(master, date(first(master, "DTSTART")!!), aliases)
                } else {
                    eventDuration(effective, start, aliases)
                }
                add(materialize(template(effective), start, duration, zone), "$uid|$key")
            }
        }
    }
    return CalendarFeed(result.values.toList(), from.toEpochDay(), through.toEpochDay())
}

private fun timezoneAliases(lines: List<String>): Map<String, String> {
    val result = HashMap<String, String>()
    var inZone = false
    var id: String? = null
    var location: String? = null
    for (raw in lines) {
        val line = readLine(raw) ?: continue
        when {
            line.name == "BEGIN" && line.value == "VTIMEZONE" -> {
                inZone = true
                id = null
                location = null
            }
            line.name == "END" && line.value == "VTIMEZONE" -> {
                if (id != null && location != null && runCatching { ZoneId.of(location) }.isSuccess) result[id] = location
                inZone = false
            }
            inZone && line.name == "TZID" -> id = line.value
            inZone && line.name == "X-LIC-LOCATION" -> location = line.value
        }
    }
    return result
}

private fun feedDate(line: Line, aliases: Map<String, String>): DateTime = try {
    val tz = line.params["TZID"]?.let { aliases[it] ?: it }
    if (tz != null) {
        val zone = ZoneId.of(tz) // Never silently substitute GMT for an unknown feed timezone.
        DateTime.parse(TimeZone.getTimeZone(zone), line.value.trim())
    } else {
        DateTime.parse(line.value.trim())
    }
} catch (_: Exception) {
    throw CalendarFeedException(CalendarFailure.UNSUPPORTED_FEED)
}

private fun instanceKey(date: DateTime): String = when {
    date.isAllDay -> "D:$date"
    date.isFloating -> "F:$date"
    else -> "T:${date.timestamp}"
}

private fun boundary(start: DateTime, day: LocalDate, zone: ZoneId): DateTime = when {
    start.isAllDay -> DateTime(day.year, day.monthValue - 1, day.dayOfMonth)
    start.isFloating -> DateTime(day.year, day.monthValue - 1, day.dayOfMonth, 0, 0, 0)
    else -> DateTime(start.timeZone, day.atStartOfDay(zone).toInstant().toEpochMilli())
}

private fun occurrenceLocal(date: DateTime, zone: ZoneId): LocalDateTime = if (date.isAllDay || date.isFloating) {
    LocalDateTime.of(date.year, date.month + 1, date.dayOfMonth, if (date.isAllDay) 0 else date.hours, if (date.isAllDay) 0 else date.minutes)
} else {
    Instant.ofEpochMilli(date.timestamp).atZone(zone).toLocalDateTime()
}

private data class FeedDuration(val duration: Duration, val absolute: Boolean = false)

private fun eventDuration(lines: List<Line>, start: DateTime, aliases: Map<String, String>): FeedDuration {
    val end = lines.firstOrNull { it.name == "DTEND" }?.let { feedDate(it, aliases) }
    val result = if (end != null) {
        if (start.isAllDay != end.isAllDay || start.isFloating != end.isFloating) throw CalendarFeedException(CalendarFailure.INVALID_FEED)
        if (!start.isFloating && start.timeZone != end.timeZone) {
            FeedDuration(Duration.ofMillis(end.timestamp - start.timestamp), absolute = true)
        } else {
            val sourceZone = start.timeZone?.toZoneId() ?: ZoneId.of("UTC")
            FeedDuration(Duration.between(occurrenceLocal(start, sourceZone), occurrenceLocal(end, sourceZone)))
        }
    } else {
        val raw = lines.firstOrNull { it.name == "DURATION" }?.value
        val duration = if (raw == null) {
            if (start.isAllDay) Duration.ofDays(1) else Duration.ZERO
        } else {
            try {
                if (Regex("^\\+?P[0-9]+W$").matches(raw)) {
                    Duration.ofDays(raw.trimStart('+').drop(1).dropLast(1).toLong() * 7)
                } else {
                    Duration.parse(raw.trimStart('+'))
                }
            } catch (_: Exception) {
                throw CalendarFeedException(CalendarFailure.UNSUPPORTED_FEED)
            }
        }
        FeedDuration(duration)
    }
    if (result.duration.isNegative || result.duration > Duration.ofDays(366)) throw CalendarFeedException(CalendarFailure.UNSUPPORTED_FEED)
    return result
}

private fun materialize(base: Event, occurrence: DateTime, duration: FeedDuration, zone: ZoneId): Event {
    val start = occurrenceLocal(occurrence, zone)
    val absolute = !occurrence.isFloating && !occurrence.isAllDay
    val sourceZone = occurrence.timeZone?.toZoneId() ?: zone
    val localEnd = occurrenceLocal(occurrence, sourceZone).plus(duration.duration)
    val endInstant = if (!absolute) {
        null
    } else if (duration.absolute) {
        Instant.ofEpochMilli(occurrence.timestamp).plus(duration.duration)
    } else {
        localEnd.atZone(sourceZone).toInstant()
    }
    val end = endInstant?.atZone(zone)?.toLocalDateTime() ?: localEnd
    val day = start.toLocalDate().toEpochDay()
    val lastDay = if (occurrence.isAllDay ||
        end.toLocalTime() == java.time.LocalTime.MIDNIGHT
    ) {
        end.toLocalDate().toEpochDay() - 1
    } else {
        end.toLocalDate().toEpochDay()
    }
    return base.copy(
        day = day,
        minute = if (occurrence.isAllDay) null else start.hour * 60 + start.minute,
        endDay = lastDay.takeIf { it > day },
        endMinute = if (occurrence.isAllDay || duration.duration.isZero) null else end.hour * 60 + end.minute,
        calendarStartInstant = if (absolute) occurrence.timestamp else null,
        calendarEndInstant = endInstant?.takeUnless { duration.duration.isZero }?.toEpochMilli(),
    )
}
