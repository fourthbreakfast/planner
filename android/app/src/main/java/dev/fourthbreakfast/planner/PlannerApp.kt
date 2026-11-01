package dev.fourthbreakfast.planner

import android.app.Application
import kotlinx.coroutines.flow.combine
import dev.fourthbreakfast.planner.widget.PlannerWidget
import androidx.core.graphics.drawable.IconCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.content.pm.ShortcutInfoCompat
import android.content.Intent
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
    val requests = Requests()

    val tasks: Tasks by lazy { Tasks(taskStore) }
    val events: Events by lazy { Events(eventStore) }
    val notes: Notes by lazy { Notes(noteStore) }
    val backups: Backups by lazy { Backups(taskStore, eventStore, noteStore) }
    private val autoBackup by lazy { AutoBackup(this, backups, settings) }

    override fun onCreate() {
        super.onCreate()
        setUpShortcuts()
        // the alarms follow the agenda: every change to an event sets or takes away its alarm
        scope.launch { events.all.collect { reminders.sync(it) } }
        // and the widget on the home screen follows the events and the tasks
        scope.launch { combine(events.all, tasks.all) { e, t -> e to t }.collect { (e, t) -> PlannerWidget.update(this@PlannerApp, e, t) } }
    }

    /** The launcher's long-press shortcuts: a new task, a new event, a new note. */
    private fun setUpShortcuts() {
        fun shortcut(id: String, label: Int, action: String) = ShortcutInfoCompat.Builder(this, id)
            .setShortLabel(getString(label))
            .setIcon(IconCompat.createWithResource(this, R.drawable.ic_shortcut_add))
            .setIntent(Intent(this, MainActivity::class.java).setAction(action))
            .build()
        ShortcutManagerCompat.setDynamicShortcuts(
            this,
            listOf(
                shortcut("new_task", R.string.shortcut_task, MainActivity.NEW_TASK),
                shortcut("new_event", R.string.shortcut_event, MainActivity.NEW_EVENT),
                shortcut("new_note", R.string.shortcut_note, MainActivity.NEW_NOTE),
            ),
        )
    }

    /** Writes the daily backup when it is due. Called whenever the app goes out of sight. */
    fun backUpIfDue() {
        scope.launch { autoBackup.runIfDue() }
    }

    /** Draws the home screen widgets again, for when the widget is added or resized or the day changes. */
    fun refreshWidgets() {
        scope.launch { PlannerWidget.update(this@PlannerApp, events.all.first(), tasks.all.first()) }
    }

    /** Sets every alarm again, for after a restart or a change of the clock. */
    fun resyncReminders() {
        scope.launch { reminders.sync(events.all.first()) }
    }
}
