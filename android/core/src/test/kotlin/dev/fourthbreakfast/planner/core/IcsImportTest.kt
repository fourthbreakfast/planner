package dev.fourthbreakfast.planner.core

import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IcsImportTest {
    private val zone = ZoneId.of("Europe/Madrid")

    private fun day(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).toEpochDay()

    private fun calendar(vararg events: String) =
        "BEGIN:VCALENDAR\r\nVERSION:2.0\r\n" + events.joinToString("") { "BEGIN:VEVENT\r\n$it\r\nEND:VEVENT\r\n" } + "END:VCALENDAR\r\n"

    private fun read(vararg events: String) = parseIcs(calendar(*events), 5, zone)

    @Test
    fun aTimedEventKeepsItsDetails() {
        val e = read(
            "UID:a\r\nSUMMARY:Dentist\r\nDTSTART:20261110T093000\r\nDTEND:20261110T103000\r\nLOCATION:Main st\\, 4\r\nDESCRIPTION:bring card\\nand forms",
        ).single()
        assertEquals("Dentist", e.title)
        assertEquals(day(2026, 11, 10), e.day)
        assertEquals(9 * 60 + 30, e.minute)
        assertEquals(10 * 60 + 30, e.endMinute)
        assertEquals("Main st, 4", e.location)
        assertEquals("bring card\nand forms", e.notes)
    }

    @Test
    fun utcTimesBecomeLocalOnes() {
        val e = read("UID:u\r\nSUMMARY:Call\r\nDTSTART:20261110T083000Z").single()
        assertEquals(9 * 60 + 30, e.minute)
    }

    @Test
    fun aTimeInAnotherZoneIsConverted() {
        val e = read("UID:z\r\nSUMMARY:Call\r\nDTSTART;TZID=America/New_York:20261110T090000").single()
        assertEquals(15 * 60, e.minute)
    }

    @Test
    fun wholeDayEndsAreTheDayBefore() {
        val e = read("UID:w\r\nSUMMARY:Trip\r\nDTSTART;VALUE=DATE:20261110\r\nDTEND;VALUE=DATE:20261113").single()
        assertNull(e.minute)
        assertEquals(day(2026, 11, 12), e.endDay)
        val one = read("UID:o\r\nSUMMARY:Day\r\nDTSTART;VALUE=DATE:20261110\r\nDTEND;VALUE=DATE:20261111").single()
        assertNull(one.endDay)
    }

    @Test
    fun repeatsComeAcrossWithTheirEnd() {
        val weekly = read("UID:r\r\nSUMMARY:Gym\r\nDTSTART:20261102T180000\r\nRRULE:FREQ=WEEKLY;UNTIL=20261130T000000Z;BYDAY=MO").single()
        assertEquals(Repeat.WEEKLY, weekly.repeat)
        assertEquals(day(2026, 11, 30), weekly.repeatUntil)
        val counted = read("UID:c\r\nSUMMARY:Pills\r\nDTSTART;VALUE=DATE:20261101\r\nRRULE:FREQ=DAILY;COUNT=5").single()
        assertEquals(day(2026, 11, 5), counted.repeatUntil)
    }

    @Test
    fun aRepeatWithMoreToItIsASingleEvent() {
        assertEquals(Repeat.NONE, read("UID:i\r\nSUMMARY:Sync\r\nDTSTART:20261102T100000\r\nRRULE:FREQ=WEEKLY;INTERVAL=2").single().repeat)
        assertEquals(Repeat.NONE, read("UID:b\r\nSUMMARY:Sync\r\nDTSTART:20261102T100000\r\nRRULE:FREQ=WEEKLY;BYDAY=MO,WE").single().repeat)
    }

    @Test
    fun daysLeftOutAndChangedTimesAreKept() {
        val events = read(
            "UID:s\r\nSUMMARY:Class\r\nDTSTART:20261102T100000\r\nRRULE:FREQ=WEEKLY\r\nEXDATE:20261109T100000",
            "UID:s\r\nRECURRENCE-ID:20261116T100000\r\nSUMMARY:Class moved\r\nDTSTART:20261117T100000",
        )
        val series = events.first { it.title == "Class" }
        assertEquals(setOf(day(2026, 11, 9), day(2026, 11, 16)), series.skipDays)
        assertEquals(day(2026, 11, 17), events.first { it.title == "Class moved" }.day)
    }

    @Test
    fun remindersCountMinutesBefore() {
        val e = read("UID:m\r\nSUMMARY:Meet\r\nDTSTART:20261110T100000\r\nBEGIN:VALARM\r\nTRIGGER:-PT1H30M\r\nACTION:DISPLAY\r\nEND:VALARM").single()
        assertEquals(90, e.reminder)
        val whole = read("UID:n\r\nSUMMARY:Day\r\nDTSTART;VALUE=DATE:20261110\r\nBEGIN:VALARM\r\nTRIGGER:-PT15M\r\nEND:VALARM").single()
        assertNull(whole.reminder)
    }

    @Test
    fun cancelledAndNamelessEventsAreLeftOutAndLongLinesJoin() {
        val events = read(
            "UID:x\r\nSTATUS:CANCELLED\r\nSUMMARY:Gone\r\nDTSTART:20261110T100000",
            "UID:y\r\nSUMMARY:\r\nDTSTART:20261110T100000",
            "UID:l\r\nSUMMARY:A very\r\n  long name\r\nDTSTART:20261110T100000",
        )
        assertEquals(listOf("A very long name"), events.map { it.title })
    }

    @Test
    fun theSameFileGivesTheSameIdsAndNothingBecomesNothingNew() = runBlocking {
        val text = calendar("UID:k\r\nSUMMARY:One\r\nDTSTART:20261110T100000")
        val backups = Backups(MemoryTaskStore(), MemoryEventStore(), MemoryNoteStore(), clock = { 5 })
        assertEquals(1, backups.importCalendar(text))
        assertEquals(0, backups.importCalendar(text))
        assertEquals(parseIcs(text, 1, zone).single().id, parseIcs(text, 2, zone).single().id)
    }

    @Test
    fun somethingThatIsNotACalendarIsRefused() {
        val refused = runCatching { parseIcs("hello", 1, zone) }.exceptionOrNull()
        assertTrue(refused is BackupException)
    }
}
