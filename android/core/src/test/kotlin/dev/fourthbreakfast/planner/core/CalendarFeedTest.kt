package dev.fourthbreakfast.planner.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarFeedTest {
    private val now = Instant.parse("2026-11-09T12:00:00Z").toEpochMilli()
    private val utc = ZoneId.of("UTC")
    private fun day(value: String) = LocalDate.parse(value).toEpochDay()
    private fun calendar(vararg events: String) = "BEGIN:VCALENDAR\r\nVERSION:2.0\r\n" + events.joinToString("\r\n") + "\r\nEND:VCALENDAR"
    private fun event(uid: String, lines: String) = "BEGIN:VEVENT\r\nUID:$uid\r\nSUMMARY:class\r\n$lines\r\nEND:VEVENT"

    @Test
    fun schoolCalendarSupportsMultipleWeekdaysAndFortnightlyIntervals() {
        val feed = calendar(event("classes", "DTSTART:20261005T090000Z\r\nRRULE:FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,WE;COUNT=6"))
        val events = parseCalendarFeed(feed, now, utc).events
        assertEquals(listOf("2026-10-05", "2026-10-07", "2026-10-19", "2026-10-21", "2026-11-02", "2026-11-04").map(::day), events.map { it.day })
        assertTrue(events.all { it.minute == 540 && it.repeat == Repeat.NONE })
    }

    @Test
    fun ordinalWeekdaysAreExpandedInsteadOfSilentlyFlattened() {
        val feed = calendar(event("meeting", "DTSTART:20261005T090000Z\r\nRRULE:FREQ=MONTHLY;BYDAY=1MO;COUNT=3"))
        assertEquals(listOf("2026-10-05", "2026-11-02", "2026-12-07").map(::day), parseCalendarFeed(feed, now, utc).events.map { it.day })
    }

    @Test
    fun recurrenceKeepsItsPublishingTimezoneAcrossDaylightSavingChanges() {
        val feed =
            calendar(event("madrid", "DTSTART;TZID=Europe/Madrid:20261018T090000\r\nDTEND;TZID=Europe/Madrid:20261018T100000\r\nRRULE:FREQ=WEEKLY;COUNT=2"))
        val events = parseCalendarFeed(feed, now, utc).events
        assertEquals(listOf(420, 480), events.map { it.minute })
        assertEquals(listOf(480, 540), events.map { it.endMinute })
    }

    @Test
    fun exclusionsCancellationsAndMovedOverridesKeepOccurrenceIdentity() {
        fun feed(moved: String) = calendar(
            event("series", "DTSTART:20261102T090000Z\r\nRRULE:FREQ=WEEKLY;COUNT=3\r\nEXDATE:20261109T090000Z"),
            event("series", "RECURRENCE-ID:20261102T090000Z\r\nSTATUS:CANCELLED"),
            event("series", "RECURRENCE-ID:20261116T090000Z\r\nDTSTART:${moved}T110000Z"),
        )
        val first = parseCalendarFeed(feed("20261117"), now, utc).events.single()
        val moved = parseCalendarFeed(feed("20261118"), now, utc).events.single()
        assertEquals(day("2026-11-17"), first.day)
        assertEquals(first.id, moved.id)
        assertEquals(660, moved.minute)
    }

    @Test
    fun recurringWindowIsBoundedAndMovesWithTheCalendar() {
        val text = calendar(event("daily", "DTSTART;VALUE=DATE:20000101\r\nRRULE:FREQ=DAILY"))
        val feed = parseCalendarFeed(text, now, utc)
        assertTrue(feed.events.size in 1_090..1_100)
        assertEquals(feed.fromDay, feed.events.minOf { it.day })
        assertEquals(feed.throughDay, feed.events.maxOf { it.day })
        assertEquals(feed.events.size, feed.events.map { it.id }.toSet().size)
    }

    @Test
    fun allDayEndsRemainExclusive() {
        val text = calendar(event("trip", "DTSTART;VALUE=DATE:20261109\r\nDTEND;VALUE=DATE:20261112"))
        val event = parseCalendarFeed(text, now, utc).events.single()
        assertEquals(day("2026-11-11"), event.endDay)
        assertEquals(null, event.minute)
    }

    @Test
    fun unknownTimezoneAndTruncatedResponsesAreRejectedAsAWhole() {
        val text = calendar(event("one", "DTSTART;TZID=Unknown/Timezone:20261109T090000"))
        assertEquals(CalendarFailure.UNSUPPORTED_FEED, (runCatching { parseCalendarFeed(text, now, utc) }.exceptionOrNull() as CalendarFeedException).reason)
        assertEquals(
            CalendarFailure.INVALID_FEED,
            (
                runCatching {
                    parseCalendarFeed(text.replace("END:VEVENT", ""), now, utc)
                }.exceptionOrNull() as CalendarFeedException
                ).reason,
        )
    }

    @Test
    fun overridesUseTheirDurationOrInheritTheMasterDurationAndDetails() {
        val text = calendar(
            event("series", "DTSTART:20261109T090000Z\r\nDURATION:PT90M\r\nRRULE:FREQ=WEEKLY;COUNT=2"),
            "BEGIN:VEVENT\r\nUID:series\r\nRECURRENCE-ID:20261109T090000Z\r\nDTSTART:20261110T100000Z\r\nEND:VEVENT",
            event("series", "RECURRENCE-ID:20261116T090000Z\r\nDTSTART:20261117T110000Z\r\nDURATION:PT30M"),
        )
        val events = parseCalendarFeed(text, now, utc).events.sortedBy { it.day }
        assertEquals(listOf(690, 690), events.map { it.endMinute })
        assertTrue(events.all { it.title == "class" })
    }

    @Test
    fun unsupportedRecurrenceAndNonEventFeedsFailExplicitly() {
        for (text in listOf(
            calendar(event("period", "DTSTART:20261109T090000Z\r\nRDATE;VALUE=PERIOD:20261110T090000Z/20261110T100000Z")),
            calendar(event("range", "RECURRENCE-ID;RANGE=THISANDFUTURE:20261109T090000Z\r\nDTSTART:20261110T090000Z")),
            "BEGIN:VCALENDAR\r\nBEGIN:VTODO\r\nUID:task\r\nEND:VTODO\r\nEND:VCALENDAR",
        )) {
            assertEquals(
                CalendarFailure.UNSUPPORTED_FEED,
                (
                    runCatching {
                        parseCalendarFeed(text, now, utc)
                    }.exceptionOrNull() as CalendarFeedException
                    ).reason,
            )
        }
    }

    @Test
    fun cancellationInterruptsExpansionInsteadOfBecomingAFeedFailure() {
        val text = calendar(event("daily", "DTSTART:20000101T090000Z\r\nRRULE:FREQ=DAILY"))
        val cancellation = kotlinx.coroutines.CancellationException("cancelled")
        val error = runCatching { parseCalendarFeed(text, now, utc, checkCancelled = { throw cancellation }) }.exceptionOrNull()
        assertTrue(error === cancellation)
    }
}
