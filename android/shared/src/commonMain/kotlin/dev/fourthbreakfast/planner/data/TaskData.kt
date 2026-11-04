package dev.fourthbreakfast.planner.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import dev.fourthbreakfast.planner.core.Priority
import dev.fourthbreakfast.planner.core.Task
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "tasks", indices = [Index("parentId")])
data class TaskEntity(
    @PrimaryKey val id: String,
    val title: String,
    val done: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val parentId: String?,
    @ColumnInfo(defaultValue = "0") val priority: Int,
    val deletedAt: Long?,
    @ColumnInfo(defaultValue = "0") val position: Double,
)

fun TaskEntity.toTask() = Task(
    id = id,
    title = title,
    done = done,
    createdAt = createdAt,
    updatedAt = updatedAt,
    parentId = parentId,
    priority = Priority.entries.getOrElse(priority) { Priority.NONE },
    deletedAt = deletedAt,
    position = position,
)

fun Task.toEntity() = TaskEntity(
    id = id,
    title = title,
    done = done,
    createdAt = createdAt,
    updatedAt = updatedAt,
    parentId = parentId,
    priority = priority.ordinal,
    deletedAt = deletedAt,
    position = position,
)

@Dao
interface TaskDao : EntityDao<TaskEntity> {
    @Query("SELECT * FROM tasks")
    override fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks")
    override suspend fun getAll(): List<TaskEntity>
}
