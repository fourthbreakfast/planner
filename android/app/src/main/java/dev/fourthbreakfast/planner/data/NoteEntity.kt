package dev.fourthbreakfast.planner.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import androidx.room.withTransaction
import dev.fourthbreakfast.planner.PlannerDatabase
import dev.fourthbreakfast.planner.core.Note
import dev.fourthbreakfast.planner.core.NoteStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(defaultValue = "") val title: String,
    val body: String,
    val position: Double,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    @ColumnInfo(defaultValue = "0") val pinned: Boolean,
)

fun NoteEntity.toNote() = Note(id, title, body, position, createdAt, updatedAt, deletedAt, pinned)

fun Note.toEntity() = NoteEntity(id, title, body, position, createdAt, updatedAt, deletedAt, pinned)

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes")
    fun observeAll(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes")
    suspend fun getAll(): List<NoteEntity>

    @Insert
    suspend fun insertAll(notes: List<NoteEntity>)

    @Update
    suspend fun updateAll(notes: List<NoteEntity>)

    @Delete
    suspend fun deleteAll(notes: List<NoteEntity>)
}

class RoomNoteStore(private val database: PlannerDatabase) : NoteStore {
    private val dao = database.noteDao()

    override fun observeAll(): Flow<List<Note>> = dao.observeAll().map { rows -> rows.map { it.toNote() } }

    override suspend fun all(): List<Note> = dao.getAll().map { it.toNote() }

    override suspend fun insert(notes: List<Note>) = dao.insertAll(notes.map { it.toEntity() })

    override suspend fun update(notes: List<Note>) = dao.updateAll(notes.map { it.toEntity() })

    override suspend fun delete(notes: List<Note>) = database.withTransaction {
        dao.deleteAll(notes.map { it.toEntity() })
        database.forget(notes.map { it.id })
    }

    override suspend fun <T> transaction(block: suspend () -> T): T = database.withTransaction { block() }
}
