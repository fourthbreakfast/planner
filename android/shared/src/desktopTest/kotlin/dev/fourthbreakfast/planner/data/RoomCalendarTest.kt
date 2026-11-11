package dev.fourthbreakfast.planner.data

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.fourthbreakfast.planner.PlannerData
import dev.fourthbreakfast.planner.PlannerDatabase
import dev.fourthbreakfast.planner.core.CalendarDownload
import dev.fourthbreakfast.planner.core.CalendarSubscription
import dev.fourthbreakfast.planner.core.CalendarSubscriptions
import dev.fourthbreakfast.planner.core.CalendarTransport
import dev.fourthbreakfast.planner.core.Event
import dev.fourthbreakfast.planner.core.Store
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomCalendarTest {
    private val database = Room.inMemoryDatabaseBuilder<PlannerDatabase>()
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
    private var now = 1_800_000_000_000L
    private val sources = RoomCalendarStore(database)
    private val events = roomEventStore(database) { now++ }
    private val ledger = RoomSyncLedger(database)
    private val transactions = roomTransactions(database)

    @After
    fun close() = database.close()

    @Test
    fun privateCacheAndProvenanceRoundTripWithoutEnteringDeviceSnapshots() = runBlocking {
        val source =
            CalendarSubscription("source", "work", "https://example.com/feed?token=private", 1, lastSuccess = 5, cachedFeed = "private cache", etag = "v1")
        val event = Event(
            id = "event", title = "class", day = 20_000, minute = 540, createdAt = 2,
            calendarSourceId = source.id, calendarName = source.name, calendarGeneration = 1,
            calendarStartInstant = 1_800_000_000_000L, calendarEndInstant = 1_800_003_600_000L,
        )
        transactions.transaction {
            sources.insert(listOf(source))
            events.insert(listOf(event))
        }
        assertEquals(listOf(source), sources.all())
        assertEquals(listOf(event), events.all())
        val data = PlannerData.create(database)
        val snapshot = data.sync.snapshot()
        assertTrue(snapshot.contains("work"))
        assertFalse(snapshot.contains("private"))
    }

    @Test
    fun failedReconciliationRollsBackEventsCacheAndDeletionMarksTogether() = runBlocking {
        var fail = false
        val failing = object : Store<CalendarSubscription> by sources {
            override suspend fun update(items: List<CalendarSubscription>) {
                if (fail) error("database write failed")
                sources.update(items)
            }
        }
        var response = CalendarDownload(feed("old"))
        val calendars = CalendarSubscriptions(failing, events, ledger, transactions, CalendarTransport { response }, clock = { now++ }, wall = { now })
        val id = calendars.subscribe("work", "https://example.com/feed")
        calendars.refresh(id)
        val goodEvents = events.all()
        val goodSource = sources.all()
        val goodGone = ledger.gone()
        response = CalendarDownload(feed("replacement"))
        fail = true
        assertTrue(runCatching { calendars.refresh(id) }.exceptionOrNull() is IllegalStateException)
        assertEquals(goodEvents, events.all())
        assertEquals(goodSource, sources.all())
        assertEquals(goodGone, ledger.gone())
    }

    @Test
    fun removingAnEmptyCalendarEmitsChangesSoItsSourceTombstoneWillBePublished() = runBlocking {
        val data = PlannerData.create(database)
        val id = data.calendars.subscribe("empty", "https://example.com/empty")
        assertTrue(events.all().isEmpty())
        val observed = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(5_000) { data.changes.first { ledger.gone().any { it.id == id } } }
        }
        data.calendars.remove(id)
        observed.await()
        assertTrue(sources.all().isEmpty())
        assertEquals(id, ledger.gone().single().id)
        assertTrue(data.sync.snapshot().contains(id))
    }

    private fun feed(uid: String) =
        "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nBEGIN:VEVENT\r\nUID:$uid\r\nSUMMARY:meeting\r\nDTSTART:20261110T090000Z\r\nEND:VEVENT\r\nEND:VCALENDAR"
}
