package dev.fourthbreakfast.planner.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * A store that keeps everything in memory, which is all a test of the rules needs. [onDelete] hears of what is deleted
 * for good, so a test can leave the mark for sync that a database store leaves.
 */
class MemoryStore<T>(private val id: (T) -> String, private val onDelete: (List<T>) -> Unit = {}) : Store<T> {
    val state = MutableStateFlow<List<T>>(emptyList())

    override fun observeAll(): Flow<List<T>> = state

    override suspend fun all(): List<T> = state.value

    override suspend fun insert(items: List<T>) {
        state.value = state.value + items
    }

    override suspend fun update(items: List<T>) {
        val changed = items.associateBy(id)
        state.value = state.value.map { changed[id(it)] ?: it }
    }

    override suspend fun delete(items: List<T>) {
        val gone = items.mapTo(HashSet(), id)
        state.value = state.value.filterNot { id(it) in gone }
        onDelete(items)
    }

    override suspend fun <R> transaction(block: suspend () -> R): R = block()
}

@Suppress("FunctionName")
fun MemoryTaskStore(onDelete: (List<Task>) -> Unit = {}) = MemoryStore(Task::id, onDelete)

@Suppress("FunctionName")
fun MemoryEventStore(onDelete: (List<Event>) -> Unit = {}) = MemoryStore(Event::id, onDelete)

@Suppress("FunctionName")
fun MemoryNoteStore(onDelete: (List<Note>) -> Unit = {}) = MemoryStore(Note::id, onDelete)

/** What sync remembers, in memory. */
class MemoryLedger : SyncLedger {
    val gone = ArrayList<Gone>()
    val agreed = HashMap<String, Long>()

    override suspend fun gone() = gone.toList()

    override suspend fun addGone(items: List<Gone>) {
        gone.removeAll { old -> items.any { it.id == old.id } }
        gone += items
    }

    override suspend fun forgetGone(ids: List<String>) {
        gone.removeAll { it.id in ids }
    }

    override suspend fun agreed() = agreed.toMap()

    override suspend fun setAgreed(items: Map<String, Long>) {
        agreed += items
    }
}
