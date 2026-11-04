package dev.fourthbreakfast.planner.agenda

import org.junit.Assert.assertEquals
import org.junit.Test

class PickedTimeTest {
    private fun hm(h: Int, m: Int) = h * 60 + m

    @Test
    fun anHourKeepsTheMinutesAndAMinuteKeepsTheHour() {
        assertEquals(hm(14, 30) to hm(16, 0), pickedTime(hm(9, 30), hm(16, 0), editingEnd = false, hour = 14, minute = null))
        assertEquals(hm(9, 45) to hm(16, 0), pickedTime(hm(9, 30), hm(16, 0), editingEnd = false, hour = null, minute = 45))
    }

    @Test
    fun aStartPastTheEndTakesTheEndAlong() {
        assertEquals(hm(17, 0) to hm(18, 0), pickedTime(hm(9, 0), hm(10, 0), editingEnd = false, hour = 17, minute = null))
        assertEquals(hm(23, 30) to hm(23, 59), pickedTime(hm(9, 0), hm(10, 0), editingEnd = false, hour = 23, minute = 30))
    }

    @Test
    fun anEndNeverComesBeforeTheStart() {
        assertEquals(hm(10, 0) to hm(10, 5), pickedTime(hm(10, 0), hm(11, 0), editingEnd = true, hour = 8, minute = null))
        assertEquals(hm(10, 0) to hm(12, 0), pickedTime(hm(10, 0), hm(11, 0), editingEnd = true, hour = 12, minute = null))
    }
}
