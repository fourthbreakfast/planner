package dev.fourthbreakfast.planner.agenda

import dev.fourthbreakfast.planner.core.Event
import dev.fourthbreakfast.planner.core.Repeat
import org.junit.Assert.assertEquals
import org.junit.Test

class MovedDraftTest {
    @Test
    fun aSingleEventMovesWithItsLength() {
        val trip = Event(title = "trip", day = 10, endDay = 12, minute = 540, location = "coast", createdAt = 0)
        val moved = movedDraft(trip, from = 10, to = 20)
        assertEquals(20L, moved.day)
        assertEquals(22L, moved.endDay)
        assertEquals(540, moved.minute)
        assertEquals("coast", moved.location)
    }

    @Test
    fun aLaterTimeOfARepeatKeepsItsOwnLengthNotTheDistanceFromTheFirst() {
        // every week, two days long: the time on day 17 lasts to day 18, and moved to day 30 it lasts to day 31
        val course = Event(title = "course", day = 10, endDay = 11, repeat = Repeat.WEEKLY, createdAt = 0)
        val moved = movedDraft(course, from = 17, to = 30)
        assertEquals(30L, moved.day)
        assertEquals(31L, moved.endDay)
    }

    @Test
    fun aOneDayEventHasNoLastDay() {
        assertEquals(null, movedDraft(Event(title = "call", day = 10, createdAt = 0), from = 10, to = 11).endDay)
    }
}
