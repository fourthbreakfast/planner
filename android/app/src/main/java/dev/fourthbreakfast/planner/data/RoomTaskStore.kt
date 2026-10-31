package dev.fourthbreakfast.planner.data

import androidx.room.withTransaction
import dev.fourthbreakfast.planner.PlannerDatabase
import dev.fourthbreakfast.planner.core.Task
import dev.fourthbreakfast.planner.core.TaskStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomTaskStore(private val database: PlannerDatabase) : TaskStore {
    private val dao = database.taskDao()

    override fun observeAll(): Flow<List<Task>> = dao.observeAll().map { rows -> rows.map { it.toTask() } }

    override suspend fun all(): List<Task> = dao.getAll().map { it.toTask() }

    override suspend fun insert(tasks: List<Task>) = dao.insertAll(tasks.map { it.toEntity() })

    override suspend fun update(tasks: List<Task>) = dao.updateAll(tasks.map { it.toEntity() })

    override suspend fun delete(tasks: List<Task>) = dao.deleteAll(tasks.map { it.toEntity() })

    override suspend fun <T> transaction(block: suspend () -> T): T = database.withTransaction { block() }
}
