package dev.fourthbreakfast.planner.data

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.fourthbreakfast.planner.PlannerData
import dev.fourthbreakfast.planner.PlannerDatabase
import dev.fourthbreakfast.planner.core.Backups
import dev.fourthbreakfast.planner.core.Event
import dev.fourthbreakfast.planner.core.Gone
import dev.fourthbreakfast.planner.core.Note
import dev.fourthbreakfast.planner.core.Repeat
import dev.fourthbreakfast.planner.core.Store
import dev.fourthbreakfast.planner.core.Sync
import dev.fourthbreakfast.planner.core.SyncLedger
import dev.fourthbreakfast.planner.core.Task
import dev.fourthbreakfast.planner.core.decodeBackup
import dev.fourthbreakfast.planner.core.encodeBackup
import dev.fourthbreakfast.planner.core.parseIcs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
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
    private val transactions = roomTransactions(database)

    @After
    fun close() = database.close()

    @Test
    fun everythingComesBackAsItWent() = runBlocking {
        val task = Task(id = "t", title = "buy milk", createdAt = 1, parentId = "p", deletedAt = 3, position = 2.5, starred = true)
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
    fun mergingDeleteAndNewerVersionPreservesOriginalDeleteTime() = runBlocking {
        val stale = Note(id = "n", body = "old", createdAt = 1, updatedAt = 5)
        val newer = stale.copy(body = "new", updatedAt = 30)
        notes.insert(listOf(stale))
        val sync = Sync(tasks, events, notes, ledger, transactions = transactions, clock = { now })

        sync.merge(encodeBackup(emptyList(), notes = listOf(newer), gone = listOf(Gone("n", 20))))

        assertEquals(listOf(newer), notes.all())
        assertEquals(listOf(Gone("n", 20)), ledger.gone())
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

    @Test
    fun backupImportRollsBackEveryKindWhenAnEventOrNoteInsertFails() = runBlocking {
        val task = Task(id = "t", title = "restored task", createdAt = 1)
        val event = Event(id = "e", title = "restored event", day = 20_000, createdAt = 1)
        val note = Note(id = "n", title = "restored note", createdAt = 1)
        val marks = listOf(Gone("t", 10), Gone("e", 10), Gone("n", 10))
        ledger.addGone(marks)
        val payload = encodeBackup(listOf(task), listOf(event), listOf(note))
        for (failEvents in listOf(true, false)) {
            val backups = Backups(
                tasks,
                if (failEvents) failingInsert(events) else events,
                if (failEvents) notes else failingInsert(notes),
                ledger,
                transactions = transactions,
                clock = { now },
            )
            val failure = runCatching { backups.import(payload) }.exceptionOrNull()
            assertEquals("insert failed", failure?.message)
            assertTrue(tasks.all().isEmpty())
            assertTrue(events.all().isEmpty())
            assertTrue(notes.all().isEmpty())
            assertEquals(marks.toSet(), ledger.gone().toSet())
        }
    }

    @Test
    fun backupImportRollsBackRowsAndRestorationMarksWhenTheLedgerFails() = runBlocking {
        val marks = listOf(Gone("t", 10), Gone("e", 10), Gone("n", 10))
        ledger.addGone(marks)
        val failingLedger = object : SyncLedger by ledger {
            override suspend fun forgetGone(ids: List<String>) {
                ledger.forgetGone(ids)
                error("ledger failed")
            }
        }
        val backups = Backups(tasks, events, notes, failingLedger, transactions = transactions, clock = { now })
        val payload = encodeBackup(
            listOf(Task(id = "t", title = "restored", createdAt = 1)),
            listOf(Event(id = "e", title = "restored", day = 20_000, createdAt = 1)),
            listOf(Note(id = "n", title = "restored", createdAt = 1)),
        )
        assertEquals("ledger failed", runCatching { backups.import(payload) }.exceptionOrNull()?.message)
        assertTrue(tasks.all().isEmpty())
        assertTrue(events.all().isEmpty())
        assertTrue(notes.all().isEmpty())
        assertEquals(marks.toSet(), ledger.gone().toSet())
    }

    @Test
    fun calendarImportRollsBackEventsAndTheirRestorationMarksTogether() = runBlocking {
        val calendar = """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            UID:calendar-test
            DTSTART;VALUE=DATE:20261106
            SUMMARY:calendar fixture
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()
        val mark = Gone(parseIcs(calendar, now).single().id, 10)
        ledger.addGone(listOf(mark))
        val failingLedger = object : SyncLedger by ledger {
            override suspend fun forgetGone(ids: List<String>) {
                ledger.forgetGone(ids)
                error("calendar ledger failed")
            }
        }
        val backups = Backups(tasks, events, notes, failingLedger, transactions = transactions, clock = { now })
        assertEquals("calendar ledger failed", runCatching { backups.importCalendar(calendar) }.exceptionOrNull()?.message)
        assertTrue(events.all().isEmpty())
        assertEquals(listOf(mark), ledger.gone())
    }

    @Test
    fun exportDoesNotMixRowsFromBeforeAndAfterAnotherTransaction() = runBlocking {
        val tasksRead = CompletableDeferred<Unit>()
        val writerRequested = CompletableDeferred<Unit>()
        val writerFinished = CompletableDeferred<Unit>()
        val taskView = object : Store<Task> by tasks {
            override suspend fun all(): List<Task> = tasks.all().also { tasksRead.complete(Unit) }
        }
        val eventView = object : Store<Event> by events {
            override suspend fun all(): List<Event> {
                writerRequested.await()
                // An unprotected export would let the writer finish between the task and event reads.
                withTimeoutOrNull(200) { writerFinished.await() }
                return events.all()
            }
        }
        val backups = Backups(taskView, eventView, notes, ledger, transactions = transactions)
        val exported = async { backups.export() }
        tasksRead.await()
        val writer = launch(Dispatchers.IO) {
            writerRequested.complete(Unit)
            transactions.transaction {
                tasks.insert(listOf(Task(id = "t", title = "new", createdAt = 1)))
                events.insert(listOf(Event(id = "e", title = "new", day = 20_000, createdAt = 1)))
            }
            writerFinished.complete(Unit)
        }
        val snapshot = decodeBackup(exported.await())
        writer.join()
        assertTrue(snapshot.tasks.isEmpty())
        assertTrue(snapshot.events.isEmpty())
        assertEquals(1, tasks.all().size)
        assertEquals(1, events.all().size)
    }

    @Test
    fun syncRollsBackDeletesAndTheirMarksWhenAnotherKindFails() = runBlocking {
        val original = Task(id = "t", title = "keep", createdAt = 1, updatedAt = 5)
        tasks.insert(listOf(original))
        val sync = Sync(tasks, failingInsert(events), notes, ledger, transactions = transactions)
        val payload = encodeBackup(
            emptyList(),
            listOf(Event(id = "e", title = "incoming", day = 20_000, createdAt = 1)),
            gone = listOf(Gone("t", 10)),
        )
        assertEquals("insert failed", runCatching { sync.merge(payload) }.exceptionOrNull()?.message)
        assertEquals(listOf(original), tasks.all())
        assertTrue(events.all().isEmpty())
        assertTrue(ledger.gone().isEmpty())
        assertTrue(ledger.agreed().isEmpty())
    }

    @Test
    fun syncRollsBackAllKindsAndAgreementWhenTheLedgerFails() = runBlocking {
        val failingLedger = object : SyncLedger by ledger {
            override suspend fun setAgreed(items: Map<String, Long>) {
                ledger.setAgreed(items)
                error("agreement failed")
            }
        }
        val sync = Sync(tasks, events, notes, failingLedger, transactions = transactions)
        val payload = encodeBackup(
            listOf(Task(id = "t", title = "incoming", createdAt = 1)),
            listOf(Event(id = "e", title = "incoming", day = 20_000, createdAt = 1)),
            listOf(Note(id = "n", title = "incoming", createdAt = 1)),
            gone = listOf(Gone("gone", 10)),
        )
        assertEquals("agreement failed", runCatching { sync.merge(payload) }.exceptionOrNull()?.message)
        assertTrue(tasks.all().isEmpty())
        assertTrue(events.all().isEmpty())
        assertTrue(notes.all().isEmpty())
        assertTrue(ledger.gone().isEmpty())
        assertTrue(ledger.agreed().isEmpty())
    }

    @Test
    fun servicesObserveSavedDeletionTimesBeforeTheFirstEdit() = runBlocking {
        val future = System.currentTimeMillis() + 86_400_000
        ledger.addGone(listOf(Gone("old deletion", future)))
        val data = PlannerData.create(database)
        data.tasks.add("first edit")
        assertTrue(tasks.all().single().updatedAt > future)
    }

    private fun <T> failingInsert(store: Store<T>): Store<T> = object : Store<T> by store {
        override suspend fun insert(items: List<T>) {
            store.insert(items)
            error("insert failed")
        }
    }
}
