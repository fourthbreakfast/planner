package dev.fourthbreakfast.planner.data

import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Update
import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import dev.fourthbreakfast.planner.PlannerDatabase
import dev.fourthbreakfast.planner.core.Event
import dev.fourthbreakfast.planner.core.EventStore
import dev.fourthbreakfast.planner.core.Note
import dev.fourthbreakfast.planner.core.NoteStore
import dev.fourthbreakfast.planner.core.Store
import dev.fourthbreakfast.planner.core.Task
import dev.fourthbreakfast.planner.core.TaskStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** What every table of things can do. Each table's own dao says where its rows are read from. */
interface EntityDao<E> {
    fun observeAll(): Flow<List<E>>

    suspend fun getAll(): List<E>

    @Insert
    suspend fun insertAll(rows: List<E>)

    @Update
    suspend fun updateAll(rows: List<E>)

    @Delete
    suspend fun deleteAll(rows: List<E>)
}

/**
 * A core [Store] kept in one table of the database: [toModel] and [toEntity] turn its rows into the core's things and
 * back. Deleting for good also leaves the mark that keeps a thing gone on the other devices.
 */
class RoomStore<T, E>(
    private val database: PlannerDatabase,
    private val dao: EntityDao<E>,
    private val id: (T) -> String,
    private val toModel: (E) -> T,
    private val toEntity: (T) -> E,
) : Store<T> {
    override fun observeAll(): Flow<List<T>> = dao.observeAll().map { rows -> rows.map(toModel) }

    override suspend fun all(): List<T> = dao.getAll().map(toModel)

    override suspend fun insert(items: List<T>) = dao.insertAll(items.map(toEntity))

    override suspend fun update(items: List<T>) = dao.updateAll(items.map(toEntity))

    override suspend fun delete(items: List<T>) = database.inTransaction {
        dao.deleteAll(items.map(toEntity))
        database.forget(items.map(id))
    }

    override suspend fun <R> transaction(block: suspend () -> R): R = database.inTransaction { block() }
}

fun roomTaskStore(database: PlannerDatabase): TaskStore = RoomStore(database, database.taskDao(), Task::id, TaskEntity::toTask, Task::toEntity)

fun roomEventStore(database: PlannerDatabase): EventStore = RoomStore(database, database.eventDao(), Event::id, EventEntity::toEvent, Event::toEntity)

fun roomNoteStore(database: PlannerDatabase): NoteStore = RoomStore(database, database.noteDao(), Note::id, NoteEntity::toNote, Note::toEntity)

/** Every store deletes for good through here, so the mark that keeps it gone on other devices is never left out. */
private suspend fun PlannerDatabase.forget(ids: List<String>) {
    if (ids.isEmpty()) return
    val now = System.currentTimeMillis()
    syncDao().addGone(ids.map { GoneEntity(it, now) })
}

/** Runs [block] as one write, on a phone or a computer alike. A write inside another one joins it. */
private suspend fun <T> PlannerDatabase.inTransaction(block: suspend () -> T): T = useWriterConnection { it.immediateTransaction { block() } }
