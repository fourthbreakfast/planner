package dev.fourthbreakfast.planner.desktop

import androidx.compose.foundation.DefaultContextMenuRepresentation
import androidx.compose.foundation.LocalContextMenuRepresentation
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.fourthbreakfast.planner.Launch
import dev.fourthbreakfast.planner.PlannerData
import dev.fourthbreakfast.planner.PlannerDatabase
import dev.fourthbreakfast.planner.Requests
import dev.fourthbreakfast.planner.UiSettings
import dev.fourthbreakfast.planner.agenda.AgendaViewModel
import dev.fourthbreakfast.planner.habits.HabitsViewModel
import dev.fourthbreakfast.planner.isDark
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
import java.awt.Dimension
import java.awt.Point
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.stringResource

/** App data: %APPDATA%\planner on Windows, XDG data home on Linux; PLANNER_DATA isolates dev runs. */
private fun dataFolder(): File = DesktopPlatform.dataFolder()

/** The Syncthing that ships beside the app, in the folder compose puts the app's own resources in. */
private fun bundledSyncthing(): File? = DesktopPlatform.syncthingBinary()

/** Everything the window needs, made once. Tests give it a folder and preferences of their own. */
internal class DesktopApp(
    private val folder: File = dataFolder(),
    // PLANNER_PREFS does the same for the settings, so a trial run shares none of them with the real app
    prefsNode: String = System.getenv("PLANNER_PREFS") ?: "dev/fourthbreakfast/planner",
) {
    // background work, with no catch-all handler on purpose, as on the phone (see PlannerApp)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val database = Room.databaseBuilder<PlannerDatabase>(File(folder, "planner.db").path)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
    val data = runBlocking(Dispatchers.IO) { PlannerData.create(database) }
    private val prefs = DesktopPrefs(prefsNode)
    val tasksViewModel = TasksViewModel(data.tasks)
    val agendaViewModel = AgendaViewModel(data.events)
    val notesViewModel = NotesViewModel(data.notes, prefs)
    val habitsViewModel = HabitsViewModel(data.habits)
    val settings = UiSettings(prefs)
    val services = PlannerServices(Requests(), settings, prefs)
    val windowGeometry = WindowGeometryStore(prefs)
    val backStack = BackStack()
    val sync = DesktopSync(
        bundledSyncthing(),
        File(folder, "syncthing"),
        data.sync,
        data.changes,
        prefs,
        scope,
    )
    val dashboard = DashboardState().apply { shortlistOnly = prefs.getBoolean("tasks_shortlist", false) }

    // the tasks as they are now, for the keys that act on the selected one
    val tasks = tasksViewModel.all.stateIn(scope, SharingStarted.Eagerly, emptyList())
    val version: String = System.getProperty("jpackage.app-version") ?: "dev"
    val updates = Updates(prefs, version, File(folder, "updates"), pick = { if (DesktopPlatform.isLinux) it.deb else it.setup })
}

/**
 * The Windows app: one window with the dashboard under a title bar of its own, and an icon in the tray. Closing the
 * window only hides it, so reminders and sync go on; quitting is in the tray's menu.
 */
fun main(args: Array<String>) {
    WindowsIdentity.install()
    val instance = SingleInstance(dataFolder())
    if (!instance.claim()) return
    val app = DesktopApp()
    // closing down in any ordinary way stops syncthing too, so it does not stay behind with its database open
    Runtime.getRuntime().addShutdownHook(
        Thread {
            try {
                runBlocking { withTimeoutOrNull(5_000) { app.sync.stop() } }
            } finally {
                instance.close()
            }
        },
    )
    // started with windows, it waits in the tray
    val startHidden = "--tray" in args
    application {
        val availableSize = remember { availableWindowSize() }
        val geometry = remember { app.windowGeometry.restore(availableSize) }
        val windowState = rememberWindowState(
            size = geometry.size,
            placement = if (geometry.maximized) WindowPlacement.Maximized else WindowPlacement.Floating,
        )
        LaunchedEffect(windowState) {
            snapshotFlow { Triple(windowState.size, windowState.placement, windowState.isMinimized) }.collectLatest { (size, placement, minimized) ->
                delay(250)
                app.windowGeometry.save(size, placement, minimized)
            }
        }
        DisposableEffect(windowState) {
            onDispose { app.windowGeometry.save(windowState.size, windowState.placement, windowState.isMinimized) }
        }
        var visible by remember { mutableStateOf(!startHidden) }
        var revealRequest by remember { mutableIntStateOf(0) }
        val scope = rememberCoroutineScope()
        LaunchedEffect(Unit) {
            instance.listen {
                scope.launch {
                    visible = true
                    windowState.isMinimized = false
                    revealRequest++
                }
            }
        }
        var menuAt by remember { mutableStateOf<Point?>(null) }
        val icon = remember { appIconPainter() }
        val dark = app.settings.theme.isDark()
        val tray = remember {
            AppTray(
                onOpen = {
                    visible = true
                    windowState.isMinimized = false
                    revealRequest++
                },
                onMenu = { menuAt = it },
            )
        }
        val reminders =
            remember {
                DesktopReminders(app.data.events.all, app.scope, is24Hour = is24HourClock()) { title, text -> tray.say(title, text) }
            }
        LaunchedEffect(Unit) {
            app.sync.startIfPaired()
            reminders.begin()
            app.updates.checkIfDue()
        }
        LaunchedEffect(Unit) {
            while (true) {
                app.data.calendars.refreshDue()
                delay(dev.fourthbreakfast.planner.core.CalendarSubscriptions.REFRESH_MILLIS)
            }
        }
        fun show(launch: Launch? = null) {
            visible = true
            windowState.isMinimized = false
            revealRequest++
            launch?.let { app.services.requests.ask(it) }
        }
        fun quit() {
            // syncthing closes its database before the app goes
            runBlocking { withTimeoutOrNull(10_000) { app.sync.stop() } }
            exitApplication()
        }
        fun closeWindow() {
            if (tray.available) visible = false else quit()
        }

        // restart to update: this app goes first, so nothing the installer replaces is in use; the installer runs on its
        // own with only its progress showing, and starts planner again when it is done
        fun install(ready: UpdateState.Ready) {
            try {
                ProcessBuilder(DesktopPlatform.installerCommand(ready.file)).start()
            } catch (_: IOException) {
                // the installer could not be started: the app stays, and the update is still offered
                return
            }
            quit()
        }
        DisposableEffect(Unit) {
            tray.install("planner")
            if (!tray.available) visible = true
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
            onCloseRequest = ::closeWindow,
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
            LaunchedEffect(visible, revealRequest) {
                if (visible) {
                    window.toFront()
                    window.requestFocus()
                }
            }
            window.minimumSize = Dimension(minOf(900, availableSize.width.value.toInt()), minOf(560, availableSize.height.value.toInt()))
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
                                TitleBar(windowState, frame, onClose = ::closeWindow)
                                Box(Modifier.weight(1f)) {
                                    Dashboard(
                                        app.dashboard,
                                        app.tasksViewModel,
                                        app.agendaViewModel,
                                        app.notesViewModel,
                                        habits = app.habitsViewModel,
                                        sync = app.sync,
                                    ) { onClose ->
                                        DesktopSettings(
                                            app.data.backups,
                                            app.sync,
                                            app.updates,
                                            app.version,
                                            onClose,
                                            install = ::install,
                                            calendars = app.data.calendars,
                                        )
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
