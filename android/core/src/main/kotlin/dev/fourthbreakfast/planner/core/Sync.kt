package dev.fourthbreakfast.planner.core

import java.util.UUID

/** Something deleted for good, remembered so that another device does not bring it back. */
data class Gone(val id: String, val at: Long)

/**
 * What sync keeps beside the things themselves. A store deletes for good by also adding to [gone], so every way of
 * deleting leaves its mark.
 */
interface SyncLedger {
    suspend fun gone(): List<Gone>

    suspend fun addGone(items: List<Gone>)

    /** Takes away the marks of things brought back from a backup. */
    suspend fun forgetGone(ids: List<String>)

    /** For each id, the updatedAt that this device and another last agreed on. */
    suspend fun agreed(): Map<String, Long>

    suspend fun setAgreed(items: Map<String, Long>)
}

/**
 * Brings what other devices have into this one. Every device writes the whole of its planner to a file of its own
 * ([snapshot]), and reads the others' files with [merge]. The newer version of a thing wins. A note changed on both
 * devices since they last agreed keeps the losing words too, as a copy in the bin. Merging the same file twice
 * changes nothing the second time, so devices settle.
 */
class Sync(
    private val tasks: TaskStore,
    private val events: EventStore,
    private val notes: NoteStore,
    private val ledger: SyncLedger,
    private val transactions: TransactionRunner = StoreTransactionRunner(tasks),
    private val habits: Store<Habit>? = null,
    private val habitChecks: Store<HabitCheck>? = null,
    private val clock: () -> Long = System::currentTimeMillis,
    // every time in another device's file is told to the clock, so this device's next change lands after it
    private val saw: (Long) -> Unit = {},
) {
    constructor(
        tasks: TaskStore,
        events: EventStore,
        notes: NoteStore,
        ledger: SyncLedger,
        clock: () -> Long,
        saw: (Long) -> Unit = {},
    ) : this(tasks, events, notes, ledger, StoreTransactionRunner(tasks), clock = clock, saw = saw)

    suspend fun snapshot(): String = transactions.transaction {
        encodeBackup(tasks.all(), events.all(), notes.all(), ledger.gone(), habits?.all().orEmpty(), habitChecks?.all().orEmpty())
    }

    /** Takes in another device's snapshot and returns how many things changed here. */
    suspend fun merge(text: String): Int {
        // a file from a newer version of the app is still read: what this version does not know of is left alone
        val other = decodeBackup(text, acceptNewer = true)
        (
            other.tasks.map { it.updatedAt } + other.events.map { it.updatedAt } + other.notes.map { it.updatedAt } + other.gone.map { it.at } +
                other.habits.map { it.updatedAt } + other.habitChecks.map { it.updatedAt }
            )
            .maxOrNull()?.let(saw)
        return transactions.transaction {
            val known = ledger.gone().associate { it.id to it.at }
            val incomingGone = other.gone.groupBy { it.id }.mapValues { (_, marks) -> marks.maxOf { it.at } }
            val newGone = incomingGone.filter { (id, at) -> (known[id] ?: Long.MIN_VALUE) < at }
            val gone = (known + newGone).toMutableMap()
            // Unsubscribing removes a source, not just the occurrences currently inside its rolling window.
            // This also rejects later snapshots from a subscribed device that was offline during removal.
            val removedCalendarEvents = events.all().filter { it.removedByCalendar(gone) }
            if (removedCalendarEvents.isNotEmpty()) {
                events.delete(removedCalendarEvents)
                for (event in removedCalendarEvents) {
                    val sourceTime = gone.getValue(event.calendarSourceId!!)
                    gone[event.id] = maxOf(gone[event.id] ?: Long.MIN_VALUE, sourceTime)
                }
            }
            val agreed = ledger.agreed()
            val nowAgreed = HashMap<String, Long>()
            val conflicts = ArrayList<Note>()

            val changed = mergeKind(
                store = tasks,
                incoming = other.tasks,
                id = Task::id,
                updatedAt = Task::updatedAt,
                tieKey = Task::toString,
                gone = gone,
                agreed = nowAgreed,
            ) + mergeKind(
                store = events,
                incoming = other.events.filterNot { it.removedByCalendar(gone) },
                id = Event::id,
                updatedAt = Event::updatedAt,
                tieKey = { it.copy(skipDays = it.skipDays.sorted().toCollection(LinkedHashSet())).toString() },
                gone = gone,
                agreed = nowAgreed,
            ) + mergeKind(
                store = notes,
                incoming = other.notes,
                id = Note::id,
                updatedAt = Note::updatedAt,
                tieKey = Note::toString,
                gone = gone,
                agreed = nowAgreed,
                beforeReplacing = { mine, theirs ->
                    val base = agreed[mine.id]
                    val bothChanged = base == null || (mine.updatedAt > base && theirs.updatedAt > base)
                    if (bothChanged && (mine.title != theirs.title || mine.body != theirs.body)) {
                        val now = clock()
                        conflicts += mine.copy(id = UUID.randomUUID().toString(), deletedAt = now, updatedAt = now)
                    }
                },
            )
            if (conflicts.isNotEmpty()) notes.insert(conflicts)
            val habitChanges = habits?.let { store ->
                mergeKind(
                    store,
                    other.habits,
                    Habit::id,
                    Habit::updatedAt,
                    { it.copy(weekdays = it.weekdays.sorted().toCollection(LinkedHashSet())).toString() },
                    gone,
                    nowAgreed,
                )
            } ?: 0
            val checkChanges = habitChecks?.let { store ->
                mergeKind(store, other.habitChecks, HabitCheck::id, HabitCheck::updatedAt, HabitCheck::toString, gone, nowAgreed)
            } ?: 0
            // the marks keep the time things were deleted on the device that deleted them. deleting them here wrote this
            // device's time over them, which would make a later change on a third device look older than the delete
            if (gone.isNotEmpty()) ledger.addGone(gone.map { (id, at) -> Gone(id, at) })
            // two moves made apart can put two tasks inside each other: one is let go to the top
            val loose = loopBreaks(tasks.all())
            if (loose.isNotEmpty()) {
                val now = clock()
                tasks.update(loose.map { it.copy(parentId = null, updatedAt = now) })
            }
            val moved = nowAgreed.filter { (id, at) -> agreed[id] != at }
            if (moved.isNotEmpty()) ledger.setAgreed(moved)
            changed + conflicts.size + loose.size + habitChanges + checkChanges + removedCalendarEvents.size
        }
    }

    /**
     * Whether [theirs] should replace [mine]: the later change wins. Two different versions changed at the very same
     * moment would otherwise each stay on its own device, so the tie goes the same way everywhere, by the text of each.
     */
    private fun <T> isNewer(theirs: T, mine: T, updatedAt: (T) -> Long, tieKey: (T) -> String): Boolean {
        val a = updatedAt(theirs)
        val b = updatedAt(mine)
        return a > b || (a == b && theirs != mine && tieKey(theirs) > tieKey(mine))
    }

    private suspend fun <T> mergeKind(
        store: Store<T>,
        incoming: List<T>,
        id: (T) -> String,
        updatedAt: (T) -> Long,
        tieKey: (T) -> String,
        gone: Map<String, Long>,
        agreed: MutableMap<String, Long>,
        beforeReplacing: (mine: T, theirs: T) -> Unit = { _, _ -> },
    ): Int {
        val local = store.all()
        // deleted for good elsewhere, and not changed here since
        val removed = local.filter { item -> gone[id(item)]?.let { updatedAt(item) <= it } == true }
        val removedIds = removed.mapTo(HashSet(), id)
        val mine = local.filterNot { id(it) in removedIds }.associateBy(id)
        val added = ArrayList<T>()
        val replaced = ArrayList<T>()
        for (theirs in incoming.groupBy(id).map { (_, copies) -> copies.reduce { a, b -> if (isNewer(b, a, updatedAt, tieKey)) b else a } }) {
            val key = id(theirs)
            val here = mine[key]
            // something deleted for good here only comes back when it was changed after that
            val wanted = gone[key]?.let { updatedAt(theirs) > it } != false
            if (!wanted) continue
            when {
                here == null -> added += theirs
                isNewer(theirs, here, updatedAt, tieKey) -> {
                    beforeReplacing(here, theirs)
                    replaced += theirs
                }
            }
            agreed[key] = updatedAt(theirs)
        }
        if (removed.isNotEmpty()) store.delete(removed)
        if (added.isNotEmpty()) store.insert(added)
        if (replaced.isNotEmpty()) store.update(replaced)
        return removed.size + added.size + replaced.size
    }
}
