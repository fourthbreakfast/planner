package dev.fourthbreakfast.planner.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import dev.fourthbreakfast.planner.PlannerDatabase
import dev.fourthbreakfast.planner.core.Event
import dev.fourthbreakfast.planner.core.EventStore
import dev.fourthbreakfast.planner.core.Repeat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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
)

fun EventEntity.toEvent() = Event(
    id, title, day, minute, endMinute, reminder, location, notes, createdAt, updatedAt, deletedAt, endDay,
    Repeat.entries.firstOrNull { it.name == repeat } ?: Repeat.NONE,
    repeatUntil,
    skipDays.split(',').mapNotNull { it.toLongOrNull() }.toSet(),
)

fun Event.toEntity() = EventEntity(
    id, title, day, minute, endMinute, reminder, location, notes, createdAt, updatedAt, deletedAt, endDay,
    repeat.name,
    repeatUntil,
    skipDays.sorted().joinToString(","),
)

@Dao
interface EventDao {
    @Query("SELECT * FROM events")
    fun observeAll(): Flow<List<EventEntity>>

    @Query("SELECT * FROM events")
    suspend fun getAll(): List<EventEntity>

    @Insert
    suspend fun insertAll(events: List<EventEntity>)

    @Update
    suspend fun updateAll(events: List<EventEntity>)

    @Delete
    suspend fun deleteAll(events: List<EventEntity>)
}

class RoomEventStore(private val database: PlannerDatabase) : EventStore {
    private val dao = database.eventDao()

    override fun observeAll(): Flow<List<Event>> = dao.observeAll().map { rows -> rows.map { it.toEvent() } }

    override suspend fun all(): List<Event> = dao.getAll().map { it.toEvent() }

    override suspend fun insert(events: List<Event>) = dao.insertAll(events.map { it.toEntity() })

    override suspend fun update(events: List<Event>) = dao.updateAll(events.map { it.toEntity() })

    override suspend fun delete(events: List<Event>) = database.inTransaction {
        dao.deleteAll(events.map { it.toEntity() })
        database.forget(events.map { it.id })
    }

    override suspend fun <T> transaction(block: suspend () -> T): T = database.inTransaction { block() }
}
