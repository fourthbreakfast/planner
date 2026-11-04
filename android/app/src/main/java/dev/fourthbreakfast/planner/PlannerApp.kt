package dev.fourthbreakfast.planner

import android.app.Application
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.room.Room
import dev.fourthbreakfast.planner.backup.AutoBackup
import dev.fourthbreakfast.planner.reminders.Reminders
import dev.fourthbreakfast.planner.sync.SyncController
import dev.fourthbreakfast.planner.ui.AndroidPrefs
import dev.fourthbreakfast.planner.ui.PlannerServices
import dev.fourthbreakfast.planner.updates.Updates
import dev.fourthbreakfast.planner.widget.PlannerWidget
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PlannerApp : Application() {
    private val database: PlannerDatabase by lazy {
        Room.databaseBuilder(this, PlannerDatabase::class.java, "planner.db").build()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** The tasks, the agenda, the notes, the backup and sync, over the database. */
    val data by lazy { PlannerData(database) }
    private val reminders by lazy { Reminders(this) }

    private val prefs by lazy { AndroidPrefs(this, "view") }
    val settings: UiSettings by lazy { UiSettings(prefs) }
    val requests = Requests()

    /** What the shared screens need from the app. */
    val services by lazy { PlannerServices(requests, settings, prefs) }

    /** The daily look for a newer release. */
    val updates by lazy {
        Updates(prefs, packageManager.getPackageInfo(packageName, 0).versionName.orEmpty(), File(cacheDir, "updates"), pick = { it.apk })
    }

    /** Checks for a newer release when a day has passed since the last look. */
    fun checkForUpdatesIfDue() {
        scope.launch { updates.checkIfDue() }
    }

    val syncController: SyncController by lazy { SyncController(this, data.sync, data.changes, scope) }
    private val autoBackup by lazy { AutoBackup(this, data.backups, settings) }

    override fun onCreate() {
        super.onCreate()
        setUpShortcuts()
        scope.launch { data.startClock() }
        // the alarms follow the agenda: every change to an event sets or takes away its alarm
        scope.launch { data.events.all.collect { reminders.sync(it) } }
        // and the widget on the home screen follows the events and the tasks
        scope.launch { combine(data.events.all, data.tasks.all) { e, t -> e to t }.collect { (e, t) -> PlannerWidget.update(this@PlannerApp, e, t) } }
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
        scope.launch { PlannerWidget.update(this@PlannerApp, data.events.all.first(), data.tasks.all.first()) }
    }

    /** Sets every alarm again, for after a restart or a change of the clock. */
    fun resyncReminders() {
        scope.launch { reminders.sync(data.events.all.first()) }
    }
}
