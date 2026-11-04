package dev.fourthbreakfast.planner.core

import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OccurrencesTest {
    private fun day(text: String) = LocalDate.parse(text).toEpochDay()

    private fun event(start: String, repeat: Repeat = Repeat.NONE, until: String? = null, end: String? = null, skip: Set<String> = emptySet()) = Event(
        id = "e",
        title = "e",
        day = day(start),
        createdAt = 1,
        repeat = repeat,
        repeatUntil = until?.let(::day),
        endDay = end?.let(::day),
        skipDays = skip.map(::day).toSet(),
    )

    private fun starts(event: Event, from: String, to: String) = occurrences(event, day(from), day(to)).map { LocalDate.ofEpochDay(it.start).toString() }

    @Test
    fun aWeeklyEventComesBackEverySevenDaysUntilItsEnd() {
        val e = event("2026-11-03", Repeat.WEEKLY, until = "2026-11-24", skip = setOf("2026-11-17"))
        assertEquals(listOf("2026-11-03", "2026-11-10", "2026-11-24"), starts(e, "2026-11-01", "2026-12-31"))
    }

    @Test
    fun aMonthlyEventOnThe31stSkipsShortMonths() {
        val e = event("2026-01-31", Repeat.MONTHLY)
        assertEquals(listOf("2026-01-31", "2026-03-31", "2026-05-31"), starts(e, "2026-01-01", "2026-06-15"))
    }

    @Test
    fun aBirthdayOnTheLeapDayComesInLeapYears() {
        val e = event("2024-02-29", Repeat.YEARLY)
        assertEquals(listOf("2024-02-29", "2028-02-29"), starts(e, "2024-01-01", "2028-12-31"))
    }

    @Test
    fun anEventOverDaysTouchesEveryDayItCovers() {
        val trip = event("2026-11-05", end = "2026-11-08")
        assertEquals(listOf("2026-11-05"), starts(trip, "2026-11-07", "2026-11-07"))
        assertEquals(3, trip.span)
        assertEquals(emptyList<String>(), starts(trip, "2026-11-09", "2026-11-30"))
    }

    @Test
    fun aRepeatingReminderIsForTheNextTime() {
        val e = event("2026-11-03", Repeat.DAILY).copy(minute = 9 * 60, reminder = 10)
        val noon = LocalDate.parse("2026-11-04").atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        val next = LocalDate.parse("2026-11-05").atTime(8, 50).toInstant(ZoneOffset.UTC).toEpochMilli()
        assertEquals(next, reminderAt(e, ZoneOffset.UTC, from = noon))
        assertNull(reminderAt(e.copy(repeatUntil = day("2026-11-04")), ZoneOffset.UTC, from = noon))
    }
}
