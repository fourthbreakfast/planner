package dev.fourthbreakfast.planner.notes

import androidx.lifecycle.ViewModel
import dev.fourthbreakfast.planner.core.plainBody
import androidx.lifecycle.viewModelScope
import dev.fourthbreakfast.planner.core.Note
import dev.fourthbreakfast.planner.core.Notes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class NotesViewModel(private val notes: Notes) : ViewModel() {
    /** Every note, the bin included. */
    val all: Flow<List<Note>> = notes.all

    /** Makes an empty note and hands over its id once it exists. */
    fun add(onAdded: (String) -> Unit) {
        viewModelScope.launch { onAdded(notes.add()) }
    }

    /** Makes a note out of text shared from another app. */
    fun addShared(text: String, subject: String, onAdded: (String) -> Unit) {
        viewModelScope.launch { onAdded(notes.add(subject, plainBody(text))) }
    }

    fun save(id: String, title: String, body: String) {
        viewModelScope.launch { notes.update(id, title, body) }
    }

    /** Saves a note one last time and takes it away again if nothing was written. */
    fun close(id: String, title: String, body: String) {
        viewModelScope.launch {
            notes.update(id, title, body)
            notes.dropIfEmpty(id)
        }
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
