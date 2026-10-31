package dev.fourthbreakfast.planner.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import androidx.room.withTransaction
import dev.fourthbreakfast.planner.PlannerDatabase
import dev.fourthbreakfast.planner.core.Event
import dev.fourthbreakfast.planner.core.EventStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Entity(tableName = "events")
data class EventEntity(
    @PrimaryKey val id: String,
    val title: String,
    val day: Long,
    val minute: Int?,
    val location: String,
    val notes: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
)

fun EventEntity.toEvent() = Event(id, title, day, minute, location, notes, createdAt, updatedAt, deletedAt)

fun Event.toEntity() = EventEntity(id, title, day, minute, location, notes, createdAt, updatedAt, deletedAt)

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

    override suspend fun delete(events: List<Event>) = dao.deleteAll(events.map { it.toEntity() })

    override suspend fun <T> transaction(block: suspend () -> T): T = database.withTransaction { block() }
}
