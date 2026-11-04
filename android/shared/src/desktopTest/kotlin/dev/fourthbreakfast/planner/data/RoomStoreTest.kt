package dev.fourthbreakfast.planner.data

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.fourthbreakfast.planner.PlannerDatabase
import dev.fourthbreakfast.planner.core.Event
import dev.fourthbreakfast.planner.core.Gone
import dev.fourthbreakfast.planner.core.Note
import dev.fourthbreakfast.planner.core.Repeat
import dev.fourthbreakfast.planner.core.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The database behind the core stores, in memory: what goes in comes back the same, and a delete leaves its mark. */
class RoomStoreTest {
    private val database = Room.inMemoryDatabaseBuilder<PlannerDatabase>()
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
    private var now = 5_000L
    private val tasks = roomTaskStore(database) { now }
    private val events = roomEventStore(database) { now }
    private val notes = roomNoteStore(database) { now }
    private val ledger = RoomSyncLedger(database)

    @After
    fun close() = database.close()

    @Test
    fun everythingComesBackAsItWent() = runBlocking {
        val task = Task(id = "t", title = "buy milk", createdAt = 1, parentId = "p", deletedAt = 3, position = 2.5)
        val event = Event(id = "e", title = "class", day = 20_000, minute = 600, createdAt = 1, repeat = Repeat.WEEKLY, skipDays = setOf(20_007, 20_014))
        val note = Note(id = "n", title = "list", body = "p0n eggs", createdAt = 1, pinned = true)
        tasks.insert(listOf(task))
        events.insert(listOf(event))
        notes.insert(listOf(note))
        assertEquals(listOf(task), tasks.all())
        assertEquals(listOf(event), events.all())
        assertEquals(listOf(note), notes.all())
    }

    @Test
    fun aDeleteForGoodIsMarkedWithTheStoresClock() = runBlocking {
        tasks.insert(listOf(Task(id = "t", title = "old", createdAt = 1)))
        now = 9_000
        tasks.delete(tasks.all())
        assertTrue(tasks.all().isEmpty())
        assertEquals(listOf(Gone("t", 9_000)), ledger.gone())
    }

    @Test
    fun aFailedTransactionLeavesNothingBehind() = runBlocking {
        runCatching {
            tasks.transaction {
                tasks.insert(listOf(Task(id = "t", title = "half", createdAt = 1)))
                // a write inside the first one joins it, and goes with it
                notes.transaction { notes.insert(listOf(Note(id = "n", createdAt = 1))) }
                error("stopped halfway")
            }
        }
        assertTrue(tasks.all().isEmpty())
        assertTrue(notes.all().isEmpty())
    }
}
