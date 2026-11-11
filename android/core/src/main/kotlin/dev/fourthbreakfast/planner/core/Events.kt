package dev.fourthbreakfast.planner.core

import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** Everything the app can do with the agenda. */
class Events(private val store: EventStore, private val tasks: TaskStore? = null, private val clock: () -> Long = System::currentTimeMillis) {
    constructor(store: EventStore, clock: () -> Long) : this(store, null, clock)

    /** Every event, the bin included, soonest first. */
    val all: Flow<List<Event>> = if (tasks == null) {
        store.observeAll().map { entries -> sortedEvents(entries.map { it.forDisplay() }) }
    } else {
        combine(store.observeAll(), tasks.observeAll()) { events, tasks ->
            val byId = tasks.associateBy { it.id }
            sortedEvents(
                events.mapNotNull { event ->
                    if (event.taskId == null || event.inBin) {
                        event.forDisplay()
                    } else {
                        byId[event.taskId]?.takeUnless { it.inBin || it.done }?.let { event.copy(title = it.title).forDisplay() }
                    }
                },
            )
        }
    }

    /** One schedule per task, with a deterministic id so simultaneous scheduling on two devices converges. */
    suspend fun scheduleTask(
        taskId: String,
        day: Long,
        minute: Int?,
        endMinute: Int? = null,
        reminder: Int? = null,
        location: String = "",
        notes: String = "",
        endDay: Long? = null,
    ) = store.transaction {
        val task = tasks?.all()?.firstOrNull { it.id == taskId && !it.inBin } ?: return@transaction
        val id = UUID.nameUUIDFromBytes("planner-task-schedule:$taskId".toByteArray(Charsets.UTF_8)).toString()
        val existing = store.all().firstOrNull { it.id == id }
        val now = clock()
        val event = Event(
            id = id, title = task.title, day = day, minute = minute, endMinute = endMinute,
            reminder = reminder, location = location, notes = notes, createdAt = existing?.createdAt ?: now,
            updatedAt = now, endDay = endDay, taskId = taskId,
        ).checked()
        if (existing == null) store.insert(listOf(event)) else store.update(listOf(event))
    }

    /** The agenda checks the original task; its schedule disappears until the task is reopened. */
    suspend fun completeTask(eventId: String) = store.transaction {
        val taskId = store.all().firstOrNull { it.id == eventId && !it.inBin && !it.readOnly }?.taskId ?: return@transaction
        tasks?.let { Tasks(it, clock).setDone(taskId, true) }
    }

    /** A blank title is ignored, so an event can never be nameless. */
    suspend fun add(
        title: String,
        day: Long,
        minute: Int?,
        location: String,
        notes: String,
        endMinute: Int? = null,
        reminder: Int? = null,
        endDay: Long? = null,
        repeat: Repeat = Repeat.NONE,
        repeatUntil: Long? = null,
    ) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        val event = Event(
            title = trimmed,
            day = day,
            minute = minute,
            endMinute = endMinute,
            reminder = reminder,
            location = location,
            notes = notes,
            createdAt = clock(),
            endDay = endDay,
            repeat = repeat,
            repeatUntil = repeatUntil,
        )
        store.insert(listOf(event.checked()))
    }

    /** A blank title leaves the event as it was. Only events outside the bin can change. */
    suspend fun update(
        id: String,
        title: String,
        day: Long,
        minute: Int?,
        location: String,
        notes: String,
        endMinute: Int? = null,
        reminder: Int? = null,
        endDay: Long? = null,
        repeat: Repeat = Repeat.NONE,
        repeatUntil: Long? = null,
    ) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        store.transaction {
            val event = store.all().firstOrNull { it.id == id && !it.inBin && !it.readOnly } ?: return@transaction
            val changed = event.copy(
                title = trimmed,
                day = day,
                minute = minute,
                endMinute = endMinute,
                reminder = reminder,
                location = location,
                notes = notes,
                endDay = endDay,
                repeat = repeat,
                repeatUntil = repeatUntil,
            ).checked()
            if (changed != event) store.update(listOf(changed.copy(updatedAt = clock())))
        }
    }

    /** Leaves one time of a repeating event out, as when only that one is deleted. */
    suspend fun skip(id: String, day: Long) {
        val now = clock()
        store.transaction {
            val event = store.all().firstOrNull { it.id == id && !it.inBin && !it.readOnly } ?: return@transaction
            store.update(listOf(event.copy(skipDays = event.skipDays + day, updatedAt = now)))
        }
    }

    /**
     * Changes only one time of a repeating event: that day is left out of the series, and an event of its own takes
     * its place with the new details.
     */
    suspend fun detach(
        id: String,
        day: Long,
        title: String,
        newDay: Long,
        minute: Int?,
        location: String,
        notes: String,
        endMinute: Int?,
        reminder: Int?,
        endDay: Long?,
    ) {
        if (title.isBlank()) return
        store.transaction {
            if (store.all().none { it.id == id && !it.inBin && !it.readOnly }) return@transaction
            skip(id, day)
            add(title, newDay, minute, location, notes, endMinute, reminder, endDay)
        }
    }

    /** Moves an event to the bin, otherwise unchanged. */
    suspend fun moveToBin(id: String) {
        val now = clock()
        store.transaction {
            val event = store.all().firstOrNull { it.id == id && !it.inBin && !it.readOnly } ?: return@transaction
            store.update(listOf(event.copy(deletedAt = now, updatedAt = now)))
        }
    }

    /** Brings an event back from the bin. */
    suspend fun restore(id: String) {
        val now = clock()
        store.transaction {
            val event = store.all().firstOrNull { it.id == id && it.inBin && !it.readOnly } ?: return@transaction
            store.update(listOf(event.copy(deletedAt = null, updatedAt = now)))
        }
    }

    /** Only what is in the bin can be deleted for good. */
    suspend fun deleteForever(id: String) {
        store.transaction { store.all().firstOrNull { it.id == id && it.inBin && !it.readOnly }?.let { store.delete(listOf(it)) } }
    }

    suspend fun emptyBin() {
        store.transaction { store.delete(store.all().filter { it.inBin && !it.readOnly }) }
    }

    /** The event as the rules let it be kept: times within the day, and only the details that mean something. */
    private fun Event.checked(): Event = copy(
        minute = minute?.let(::clampMinute),
        endMinute = validEnd(minute, endMinute),
        reminder = validReminder(minute, reminder),
        location = location.trim(),
        notes = notes.trim(),
        endDay = validEndDay(day, endDay),
        repeatUntil = validUntil(day, repeat, repeatUntil),
    )

    private fun clampMinute(minute: Int) = minute.coerceIn(0, 24 * 60 - 1)

    /** A reminder only counts with a start time, and cannot be a negative number of minutes. */
    private fun validReminder(start: Int?, reminder: Int?): Int? = reminder?.takeIf { start != null && it >= 0 }

    /** A last day only counts after the first. */
    private fun validEndDay(day: Long, endDay: Long?): Long? = endDay?.takeIf { it > day }

    /** An end to repeating only counts for an event that repeats, and not before its first day. */
    private fun validUntil(day: Long, repeat: Repeat, until: Long?): Long? = until?.takeIf { repeat != Repeat.NONE && it >= day }

    /** An end only counts when there is a start and it comes after it, on the same day. */
    private fun validEnd(start: Int?, end: Int?): Int? {
        if (start == null || end == null) return null
        return clampMinute(end).takeIf { it > clampMinute(start) }
    }
}
