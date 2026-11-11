package dev.fourthbreakfast.planner.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import dev.fourthbreakfast.planner.core.Event
import dev.fourthbreakfast.planner.core.Repeat
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "events")
data class EventEntity(
    @PrimaryKey val id: String,
    val title: String,
    val day: Long,
    val minute: Int?,
    val endMinute: Int?,
    val reminder: Int?,
    val location: String,
    val notes: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    val endDay: Long?,
    @ColumnInfo(defaultValue = "NONE") val repeat: String,
    val repeatUntil: Long?,
    // the days left out of a repeating event, written as numbers with commas between
    @ColumnInfo(defaultValue = "") val skipDays: String,
    val taskId: String?,
    val calendarSourceId: String?,
    val calendarName: String?,
    @ColumnInfo(defaultValue = "0") val calendarGeneration: Long,
    val calendarStartInstant: Long?,
    val calendarEndInstant: Long?,
)

fun EventEntity.toEvent() = Event(
    id, title, day, minute, endMinute, reminder, location, notes, createdAt, updatedAt, deletedAt, endDay,
    Repeat.entries.firstOrNull { it.name == repeat } ?: Repeat.NONE,
    repeatUntil,
    skipDays.split(',').mapNotNull { it.toLongOrNull() }.toSet(),
    taskId,
    calendarSourceId, calendarName, calendarGeneration, calendarStartInstant, calendarEndInstant,
)

fun Event.toEntity() = EventEntity(
    id, title, day, minute, endMinute, reminder, location, notes, createdAt, updatedAt, deletedAt, endDay,
    repeat.name,
    repeatUntil,
    skipDays.sorted().joinToString(","),
    taskId,
    calendarSourceId, calendarName, calendarGeneration, calendarStartInstant, calendarEndInstant,
)

@Dao
interface EventDao : EntityDao<EventEntity> {
    @Query("SELECT * FROM events")
    override fun observeAll(): Flow<List<EventEntity>>

    @Query("SELECT * FROM events")
    override suspend fun getAll(): List<EventEntity>
}
