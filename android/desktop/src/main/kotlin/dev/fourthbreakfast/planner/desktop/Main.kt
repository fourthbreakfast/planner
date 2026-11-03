package dev.fourthbreakfast.planner.desktop

import androidx.compose.foundation.DefaultContextMenuRepresentation
import androidx.compose.foundation.LocalContextMenuRepresentation
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.fourthbreakfast.planner.Launch
import dev.fourthbreakfast.planner.PlannerDatabase
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
import dev.fourthbreakfast.planner.updates.Release
import dev.fourthbreakfast.planner.updates.Updates
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/** Where the app keeps its database and its Syncthing: %APPDATA%\planner on Windows. */
private fun dataFolder(): File {
    // PLANNER_DATA points a trial run somewhere else, so it never touches the real data
    System.getenv("PLANNER_DATA")?.let { return File(it).apply { mkdirs() } }
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
    val notesViewModel = NotesViewModel(Notes(noteStore), prefs)
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
    val dashboard = DashboardState()

    // the tasks as they are now, for the keys that act on the selected one
    val tasks = tasksViewModel.all.stateIn(scope, SharingStarted.Eagerly, emptyList())
    val version: String = System.getProperty("jpackage.app-version") ?: "dev"
    val updates = Updates(prefs, version)
}

/**
 * The Windows app: one window with the dashboard under a title bar of its own, and an icon in the tray. Closing the
 * window only hides it, so reminders and sync go on; quitting is in the tray's menu.
 */
fun main(args: Array<String>) {
    val app = DesktopApp()
    // started with windows, it waits in the tray
    val startHidden = "--tray" in args
    application {
        val windowState = rememberWindowState(size = DpSize(1280.dp, 820.dp))
        var visible by remember { mutableStateOf(!startHidden) }
        val scope = rememberCoroutineScope()
        val tray = rememberTrayState()
        val icon = remember { appIconPainter() }
        val reminders =
            remember { DesktopReminders(app.events.all, app.scope, is24Hour = true) { title, text -> tray.sendNotification(Notification(title, text)) } }
        LaunchedEffect(Unit) {
            app.sync.begin()
            reminders.begin()
            app.updates.checkIfDue()
        }
        fun show(launch: Launch? = null) {
            visible = true
            windowState.isMinimized = false
            launch?.let { app.services.requests.ask(it) }
        }
        fun quit() {
            // syncthing closes its database before the app goes
            runBlocking { withTimeoutOrNull(10_000) { app.sync.stop() } }
            exitApplication()
        }

        // a newer version: its installer starts, and this app goes, so nothing the installer replaces is in use
        suspend fun install(release: Release) {
            val url = release.msi ?: return
            val file = File(System.getProperty("java.io.tmpdir"), "planner-${release.version}.msi")
            if (!app.updates.download(url, file)) return
            ProcessBuilder("msiexec", "/i", file.path).start()
            quit()
        }
        Tray(
            icon = icon,
            state = tray,
            tooltip = "planner",
            onAction = { show() },
            menu = {
                Item("open planner", onClick = { show() })
                Item("new task", onClick = { show(Launch.NewTask) })
                Item("new event", onClick = { show(Launch.NewEvent) })
                Item("new note", onClick = { show(Launch.NewNote) })
                Separator()
                Item("quit", onClick = ::quit)
            },
        )
        Window(
            onCloseRequest = { visible = false },
            visible = visible,
            state = windowState,
            title = "planner",
            icon = icon,
            undecorated = true,
            onPreviewKeyEvent = { event ->
                (event.key == Key.Escape && event.type == KeyEventType.KeyDown && app.backStack.back()) ||
                    handleDashboardKey(event, app.dashboard, app.tasksViewModel, app.agendaViewModel, app.notesViewModel) {
                        app.tasks.value
                    }
            },
        ) {
            window.minimumSize = java.awt.Dimension(900, 560)
            val dark = when (app.settings.theme) {
                ThemeChoice.LIGHT -> false
                ThemeChoice.DARK -> true
                ThemeChoice.SYSTEM -> isSystemInDarkTheme()
            }
            CompositionLocalProvider(LocalServices provides app.services, LocalBackStack provides app.backStack) {
                PlannerTheme(darkTheme = dark) {
                    val colors = MaterialTheme.colorScheme
                    // right click menus in the app's own colours, not the system grey
                    val menus = remember(colors) {
                        DefaultContextMenuRepresentation(
                            backgroundColor = colors.surfaceVariant,
                            textColor = colors.onBackground,
                            itemHoverColor = colors.secondaryContainer,
                        )
                    }
                    CompositionLocalProvider(LocalContextMenuRepresentation provides menus) {
                        Box(Modifier.fillMaxSize().background(colors.background)) {
                            Column(Modifier.fillMaxSize()) {
                                TitleBar(windowState, onClose = { visible = false })
                                Box(Modifier.weight(1f)) {
                                    Dashboard(app.dashboard, app.tasksViewModel, app.agendaViewModel, app.notesViewModel) { onClose ->
                                        DesktopSettings(app.tasksViewModel, app.sync, app.updates, app.version, onClose, install = { install(it) })
                                    }
                                }
                                StatusBar(app.sync, app.updates, onUpdate = { scope.launch { install(it) } }, onSettings = {
                                    app.dashboard.panel =
                                        Panel.Settings
                                })
                            }
                            ResizeEdges(window)
                        }
                    }
                }
            }
        }
    }
}
