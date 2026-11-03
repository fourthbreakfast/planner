package dev.fourthbreakfast.planner.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class MemorySyncTasks : TaskStore {
    val state = MutableStateFlow<List<Task>>(emptyList())

    override fun observeAll(): Flow<List<Task>> = state

    override suspend fun all() = state.value

    override suspend fun insert(tasks: List<Task>) {
        state.value = state.value + tasks
    }

    override suspend fun update(tasks: List<Task>) {
        val changed = tasks.associateBy { it.id }
        state.value = state.value.map { changed[it.id] ?: it }
    }

    override suspend fun delete(tasks: List<Task>) {
        val gone = tasks.mapTo(HashSet()) { it.id }
        state.value = state.value.filterNot { it.id in gone }
    }

    override suspend fun <T> transaction(block: suspend () -> T): T = block()
}

private class MemoryLedger : SyncLedger {
    val gone = ArrayList<Gone>()
    val agreed = HashMap<String, Long>()

    override suspend fun gone() = gone.toList()

    override suspend fun addGone(items: List<Gone>) {
        gone.removeAll { old -> items.any { it.id == old.id } }
        gone += items
    }

    override suspend fun agreed() = agreed.toMap()

    override suspend fun setAgreed(items: Map<String, Long>) {
        agreed += items
    }
}

/** One device: its stores, what sync remembers, and its sync. */
private class Device {
    val tasks = MemorySyncTasks()
    val events = MemoryEventStore()
    val notes = MemoryNoteStore()
    val ledger = MemoryLedger()
    var now = 1_000L
    val sync = Sync(tasks, events, notes, ledger) { now++ }

    fun note(id: String) = notes.state.value.firstOrNull { it.id == id }

    /** Deletes a note for good, the way the app's stores do: the mark goes with it. */
    fun deleteNoteForGood(id: String) = runBlocking {
        notes.delete(listOfNotNull(note(id)))
        ledger.addGone(listOf(Gone(id, now++)))
    }
}

class SyncTest {
    private val phone = Device()
    private val pc = Device()

    private fun Device.from(other: Device): Int = runBlocking { sync.merge(other.sync.snapshot()) }

    private fun note(id: String, body: String, at: Long) = Note(id = id, title = "", body = body, createdAt = 1, updatedAt = at)

    @Test
    fun whatIsNewArrives() = runBlocking {
        phone.tasks.insert(listOf(Task(id = "t", title = "buy milk", createdAt = 5)))
        phone.events.insert(listOf(Event(id = "e", title = "dentist", day = 20_000, createdAt = 5)))
        phone.notes.insert(listOf(note("n", "hello", 5)))

        assertEquals(3, pc.from(phone))
        assertEquals("buy milk", pc.tasks.state.value.single().title)
        assertEquals("dentist", pc.events.state.value.single().title)
        assertEquals("hello", pc.note("n")?.body)
    }

    @Test
    fun theNewerVersionWins() = runBlocking {
        phone.tasks.insert(listOf(Task(id = "t", title = "old", createdAt = 1, updatedAt = 10)))
        pc.tasks.insert(listOf(Task(id = "t", title = "new", createdAt = 1, updatedAt = 20)))

        assertEquals(0, pc.from(phone))
        assertEquals(1, phone.from(pc))
        assertEquals("new", phone.tasks.state.value.single().title)
        assertEquals("new", pc.tasks.state.value.single().title)
    }

    @Test
    fun mergingTwiceChangesNothing() = runBlocking {
        phone.notes.insert(listOf(note("n", "hello", 5)))
        assertEquals(1, pc.from(phone))
        assertEquals(0, pc.from(phone))
        assertEquals(0, phone.from(pc))
    }

    @Test
    fun somethingDeletedForGoodStaysGone() = runBlocking {
        phone.notes.insert(listOf(note("n", "hello", 5)))
        pc.from(phone)
        phone.deleteNoteForGood("n")

        assertEquals(1, pc.from(phone))
        assertNull(pc.note("n"))
        // an old copy that turns up again does not bring it back
        val stale = Device().apply { notes.state.value = listOf(note("n", "hello", 5)) }
        assertEquals(0, phone.from(stale))
        assertNull(phone.note("n"))
    }

    @Test
    fun aChangeAfterTheDeleteKeepsIt() = runBlocking {
        phone.notes.insert(listOf(note("n", "hello", 5)))
        pc.from(phone)
        phone.deleteNoteForGood("n")
        pc.notes.update(listOf(note("n", "still needed", 50_000)))

        assertEquals(0, pc.from(phone))
        assertEquals("still needed", pc.note("n")?.body)
        assertEquals(1, phone.from(pc))
        assertEquals("still needed", phone.note("n")?.body)
    }

    @Test
    fun aNoteChangedOnBothKeepsTheOlderInTheBin() = runBlocking {
        phone.notes.insert(listOf(note("n", "first", 5)))
        pc.from(phone)
        phone.from(pc)
        phone.notes.update(listOf(note("n", "phone words", 10)))
        pc.notes.update(listOf(note("n", "pc words", 20)))

        phone.from(pc)
        val kept = phone.notes.state.value
        assertEquals("pc words", phone.note("n")?.body)
        val copy = kept.single { it.id != "n" }
        assertEquals("phone words", copy.body)
        assertTrue(copy.inBin)
    }

    @Test
    fun aNoteChangedOnOneSideOnlyMakesNoCopy() = runBlocking {
        phone.notes.insert(listOf(note("n", "first", 5)))
        pc.from(phone)
        phone.from(pc)
        pc.notes.update(listOf(note("n", "pc words", 20)))

        assertEquals(1, phone.from(pc))
        assertEquals(listOf("n"), phone.notes.state.value.map { it.id })
        assertEquals("pc words", phone.note("n")?.body)
    }

    @Test
    fun theMarksTravelInTheFile() = runBlocking {
        phone.ledger.addGone(listOf(Gone("x", 42)))
        val backup = decodeBackup(phone.sync.snapshot())
        assertEquals(listOf(Gone("x", 42)), backup.gone)
        pc.from(phone)
        assertEquals(listOf(Gone("x", 42)), pc.ledger.gone)
    }
}
