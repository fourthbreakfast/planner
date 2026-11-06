package dev.fourthbreakfast.planner.core

/** Saves and brings back everything the app keeps: the tasks, the bin, the agenda and the notes. */
class Backups(
    private val tasks: TaskStore,
    private val events: EventStore,
    private val notes: NoteStore,
    private val ledger: SyncLedger? = null,
    private val transactions: TransactionRunner = StoreTransactionRunner(tasks),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    constructor(tasks: TaskStore, events: EventStore, notes: NoteStore, ledger: SyncLedger?, clock: () -> Long) :
        this(tasks, events, notes, ledger, StoreTransactionRunner(tasks), clock)

    suspend fun export(): String = transactions.transaction { encodeBackup(tasks.all(), events.all(), notes.all()) }

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
            val addedEvents = importEvents(backup.events.distinctBy { it.id }.map { it.copy(updatedAt = now) })
            val addedNotes = importNotes(backup.notes.distinctBy { it.id }.map { it.copy(updatedAt = now) })
            ledger?.forgetGone(addedTasks + addedEvents + addedNotes)
            addedTasks.size + addedEvents.size + addedNotes.size
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
}
