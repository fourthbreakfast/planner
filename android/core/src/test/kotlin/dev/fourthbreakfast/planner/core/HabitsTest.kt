package dev.fourthbreakfast.planner.core

import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HabitsTest {
    private fun day(date: String) = LocalDate.parse(date).toEpochDay()

    @Test
    fun weekendsDoNotBreakAWeekdayStreakAndTodayIsStillOpen() {
        val habit = Habit(id = "h", title = "read", startDay = day("2026-11-02"), weekdays = (1..5).toSet(), createdAt = 1)
        val checks = listOf("2026-11-04", "2026-11-05", "2026-11-06").mapIndexed { i, date ->
            HabitCheck("$i", "h", day(date), true, 1)
        }
        assertEquals(HabitProgress(3, 3, 3), habitProgress(habit, checks, day("2026-11-09")))
        val monday = HabitCheck("mon", "h", day("2026-11-09"), true, 2)
        assertEquals(HabitProgress(4, 4, 4), habitProgress(habit, checks + monday, day("2026-11-09")))
        assertEquals(HabitProgress(0, 4, 4), habitProgress(habit, checks + monday, day("2026-11-11")))
    }

    private class Device {
        val habitStore = MemoryStore(Habit::id)
        val checks = MemoryStore(HabitCheck::id)
        var now = 100L
        val habits = Habits(habitStore, checks) { now }
        val sync = Sync(
            MemoryTaskStore(),
            MemoryEventStore(),
            MemoryNoteStore(),
            MemoryLedger(),
            habits = habitStore,
            habitChecks = checks,
        )
    }

    @Test
    fun differentDaysMergeIndependentlyAndUncheckingIsAlsoSynced() = runBlocking {
        val a = Device()
        val b = Device()
        a.habits.add("read", 20_000, (1..7).toSet())
        b.sync.merge(a.sync.snapshot())
        val id = a.habitStore.all().single().id
        a.now = 200
        b.now = 300
        a.habits.setDone(id, 20_000, true, 20_010)
        b.habits.setDone(id, 20_001, true, 20_010)
        a.sync.merge(b.sync.snapshot())
        b.sync.merge(a.sync.snapshot())
        assertEquals(2, a.checks.all().size)
        assertEquals(a.checks.all().toSet(), b.checks.all().toSet())
        b.now = 400
        b.habits.setDone(id, 20_000, false, 20_010)
        a.sync.merge(b.sync.snapshot())
        assertFalse(a.checks.all().single { it.day == 20_000L }.done)
        assertTrue(a.checks.all().single { it.day == 20_001L }.done)
        val backup = decodeBackup(a.sync.snapshot())
        assertEquals(a.habitStore.all(), backup.habits)
        assertEquals(a.checks.all().toSet(), backup.habitChecks.toSet())
    }

    @Test
    fun futureOrUnscheduledDaysCannotBeCompletedAndArchivingKeepsHistory() = runBlocking {
        val device = Device()
        val monday = day("2026-11-09")
        device.habits.add("read", monday, setOf(1))
        val id = device.habitStore.all().single().id
        device.habits.setDone(id, monday + 1, true, monday + 1)
        device.habits.setDone(id, monday + 7, true, monday)
        assertTrue(device.checks.all().isEmpty())
        device.habits.setDone(id, monday, true, monday)
        device.habits.archive(id, true)
        device.habits.setDone(id, monday, false, monday)
        assertTrue(device.checks.all().single().done)
        device.habits.archive(id, false)
        assertFalse(device.habitStore.all().single().inBin)
    }
}
