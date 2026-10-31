package dev.fourthbreakfast.planner.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks")
    suspend fun getAll(): List<TaskEntity>

    @Insert
    suspend fun insertAll(tasks: List<TaskEntity>)

    @Update
    suspend fun updateAll(tasks: List<TaskEntity>)

    @Delete
    suspend fun deleteAll(tasks: List<TaskEntity>)
}
