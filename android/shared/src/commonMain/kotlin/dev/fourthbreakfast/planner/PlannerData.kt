package dev.fourthbreakfast.planner

import dev.fourthbreakfast.planner.core.Backups
import dev.fourthbreakfast.planner.core.Events
import dev.fourthbreakfast.planner.core.LogicalClock
import dev.fourthbreakfast.planner.core.Notes
import dev.fourthbreakfast.planner.core.Sync
import dev.fourthbreakfast.planner.core.Tasks
import dev.fourthbreakfast.planner.core.newestChange
import dev.fourthbreakfast.planner.data.RoomSyncLedger
import dev.fourthbreakfast.planner.data.roomEventStore
import dev.fourthbreakfast.planner.data.roomNoteStore
import dev.fourthbreakfast.planner.data.roomTaskStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.merge

/**
 * Everything the app keeps and the rules over it, made once from the [database] on a phone or a computer alike: the
 * tasks, the agenda and the notes, the backup, and sync. Every change is stamped by one clock, which never goes back past
 * a time already seen here or from another device.
 */
class PlannerData(database: PlannerDatabase) {
    private val clock = LogicalClock()
    private val taskStore = roomTaskStore(database, clock::now)
    private val eventStore = roomEventStore(database, clock::now)
    private val noteStore = roomNoteStore(database, clock::now)
    private val ledger = RoomSyncLedger(database)

    val tasks = Tasks(taskStore, clock::now)
    val events = Events(eventStore, clock::now)
    val notes = Notes(noteStore, clock::now)
    val backups = Backups(taskStore, eventStore, noteStore, ledger, clock::now)
    val sync = Sync(taskStore, eventStore, noteStore, ledger, clock::now, clock::saw)

    /** Says something whenever anything kept changes, so sync writes this device's file again. */
    val changes: Flow<Any> = merge(taskStore.observeAll(), eventStore.observeAll(), noteStore.observeAll())

    /** Sets the clock past the newest change already saved. Called once, as the app starts. */
    suspend fun startClock() = clock.saw(newestChange(taskStore, eventStore, noteStore))
}
