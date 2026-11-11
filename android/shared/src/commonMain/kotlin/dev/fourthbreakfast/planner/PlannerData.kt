package dev.fourthbreakfast.planner

import dev.fourthbreakfast.planner.core.Backups
import dev.fourthbreakfast.planner.core.CalendarSubscriptions
import dev.fourthbreakfast.planner.core.Events
import dev.fourthbreakfast.planner.core.Habits
import dev.fourthbreakfast.planner.core.LogicalClock
import dev.fourthbreakfast.planner.core.Notes
import dev.fourthbreakfast.planner.core.Sync
import dev.fourthbreakfast.planner.core.Tasks
import dev.fourthbreakfast.planner.core.newestChange
import dev.fourthbreakfast.planner.data.RoomCalendarStore
import dev.fourthbreakfast.planner.data.RoomSyncLedger
import dev.fourthbreakfast.planner.data.roomEventStore
import dev.fourthbreakfast.planner.data.roomHabitCheckStore
import dev.fourthbreakfast.planner.data.roomHabitStore
import dev.fourthbreakfast.planner.data.roomNoteStore
import dev.fourthbreakfast.planner.data.roomTaskStore
import dev.fourthbreakfast.planner.data.roomTransactions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

/**
 * Everything the app keeps and the rules over it, made once from the [database] on a phone or a computer alike: the
 * tasks, the agenda and the notes, the backup, and sync. Every change is stamped by one clock, which never goes back past
 * a time already seen here or from another device.
 */
class PlannerData private constructor(database: PlannerDatabase) {
    private val clock = LogicalClock()
    private val taskStore = roomTaskStore(database, clock::now)
    private val eventStore = roomEventStore(database, clock::now)
    private val noteStore = roomNoteStore(database, clock::now)
    private val ledger = RoomSyncLedger(database)
    private val habitStore = roomHabitStore(database, clock::now)
    private val habitCheckStore = roomHabitCheckStore(database, clock::now)
    private val transactions = roomTransactions(database)
    private val calendarStore = RoomCalendarStore(database)

    val tasks = Tasks(taskStore, clock::now)
    val events = Events(eventStore, taskStore, clock::now)
    val notes = Notes(noteStore, clock::now)
    val habits = Habits(habitStore, habitCheckStore, clock::now)
    val calendars = CalendarSubscriptions(calendarStore, eventStore, ledger, transactions, clock = clock::now)
    val backups = Backups(
        taskStore,
        eventStore,
        noteStore,
        ledger,
        transactions = transactions,
        habits = habitStore,
        habitChecks = habitCheckStore,
        clock = clock::now,
    )
    val sync = Sync(
        taskStore, eventStore, noteStore, ledger, transactions = transactions,
        habits = habitStore, habitChecks = habitCheckStore, clock = clock::now, saw = clock::saw,
    )

    /** Says something whenever anything kept changes, so sync writes this device's file again. */
    val changes: Flow<Any> = merge(
        taskStore.observeAll(),
        eventStore.observeAll(),
        noteStore.observeAll(),
        habitStore.observeAll(),
        habitCheckStore.observeAll(),
        calendarStore.observeAll().map { sources -> sources.map { Triple(it.id, it.createdAt, it.name) } }.distinctUntilChanged(),
    )

    companion object {
        /** Returns writable services only after the persisted clock has been observed. */
        suspend fun create(database: PlannerDatabase): PlannerData = PlannerData(database).also {
            it.clock.saw(newestChange(it.taskStore, it.eventStore, it.noteStore, it.ledger, it.habitStore, it.habitCheckStore))
        }
    }
}
