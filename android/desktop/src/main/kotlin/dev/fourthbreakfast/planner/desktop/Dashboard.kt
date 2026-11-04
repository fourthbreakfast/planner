package dev.fourthbreakfast.planner.desktop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import dev.fourthbreakfast.planner.agenda.EventEditor
import dev.fourthbreakfast.planner.agenda.WhichTimesChooser
import dev.fourthbreakfast.planner.agenda.movedDraft
import dev.fourthbreakfast.planner.agenda.today
import dev.fourthbreakfast.planner.core.Repeat
import dev.fourthbreakfast.planner.core.Task
import dev.fourthbreakfast.planner.core.binEntries
import dev.fourthbreakfast.planner.notes.NoteEditor
import dev.fourthbreakfast.planner.notes.NotesBin
import dev.fourthbreakfast.planner.notes.NotesViewModel
import dev.fourthbreakfast.planner.search.SearchScreen
import dev.fourthbreakfast.planner.tasks.BinScreen
import dev.fourthbreakfast.planner.tasks.TasksViewModel
import dev.fourthbreakfast.planner.ui.LocalServices

/** The three parts of the dashboard, which one the keyboard is in. */
enum class Section { TASKS, AGENDA, NOTES }

/** What slides in from the right over the dashboard, if anything. */
sealed interface Panel {
    data class NoteOpen(val id: String) : Panel

    // [occurrence] is the day of the time of a repeating event that was opened
    data class EventOpen(val id: String?, val day: Long?, val occurrence: Long? = null) : Panel

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

    /** An event time asked to be removed, as its id and day, until it is known whether that is one time or every time. */
    var eventToRemove by mutableStateOf<String?>(null)
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

    /** Opens an event by its id, or one time of it by a key of the id and its day. */
    fun openEvent(key: String) {
        panel = Panel.EventOpen(key.substringBefore('@'), null, key.substringAfter('@', "").toLongOrNull())
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
    allTasks: () -> List<Task>,
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
            Section.AGENDA -> state.eventToRemove = state.selectedEvent
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
    val taskBin = remember(allTasks) { binEntries(allTasks) }
    val toolbarAbove = LocalServices.current.settings.toolbarAbove
    val requests = LocalServices.current.requests

    state.eventToRemove?.let { key ->
        val id = key.substringBefore('@')
        val day = key.substringAfter('@', "").toLongOrNull()
        val event = allEvents.firstOrNull { it.id == id }
        if (event == null || event.repeat == Repeat.NONE || day == null) {
            LaunchedEffect(key) {
                agendaViewModel.moveToBin(id)
                state.eventToRemove = null
            }
        } else {
            WhichTimesChooser(
                onPick = { onlyThisTime ->
                    agendaViewModel.delete(id, day, onlyThisTime)
                    state.eventToRemove = null
                },
                onCancel = { state.eventToRemove = null },
            )
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(deskGround())) {
        Row(Modifier.fillMaxSize().padding(Desk.gap), horizontalArrangement = Arrangement.spacedBy(Desk.gap)) {
            Box(Modifier.weight(1f).fillMaxHeight().deskPanel()) {
                TaskColumn(allTasks, taskBin.size, state, tasksViewModel)
            }
            Box(Modifier.weight(1f).fillMaxHeight().deskPanel()) {
                val events = allEvents.filterNot { it.inBin }
                AgendaColumn(
                    events,
                    allEvents.count { it.inBin },
                    state,
                    onRemove = { state.eventToRemove = it },
                    onMove = { event, from, day ->
                        // a repeating event moves only the time that was dragged; the others stay
                        agendaViewModel.save(event.id, from, movedDraft(event, from, day), onlyThisTime = event.repeat != Repeat.NONE)
                        state.chosenDay = day
                    },
                )
            }
            Box(Modifier.weight(1f).fillMaxHeight().deskPanel()) {
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
                        key(note.id) {
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
                        key(panel) {
                            EventEditor(
                                initial = editing,
                                occurrence = panel.occurrence,
                                newDay = panel.day,
                                onSave = { draft, onlyThisTime ->
                                    if (editing == null) agendaViewModel.add(draft) else agendaViewModel.save(editing.id, panel.occurrence, draft, onlyThisTime)
                                    close()
                                },
                                onDelete = editing?.let { event ->
                                    { onlyThisTime ->
                                        agendaViewModel.delete(event.id, panel.occurrence, onlyThisTime)
                                        close()
                                    }
                                },
                                onClose = close,
                            )
                        }
                    }
                    Panel.TaskBin -> BinScreen(taskBin, tasksViewModel, onClose = close)
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
        LaunchedEffect(requests.pending) {
            when (val request = requests.pending) {
                Launch.NewTask -> {
                    state.focused = Section.TASKS
                    state.focusAdd++
                }
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
