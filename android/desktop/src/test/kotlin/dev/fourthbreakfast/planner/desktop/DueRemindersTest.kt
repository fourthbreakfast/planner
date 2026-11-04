package dev.fourthbreakfast.planner.desktop

import dev.fourthbreakfast.planner.core.Event
import dev.fourthbreakfast.planner.core.reminderAt
import org.junit.Assert.assertEquals
import org.junit.Test

class DueRemindersTest {
    private val hour = 60 * 60_000L
    private val event = Event(id = "e", title = "dentist", day = 20_000, minute = 600, reminder = 10, createdAt = 0)
    private val ringsAt = reminderAt(event)!!

    @Test
    fun aReminderShowsOnceWhenItsTimeComes() {
        val due = DueReminders(late = 6 * hour)
        assertEquals(emptyList<Event>(), due.at(listOf(event), ringsAt - 1))
        assertEquals(listOf(event), due.at(listOf(event), ringsAt))
        assertEquals(emptyList<Event>(), due.at(listOf(event), ringsAt + 15_000))
    }

    @Test
    fun aLateReminderStillShowsWithinTheHoursAllowedButNotAfter() {
        assertEquals(listOf(event), DueReminders(late = 6 * hour).at(listOf(event), ringsAt + 5 * hour))
        assertEquals(emptyList<Event>(), DueReminders(late = 6 * hour).at(listOf(event), ringsAt + 7 * hour))
    }

    @Test
    fun whatWasShownIsForgottenOnceItCannotComeDueAgain() {
        val due = DueReminders(late = 6 * hour)
        due.at(listOf(event), ringsAt)
        assertEquals(1, due.remembered)
        due.at(emptyList(), ringsAt + 7 * hour)
        assertEquals(0, due.remembered)
    }

    @Test
    fun anEventInTheBinDoesNotRemind() {
        assertEquals(emptyList<Event>(), DueReminders().at(listOf(event.copy(deletedAt = 1)), ringsAt))
    }
}
