package dev.fourthbreakfast.planner.agenda

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fourthbreakfast.planner.core.Event
import dev.fourthbreakfast.planner.core.Events
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class AgendaViewModel(private val events: Events) : ViewModel() {
    val all: Flow<List<Event>> = events.all

    fun add(draft: EventDraft) {
        viewModelScope.launch { events.add(draft.title, draft.day, draft.minute, draft.location, draft.notes) }
    }

    fun update(id: String, draft: EventDraft) {
        viewModelScope.launch { events.update(id, draft.title, draft.day, draft.minute, draft.location, draft.notes) }
    }

    /** Removes an event and hands it to [onRemoved], so the screen can offer to bring it back. */
    fun remove(id: String, onRemoved: (Event) -> Unit) {
        viewModelScope.launch { events.remove(id)?.let(onRemoved) }
    }

    fun restore(event: Event) {
        viewModelScope.launch { events.restore(event) }
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
