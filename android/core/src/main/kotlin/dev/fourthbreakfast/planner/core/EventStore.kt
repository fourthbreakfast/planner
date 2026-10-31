package dev.fourthbreakfast.planner.core

import kotlinx.coroutines.flow.Flow

/** Where events are kept. It knows nothing about the rules, those live in [Events]. */
interface EventStore {
    fun observeAll(): Flow<List<Event>>

    suspend fun all(): List<Event>

    suspend fun insert(events: List<Event>)

    suspend fun update(events: List<Event>)

    suspend fun delete(events: List<Event>)

    suspend fun <T> transaction(block: suspend () -> T): T
}
