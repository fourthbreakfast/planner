package dev.fourthbreakfast.planner.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.fourthbreakfast.planner.agenda.AgendaViewModel
import dev.fourthbreakfast.planner.agenda.movedDraft
import dev.fourthbreakfast.planner.core.Event
import dev.fourthbreakfast.planner.core.Note
import dev.fourthbreakfast.planner.core.Repeat
import dev.fourthbreakfast.planner.core.Task
import dev.fourthbreakfast.planner.core.binEntries
import dev.fourthbreakfast.planner.habits.HabitsScreen
import dev.fourthbreakfast.planner.habits.HabitsViewModel
import dev.fourthbreakfast.planner.notes.NotesViewModel
import dev.fourthbreakfast.planner.sync.SyncHealth
import dev.fourthbreakfast.planner.sync.health
import dev.fourthbreakfast.planner.tasks.TasksViewModel
import dev.fourthbreakfast.planner.today.TodayWorkspace
import dev.fourthbreakfast.planner.ui.NavigationIcon
import dev.fourthbreakfast.planner.ui.Tab
import dev.fourthbreakfast.planner.ui.cog
import dev.fourthbreakfast.planner.ui.tabLabel
import dev.fourthbreakfast.planner.ui.theme.PriorityHigh
import dev.fourthbreakfast.planner.ui.theme.PriorityLow
import dev.fourthbreakfast.planner.ui.theme.PriorityMedium
import kotlinx.coroutines.launch

@Composable
internal fun DesktopWorkspace(
    state: DashboardState,
    tasks: List<Task>,
    events: List<Event>,
    notes: List<Note>,
    taskModel: TasksViewModel,
    agenda: AgendaViewModel,
    noteModel: NotesViewModel,
    habits: HabitsViewModel?,
    sync: DesktopSync?,
) {
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val syncState = sync?.state?.collectAsState()?.value
    val scope = rememberCoroutineScope()
    val statusColor = when (syncState?.health) {
        SyncHealth.CURRENT -> PriorityLow
        SyncHealth.WORKING -> PriorityMedium
        SyncHealth.ERROR -> colors.error
        else -> colors.onSurfaceVariant
    }
    var panelEnd by remember { mutableStateOf(Float.POSITIVE_INFINITY) }
    var origin by remember { mutableStateOf(0f) }
    Row(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot().y }) {
        BoxWithConstraints(Modifier.width(Desk.sidebarWidth).fillMaxHeight()) {
            val header = 56.dp
            val groupHeight = 240.dp
            val top = navigationGroupTop(header.value, with(density) { (panelEnd - origin).toDp() }.value, groupHeight.value, maxHeight.value).dp
            Box(Modifier.fillMaxWidth().height(header).padding(start = 16.dp), contentAlignment = Alignment.CenterStart) {
                Image(remember { appIconPainter() }, contentDescription = "Planner", modifier = Modifier.size(28.dp))
            }
            Column(Modifier.offset(y = top).fillMaxWidth().padding(horizontal = 16.dp)) {
                Tab.entries.forEach { tab ->
                    Row(
                        Modifier.fillMaxWidth().height(48.dp).semantics {
                            role = Role.Tab
                            selected = state.destination == tab
                        }.clickable {
                            state.destination = tab
                            state.panel = null
                            state.focused = when (tab) {
                                Tab.AGENDA -> Section.AGENDA
                                Tab.NOTES -> Section.NOTES
                                else -> Section.TASKS
                            }
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        NavigationIcon(tab, Modifier.size(18.dp), state.destination == tab)
                        Text(
                            tabLabel(tab),
                            Modifier.weight(1f).padding(start = 12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (state.destination == tab) colors.onBackground else colors.onSurfaceVariant,
                        )
                        Box(Modifier.width(24.dp), contentAlignment = Alignment.CenterEnd) {
                            if (state.destination == tab) Canvas(Modifier.size(6.dp)) { drawCircle(PriorityHigh) }
                        }
                    }
                }
            }
            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(18.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(6.dp)) { drawCircle(statusColor) }
                }
                Box(
                    Modifier.weight(1f).heightIn(min = 32.dp).padding(start = 12.dp).clickable {
                        if (sync != null) scope.launch { sync.syncNow() } else state.panel = Panel.Settings
                    },
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text("Sync", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                }
                Box(
                    Modifier.width(32.dp).height(32.dp).semantics {
                        contentDescription = "Settings"
                    }.clickable { state.panel = Panel.Settings },
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Canvas(Modifier.size(16.dp)) { cog(colors.onSurfaceVariant) }
                }
            }
            Box(Modifier.align(Alignment.CenterEnd).width(1.dp).fillMaxHeight().background(colors.outlineVariant))
        }
        Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(tabLabel(state.destination), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                HeaderButton("Search notes", { state.panel = Panel.Search }) { magnifier(it) }
            }
            HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = colors.outlineVariant, thickness = 1.dp)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (state.destination) {
                    Tab.TODAY -> TodayWorkspace(
                        tasks,
                        events,
                        taskModel,
                        onSchedule = { state.panel = Panel.TaskSchedule(it.id) },
                        onOpenEvent = state::openEvent,
                        onTaskPanelEnd = { panelEnd = it },
                        onEditingChange = { state.editingTask = it },
                    )
                    Tab.TASKS -> TaskColumn(tasks, binEntries(tasks).size, state, taskModel)
                    Tab.HABITS -> habits?.let { HabitsScreen(it, { state.destination = Tab.TODAY }, embedded = true) }
                    Tab.AGENDA -> AgendaColumn(
                        events.filterNot { it.inBin },
                        events.count { it.inBin },
                        state,
                        onRemove = { state.eventToRemove = it },
                        onEmptyBin = agenda::emptyBin,
                        onMove = { event, from, day ->
                            agenda.save(event.id, from, movedDraft(event, from, day), onlyThisTime = event.repeat != Repeat.NONE)
                            state.chosenDay = day
                        },
                    )
                    Tab.NOTES -> NoteColumn(notes.filterNot { it.inBin }, notes.count { it.inBin }, state, noteModel)
                }
            }
        }
    }
}
