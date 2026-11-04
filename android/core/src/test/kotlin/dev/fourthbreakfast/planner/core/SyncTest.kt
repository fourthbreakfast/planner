package dev.fourthbreakfast.planner.core

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * One device. Its stores delete the way the app's do: whatever goes for good leaves a mark with this device's own time,
 * whether a person deleted it or a merge did.
 */
private class Device(var now: Long = 1_000L) {
    val ledger = MemoryLedger()

    private fun mark(ids: List<String>) = runBlocking { ledger.addGone(ids.map { Gone(it, now++) }) }

    val tasks = MemoryTaskStore { mark(it.map(Task::id)) }
    val events = MemoryEventStore()
    val notes = MemoryNoteStore { mark(it.map(Note::id)) }

    val sync = Sync(tasks, events, notes, ledger) { now++ }

    fun note(id: String) = notes.state.value.firstOrNull { it.id == id }

    fun task(id: String) = tasks.state.value.first { it.id == id }

    fun deleteNoteForGood(id: String) = runBlocking { notes.delete(listOfNotNull(note(id))) }
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

    @Test
    fun aDeleteKeepsItsOwnTimeAcrossDevices() = runBlocking {
        // the laptop's clock is far ahead: taking in the delete must not stamp it with the laptop's time
        val laptop = Device(now = 1_000_000)
        phone.notes.insert(listOf(note("n", "hello", 5)))
        pc.from(phone)
        laptop.from(phone)
        phone.deleteNoteForGood("n")
        laptop.from(phone)
        assertNull(laptop.note("n"))
        // the pc changed the note after the phone deleted it, so the change keeps it everywhere
        pc.notes.update(listOf(note("n", "still needed", 50_000)))
        pc.from(laptop)
        assertEquals("still needed", pc.note("n")?.body)
        laptop.from(pc)
        assertEquals("still needed", laptop.note("n")?.body)
    }

    @Test
    fun twoTasksMovedIntoEachOtherAreUntangled() = runBlocking {
        phone.tasks.insert(listOf(Task(id = "x", title = "x", createdAt = 1), Task(id = "y", title = "y", createdAt = 1)))
        pc.from(phone)
        phone.tasks.update(listOf(phone.task("x").copy(parentId = "y", updatedAt = 10)))
        pc.tasks.update(listOf(pc.task("y").copy(parentId = "x", updatedAt = 20)))
        phone.from(pc)
        pc.from(phone)
        // the later move made the loop, so that task goes back to the top; both devices agree
        assertEquals(null, phone.task("y").parentId)
        assertEquals("y", phone.task("x").parentId)
        assertEquals(phone.tasks.state.value.map { it.parentId }.sortedBy { it.orEmpty() }, pc.tasks.state.value.map { it.parentId }.sortedBy { it.orEmpty() })
        assertTrue(loopBreaks(phone.tasks.state.value).isEmpty())
    }
}

class SyncDetailsTest {
    @Test
    fun aClockNeverGoesBackPastWhatItSaw() {
        var wall = 100L
        val clock = LogicalClock { wall }
        assertEquals(100, clock.now())
        clock.saw(5_000)
        assertEquals(5_001, clock.now())
        assertEquals(5_002, clock.now())
        wall = 9_000
        assertEquals(9_000, clock.now())
    }

    @Test
    fun aPlaceIsFoundBetweenNeighbours() {
        assertEquals(1.0, placeBetween(null, null)!!, 0.0)
        assertEquals(1.5, placeBetween(1.0, 2.0)!!, 0.0)
        assertEquals(0.0, placeBetween(null, 1.0)!!, 0.0)
        assertEquals(3.0, placeBetween(2.0, null)!!, 0.0)
        assertNull(placeBetween(1.0, 1.0))
    }

    @Test
    fun aFileFromANewerVersionIsStillMerged() = runBlocking {
        val sync = Sync(MemoryTaskStore(), MemoryEventStore(), MemoryNoteStore(), MemoryLedger())
        val newer = encodeBackup(emptyList(), notes = listOf(Note(id = "n", body = "x", createdAt = 1))).replace(Regex("\"version\": \\d+"), "\"version\": 99")
        assertTrue(newer.contains("\"version\": 99"))
        assertEquals(1, sync.merge(newer))
    }
}
