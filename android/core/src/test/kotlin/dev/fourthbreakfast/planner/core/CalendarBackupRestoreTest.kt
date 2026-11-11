package dev.fourthbreakfast.planner.core

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalendarBackupRestoreTest {
    private val subscribed = Event(
        id = "feed-event",
        title = "class",
        day = 20_000,
        createdAt = 1,
        calendarSourceId = "feed",
        calendarName = "school",
        calendarGeneration = 1,
    )

    @Test
    fun unsubscribedCalendarRestoresAsAnIdempotentEditableSnapshot() = runBlocking {
        val tasks = MemoryTaskStore()
        val events = MemoryEventStore()
        val notes = MemoryNoteStore()
        val ledger = MemoryLedger().apply { gone += Gone("feed", 50) }
        val backups = Backups(tasks, events, notes, ledger) { 100 }
        val source = encodeBackup(emptyList(), listOf(subscribed))
        assertEquals(1, backups.import(source))
        val restored = events.all().single()
        assertNotEquals(subscribed.id, restored.id)
        assertFalse(restored.readOnly)
        assertEquals(subscribed.title, restored.title)
        assertEquals(0, backups.import(source))
        assertEquals(listOf(Gone("feed", 50)), ledger.gone())
        // A future subscription refresh can safely insert the original source-scoped id.
        events.insert(listOf(subscribed.copy(calendarGeneration = 200, updatedAt = 200)))
        assertEquals(2, events.all().map { it.id }.distinct().size)
    }

    @Test
    fun currentlyManagedEventsAreNotDuplicatedByImport() = runBlocking {
        val events = MemoryEventStore()
        events.insert(listOf(subscribed))
        val backups = Backups(MemoryTaskStore(), events, MemoryNoteStore())
        assertEquals(0, backups.import(encodeBackup(emptyList(), listOf(subscribed))))
        assertEquals(listOf(subscribed), events.all())
    }

    @Test
    fun aMissingTaskLinkCannotHideARestoredEvent() = runBlocking {
        val events = MemoryEventStore()
        val backups = Backups(MemoryTaskStore(), events, MemoryNoteStore())
        val orphan = subscribed.copy(calendarSourceId = null, calendarName = null, taskId = "missing-task")
        assertEquals(1, backups.import(encodeBackup(emptyList(), listOf(orphan))))
        assertNull(events.all().single().taskId)
    }
}
