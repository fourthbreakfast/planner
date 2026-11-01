package dev.fourthbreakfast.planner.core

import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** A note. Its [body] is the text [formatBody] makes, one line to a line. */
data class Note(
    val id: String = UUID.randomUUID().toString(),
    /** Typed above the text. Left empty, the first line of the text stands in for it. */
    val title: String = "",
    val body: String = formatBody(listOf(NoteLine())),
    /** Where it sits among the others, set by dragging. */
    val position: Double = 0.0,
    val createdAt: Long,
    val updatedAt: Long = createdAt,
    /** Set while the note is in the bin. */
    val deletedAt: Long? = null,
    /** A pinned note stays above the others. */
    val pinned: Boolean = false,
) {
    val inBin: Boolean get() = deletedAt != null
}

/** Where notes are kept. It knows nothing about the rules, those live in [Notes]. */
interface NoteStore {
    fun observeAll(): Flow<List<Note>>

    suspend fun all(): List<Note>

    suspend fun insert(notes: List<Note>)

    suspend fun update(notes: List<Note>)

    suspend fun delete(notes: List<Note>)

    suspend fun <T> transaction(block: suspend () -> T): T
}

fun sortedNotes(notes: List<Note>): List<Note> = notes.sortedWith(compareBy({ !it.pinned }, { it.position }, { it.createdAt }))

/** Everything the app can do with notes. */
class Notes(
    private val store: NoteStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** Every note, the bin included, in the order they were put in. */
    val all: Flow<List<Note>> = store.observeAll().map(::sortedNotes)

    /** Makes an empty note at the end and returns its id, so it can be opened and written in. */
    suspend fun add(title: String = "", body: String = formatBody(listOf(NoteLine()))): String {
        val now = clock()
        return store.transaction {
            val last = store.all().filterNot { it.inBin }.maxOfOrNull { it.position } ?: 0.0
            val note = Note(title = title.trim(), body = body, position = last + 1, createdAt = now)
            store.insert(listOf(note))
            note.id
        }
    }

    suspend fun update(id: String, title: String, body: String) {
        val now = clock()
        store.transaction {
            val note = store.all().firstOrNull { it.id == id && !it.inBin } ?: return@transaction
            if (note.body != body || note.title != title) store.update(listOf(note.copy(title = title, body = body, updatedAt = now)))
        }
    }

    /** Puts a note before another, or last when there is no other. A note cannot be put before itself. */
    suspend fun move(id: String, beforeId: String?) {
        if (id == beforeId) return
        val now = clock()
        store.transaction {
            val active = sortedNotes(store.all().filterNot { it.inBin })
            val note = active.firstOrNull { it.id == id } ?: return@transaction
            val others = active.filter { it.id != id }
            val index = if (beforeId == null) others.size else others.indexOfFirst { it.id == beforeId }.takeIf { it >= 0 } ?: others.size
            val ordered = others.toMutableList().apply { add(index, note) }
            store.update(
                ordered.mapIndexedNotNull { i, item ->
                    val position = (i + 1).toDouble()
                    if (item.position != position) item.copy(position = position, updatedAt = now) else null
                },
            )
        }
    }

    suspend fun setPinned(id: String, pinned: Boolean) {
        val now = clock()
        store.transaction {
            val note = store.all().firstOrNull { it.id == id && !it.inBin } ?: return@transaction
            if (note.pinned != pinned) store.update(listOf(note.copy(pinned = pinned, updatedAt = now)))
        }
    }

    suspend fun moveToBin(id: String) {
        val now = clock()
        store.transaction {
            val note = store.all().firstOrNull { it.id == id && !it.inBin } ?: return@transaction
            store.update(listOf(note.copy(deletedAt = now, updatedAt = now)))
        }
    }

    suspend fun restore(id: String) {
        val now = clock()
        store.transaction {
            val note = store.all().firstOrNull { it.id == id && it.inBin } ?: return@transaction
            store.update(listOf(note.copy(deletedAt = null, updatedAt = now)))
        }
    }

    suspend fun deleteForever(id: String) {
        store.transaction { store.all().firstOrNull { it.id == id && it.inBin }?.let { store.delete(listOf(it)) } }
    }

    suspend fun emptyBin() {
        store.transaction { store.delete(store.all().filter { it.inBin }) }
    }

    /** A note nobody wrote anything in is not kept: closing it takes it away for good, bin and all. */
    suspend fun dropIfEmpty(id: String) {
        store.transaction {
            store.all().firstOrNull { it.id == id && !it.inBin && (it.title.isBlank() && isBlankBody(it.body)) }?.let { store.delete(listOf(it)) }
        }
    }
}

/** What a note is called: its own title, or else its first line of words. */
fun noteTitle(note: Note): String =
    note.title.trim().ifEmpty { parseBody(note.body).firstOrNull { it.text.isNotBlank() }?.text?.trim() ?: "" }

/** The lines to show under the title: all of them, or all but the first when that line is what the title is borrowing. */
fun linesUnderTitle(note: Note): List<NoteLine> {
    val lines = parseBody(note.body)
    if (note.title.isNotBlank()) return lines
    val first = lines.indexOfFirst { it.text.isNotBlank() }
    return if (first < 0) emptyList() else lines.drop(first + 1)
}
