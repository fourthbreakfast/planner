package dev.fourthbreakfast.planner.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarTimeZoneTest {
    private val now = Instant.parse("2026-11-09T12:00:00Z").toEpochMilli()
    private val utc = ZoneId.of("UTC")
    private val madrid = ZoneId.of("Europe/Madrid")
    private val newYork = ZoneId.of("America/New_York")
    private fun feed(start: String, more: String = "") =
        "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nBEGIN:VEVENT\r\nUID:meeting\r\nSUMMARY:meeting\r\n$start\r\n$more\r\nEND:VEVENT\r\nEND:VCALENDAR"
    private fun subscribed(text: String) = parseCalendarFeed(text, now, utc).events.map {
        it.copy(calendarSourceId = "source", calendarName = "work", calendarGeneration = 1)
    }

    @Test
    fun publisherDstAndDeviceDstRemainIndependentAndWireValuesAreUtc() {
        val events = subscribed(feed("DTSTART;TZID=Europe/Madrid:20261018T090000", "DURATION:PT1H\r\nRRULE:FREQ=WEEKLY;COUNT=4"))
        assertEquals(listOf(420, 480, 480, 480), events.map { it.minute })
        assertEquals(listOf(540, 540, 540, 540), events.map { it.forDisplay(madrid).minute })
        assertEquals(listOf(180, 240, 180, 180), events.map { it.forDisplay(newYork).minute })
        assertEquals(listOf(480, 540, 540, 540), events.map { it.endMinute })
        assertEquals(events, decodeBackup(encodeBackup(emptyList(), events)).events)
    }

    @Test
    fun projectionHandlesCrossingMidnightAndMultiDayEndsWithoutChangingIdentity() {
        val event = subscribed(feed("DTSTART:20261110T003000Z", "DTEND:20261112T013000Z")).single()
        val shown = event.forDisplay(newYork)
        assertEquals(LocalDate.parse("2026-11-09").toEpochDay(), shown.day)
        assertEquals(LocalDate.parse("2026-11-11").toEpochDay(), shown.endDay)
        assertEquals(1170, shown.minute)
        assertEquals(1230, shown.endMinute)
        assertEquals(event.id, shown.id)
        assertEquals(event.updatedAt, shown.updatedAt)
        assertEquals(event.calendarStartInstant, shown.calendarStartInstant)
    }

    @Test
    fun allDayFloatingAndManualEventsKeepTheirWrittenWallTimesInEveryZone() {
        for (event in subscribed(feed("DTSTART;VALUE=DATE:20261110", "DTEND;VALUE=DATE:20261112")) +
            subscribed(feed("DTSTART:20261110T090000", "DURATION:PT1H")) +
            Event(id = "manual", title = "call", day = 20_000, minute = 540, createdAt = 1)) {
            assertNull(event.calendarStartInstant)
            assertNull(event.calendarEndInstant)
            assertEquals(event, event.forDisplay(madrid))
            assertEquals(event, event.forDisplay(newYork))
        }
    }

    @Test
    fun absoluteRemindersUseTheirExactInstantEvenDuringAnAmbiguousDstHour() {
        val event = subscribed(feed("DTSTART:20261101T063000Z", "BEGIN:VALARM\r\nACTION:DISPLAY\r\nTRIGGER:-PT10M\r\nEND:VALARM")).single()
        val expected = Instant.parse("2026-11-01T06:20:00Z").toEpochMilli()
        assertEquals(expected, reminderAt(event, madrid))
        assertEquals(expected, reminderAt(event.forDisplay(newYork), newYork))
    }

    @Test
    fun backupsRestoreAnEditableLocalSnapshotWithoutRetainingAbsoluteSubscriptionFields() = runBlocking {
        val event = subscribed(feed("DTSTART:20261110T003000Z", "DURATION:PT1H")).single()
        val events = MemoryEventStore()
        val backups = Backups(MemoryTaskStore(), events, MemoryNoteStore(), clock = { now })
        val text = encodeBackup(emptyList(), listOf(event))
        assertEquals(1, backups.import(text))
        val restored = events.all().single()
        val local = event.forDisplay()
        assertEquals(local.day, restored.day)
        assertEquals(local.minute, restored.minute)
        assertEquals(local.endMinute, restored.endMinute)
        assertNull(restored.calendarStartInstant)
        assertNull(restored.calendarEndInstant)
        assertNull(restored.calendarSourceId)
        assertTrue(!restored.readOnly && restored.id != event.id)
        assertEquals(0, backups.import(text))
        assertEquals(restored, Events(events).all.first().single())
    }
}
