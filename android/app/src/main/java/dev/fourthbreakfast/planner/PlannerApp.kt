package dev.fourthbreakfast.planner

import android.app.Application
import dev.fourthbreakfast.planner.backup.AutoBackup
import androidx.room.Room
import dev.fourthbreakfast.planner.core.Backups
import dev.fourthbreakfast.planner.core.Events
import dev.fourthbreakfast.planner.core.Notes
import dev.fourthbreakfast.planner.core.Tasks
import dev.fourthbreakfast.planner.data.RoomEventStore
import dev.fourthbreakfast.planner.data.RoomNoteStore
import dev.fourthbreakfast.planner.data.RoomTaskStore
import dev.fourthbreakfast.planner.reminders.Reminders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PlannerApp : Application() {
    private val database: PlannerDatabase by lazy {
        Room.databaseBuilder(this, PlannerDatabase::class.java, "planner.db").build()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val taskStore by lazy { RoomTaskStore(database) }
    private val eventStore by lazy { RoomEventStore(database) }
    private val noteStore by lazy { RoomNoteStore(database) }
    private val reminders by lazy { Reminders(this) }

    val settings: UiSettings by lazy { UiSettings(this) }

    val tasks: Tasks by lazy { Tasks(taskStore) }
    val events: Events by lazy { Events(eventStore) }
    val notes: Notes by lazy { Notes(noteStore) }
    val backups: Backups by lazy { Backups(taskStore, eventStore, noteStore) }
    private val autoBackup by lazy { AutoBackup(this, backups, settings) }

    override fun onCreate() {
        super.onCreate()
        // the alarms follow the agenda: every change to an event sets or takes away its alarm
        scope.launch { events.all.collect { reminders.sync(it) } }
    }

    /** Writes the daily backup when it is due. Called whenever the app goes out of sight. */
    fun backUpIfDue() {
        scope.launch { autoBackup.runIfDue() }
    }

    /** Sets every alarm again, for after a restart or a change of the clock. */
    fun resyncReminders() {
        scope.launch { reminders.sync(events.all.first()) }
    }
}
