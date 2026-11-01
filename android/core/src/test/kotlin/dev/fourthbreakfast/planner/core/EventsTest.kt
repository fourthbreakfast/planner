package dev.fourthbreakfast.planner.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
    fun anEndTimeIsKeptOnlyWhenItFollowsAStart() = runBlocking {
        events.add("fine", 20_000, 540, "", "", endMinute = 630)
        events.add("no start", 20_000, null, "", "", endMinute = 630)
        events.add("before", 20_000, 540, "", "", endMinute = 500)
        events.add("same", 20_000, 540, "", "", endMinute = 540)
        assertEquals(630, get("fine").endMinute)
        assertNull(get("no start").endMinute)
        assertNull(get("before").endMinute)
        assertNull(get("same").endMinute)
    }

    @Test
    fun changingToAllDayDropsTheEndToo() = runBlocking {
        events.add("talk", 20_000, 540, "", "", endMinute = 600)
        events.update(get("talk").id, "talk", 20_000, null, "", "")
        assertNull(get("talk").minute)
        assertNull(get("talk").endMinute)
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
    fun movingToTheBinKeepsTheEventAndRestoreBringsItBack() = runBlocking {
        events.add("dentist", 20_000, 570, "clinic", "notes")
        val id = get("dentist").id
        events.moveToBin(id)
        assertEquals(true, get("dentist").inBin)
        events.restore(id)
        assertEquals(false, get("dentist").inBin)
        assertEquals("clinic", get("dentist").location)
    }

    @Test
    fun anEventInTheBinCannotBeChanged() = runBlocking {
        events.add("dentist", 20_000, 570, "", "")
        val id = get("dentist").id
        events.moveToBin(id)
        events.update(id, "renamed", 20_009, null, "", "")
        assertEquals("dentist", get("dentist").title)
    }

    @Test
    fun onlyWhatIsInTheBinCanBeDeletedForGood() = runBlocking {
        events.add("kept", 20_000, null, "", "")
        events.add("binned", 20_000, null, "", "")
        events.deleteForever(get("kept").id)
        assertEquals(2, store.state.value.size)
        events.moveToBin(get("binned").id)
        events.deleteForever(get("binned").id)
        assertEquals(listOf("kept"), store.state.value.map { it.title })
    }

    @Test
    fun emptyingTheBinLeavesTheRest() = runBlocking {
        events.add("kept", 20_000, null, "", "")
        events.add("a", 20_000, null, "", "")
        events.add("b", 20_000, null, "", "")
        events.moveToBin(get("a").id)
        events.moveToBin(get("b").id)
        events.emptyBin()
        assertEquals(listOf("kept"), store.state.value.map { it.title })
    }

    @Test
    fun anythingMissingIsIgnored() = runBlocking {
        events.moveToBin("missing")
        events.restore("missing")
        events.deleteForever("missing")
        assertEquals(emptyList<Event>(), store.state.value)
    }

    @Test
    fun aReminderNeedsAStartTimeAndNeverGoesNegative() = runBlocking {
        events.add("timed", day = 20_000, minute = 570, location = "", notes = "", reminder = 10)
        events.add("all day", day = 20_000, minute = null, location = "", notes = "", reminder = 10)
        events.add("odd", day = 20_000, minute = 600, location = "", notes = "", reminder = -5)
        assertEquals(10, get("timed").reminder)
        assertNull(get("all day").reminder)
        assertNull(get("odd").reminder)
    }

    @Test
    fun takingTheTimeAwayTakesTheReminderAway() = runBlocking {
        events.add("walk", day = 20_000, minute = 570, location = "", notes = "", reminder = 60)
        events.update(get("walk").id, "walk", 20_000, null, "", "", reminder = 60)
        assertNull(get("walk").reminder)
    }
}

class ReminderTimeTest {
    private val utc = java.time.ZoneId.of("UTC")

    @Test
    fun theReminderIsTheStartMinusTheMinutesBefore() {
        val event = Event(title = "dentist", day = 20_000, minute = 9 * 60 + 30, reminder = 10, createdAt = 0)
        val start = 20_000L * 86_400_000L + (9 * 60 + 30) * 60_000L
        assertEquals(start - 10 * 60_000L, reminderAt(event, utc))
        assertEquals(start, reminderAt(event.copy(reminder = 0), utc))
    }

    @Test
    fun noReminderOrNoStartMeansNoTime() {
        assertNull(reminderAt(Event(title = "a", day = 1, minute = 60, createdAt = 0), utc))
        assertNull(reminderAt(Event(title = "a", day = 1, reminder = 10, createdAt = 0), utc))
    }

    @Test
    fun theSameWallClockTimeIsADifferentMomentInAnotherZone() {
        val event = Event(title = "a", day = 20_000, minute = 600, reminder = 0, createdAt = 0)
        val paris = java.time.ZoneId.of("Europe/Paris")
        org.junit.Assert.assertTrue(reminderAt(event, utc)!! > reminderAt(event, paris)!!)
    }
}
