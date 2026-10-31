package dev.fourthbreakfast.planner.agenda

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fourthbreakfast.planner.core.Event
import dev.fourthbreakfast.planner.core.Events
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class AgendaViewModel(private val events: Events) : ViewModel() {
    /** Every event, the bin included. */
    val all: Flow<List<Event>> = events.all

    fun add(draft: EventDraft) {
        viewModelScope.launch { events.add(draft.title, draft.day, draft.minute, draft.location, draft.notes) }
    }

    fun update(id: String, draft: EventDraft) {
        viewModelScope.launch { events.update(id, draft.title, draft.day, draft.minute, draft.location, draft.notes) }
    }

    fun moveToBin(id: String) {
        viewModelScope.launch { events.moveToBin(id) }
    }

    fun restore(id: String) {
        viewModelScope.launch { events.restore(id) }
    }

    fun deleteForever(id: String) {
        viewModelScope.launch { events.deleteForever(id) }
    }

    fun emptyBin() {
        viewModelScope.launch { events.emptyBin() }
    }
}

/** What the editor hands back: everything an event is, before it has an id. */
data class EventDraft(
    val title: String,
    val day: Long,
    val minute: Int?,
    val location: String,
    val notes: String,
)
