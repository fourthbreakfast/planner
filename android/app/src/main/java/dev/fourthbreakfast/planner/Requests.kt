package dev.fourthbreakfast.planner

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Somewhere the app is asked to go: from a launcher shortcut, a notification, a share or the search. */
sealed interface Launch {
    data object NewTask : Launch

    data object NewEvent : Launch

    data object NewNote : Launch

    data object ShowTasks : Launch

    data class ShowTask(val id: String) : Launch

    data class OpenEvent(val id: String) : Launch

    data class OpenNote(val id: String) : Launch

    data class Shared(val text: String, val subject: String) : Launch
}

private const val STALE_MILLIS = 10_000L

/**
 * One waiting request. The screens read it and clear it when they have acted, so it happens once. A request nobody
 * could act on, an event that was deleted since, goes stale after a few seconds instead of lingering.
 */
class Requests {
    private var current by mutableStateOf<Launch?>(null)
    private var askedAt = 0L

    val pending: Launch? get() = current?.takeIf { System.currentTimeMillis() - askedAt < STALE_MILLIS }

    fun ask(launch: Launch) {
        askedAt = System.currentTimeMillis()
        current = launch
    }

    fun clear() {
        current = null
    }
}
