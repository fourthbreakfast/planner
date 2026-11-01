package dev.fourthbreakfast.planner.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Everything the app can do with tasks. Any screen, on any platform, goes through here. */
class Tasks(private val store: TaskStore, private val clock: () -> Long = System::currentTimeMillis) {
    /** Every task, including the bin, in tree order. */
    val all: Flow<List<Task>> = store.observeAll().map { sorted(it, SortOrder.TREE) }

    /** A done task must never hide open work, so a new open task reopens the tasks above it. */
    suspend fun add(title: String, parentId: String? = null) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        val now = clock()
        store.transaction {
            if (parentId != null && depthOf(active(), parentId) + 1 > MAX_DEPTH) return@transaction
            val position = siblings(active(), parentId).maxOfOrNull { it.position } ?: 0.0
            store.insert(
                listOf(
                    Task(
                        title = trimmed,
                        parentId = parentId,
                        position = position + 1,
                        createdAt = now,
                    ),
                ),
            )
            reopenAncestors(parentId, now)
        }
    }

    /** A blank title is ignored, so a task can never end up nameless. */
    suspend fun rename(id: String, title: String) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        val now = clock()
        store.transaction {
            val task = active().firstOrNull { it.id == id } ?: return@transaction
            if (task.title != trimmed) store.update(listOf(task.copy(title = trimmed, updatedAt = now)))
        }
    }

    /** Checking covers everything inside. Unchecking reopens the tasks above. */
    suspend fun setDone(id: String, done: Boolean) {
        val now = clock()
        store.transaction {
            val active = active()
            val task = active.firstOrNull { it.id == id } ?: return@transaction
            if (done) {
                store.update(subtree(active, id).filterNot { it.done }.map { it.copy(done = true, updatedAt = now) })
            } else {
                store.update(listOf(task.copy(done = false, updatedAt = now)))
                reopenAncestors(task.parentId, now)
            }
        }
    }

    suspend fun cyclePriority(id: String) {
        val now = clock()
        store.transaction {
            val task = active().firstOrNull { it.id == id } ?: return@transaction
            store.update(listOf(task.copy(priority = task.priority.next(), updatedAt = now)))
        }
    }

    /**
     * Moves a task, with everything inside it, to a new place. A task cannot move inside itself.
     * Open work moved under a done task reopens the tasks above, like adding does.
     */
    suspend fun move(id: String, to: Destination) {
        val now = clock()
        store.transaction {
            val active = active()
            val task = active.firstOrNull { it.id == id } ?: return@transaction
            val moving = subtree(active, id)
            if (to.parentId != null && moving.any { it.id == to.parentId }) return@transaction
            // a task with subtasks takes its levels with it, so all of them must still fit
            val newDepth = if (to.parentId == null) 0 else depthOf(active, to.parentId) + 1
            if (newDepth + height(active, id) > MAX_DEPTH) return@transaction
            val others = siblings(active, to.parentId).filter { it.id != id }
            val index = when {
                to.beforeId != null -> others.indexOfFirst { it.id == to.beforeId }.takeIf { it >= 0 } ?: others.size
                to.afterId != null -> others.indexOfFirst { it.id == to.afterId }.let { if (it >= 0) it + 1 else others.size }
                else -> others.size
            }
            val moved = task.copy(parentId = to.parentId, updatedAt = now)
            val ordered = others.toMutableList().apply { add(index, moved) }
            val changed = ordered.mapIndexedNotNull { i, sibling ->
                val position = (i + 1).toDouble()
                when {
                    sibling.id == id -> sibling.copy(position = position)
                    sibling.position != position -> sibling.copy(position = position, updatedAt = now)
                    else -> null
                }
            }
            store.update(changed)
            if (moving.any { !it.done }) reopenAncestors(to.parentId, now)
        }
    }

    /** Moves the task and everything inside it to the bin, otherwise unchanged. */
    suspend fun moveToBin(id: String) {
        val now = clock()
        store.transaction {
            store.update(subtree(active(), id).map { it.copy(deletedAt = now, updatedAt = now) })
        }
    }

    /** Brings back what was binned together with this task. */
    suspend fun restore(id: String) {
        val now = clock()
        store.transaction {
            val batch = binBatch(store.all(), id)
            store.update(batch.map { it.copy(deletedAt = null, updatedAt = now) })
            val root = batch.firstOrNull() ?: return@transaction
            if (batch.any { !it.done }) reopenAncestors(root.parentId, now)
        }
    }

    suspend fun deleteForever(id: String) {
        store.transaction { store.delete(binBatch(store.all(), id)) }
    }

    suspend fun emptyBin() {
        store.transaction { store.delete(store.all().filter { it.inBin }) }
    }

    private suspend fun active(): List<Task> = store.all().filterNot { it.inBin }

    private suspend fun reopenAncestors(parentId: String?, now: Long) {
        val reopened = ancestors(active(), parentId).filter { it.done }
        store.update(reopened.map { it.copy(done = false, updatedAt = now) })
    }
}
