package dev.fourthbreakfast.planner.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import dev.fourthbreakfast.planner.PlannerDatabase
import dev.fourthbreakfast.planner.core.Habit
import dev.fourthbreakfast.planner.core.HabitCheck
import dev.fourthbreakfast.planner.core.Store
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey val id: String,
    val title: String,
    val startDay: Long,
    val weekdays: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
)

@Entity(tableName = "habit_checks", indices = [Index("habitId")])
data class HabitCheckEntity(@PrimaryKey val id: String, val habitId: String, val day: Long, val done: Boolean, val createdAt: Long, val updatedAt: Long)

fun HabitEntity.toHabit() = Habit(id, title, startDay, weekdays.split(',').mapNotNull(String::toIntOrNull).toSet(), createdAt, updatedAt, deletedAt)
fun Habit.toEntity() = HabitEntity(id, title, startDay, weekdays.sorted().joinToString(","), createdAt, updatedAt, deletedAt)
fun HabitCheckEntity.toCheck() = HabitCheck(id, habitId, day, done, createdAt, updatedAt)
fun HabitCheck.toEntity() = HabitCheckEntity(id, habitId, day, done, createdAt, updatedAt)

@Dao
interface HabitDao : EntityDao<HabitEntity> {
    @Query("SELECT * FROM habits ORDER BY createdAt, id")
    override fun observeAll(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits ORDER BY createdAt, id")
    override suspend fun getAll(): List<HabitEntity>
}

@Dao
interface HabitCheckDao : EntityDao<HabitCheckEntity> {
    @Query("SELECT * FROM habit_checks")
    override fun observeAll(): Flow<List<HabitCheckEntity>>

    @Query("SELECT * FROM habit_checks")
    override suspend fun getAll(): List<HabitCheckEntity>
}

internal fun roomHabitStore(database: PlannerDatabase, clock: () -> Long): Store<Habit> =
    RoomStore(database, database.habitDao(), Habit::id, HabitEntity::toHabit, Habit::toEntity, clock)

internal fun roomHabitCheckStore(database: PlannerDatabase, clock: () -> Long): Store<HabitCheck> =
    RoomStore(database, database.habitCheckDao(), HabitCheck::id, HabitCheckEntity::toCheck, HabitCheck::toEntity, clock)
