package dev.fourthbreakfast.planner.desktop

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.fourthbreakfast.planner.PlannerDatabase
import dev.fourthbreakfast.planner.PlannerScreen
import dev.fourthbreakfast.planner.Requests
import dev.fourthbreakfast.planner.ThemeChoice
import dev.fourthbreakfast.planner.UiSettings
import dev.fourthbreakfast.planner.agenda.AgendaViewModel
import dev.fourthbreakfast.planner.core.Backups
import dev.fourthbreakfast.planner.core.Events
import dev.fourthbreakfast.planner.core.Notes
import dev.fourthbreakfast.planner.core.Sync
import dev.fourthbreakfast.planner.core.Tasks
import dev.fourthbreakfast.planner.data.RoomEventStore
import dev.fourthbreakfast.planner.data.RoomNoteStore
import dev.fourthbreakfast.planner.data.RoomSyncLedger
import dev.fourthbreakfast.planner.data.RoomTaskStore
import dev.fourthbreakfast.planner.notes.NotesViewModel
import dev.fourthbreakfast.planner.tasks.TasksViewModel
import dev.fourthbreakfast.planner.ui.BackStack
import dev.fourthbreakfast.planner.ui.DesktopPrefs
import dev.fourthbreakfast.planner.ui.LocalBackStack
import dev.fourthbreakfast.planner.ui.LocalServices
import dev.fourthbreakfast.planner.ui.PlannerServices
import dev.fourthbreakfast.planner.ui.theme.PlannerTheme
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/** Where the app keeps its database and its Syncthing: %APPDATA%\planner on Windows. */
private fun dataFolder(): File {
    val base = System.getenv("APPDATA")?.let(::File) ?: File(System.getProperty("user.home"), ".config")
    return File(base, "planner").apply { mkdirs() }
}

/** The Syncthing that ships beside the app, in the folder compose puts the app's own resources in. */
private fun bundledSyncthing(): File? = System.getProperty("compose.application.resources.dir")
    ?.let { File(it, "syncthing.exe") }
    ?.takeIf { it.exists() }

/** Everything the window needs, made once. Tests give it a folder and preferences of their own. */
internal class DesktopApp(private val folder: File = dataFolder(), prefsNode: String = "dev/fourthbreakfast/planner") {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val database = Room.databaseBuilder<PlannerDatabase>(File(folder, "planner.db").path)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
    private val taskStore = RoomTaskStore(database)
    private val eventStore = RoomEventStore(database)
    private val noteStore = RoomNoteStore(database)
    private val prefs = DesktopPrefs(prefsNode)

    val events = Events(eventStore)
    val tasksViewModel = TasksViewModel(Tasks(taskStore), Backups(taskStore, eventStore, noteStore))
    val agendaViewModel = AgendaViewModel(events)
    val notesViewModel = NotesViewModel(Notes(noteStore))
    val settings = UiSettings(prefs)
    val services = PlannerServices(Requests(), settings, prefs)
    val backStack = BackStack()
    val sync = DesktopSync(
        bundledSyncthing(),
        File(folder, "syncthing"),
        Sync(taskStore, eventStore, noteStore, RoomSyncLedger(database)),
        merge(taskStore.observeAll(), eventStore.observeAll(), noteStore.observeAll()),
        prefs,
        scope,
    )
    val reminders = DesktopReminders(events.all, scope, is24Hour = true, icon = appIcon(32))
    val version: String = System.getProperty("jpackage.app-version") ?: "dev"
}

fun main() {
    val app = DesktopApp()
    application {
        val windowState = rememberWindowState(size = DpSize(460.dp, 880.dp))
        LaunchedEffect(Unit) {
            app.sync.begin()
            app.reminders.begin()
        }
        Window(
            onCloseRequest = {
                // syncthing closes its database before the app goes
                runBlocking { withTimeoutOrNull(10_000) { app.sync.stop() } }
                app.reminders.end()
                exitApplication()
            },
            state = windowState,
            title = "planner",
            icon = remember { appIconPainter() },
            onPreviewKeyEvent = { event ->
                event.key == Key.Escape && event.type == KeyEventType.KeyDown && app.backStack.back()
            },
        ) {
            window.minimumSize = java.awt.Dimension(360, 560)
            val dark = when (app.settings.theme) {
                ThemeChoice.LIGHT -> false
                ThemeChoice.DARK -> true
                ThemeChoice.SYSTEM -> isSystemInDarkTheme()
            }
            CompositionLocalProvider(LocalServices provides app.services, LocalBackStack provides app.backStack) {
                PlannerTheme(darkTheme = dark) {
                    PlannerScreen(app.tasksViewModel, app.agendaViewModel, app.notesViewModel) { onClose ->
                        DesktopSettings(app.tasksViewModel, app.sync, app.version, onClose)
                    }
                }
            }
        }
    }
}
