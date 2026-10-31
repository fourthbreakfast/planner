package dev.fourthbreakfast.planner.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Everything the app can do with the agenda. */
class Events(
    private val store: EventStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** Every event, soonest first. */
    val all: Flow<List<Event>> = store.observeAll().map(::sortedEvents)

    /** A blank title is ignored, so an event can never be nameless. */
    suspend fun add(title: String, day: Long, minute: Int?, location: String, notes: String) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        store.insert(listOf(Event(title = trimmed, day = day, minute = minute?.let(::clampMinute), location = location.trim(), notes = notes.trim(), createdAt = clock())))
    }

    /** Changes everything but the title blank rule: a blank title leaves the event as it was. */
    suspend fun update(id: String, title: String, day: Long, minute: Int?, location: String, notes: String) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        store.transaction {
            val event = store.all().firstOrNull { it.id == id } ?: return@transaction
            val changed = event.copy(
                title = trimmed,
                day = day,
                minute = minute?.let(::clampMinute),
                location = location.trim(),
                notes = notes.trim(),
            )
            if (changed != event) store.update(listOf(changed.copy(updatedAt = clock())))
        }
    }

    /** Takes the event away and returns it, so it can be put back. */
    suspend fun remove(id: String): Event? = store.transaction {
        val event = store.all().firstOrNull { it.id == id } ?: return@transaction null
        store.delete(listOf(event))
        event
    }

    /** Puts a removed event back, as it was. */
    suspend fun restore(event: Event) {
        store.transaction {
            if (store.all().none { it.id == event.id }) store.insert(listOf(event))
        }
    }

    private fun clampMinute(minute: Int) = minute.coerceIn(0, 24 * 60 - 1)
}
