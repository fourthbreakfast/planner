package dev.fourthbreakfast.planner.today

import dev.fourthbreakfast.planner.core.Event
import dev.fourthbreakfast.planner.core.Repeat
import dev.fourthbreakfast.planner.core.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TodayItemsTest {
    @Test fun ongoingMultiDayEventRemainsUntilItsFinalDayEnds() {
        val event = Event(id = "trip", title = "Travel", day = 99, minute = 600, endDay = 101, endMinute = 660, createdAt = 1)
        assertEquals(listOf("trip"), upNext(listOf(event), emptyList(), 100, 720).map { it.event.id })
        assertTrue(upNext(listOf(event), emptyList(), 101, 720).isEmpty())
    }

    @Test fun completedScheduledTaskDoesNotRemainInAgendaSummary() {
        val task = Task(id = "task", title = "Read", done = true, createdAt = 1)
        val event = Event(title = "Read", taskId = "task", day = 100, minute = 900, createdAt = 1)
        assertTrue(upNext(listOf(event), listOf(task), 100, 720).isEmpty())
    }

    @Test fun repeatingEventsUseTheCurrentOccurrenceAndKeepNoTimeEvents() {
        val repeat = Event(id = "repeat", title = "Lunch", day = 90, minute = 800, repeat = Repeat.DAILY, createdAt = 1)
        val untimed = Event(id = "birthday", title = "Birthday", day = 100, createdAt = 2)
        val result = upNext(listOf(repeat, untimed), emptyList(), 100, 720)
        assertEquals("birthday", result.first().event.id)
        assertEquals(100L, result.first { it.event.id == "repeat" }.start)
    }
}
