package dev.fourthbreakfast.planner.core

/** Saves and brings back everything the app keeps: the tasks, the bin, the agenda and the notes. */
class Backups(private val tasks: TaskStore, private val events: EventStore, private val notes: NoteStore) {
    suspend fun export(): String = encodeBackup(tasks.all(), events.all(), notes.all())

    /**
     * Adds what a backup holds and the app does not, and returns how many things that was. It never changes
     * or removes anything already here, so importing the same file twice is harmless.
     */
    suspend fun import(text: String): Int {
        val backup = decodeBackup(text)
        val addedTasks = importTasks(backup.tasks.distinctBy { it.id })
        val addedEvents = importEvents(backup.events.distinctBy { it.id })
        val addedNotes = importNotes(backup.notes.distinctBy { it.id })
        return addedTasks + addedEvents + addedNotes
    }

    private suspend fun importTasks(incoming: List<Task>): Int = tasks.transaction {
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
        added.size
    }

    private suspend fun importEvents(incoming: List<Event>): Int = events.transaction {
        val existing = events.all().mapTo(HashSet()) { it.id }
        val added = incoming.filterNot { it.id in existing }
        if (added.isNotEmpty()) events.insert(added)
        added.size
    }

    private suspend fun importNotes(incoming: List<Note>): Int = notes.transaction {
        val existing = notes.all().mapTo(HashSet()) { it.id }
        val added = incoming.filterNot { it.id in existing }
        if (added.isNotEmpty()) notes.insert(added)
        added.size
    }
}
