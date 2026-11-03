package dev.fourthbreakfast.planner.agenda

import dev.fourthbreakfast.planner.core.Event
import org.junit.Assert.assertEquals
import org.junit.Test

class AgendaRowsTest {
    private fun event(id: String, day: Long, minute: Int? = null) = Event(id = id, title = id, day = day, minute = minute, createdAt = 0)

    private fun List<AgendaRow>.names() = map {
        when (it) {
            is DayRow -> "day${it.day}"
            is EventItem -> it.event.id
            EarlierRow -> "earlier"
        }
    }

    @Test
    fun daysAheadAreSoonestFirstWithEachEventUnderItsDay() {
        val rows = agendaRows(listOf(event("b", 12, 600), event("a", 10), event("c", 12, 480)), today = 10)
        assertEquals(listOf("day10", "a", "day12", "c", "b"), rows.names())
    }

    @Test
    fun todayCountsAsAhead() {
        assertEquals(listOf("day10", "a"), agendaRows(listOf(event("a", 10)), today = 10).names())
    }

    @Test
    fun whatAlreadyHappenedComesLastUnderADividerNewestFirst() {
        val rows = agendaRows(listOf(event("old", 3), event("older", 1), event("next", 11)), today = 10)
        assertEquals(listOf("day11", "next", "earlier", "day3", "old", "day1", "older"), rows.names())
    }

    @Test
    fun noEventsMeansNoRows() {
        assertEquals(emptyList<AgendaRow>(), agendaRows(emptyList(), today = 10))
    }

    @Test
    fun onlyTodayAndLaterAreCountedAsUpcoming() {
        assertEquals(2, upcomingCount(listOf(event("a", 9), event("b", 10), event("c", 20)), today = 10))
    }

    @Test
    fun withinAnEarlierDayEventsStayInTheOrderTheyHappen() {
        val rows = agendaRows(listOf(event("late", 3, 900), event("early", 3, 100)), today = 10)
        assertEquals(listOf("earlier", "day3", "early", "late"), rows.names())
    }

    @Test
    fun timesReadInTwentyFourOrTwelveHours() {
        assertEquals("19:30", timeText(19 * 60 + 30, true))
        assertEquals("07:05", timeText(7 * 60 + 5, true))
        assertEquals("7:30 pm", timeText(19 * 60 + 30, false))
        assertEquals("12:00 am", timeText(0, false))
        assertEquals("12:15 pm", timeText(12 * 60 + 15, false))
    }
}
