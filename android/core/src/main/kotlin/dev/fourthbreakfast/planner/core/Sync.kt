package dev.fourthbreakfast.planner.core

import java.util.UUID

/** Something deleted for good, remembered so that another device does not bring it back. */
data class Gone(val id: String, val at: Long)

/**
 * What sync keeps beside the things themselves. A store deletes for good by also adding to [gone], so every way of
 * deleting leaves its mark.
 */
interface SyncLedger {
    suspend fun gone(): List<Gone>

    suspend fun addGone(items: List<Gone>)

    /** For each id, the updatedAt that this device and another last agreed on. */
    suspend fun agreed(): Map<String, Long>

    suspend fun setAgreed(items: Map<String, Long>)
}

/**
 * Brings what other devices have into this one. Every device writes the whole of its planner to a file of its own
 * ([snapshot]), and reads the others' files with [merge]. The newer version of a thing wins. A note changed on both
 * devices since they last agreed keeps the losing words too, as a copy in the bin. Merging the same file twice
 * changes nothing the second time, so devices settle.
 */
class Sync(
    private val tasks: TaskStore,
    private val events: EventStore,
    private val notes: NoteStore,
    private val ledger: SyncLedger,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun snapshot(): String = tasks.transaction { encodeBackup(tasks.all(), events.all(), notes.all(), ledger.gone()) }

    /** Takes in another device's snapshot and returns how many things changed here. */
    suspend fun merge(text: String): Int {
        val other = decodeBackup(text)
        return tasks.transaction {
            val known = ledger.gone().associate { it.id to it.at }
            val incomingGone = other.gone.groupBy { it.id }.mapValues { (_, marks) -> marks.maxOf { it.at } }
            val newGone = incomingGone.filter { (id, at) -> (known[id] ?: Long.MIN_VALUE) < at }
            if (newGone.isNotEmpty()) ledger.addGone(newGone.map { (id, at) -> Gone(id, at) })
            val gone = known + newGone
            val agreed = ledger.agreed()
            val nowAgreed = HashMap<String, Long>()
            val conflicts = ArrayList<Note>()

            val changed = mergeKind(
                local = tasks.all(),
                incoming = other.tasks,
                id = Task::id,
                updatedAt = Task::updatedAt,
                gone = gone,
                incomingGone = incomingGone,
                agreed = nowAgreed,
                store = Writer(tasks::insert, tasks::update, tasks::delete),
            ) + mergeKind(
                local = events.all(),
                incoming = other.events,
                id = Event::id,
                updatedAt = Event::updatedAt,
                gone = gone,
                incomingGone = incomingGone,
                agreed = nowAgreed,
                store = Writer(events::insert, events::update, events::delete),
            ) + mergeKind(
                local = notes.all(),
                incoming = other.notes,
                id = Note::id,
                updatedAt = Note::updatedAt,
                gone = gone,
                incomingGone = incomingGone,
                agreed = nowAgreed,
                store = Writer(notes::insert, notes::update, notes::delete),
                beforeReplacing = { mine, theirs ->
                    val base = agreed[mine.id]
                    val bothChanged = base == null || (mine.updatedAt > base && theirs.updatedAt > base)
                    if (bothChanged && (mine.title != theirs.title || mine.body != theirs.body)) {
                        val now = clock()
                        conflicts += mine.copy(id = UUID.randomUUID().toString(), deletedAt = now, updatedAt = now)
                    }
                },
            )
            if (conflicts.isNotEmpty()) notes.insert(conflicts)
            val moved = nowAgreed.filter { (id, at) -> agreed[id] != at }
            if (moved.isNotEmpty()) ledger.setAgreed(moved)
            changed + conflicts.size
        }
    }

    private class Writer<T>(val insert: suspend (List<T>) -> Unit, val update: suspend (List<T>) -> Unit, val delete: suspend (List<T>) -> Unit)

    private suspend fun <T> mergeKind(
        local: List<T>,
        incoming: List<T>,
        id: (T) -> String,
        updatedAt: (T) -> Long,
        gone: Map<String, Long>,
        incomingGone: Map<String, Long>,
        agreed: MutableMap<String, Long>,
        store: Writer<T>,
        beforeReplacing: (mine: T, theirs: T) -> Unit = { _, _ -> },
    ): Int {
        // deleted for good elsewhere, and not changed here since
        val removed = local.filter { item -> incomingGone[id(item)]?.let { updatedAt(item) <= it } == true }
        val removedIds = removed.mapTo(HashSet(), id)
        val mine = local.filterNot { id(it) in removedIds }.associateBy(id)
        val added = ArrayList<T>()
        val replaced = ArrayList<T>()
        for (theirs in incoming.groupBy(id).map { (_, copies) -> copies.maxBy(updatedAt) }) {
            val key = id(theirs)
            if (key in removedIds) continue
            val here = mine[key]
            // something deleted for good here only comes back when it was changed after that
            val wanted = here != null || gone[key]?.let { updatedAt(theirs) > it } != false
            if (!wanted) continue
            when {
                here == null -> added += theirs
                updatedAt(theirs) > updatedAt(here) -> {
                    beforeReplacing(here, theirs)
                    replaced += theirs
                }
            }
            agreed[key] = updatedAt(theirs)
        }
        if (removed.isNotEmpty()) store.delete(removed)
        if (added.isNotEmpty()) store.insert(added)
        if (replaced.isNotEmpty()) store.update(replaced)
        return removed.size + added.size + replaced.size
    }
}
