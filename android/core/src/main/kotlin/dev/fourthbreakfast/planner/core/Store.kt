package dev.fourthbreakfast.planner.core

import kotlinx.coroutines.flow.Flow

/**
 * Where one kind of thing is kept: the tasks, the events or the notes. It knows nothing about the rules, those live in
 * [Tasks], [Events] and [Notes]. A store that deletes for good also leaves the mark sync needs, see [SyncLedger].
 */
interface Store<T> {
    fun observeAll(): Flow<List<T>>

    suspend fun all(): List<T>

    suspend fun insert(items: List<T>)

    suspend fun update(items: List<T>)

    suspend fun delete(items: List<T>)

    /** Runs [block] as one change: a transaction inside another one joins it. */
    suspend fun <R> transaction(block: suspend () -> R): R
}

typealias TaskStore = Store<Task>

typealias EventStore = Store<Event>

typealias NoteStore = Store<Note>

/**
 * Runs a consistent read or an atomic change across every store and the sync ledger. All participants must belong to
 * the same transaction context; a failure rolls back their writes together, and nested transactions join the caller.
 */
interface TransactionRunner {
    suspend fun <R> transaction(block: suspend () -> R): R
}

/**
 * Compatibility adapter for stores that already share a transaction context. In-memory rule tests may use their
 * no-op store transactions; production callers must provide a runner covering all stores and the ledger together.
 */
class StoreTransactionRunner(private val store: Store<*>) : TransactionRunner {
    override suspend fun <R> transaction(block: suspend () -> R): R = store.transaction(block)
}
