package dev.fourthbreakfast.planner.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Everything the app can do with the agenda. */
class Events(
    private val store: EventStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** Every event, the bin included, soonest first. */
    val all: Flow<List<Event>> = store.observeAll().map(::sortedEvents)

    /** A blank title is ignored, so an event can never be nameless. */
    suspend fun add(title: String, day: Long, minute: Int?, location: String, notes: String, endMinute: Int? = null, reminder: Int? = null) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        store.insert(
            listOf(
                Event(
                    title = trimmed,
                    day = day,
                    minute = minute?.let(::clampMinute),
                    endMinute = validEnd(minute, endMinute),
                    reminder = validReminder(minute, reminder),
                    location = location.trim(),
                    notes = notes.trim(),
                    createdAt = clock(),
                ),
            ),
        )
    }

    /** A blank title leaves the event as it was. Only events outside the bin can change. */
    suspend fun update(id: String, title: String, day: Long, minute: Int?, location: String, notes: String, endMinute: Int? = null, reminder: Int? = null) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        store.transaction {
            val event = store.all().firstOrNull { it.id == id && !it.inBin } ?: return@transaction
            val changed = event.copy(
                title = trimmed,
                day = day,
                minute = minute?.let(::clampMinute),
                endMinute = validEnd(minute, endMinute),
                reminder = validReminder(minute, reminder),
                location = location.trim(),
                notes = notes.trim(),
            )
            if (changed != event) store.update(listOf(changed.copy(updatedAt = clock())))
        }
    }

    /** Moves an event to the bin, otherwise unchanged. */
    suspend fun moveToBin(id: String) {
        val now = clock()
        store.transaction {
            val event = store.all().firstOrNull { it.id == id && !it.inBin } ?: return@transaction
            store.update(listOf(event.copy(deletedAt = now, updatedAt = now)))
        }
    }

    /** Brings an event back from the bin. */
    suspend fun restore(id: String) {
        val now = clock()
        store.transaction {
            val event = store.all().firstOrNull { it.id == id && it.inBin } ?: return@transaction
            store.update(listOf(event.copy(deletedAt = null, updatedAt = now)))
        }
    }

    /** Only what is in the bin can be deleted for good. */
    suspend fun deleteForever(id: String) {
        store.transaction { store.all().firstOrNull { it.id == id && it.inBin }?.let { store.delete(listOf(it)) } }
    }

    suspend fun emptyBin() {
        store.transaction { store.delete(store.all().filter { it.inBin }) }
    }

    private fun clampMinute(minute: Int) = minute.coerceIn(0, 24 * 60 - 1)

    /** A reminder only counts with a start time, and cannot be a negative number of minutes. */
    private fun validReminder(start: Int?, reminder: Int?): Int? = reminder?.takeIf { start != null && it >= 0 }

    /** An end only counts when there is a start and it comes after it, on the same day. */
    private fun validEnd(start: Int?, end: Int?): Int? {
        if (start == null || end == null) return null
        return clampMinute(end).takeIf { it > clampMinute(start) }
    }
}
