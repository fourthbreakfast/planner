package dev.fourthbreakfast.planner.core

import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderScheduleTest {
    private val utc = ZoneId.of("UTC")
    private val day = 20_000L
    private val now = day * 86_400_000L + 600 * 60_000L

    private fun event(minute: Int) = Event(title = "event", day = day, minute = minute, reminder = 0, createdAt = 0)

    @Test
    fun firingOneAlarmKeepsOtherAlarmsInTheNextThirtyMinutes() {
        val events = listOf(event(600), event(610), event(620))
        assertEquals(listOf(events[1], events[2]), scheduledReminders(events, now, utc).map { it.first })
    }

    @Test
    fun aRepeatingAlarmMovesToItsNextOccurrenceWithoutDroppingOtherEvents() {
        val repeating = event(600).copy(repeat = Repeat.DAILY)
        val next = event(610)
        val alarms = scheduledReminders(listOf(repeating, next), now, utc).toMap()
        assertEquals(now + 86_400_000L, alarms[repeating])
        assertEquals(now + 10 * 60_000L, alarms[next])
    }

    @Test
    fun removedAndUnscheduledEventsHaveNoAlarm() {
        assertEquals(emptyList<Pair<Event, Long>>(), scheduledReminders(listOf(event(610).copy(deletedAt = 1), event(620).copy(reminder = null)), now, utc))
    }
}
