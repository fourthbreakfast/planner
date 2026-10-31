package dev.fourthbreakfast.planner.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MemoryEventStore : EventStore {
    val state = MutableStateFlow<List<Event>>(emptyList())

    override fun observeAll(): Flow<List<Event>> = state

    override suspend fun all() = state.value

    override suspend fun insert(events: List<Event>) {
        state.value = state.value + events
    }

    override suspend fun update(events: List<Event>) {
        val changed = events.associateBy { it.id }
        state.value = state.value.map { changed[it.id] ?: it }
    }

    override suspend fun delete(events: List<Event>) {
        val gone = events.mapTo(HashSet()) { it.id }
        state.value = state.value.filterNot { it.id in gone }
    }

    override suspend fun <T> transaction(block: suspend () -> T): T = block()
}

class EventsTest {
    private val store = MemoryEventStore()
    private var now = 100L
    private val events = Events(store) { now++ }

    private fun get(title: String) = store.state.value.first { it.title == title }

    @Test
    fun addTrimsEverythingAndIgnoresABlankTitle() = runBlocking {
        events.add("  dentist  ", day = 20_000, minute = 570, location = " clinic ", notes = " bring the card ")
        events.add("   ", day = 20_000, minute = null, location = "", notes = "")
        val event = store.state.value.single()
        assertEquals("dentist", event.title)
        assertEquals("clinic", event.location)
        assertEquals("bring the card", event.notes)
    }

    @Test
    fun theListIsSoonestFirstWithWholeDayEventsAheadOfTimedOnes() = runBlocking {
        events.add("late", 20_001, 60, "", "")
        events.add("evening", 20_000, 20 * 60, "", "")
        events.add("whole day", 20_000, null, "", "")
        events.add("morning", 20_000, 8 * 60, "", "")
        assertEquals(listOf("whole day", "morning", "evening", "late"), events.all.first().map { it.title })
    }

    @Test
    fun aTimeOutsideTheDayIsPulledIntoIt() = runBlocking {
        events.add("too late", 20_000, 5_000, "", "")
        events.add("too early", 20_000, -5, "", "")
        assertEquals(24 * 60 - 1, get("too late").minute)
        assertEquals(0, get("too early").minute)
    }

    @Test
    fun updateChangesTheFieldsAndKeepsTheRest() = runBlocking {
        events.add("dentist", 20_000, 570, "clinic", "")
        val before = get("dentist")
        events.update(before.id, " dentist ", 20_003, null, "", "bring the card")
        val after = store.state.value.single()
        assertEquals(before.id, after.id)
        assertEquals(20_003L, after.day)
        assertNull(after.minute)
        assertEquals("bring the card", after.notes)
        assertEquals(before.createdAt, after.createdAt)
    }

    @Test
    fun updateWithABlankTitleChangesNothing() = runBlocking {
        events.add("dentist", 20_000, 570, "", "")
        val before = get("dentist")
        events.update(before.id, "  ", 20_005, null, "x", "y")
        assertEquals(before, store.state.value.single())
    }

    @Test
    fun removeReturnsTheEventAndRestorePutsItBackAsItWas() = runBlocking {
        events.add("dentist", 20_000, 570, "clinic", "notes")
        val removed = events.remove(get("dentist").id)
        assertNotNull(removed)
        assertEquals(emptyList<Event>(), store.state.value)
        events.restore(removed!!)
        assertEquals(listOf(removed), store.state.value)
        events.restore(removed)
        assertEquals(1, store.state.value.size)
    }

    @Test
    fun removingSomethingThatIsNotThereReturnsNothing() = runBlocking {
        assertNull(events.remove("missing"))
    }
}
