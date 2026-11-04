package dev.fourthbreakfast.planner.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fourthbreakfast.planner.core.Note
import dev.fourthbreakfast.planner.core.Notes
import dev.fourthbreakfast.planner.core.isBlankBody
import dev.fourthbreakfast.planner.core.plainBody
import dev.fourthbreakfast.planner.ui.Prefs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * The notes for the screens. [drafts] remembers the notes made on this device that nobody has written in yet: only those
 * are taken away when left empty, never an empty note that came from another device, where someone may be writing it.
 */
class NotesViewModel(private val notes: Notes, private val drafts: Prefs) : ViewModel() {
    /** Every note, the bin included. */
    val all: Flow<List<Note>> = notes.all

    /** Makes an empty note and hands over its id once it exists. */
    fun add(onAdded: (String) -> Unit) {
        viewModelScope.launch {
            val id = notes.add()
            remember(id, true)
            onAdded(id)
        }
    }

    private fun ownDrafts(): Set<String> = drafts.getString(PREF_DRAFTS).orEmpty().split(',').filter { it.isNotEmpty() }.toSet()

    private fun remember(id: String, draft: Boolean) {
        val now = ownDrafts().let { if (draft) it + id else it - id }
        drafts.putString(PREF_DRAFTS, now.joinToString(","))
    }

    /** Whether a note was made here and is still empty, so leaving it takes it away. */
    fun isOwnDraft(id: String): Boolean = id in ownDrafts()

    /** Makes a note out of text shared from another app. */
    fun addShared(text: String, subject: String, onAdded: (String) -> Unit) {
        viewModelScope.launch { onAdded(notes.add(subject, plainBody(text))) }
    }

    /** Saves what an editor holds, telling it the version it saved. See [Notes.update] for [base]. */
    fun save(id: String, title: String, body: String, base: Long, onSaved: (Long) -> Unit) {
        viewModelScope.launch {
            notes.update(id, title, body, base)?.let(onSaved)
            if (title.isNotBlank() || !isBlankBody(body)) remember(id, false)
        }
    }

    /** A note is left: an empty one made here goes. */
    fun closed(id: String) {
        viewModelScope.launch {
            if (isOwnDraft(id)) notes.dropIfEmpty(id)
            remember(id, false)
        }
    }

    /** Takes away the empty notes made here and left behind when the app was closed before the note was. */
    fun dropLeftDrafts(openId: String?) {
        viewModelScope.launch {
            for (id in ownDrafts() - setOfNotNull(openId)) {
                notes.dropIfEmpty(id)
                remember(id, false)
            }
        }
    }

    fun setPinned(id: String, pinned: Boolean) {
        viewModelScope.launch { notes.setPinned(id, pinned) }
    }

    fun move(id: String, beforeId: String?) {
        viewModelScope.launch { notes.move(id, beforeId) }
    }

    fun moveToBin(id: String) {
        viewModelScope.launch { notes.moveToBin(id) }
    }

    fun restore(id: String) {
        viewModelScope.launch { notes.restore(id) }
    }

    fun deleteForever(id: String) {
        viewModelScope.launch { notes.deleteForever(id) }
    }

    fun emptyBin() {
        viewModelScope.launch { notes.emptyBin() }
    }
}

private const val PREF_DRAFTS = "note_drafts"
