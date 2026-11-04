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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.fourthbreakfast.planner.Launch
import dev.fourthbreakfast.planner.PlannerData
import dev.fourthbreakfast.planner.PlannerDatabase
import dev.fourthbreakfast.planner.Requests
import dev.fourthbreakfast.planner.ThemeChoice
import dev.fourthbreakfast.planner.UiSettings
import dev.fourthbreakfast.planner.agenda.AgendaViewModel
import dev.fourthbreakfast.planner.notes.NotesViewModel
import dev.fourthbreakfast.planner.resources.Res
import dev.fourthbreakfast.planner.resources.shortcut_event
import dev.fourthbreakfast.planner.resources.shortcut_note
import dev.fourthbreakfast.planner.resources.shortcut_task
import dev.fourthbreakfast.planner.resources.tray_open
import dev.fourthbreakfast.planner.resources.tray_quit
import dev.fourthbreakfast.planner.tasks.TasksViewModel
import dev.fourthbreakfast.planner.ui.BackStack
import dev.fourthbreakfast.planner.ui.DesktopPrefs
import dev.fourthbreakfast.planner.ui.LocalBackStack
import dev.fourthbreakfast.planner.ui.LocalServices
import dev.fourthbreakfast.planner.ui.PlannerServices
import dev.fourthbreakfast.planner.ui.is24HourClock
import dev.fourthbreakfast.planner.ui.theme.PlannerTheme
import dev.fourthbreakfast.planner.updates.UpdateState
import dev.fourthbreakfast.planner.updates.Updates
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.stringResource

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
    val data = PlannerData(database).also { data -> scope.launch { data.startClock() } }
    private val prefs = DesktopPrefs(prefsNode)
    val tasksViewModel = TasksViewModel(data.tasks, data.backups)
    val agendaViewModel = AgendaViewModel(data.events)
    val notesViewModel = NotesViewModel(data.notes, prefs)
    val settings = UiSettings(prefs)
    val services = PlannerServices(Requests(), settings, prefs)
    val backStack = BackStack()
    val sync = DesktopSync(
        bundledSyncthing(),
        File(folder, "syncthing"),
        data.sync,
        data.changes,
        prefs,
        scope,
    )
    val dashboard = DashboardState()

    // the tasks as they are now, for the keys that act on the selected one
    val tasks = tasksViewModel.all.stateIn(scope, SharingStarted.Eagerly, emptyList())
    val version: String = System.getProperty("jpackage.app-version") ?: "dev"
    val updates = Updates(prefs, version, File(folder, "updates"), pick = { it.msi })
}

/**
 * The Windows app: one window with the dashboard under a title bar of its own, and an icon in the tray. Closing the
 * window only hides it, so reminders and sync go on; quitting is in the tray's menu.
 */
fun main(args: Array<String>) {
    val instance = SingleInstance(dataFolder())
    if (!instance.claim()) return
    val app = DesktopApp()
    // closing down in any ordinary way stops syncthing too, so it does not stay behind with its database open
    Runtime.getRuntime().addShutdownHook(Thread { runBlocking { withTimeoutOrNull(5_000) { app.sync.stop() } } })
    // started with windows, it waits in the tray
    val startHidden = "--tray" in args
    application {
        val windowState = rememberWindowState(size = DpSize(1280.dp, 820.dp))
        var visible by remember { mutableStateOf(!startHidden) }
        val scope = rememberCoroutineScope()
        LaunchedEffect(Unit) {
            instance.listen {
                visible = true
                windowState.isMinimized = false
            }
        }
        var menuAt by remember { mutableStateOf<java.awt.Point?>(null) }
        val icon = remember { appIconPainter() }
        val dark = when (app.settings.theme) {
            ThemeChoice.LIGHT -> false
            ThemeChoice.DARK -> true
            ThemeChoice.SYSTEM -> isSystemInDarkTheme()
        }
        val tray = remember {
            AppTray(
                onOpen = {
                    visible = true
                    windowState.isMinimized = false
                },
                onMenu = { menuAt = it },
            )
        }
        val reminders =
            remember {
                DesktopReminders(app.data.events.all, app.scope, is24Hour = is24HourClock()) { title, text -> tray.say(title, text) }
            }
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

        // restart to update: this app goes first, so nothing the installer replaces is in use; the installer runs on its
        // own with only its progress showing, then planner starts again
        fun install(ready: UpdateState.Ready) {
            val exe = System.getProperty("jpackage.app-path")
            val then = if (exe != null) " && start \"\" \"$exe\"" else ""
            ProcessBuilder("cmd", "/c", "msiexec /i \"${ready.file.path}\" /passive /norestart$then").start()
            quit()
        }
        DisposableEffect(Unit) {
            tray.install("planner")
            onDispose { tray.remove() }
        }
        TrayMenu(
            at = menuAt,
            entries = listOf(
                TrayEntry.Item(stringResource(Res.string.tray_open)) { show() },
                TrayEntry.Item(stringResource(Res.string.shortcut_task)) { show(Launch.NewTask) },
                TrayEntry.Item(stringResource(Res.string.shortcut_event)) { show(Launch.NewEvent) },
                TrayEntry.Item(stringResource(Res.string.shortcut_note)) { show(Launch.NewNote) },
                TrayEntry.Line,
                TrayEntry.Item(stringResource(Res.string.tray_quit), ::quit),
            ),
            dark = dark,
            onDismiss = { menuAt = null },
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
                    handleDashboardKey(event, plainKeys = false, app.dashboard, app.tasksViewModel, app.agendaViewModel, app.notesViewModel) {
                        app.tasks.value
                    }
            },
            onKeyEvent = { event ->
                handleDashboardKey(event, plainKeys = true, app.dashboard, app.tasksViewModel, app.agendaViewModel, app.notesViewModel) {
                    app.tasks.value
                }
            },
        ) {
            window.minimumSize = java.awt.Dimension(900, 560)
            val frame = remember { NativeFrame() }
            LaunchedEffect(Unit) { frame.install(window, Desk.edge.value.toInt()) }
            CompositionLocalProvider(LocalServices provides app.services, LocalBackStack provides app.backStack) {
                PlannerTheme(darkTheme = dark, textScale = Desk.textScale) {
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
                                TitleBar(windowState, frame, onClose = { visible = false }) {
                                    TitleStatus(app.sync, app.updates, onUpdate = ::install, onSettings = { app.dashboard.panel = Panel.Settings })
                                }
                                Box(Modifier.weight(1f)) {
                                    Dashboard(
                                        app.dashboard,
                                        app.tasksViewModel,
                                        app.agendaViewModel,
                                        app.notesViewModel,
                                    ) { onClose ->
                                        DesktopSettings(app.tasksViewModel, app.sync, app.updates, app.version, onClose, install = ::install)
                                    }
                                }
                            }
                            if (!frame.installed) ResizeEdges(window)
                        }
                    }
                }
            }
        }
    }
}
