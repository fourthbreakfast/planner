package dev.fourthbreakfast.planner.agenda

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fourthbreakfast.planner.core.Event
import dev.fourthbreakfast.planner.core.Events
import dev.fourthbreakfast.planner.core.Repeat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class AgendaViewModel(private val events: Events) : ViewModel() {
    /** Every event, the bin included. */
    val all: Flow<List<Event>> = events.all

    fun add(draft: EventDraft) {
        viewModelScope.launch {
            events.add(
                draft.title, draft.day, draft.minute, draft.location, draft.notes, draft.endMinute, draft.reminder,
                draft.endDay, draft.repeat, draft.repeatUntil,
            )
        }
    }

    fun update(id: String, draft: EventDraft) {
        viewModelScope.launch {
            events.update(
                id, draft.title, draft.day, draft.minute, draft.location, draft.notes, draft.endMinute, draft.reminder,
                draft.endDay, draft.repeat, draft.repeatUntil,
            )
        }
    }

    /** Saves what the editor holds, for every time of the event or, for a repeating one, only the time on [day]. */
    fun save(id: String, day: Long?, draft: EventDraft, onlyThisTime: Boolean) {
        if (onlyThisTime && day != null) {
            viewModelScope.launch {
                events.detach(id, day, draft.title, draft.day, draft.minute, draft.location, draft.notes, draft.endMinute, draft.reminder, draft.endDay)
            }
        } else {
            update(id, draft)
        }
    }

    /** Deletes every time of the event to the bin or, for a repeating one, leaves only the time on [day] out. */
    fun delete(id: String, day: Long?, onlyThisTime: Boolean) {
        if (onlyThisTime && day != null) viewModelScope.launch { events.skip(id, day) } else moveToBin(id)
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
    val endMinute: Int?,
    val location: String,
    val notes: String,
    val reminder: Int? = null,
    val endDay: Long? = null,
    val repeat: Repeat = Repeat.NONE,
    val repeatUntil: Long? = null,
)
