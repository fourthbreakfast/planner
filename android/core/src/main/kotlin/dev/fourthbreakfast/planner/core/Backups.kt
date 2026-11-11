package dev.fourthbreakfast.planner.core

import java.util.UUID

/** Saves and brings back everything the app keeps: the tasks, the bin, the agenda and the notes. */
class Backups(
    private val tasks: TaskStore,
    private val events: EventStore,
    private val notes: NoteStore,
    private val ledger: SyncLedger? = null,
    private val transactions: TransactionRunner = StoreTransactionRunner(tasks),
    private val habits: Store<Habit>? = null,
    private val habitChecks: Store<HabitCheck>? = null,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    constructor(tasks: TaskStore, events: EventStore, notes: NoteStore, ledger: SyncLedger?, clock: () -> Long) :
        this(tasks, events, notes, ledger, StoreTransactionRunner(tasks), clock = clock)

    suspend fun export(): String = transactions.transaction {
        encodeBackup(tasks.all(), events.all(), notes.all(), habits = habits?.all().orEmpty(), habitChecks = habitChecks?.all().orEmpty())
    }

    /**
     * Adds what a backup holds and the app does not, and returns how many things that was. It never changes
     * or removes anything already here, so importing the same file twice is harmless. What it brings back counts as
     * changed now, and is no longer marked as deleted for good, so sync spreads it instead of deleting it again.
     */
    suspend fun import(text: String): Int {
        val backup = decodeBackup(text)
        return transactions.transaction {
            val now = clock()
            val addedTasks = importTasks(backup.tasks.distinctBy { it.id }.map { it.copy(updatedAt = now) })
            val knownTasks = tasks.all().mapTo(HashSet()) { it.id }
            val knownEvents = events.all().mapTo(HashSet()) { it.id }
            val restoredEvents = backup.events.distinctBy { it.id }.filterNot { it.id in knownEvents }.map { event ->
                if (event.readOnly) {
                    // A backup has no private feed credentials. Restore a stable, editable snapshot, never revive its source.
                    event.forDisplay().copy(
                        id = UUID.nameUUIDFromBytes("planner-restored-calendar:${event.id}".toByteArray(Charsets.UTF_8)).toString(),
                        calendarSourceId = null,
                        calendarName = null,
                        calendarGeneration = 0,
                        taskId = null,
                        updatedAt = now,
                        calendarStartInstant = null,
                        calendarEndInstant = null,
                    )
                } else {
                    event.copy(taskId = event.taskId?.takeIf { it in knownTasks }, updatedAt = now)
                }
            }
            val addedEvents = importEvents(restoredEvents)
            val addedNotes = importNotes(backup.notes.distinctBy { it.id }.map { it.copy(updatedAt = now) })
            val addedHabits = importNew(habits, backup.habits.map { it.copy(updatedAt = now) }, Habit::id)
            val addedChecks = importNew(habitChecks, backup.habitChecks.map { it.copy(updatedAt = now) }, HabitCheck::id)
            ledger?.forgetGone(addedTasks + addedEvents + addedNotes + addedHabits + addedChecks)
            addedTasks.size + addedEvents.size + addedNotes.size + addedHabits.size + addedChecks.size
        }
    }

    /** Adds the events of a calendar file the agenda does not have, and returns how many that was. Reading it again adds nothing. */
    suspend fun importCalendar(text: String): Int {
        val incoming = parseIcs(text, clock()).distinctBy { it.id }
        return transactions.transaction {
            val added = importEvents(incoming)
            ledger?.forgetGone(added)
            added.size
        }
    }

    private suspend fun importTasks(incoming: List<Task>): List<String> {
        val existing = tasks.all().mapTo(HashSet()) { it.id }
        val known = existing + incoming.map { it.id }
        val added = incoming
            .filterNot { it.id in existing }
            .map { task -> if (task.parentId in known) task else task.copy(parentId = null) }
            .let { list ->
                // a damaged file can hold tasks inside each other: those come in at the top
                val loose = loopBreaks(tasks.all() + list).mapTo(HashSet()) { it.id }
                list.map { if (it.id in loose) it.copy(parentId = null) else it }
            }
        if (added.isNotEmpty()) tasks.insert(added)
        return added.map { it.id }
    }

    private suspend fun importEvents(incoming: List<Event>): List<String> {
        val existing = events.all().mapTo(HashSet()) { it.id }
        val added = incoming.filterNot { it.id in existing }
        if (added.isNotEmpty()) events.insert(added)
        return added.map { it.id }
    }

    private suspend fun importNotes(incoming: List<Note>): List<String> {
        val existing = notes.all().mapTo(HashSet()) { it.id }
        val added = incoming.filterNot { it.id in existing }
        if (added.isNotEmpty()) notes.insert(added)
        return added.map { it.id }
    }

    private suspend fun <T> importNew(store: Store<T>?, incoming: List<T>, id: (T) -> String): List<String> {
        store ?: return emptyList()
        val existing = store.all().mapTo(HashSet(), id)
        val added = incoming.distinctBy(id).filterNot { id(it) in existing }
        if (added.isNotEmpty()) store.insert(added)
        return added.map(id)
    }
}
