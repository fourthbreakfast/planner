package dev.fourthbreakfast.planner.core

import java.net.URI
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class CalendarFailure { INVALID_URL, INVALID_FEED, UNSUPPORTED_FEED, TOO_LARGE, NETWORK, AUTHENTICATION, SERVER }

/** URL/cache details belong to this device. Only source IDs, names and their events go through planner sync. */
data class CalendarSubscription(
    val id: String,
    val name: String,
    val url: String,
    val createdAt: Long,
    val lastAttempt: Long? = null,
    val lastSuccess: Long? = null,
    val etag: String? = null,
    val lastModified: String? = null,
    val failure: CalendarFailure? = null,
    val fromDay: Long? = null,
    val throughDay: Long? = null,
    val cachedFeed: String? = null,
) {
    override fun toString(): String = "CalendarSubscription(id=$id, name=$name, url=<private>, failure=$failure)"
}

data class CalendarDownload(val text: String? = null, val etag: String? = null, val lastModified: String? = null, val notModified: Boolean = false)

fun interface CalendarTransport {
    suspend fun fetch(subscription: CalendarSubscription): CalendarDownload
}

/** webcal[s] is a calendar link, not a separate file format. Prefer TLS even for unqualified webcal links. */
fun calendarUrl(value: String): String {
    val raw = value.trim()
    val mapped = raw.replace(Regex("^webcals?://", RegexOption.IGNORE_CASE), "https://")
    val uri = runCatching { URI(mapped) }.getOrNull()
    if (uri == null || !uri.scheme.equals("https", true) || uri.host.isNullOrBlank() || uri.rawUserInfo != null || uri.port !in -1..65535 || uri.port == 0) {
        throw CalendarFeedException(CalendarFailure.INVALID_URL)
    }
    // Preserve encoded path/query bytes exactly: private feed tokens may contain percent-encoded characters.
    return URI(
        buildString {
            append("https://").append(uri.rawAuthority.lowercase()).append(uri.rawPath.ifEmpty { "/" })
            uri.rawQuery?.let { append('?').append(it) }
        },
    ).toASCIIString()
}

internal fun Event.removedByCalendar(gone: Map<String, Long>): Boolean = calendarSourceId?.let { source ->
    gone[source]?.let { calendarGeneration <= it }
} == true

class CalendarSubscriptions(
    private val subscriptions: Store<CalendarSubscription>,
    private val events: EventStore,
    private val ledger: SyncLedger,
    private val transactions: TransactionRunner,
    private val transport: CalendarTransport = HttpsCalendarTransport(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val wall: () -> Long = System::currentTimeMillis,
) {
    private val lock = Mutex()
    private val busy = MutableStateFlow<Set<String>>(emptySet())
    val refreshing: StateFlow<Set<String>> = busy

    /** Only locally held feed links require network work on this device. */
    val hasLocalSubscriptions: Flow<Boolean> = subscriptions.observeAll().map { it.isNotEmpty() }.distinctUntilChanged()

    /** Includes calendars refreshed by another paired device, so they can still be removed from this device. */
    val all: Flow<List<CalendarSubscription>> = combine(subscriptions.observeAll(), events.observeAll()) { local, incoming ->
        val known = local.mapTo(HashSet()) { it.id }
        val remote = incoming.filter { !it.inBin && it.calendarSourceId != null && it.calendarSourceId !in known }
            .groupBy { it.calendarSourceId!! }.map { (id, entries) ->
                val newest = entries.maxBy { it.updatedAt }
                CalendarSubscription(id, newest.calendarName ?: "calendar", "", newest.calendarGeneration)
            }
        (local + remote).sortedWith(compareBy({ it.name.lowercase() }, { it.id }))
    }

    suspend fun subscribe(name: String, url: String): String = lock.withLock {
        val normalized = calendarUrl(url)
        val id = UUID.nameUUIDFromBytes("planner-calendar:$normalized".toByteArray(Charsets.UTF_8)).toString()
        if (subscriptions.all().none { it.id == id }) {
            val title = name.trim().take(100).ifEmpty { URI(normalized).host }
            subscriptions.insert(listOf(CalendarSubscription(id, title, normalized, clock())))
        }
        id
    }

    suspend fun refreshDue(force: Boolean = false) {
        // A bounded background run may stop partway through. Untouched/oldest sources lead its next pass.
        val ordered = subscriptions.all().sortedWith(compareBy<CalendarSubscription> { it.lastAttempt ?: Long.MIN_VALUE }.thenBy { it.id })
        for (subscription in ordered) {
            val now = wall()
            val last = subscription.lastAttempt
            if (force || last == null || now < last || now - last >= REFRESH_MILLIS) refresh(subscription.id)
        }
    }

    suspend fun refresh(id: String) = lock.withLock {
        val subscription = subscriptions.all().firstOrNull { it.id == id } ?: return@withLock
        val sourceGone = ledger.gone().firstOrNull { it.id == id }?.at
        if (sourceGone != null && sourceGone >= subscription.createdAt) {
            transactions.transaction {
                val obsolete = events.all().filter { it.calendarSourceId == id && it.calendarGeneration <= sourceGone }
                if (obsolete.isNotEmpty()) events.delete(obsolete)
                subscriptions.delete(listOf(subscription))
            }
            return@withLock
        }
        busy.value = setOf(id)
        val attempted = wall()
        try {
            val response = transport.fetch(subscription)
            // Even an unchanged feed needs re-expansion as the rolling window moves.
            val text = (if (response.notModified) subscription.cachedFeed else response.text)
                ?: throw CalendarFeedException(CalendarFailure.INVALID_FEED)
            val feed = withContext(Dispatchers.Default) {
                val context = currentCoroutineContext()
                parseCalendarFeed(text, attempted, ZoneId.of("UTC"), checkCancelled = { context.ensureActive() })
            }
            transactions.transaction {
                // Sync may receive an unsubscribe while this request is in flight. Its mark must win atomically
                // with reconciliation, even if the feed itself is valid and has a newer download timestamp.
                val removedAt = ledger.gone().firstOrNull { it.id == id }?.at
                if (removedAt != null && removedAt >= subscription.createdAt) {
                    val obsolete = events.all().filter { it.calendarSourceId == id && it.calendarGeneration <= removedAt }
                    if (obsolete.isNotEmpty()) events.delete(obsolete)
                    subscriptions.delete(listOf(subscription))
                    return@transaction
                }
                val current = events.all().filter { it.calendarSourceId == id }
                val now = clock()
                run {
                    val byId = current.associateBy { it.id }
                    val incoming = feed.events.map { event ->
                        val eventId = UUID.nameUUIDFromBytes("$id:${event.id}".toByteArray(Charsets.UTF_8)).toString()
                        event.copy(
                            id = eventId,
                            createdAt = byId[eventId]?.createdAt ?: now,
                            updatedAt = now,
                            calendarSourceId = id,
                            calendarName = subscription.name,
                            calendarGeneration = subscription.createdAt,
                        )
                    }
                    val incomingIds = incoming.mapTo(HashSet()) { it.id }
                    val obsolete = current.filter { it.id !in incomingIds }
                    if (obsolete.isNotEmpty()) events.delete(obsolete)
                    val added = incoming.filter { it.id !in byId }
                    val changed = incoming.filter { candidate ->
                        byId[candidate.id]?.let { old -> candidate.copy(updatedAt = old.updatedAt) != old } == true
                    }
                    if (added.isNotEmpty()) events.insert(added)
                    if (changed.isNotEmpty()) events.update(changed)
                }
                subscriptions.update(
                    listOf(
                        subscription.copy(
                            lastAttempt = attempted,
                            lastSuccess = attempted,
                            etag = if (response.notModified) response.etag ?: subscription.etag else response.etag,
                            lastModified = if (response.notModified) response.lastModified ?: subscription.lastModified else response.lastModified,
                            failure = null,
                            fromDay = feed.fromDay,
                            throughDay = feed.throughDay,
                            cachedFeed = text,
                        ),
                    ),
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: CalendarFeedException) {
            subscriptions.update(listOf(subscription.copy(lastAttempt = attempted, failure = e.reason)))
        } catch (_: java.io.IOException) {
            subscriptions.update(listOf(subscription.copy(lastAttempt = attempted, failure = CalendarFailure.NETWORK)))
        } finally {
            busy.value = emptySet()
        }
    }

    /** The source mark also suppresses stale events sent by an offline subscribed device. Re-subscribing makes a new generation. */
    suspend fun remove(id: String) = lock.withLock {
        transactions.transaction {
            val local = subscriptions.all().filter { it.id == id }
            val managed = events.all().filter { it.calendarSourceId == id }
            if (local.isEmpty() && managed.isEmpty()) return@transaction
            if (managed.isNotEmpty()) events.delete(managed)
            if (local.isNotEmpty()) subscriptions.delete(local)
            ledger.addGone(listOf(Gone(id, clock())))
        }
    }

    companion object {
        const val REFRESH_MILLIS = 30 * 60 * 1000L
    }
}
