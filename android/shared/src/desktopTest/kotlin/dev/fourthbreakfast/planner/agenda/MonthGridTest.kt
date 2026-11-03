package dev.fourthbreakfast.planner.agenda

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MonthGridTest {
    private fun day(text: String) = LocalDate.parse(text).toEpochDay()

    @Test
    fun aMonthStartsOnItsWeekday() {
        // november 2026 starts on a sunday
        val cells = monthCells(YearMonth.of(2026, 11), DayOfWeek.MONDAY)
        assertEquals(6, cells.indexOf(day("2026-11-01")))
        assertNull(cells[5])
        assertEquals(day("2026-11-30"), cells[35])
        assertEquals(0, cells.size % 7)
    }

    @Test
    fun weeksCanStartOnSunday() {
        val cells = monthCells(YearMonth.of(2026, 11), DayOfWeek.SUNDAY)
        assertEquals(day("2026-11-01"), cells[0])
        assertEquals(35, cells.size)
    }

    @Test
    fun theWeekdayNamesFollowTheFirstDay() {
        assertEquals(listOf("mo", "tu", "we", "th", "fr", "sa", "su"), weekdayNames(DayOfWeek.MONDAY, Locale.ENGLISH))
        assertEquals("su", weekdayNames(DayOfWeek.SUNDAY, Locale.ENGLISH).first())
    }

    @Test
    fun theMonthIsNamedInLowercase() {
        assertEquals("november 2026", monthTitle(YearMonth.of(2026, 11), Locale.ENGLISH))
    }
}
