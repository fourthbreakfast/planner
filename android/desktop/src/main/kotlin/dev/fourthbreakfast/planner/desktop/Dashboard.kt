package dev.fourthbreakfast.planner.desktop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.min
import dev.fourthbreakfast.planner.Launch
import dev.fourthbreakfast.planner.agenda.AgendaBin
import dev.fourthbreakfast.planner.agenda.AgendaViewModel
import dev.fourthbreakfast.planner.agenda.EventDraft
import dev.fourthbreakfast.planner.agenda.EventEditor
import dev.fourthbreakfast.planner.agenda.today
import dev.fourthbreakfast.planner.core.binEntries
import dev.fourthbreakfast.planner.notes.NoteEditor
import dev.fourthbreakfast.planner.notes.NotesBin
import dev.fourthbreakfast.planner.notes.NotesViewModel
import dev.fourthbreakfast.planner.search.SearchScreen
import dev.fourthbreakfast.planner.tasks.BinScreen
import dev.fourthbreakfast.planner.tasks.TasksViewModel
import dev.fourthbreakfast.planner.ui.LocalServices
import dev.fourthbreakfast.planner.ui.theme.Space

/** The three parts of the dashboard, which one the keyboard is in. */
enum class Section { TASKS, AGENDA, NOTES }

/** What slides in from the right over the dashboard, if anything. */
sealed interface Panel {
    data class NoteOpen(val id: String) : Panel

    data class EventOpen(val id: String?, val day: Long?) : Panel

    data object TaskBin : Panel

    data object AgendaBin : Panel

    data object NoteBin : Panel

    data object Search : Panel

    data object Settings : Panel
}

/** Where the dashboard is: which section has the keyboard, what is selected in each, and what panel is open. */
class DashboardState {
    var focused by mutableStateOf(Section.TASKS)
    var panel by mutableStateOf<Panel?>(null)
    var selectedTask by mutableStateOf<String?>(null)
    var selectedEvent by mutableStateOf<String?>(null)
    var selectedNote by mutableStateOf<String?>(null)
    var renamingTask by mutableStateOf<String?>(null)
    var aimedTask by mutableStateOf<String?>(null)
    var openTasks by mutableStateOf(emptySet<String>())
    var chosenDay by mutableLongStateOf(today())

    /** Bumped to put the cursor in the add line. */
    var focusAdd by mutableIntStateOf(0)

    // what each column shows, in order, for the arrow keys
    var visibleTasks: List<String> = emptyList()
    var visibleEvents: List<String> = emptyList()
    var visibleNotes: List<String> = emptyList()

    fun openNote(id: String) {
        panel = Panel.NoteOpen(id)
    }

    fun openEvent(id: String) {
        panel = Panel.EventOpen(id, null)
    }

    fun newEvent(day: Long?) {
        panel = Panel.EventOpen(null, day)
    }
}

/**
 * What the keyboard can do on the dashboard. Returns whether the key was used. The control shortcuts are taken before
 * anything else sees them ([plainKeys] false); the plain keys, arrows, enter, space, delete and f2, only once a text
 * field had its turn and left them ([plainKeys] true), so typing in a field never moves or bins a task.
 */
fun handleDashboardKey(
    event: KeyEvent,
    plainKeys: Boolean,
    state: DashboardState,
    tasks: TasksViewModel,
    agenda: AgendaViewModel,
    notes: NotesViewModel,
    allTasks: () -> List<dev.fourthbreakfast.planner.core.Task>,
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    val ctrl = event.isCtrlPressed
    if (ctrl == plainKeys) return false
    when {
        ctrl && event.key == Key.One -> state.focused = Section.TASKS
        ctrl && event.key == Key.Two -> state.focused = Section.AGENDA
        ctrl && event.key == Key.Three -> state.focused = Section.NOTES
        ctrl && event.key == Key.F -> state.panel = Panel.Search
        ctrl && event.key == Key.Comma -> state.panel = Panel.Settings
        ctrl && event.key == Key.N -> when (state.focused) {
            Section.TASKS -> state.focusAdd++
            Section.AGENDA -> state.newEvent(null)
            Section.NOTES -> notes.add { state.openNote(it) }
        }
        state.panel != null || state.renamingTask != null -> return false
        event.key == Key.DirectionDown || event.key == Key.DirectionUp -> {
            val step = if (event.key == Key.DirectionDown) 1 else -1
            fun next(ids: List<String>, current: String?) = ids.getOrNull((ids.indexOf(current) + step).coerceIn(0, (ids.size - 1).coerceAtLeast(0)))
            when (state.focused) {
                Section.TASKS -> state.selectedTask = next(state.visibleTasks, state.selectedTask)
                Section.AGENDA -> state.selectedEvent = next(state.visibleEvents, state.selectedEvent)
                Section.NOTES -> state.selectedNote = next(state.visibleNotes, state.selectedNote)
            }
        }
        event.key == Key.Enter -> when (state.focused) {
            Section.TASKS -> state.selectedTask?.let { state.renamingTask = it }
            Section.AGENDA -> state.selectedEvent?.let { state.openEvent(it) }
            Section.NOTES -> state.selectedNote?.let { state.openNote(it) }
        }
        event.key == Key.F2 && state.focused == Section.TASKS -> state.selectedTask?.let { state.renamingTask = it }
        event.key == Key.Spacebar && state.focused == Section.TASKS ->
            allTasks().firstOrNull { it.id == state.selectedTask }?.let { tasks.setDone(it, !it.done) }
        event.key == Key.Delete -> when (state.focused) {
            Section.TASKS -> allTasks().firstOrNull { it.id == state.selectedTask }?.let { tasks.moveToBin(it) }
            Section.AGENDA -> state.selectedEvent?.let { agenda.moveToBin(it) }
            Section.NOTES -> state.selectedNote?.let { notes.moveToBin(it) }
        }
        else -> return false
    }
    return true
}

/**
 * The dashboard: tasks, agenda and notes side by side, each scrolling on its own. Whatever is opened, a note, an event,
 * a bin, the search or the settings, slides in from the right over part of it, and escape or a click beside it closes it.
 */
@Composable
fun Dashboard(
    state: DashboardState,
    tasksViewModel: TasksViewModel,
    agendaViewModel: AgendaViewModel,
    notesViewModel: NotesViewModel,
    settings: @Composable (onClose: () -> Unit) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val allTasks by tasksViewModel.all.collectAsState(initial = emptyList())
    val allEvents by agendaViewModel.all.collectAsState(initial = emptyList())
    val allNotes by notesViewModel.all.collectAsState(initial = emptyList())
    val toolbarAbove = LocalServices.current.settings.toolbarAbove
    val requests = LocalServices.current.requests

    BoxWithConstraints(Modifier.fillMaxSize().background(colors.background)) {
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxHeight()) {
                TaskColumn(allTasks, binEntries(allTasks).size, state, tasksViewModel)
            }
            Divider()
            Box(Modifier.weight(1f).fillMaxHeight()) {
                val events = allEvents.filterNot { it.inBin }
                AgendaColumn(
                    events,
                    allEvents.count { it.inBin },
                    state,
                    onRemove = { agendaViewModel.moveToBin(it) },
                    onMove = { event, day ->
                        agendaViewModel.update(
                            event.id,
                            EventDraft(event.title, day, event.minute, event.endMinute, event.location, event.notes, event.reminder),
                        )
                        state.chosenDay = day
                    },
                )
            }
            Divider()
            Box(Modifier.weight(1f).fillMaxHeight()) {
                NoteColumn(allNotes.filterNot { it.inBin }, allNotes.count { it.inBin }, state, notesViewModel)
            }
        }

        // leaving a note, however the panel changes: an empty one made here goes, and the app starts with none left over
        var lastNote by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(Unit) { notesViewModel.dropLeftDrafts(null) }
        LaunchedEffect(state.panel) {
            val now = (state.panel as? Panel.NoteOpen)?.id
            lastNote?.takeIf { it != now }?.let(notesViewModel::closed)
            lastNote = now
        }
        val panel = state.panel
        // a dim layer over the dashboard, a click on it closes the panel
        AnimatedVisibility(panel != null, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(colors.scrim.copy(alpha = 0.32f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { state.panel = null },
            )
        }
        AnimatedVisibility(
            panel != null,
            enter = slideInHorizontally { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            Box(Modifier.width(min(Desk.panel, maxWidth * 0.7f)).fillMaxHeight().background(colors.background)) {
                val close = { state.panel = null }
                when (panel) {
                    is Panel.NoteOpen -> allNotes.firstOrNull { it.id == panel.id }?.let { note ->
                        androidx.compose.runtime.key(note.id) {
                            NoteEditor(
                                note = note,
                                onPin = { notesViewModel.setPinned(note.id, !note.pinned) },
                                toolbarAbove = toolbarAbove,
                                onSave = { title, body, base, onSaved -> notesViewModel.save(note.id, title, body, base, onSaved) },
                                onClose = close,
                            )
                        }
                    }
                    is Panel.EventOpen -> {
                        val editing = panel.id?.let { id -> allEvents.firstOrNull { it.id == id } }
                        androidx.compose.runtime.key(panel) {
                            EventEditor(
                                initial = editing,
                                newDay = panel.day,
                                onSave = { draft ->
                                    if (editing == null) agendaViewModel.add(draft) else agendaViewModel.update(editing.id, draft)
                                    close()
                                },
                                onDelete = editing?.let { event ->
                                    {
                                        agendaViewModel.moveToBin(event.id)
                                        close()
                                    }
                                },
                                onClose = close,
                            )
                        }
                    }
                    Panel.TaskBin -> BinScreen(binEntries(allTasks), tasksViewModel, onClose = close)
                    Panel.AgendaBin -> AgendaBin(allEvents.filter { it.inBin }.sortedByDescending { it.deletedAt }, agendaViewModel, onClose = close)
                    Panel.NoteBin -> NotesBin(allNotes.filter { it.inBin }.sortedByDescending { it.deletedAt }, notesViewModel, onClose = close)
                    Panel.Search -> SearchScreen(notesViewModel, onOpen = { launch ->
                        when (launch) {
                            is Launch.OpenNote -> state.openNote(launch.id)
                            else -> requests.ask(launch)
                        }
                    }, onClose = close)
                    Panel.Settings -> settings(close)
                    null -> Unit
                }
            }
        }
        // a share or a tray action asks for something: the dashboard acts on it
        androidx.compose.runtime.LaunchedEffect(requests.pending) {
            when (val request = requests.pending) {
                Launch.NewTask -> state.focused = Section.TASKS.also { state.focusAdd++ }
                Launch.NewEvent -> state.newEvent(null)
                Launch.NewNote -> notesViewModel.add { state.openNote(it) }
                is Launch.OpenEvent -> state.openEvent(request.id)
                is Launch.OpenNote -> state.openNote(request.id)
                else -> Unit
            }
            if (requests.pending != null) requests.clear()
        }
    }
}

@Composable
private fun Divider() {
    Box(Modifier.width(Space.strokeThin).fillMaxHeight().background(MaterialTheme.colorScheme.outline))
}
