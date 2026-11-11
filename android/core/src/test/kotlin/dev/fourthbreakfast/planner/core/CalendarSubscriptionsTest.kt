package dev.fourthbreakfast.planner.core

import java.io.IOException
import java.time.Instant
import java.util.TimeZone
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarSubscriptionsTest {
    private class Fixture {
        var wall = Instant.parse("2026-11-09T12:00:00Z").toEpochMilli()
        var now = wall
        var response = CalendarDownload(feed("20261110T090000Z"), etag = "v1")
        var failure: Exception? = null
        var duringFetch: suspend () -> Unit = {}
        val sources = MemoryStore(CalendarSubscription::id)
        val events = MemoryEventStore()
        val ledger = MemoryLedger()
        val tasks = MemoryTaskStore()
        val notes = MemoryNoteStore()
        val calendars = CalendarSubscriptions(
            sources,
            events,
            ledger,
            StoreTransactionRunner(events),
            CalendarTransport {
                duringFetch()
                failure?.let { throw it }
                response
            },
            clock = { now++ },
            wall = { wall },
        )
        val sync = Sync(tasks, events, notes, ledger, clock = { now++ }, saw = { now = maxOf(now, it + 1) })
    }

    @Test
    fun refreshReconcilesStableSourceScopedIdsAndLeavesImportedEventsAlone() = runBlocking {
        val f = Fixture()
        val imported = Event(id = "import", title = "mine", day = 20_000, createdAt = 1)
        f.events.insert(listOf(imported))
        val source = f.calendars.subscribe("work", "webcal://EXAMPLE.com/calendar?token=private")
        f.calendars.refresh(source)
        val first = f.events.all().single { it.readOnly }
        f.response = CalendarDownload(feed("20261112T100000Z", "renamed"), etag = "v2")
        f.calendars.refresh(source)
        val moved = f.events.all().single { it.readOnly }
        assertEquals(first.id, moved.id)
        assertEquals(first.createdAt, moved.createdAt)
        assertEquals("renamed", moved.title)
        assertEquals(600, moved.minute)
        assertEquals("work", moved.calendarName)
        assertEquals(imported, f.events.all().single { !it.readOnly })
        val unchanged = moved.updatedAt
        f.calendars.refresh(source)
        assertEquals(unchanged, f.events.all().single { it.readOnly }.updatedAt)

        val other = f.calendars.subscribe("personal", "https://example.com/other")
        f.calendars.refresh(other)
        assertEquals(2, f.events.all().count { it.readOnly })
        assertEquals(2, f.events.all().filter { it.readOnly }.map { it.id }.toSet().size)
        f.response = CalendarDownload(EMPTY_FEED)
        f.calendars.refresh(source)
        assertEquals(listOf(imported.id, f.events.all().single { it.calendarSourceId == other }.id).toSet(), f.events.all().map { it.id }.toSet())
    }

    @Test
    fun failuresKeepLastGoodEventsAndCacheButAValidEmptyFeedRemovesOccurrences() = runBlocking {
        val f = Fixture()
        val source = f.calendars.subscribe("work", "https://example.com/feed?secret=hidden")
        f.calendars.refresh(source)
        val good = f.events.all()
        val cache = f.sources.all().single().cachedFeed
        f.failure = IOException("https://example.com/feed?secret=hidden")
        f.calendars.refresh(source)
        assertEquals(good, f.events.all())
        assertEquals(CalendarFailure.NETWORK, f.sources.all().single().failure)
        assertFalse(f.sources.all().single().toString().contains("hidden"))
        f.failure = null
        f.response = CalendarDownload("BEGIN:VCALENDAR\r\nBEGIN:VEVENT\r\nUID:broken\r\nEND:VCALENDAR")
        f.calendars.refresh(source)
        assertEquals(good, f.events.all())
        assertEquals(cache, f.sources.all().single().cachedFeed)
        assertEquals(CalendarFailure.INVALID_FEED, f.sources.all().single().failure)
        f.response = CalendarDownload(EMPTY_FEED)
        f.calendars.refresh(source)
        assertTrue(f.events.all().isEmpty())
        assertEquals(null, f.sources.all().single().failure)
    }

    @Test
    fun notModifiedReexpandsTheCachedRecurringFeedAsTimeMoves() = runBlocking {
        val f = Fixture()
        f.response = CalendarDownload(feed("20000101T090000Z", extra = "RRULE:FREQ=DAILY"), etag = "same")
        val source = f.calendars.subscribe("daily", "https://example.com/feed")
        f.calendars.refresh(source)
        val previousWindow = f.sources.all().single().throughDay!!
        val previousIds = f.events.all().map { it.id }.toSet()
        f.wall = Instant.parse("2027-11-09T12:00:00Z").toEpochMilli()
        f.response = CalendarDownload(notModified = true)
        f.calendars.refresh(source)
        assertTrue(f.sources.all().single().throughDay!! > previousWindow)
        assertTrue(f.events.all().any { it.id !in previousIds })
        assertEquals("same", f.sources.all().single().etag)
    }

    @Test
    fun sourceTombstoneRejectsOfflineEventsEvenWhenTheyWereGeneratedLater() = runBlocking {
        val a = Fixture()
        val b = Fixture()
        val source = a.calendars.subscribe("work", "https://example.com/feed")
        b.calendars.subscribe("work", "https://example.com/feed")
        a.calendars.refresh(source)
        b.calendars.refresh(source)
        a.calendars.remove(source)
        b.response = CalendarDownload(feed("20261201T090000Z", extra = "RRULE:FREQ=DAILY;COUNT=3"))
        b.now += 50_000
        b.calendars.refresh(source)
        assertTrue(b.events.all().isNotEmpty())
        a.sync.merge(b.sync.snapshot())
        assertTrue(a.events.all().isEmpty())
        b.sync.merge(a.sync.snapshot())
        assertTrue(b.events.all().isEmpty())
        b.calendars.refresh(source)
        assertTrue(b.sources.all().isEmpty())
        assertFalse(b.calendars.hasLocalSubscriptions.first())

        a.calendars.subscribe("work again", "https://example.com/feed")
        a.calendars.refresh(source)
        assertTrue(a.events.all().isNotEmpty())
        assertTrue(a.events.all().all { it.calendarGeneration > a.ledger.gone().first { mark -> mark.id == source }.at })
        b.sync.merge(a.sync.snapshot())
        assertEquals(a.events.all(), b.events.all())
    }

    @Test
    fun readOnlyEventsCannotBeEditedDetachedCompletedOrBinned() = runBlocking {
        val store = MemoryEventStore()
        val subscribed = Event(id = "source-event", title = "read only", day = 1, createdAt = 1, calendarSourceId = "source")
        store.insert(listOf(subscribed))
        val events = Events(store, clock = { 99 })
        events.update(subscribed.id, "changed", 2, null, "", "")
        events.skip(subscribed.id, 1)
        events.detach(subscribed.id, 1, "changed", 2, null, "", "", null, null, null)
        events.moveToBin(subscribed.id)
        events.completeTask(subscribed.id)
        assertEquals(listOf(subscribed), store.all())
        val binned = subscribed.copy(deletedAt = 3)
        store.update(listOf(binned))
        events.restore(subscribed.id)
        events.deleteForever(subscribed.id)
        events.emptyBin()
        assertEquals(listOf(binned), store.all())
    }

    @Test
    fun anUnsubscribeReceivedWhileFetchingWinsBeforeReconciliationCommits() = runBlocking {
        val f = Fixture()
        val id = f.calendars.subscribe("work", "https://example.com/feed")
        f.calendars.refresh(id)
        assertEquals(1, f.events.all().size)
        f.response = CalendarDownload(feed("20261201T090000Z", extra = "RRULE:FREQ=DAILY;COUNT=3"))
        val sourceGeneration = f.sources.all().single().createdAt
        f.duringFetch = { f.ledger.addGone(listOf(Gone(id, f.now++))) }
        f.calendars.refresh(id)
        assertTrue(f.events.all().isEmpty())
        assertTrue(f.sources.all().isEmpty())
        assertTrue(f.ledger.gone().single().at > sourceGeneration)
        assertTrue(f.calendars.refreshing.value.isEmpty())
    }

    @Test
    fun privateLinksAreNormalizedWithoutChangingEscapedTokensAndNeverEnterSnapshots() = runBlocking {
        assertEquals("https://example.com/feed%2Fprivate?token=A%2Fb%3D", calendarUrl(" WEBCAL://EXAMPLE.com/feed%2Fprivate?token=A%2Fb%3D#ignored "))
        for (url in listOf("http://example.com/feed", "https://user:password@example.com/feed", "file:///feed.ics", "https://example.com:0/feed")) {
            val error = runCatching { calendarUrl(url) }.exceptionOrNull() as CalendarFeedException
            assertEquals(CalendarFailure.INVALID_URL, error.reason)
            assertEquals(null, error.message)
        }
        val f = Fixture()
        val id = f.calendars.subscribe("work", "https://example.com/feed?token=private-token")
        f.calendars.refresh(id)
        val snapshot = f.sync.snapshot()
        assertFalse(snapshot.contains("private-token"))
        assertFalse(snapshot.contains("BEGIN:VCALENDAR"))
        assertEquals(f.events.all(), decodeBackup(snapshot).events)
    }

    @Test
    fun devicesInDifferentZonesStoreAndSyncTheSameCanonicalEventsButDisplayTheirOwnTime() = runBlocking {
        val previous = TimeZone.getDefault()
        try {
            val madrid = Fixture()
            val newYork = Fixture()
            for ((fixture, zone) in listOf(madrid to "Europe/Madrid", newYork to "America/New_York")) {
                TimeZone.setDefault(TimeZone.getTimeZone(zone))
                fixture.wall = Instant.parse("2026-11-09T00:15:00Z").toEpochMilli()
                fixture.response = CalendarDownload(feed("20261110T090000Z", extra = "DURATION:PT1H\r\nRRULE:FREQ=WEEKLY;COUNT=2"))
                val source = fixture.calendars.subscribe("work", "https://example.com/feed")
                fixture.calendars.refresh(source)
                val shown = Events(fixture.events).all.first().first()
                assertEquals(if (zone == "Europe/Madrid") 600 else 240, shown.minute)
                assertEquals(if (zone == "Europe/Madrid") 660 else 300, shown.endMinute)
            }
            assertEquals(madrid.events.all(), newYork.events.all())
            assertEquals(madrid.sources.all().single().fromDay, newYork.sources.all().single().fromDay)
            assertEquals(0, madrid.sync.merge(newYork.sync.snapshot()))
            assertEquals(0, newYork.sync.merge(madrid.sync.snapshot()))
            assertTrue(madrid.events.all().all { it.minute == 540 && it.calendarStartInstant != null })
        } finally {
            TimeZone.setDefault(previous)
        }
    }

    @Test
    fun interruptedRefreshPassLetsUntouchedAndOldestSourcesLeadTheNextRun() = runBlocking {
        val wall = Instant.parse("2026-11-09T12:00:00Z").toEpochMilli()
        val sources = MemoryStore(CalendarSubscription::id)
        sources.insert(
            listOf(
                CalendarSubscription("d", "a newer", "https://example.com/d", 1, lastAttempt = wall - CalendarSubscriptions.REFRESH_MILLIS),
                CalendarSubscription("b", "b untouched", "https://example.com/b", 1),
                CalendarSubscription("c", "c oldest", "https://example.com/c", 1, lastAttempt = wall - 2 * CalendarSubscriptions.REFRESH_MILLIS),
                CalendarSubscription("a", "z untouched", "https://example.com/a", 1),
                CalendarSubscription("e", "already fresh", "https://example.com/e", 1, lastAttempt = wall),
            ),
        )
        val events = MemoryEventStore()
        val calls = mutableListOf<String>()
        var interrupt = true
        val calendars = CalendarSubscriptions(
            sources,
            events,
            MemoryLedger(),
            StoreTransactionRunner(events),
            CalendarTransport { source ->
                calls += source.id
                if (interrupt && calls.size == 3) throw kotlinx.coroutines.CancellationException("background budget reached")
                CalendarDownload(EMPTY_FEED)
            },
            clock = { wall },
            wall = { wall },
        )
        assertTrue(runCatching { calendars.refreshDue() }.exceptionOrNull() is kotlinx.coroutines.CancellationException)
        assertEquals(listOf("a", "b", "c"), calls)
        calls.clear()
        interrupt = false
        calendars.refreshDue()
        assertEquals(listOf("c", "d"), calls)
    }

    companion object {
        private const val EMPTY_FEED = "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nEND:VCALENDAR"
        private fun feed(start: String, title: String = "meeting", extra: String = "") =
            "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nBEGIN:VEVENT\r\nUID:stable\r\nSUMMARY:$title\r\nDTSTART:$start\r\n$extra\r\nEND:VEVENT\r\nEND:VCALENDAR"
    }
}
