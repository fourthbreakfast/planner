package dev.fourthbreakfast.planner.core

import kotlinx.coroutines.flow.Flow

/** Where tasks are kept. It knows nothing about the rules, those live in [Tasks]. */
interface TaskStore {
    fun observeAll(): Flow<List<Task>>

    suspend fun all(): List<Task>

    suspend fun insert(tasks: List<Task>)

    suspend fun update(tasks: List<Task>)

    suspend fun delete(tasks: List<Task>)

    suspend fun <T> transaction(block: suspend () -> T): T
}
