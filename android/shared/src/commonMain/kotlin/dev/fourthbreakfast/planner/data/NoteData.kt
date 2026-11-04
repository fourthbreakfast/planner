package dev.fourthbreakfast.planner.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import dev.fourthbreakfast.planner.core.Note
import kotlinx.coroutines.flow.Flow

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
interface NoteDao : EntityDao<NoteEntity> {
    @Query("SELECT * FROM notes")
    override fun observeAll(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes")
    override suspend fun getAll(): List<NoteEntity>
}
