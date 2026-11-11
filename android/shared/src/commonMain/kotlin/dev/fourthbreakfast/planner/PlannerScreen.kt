package dev.fourthbreakfast.planner

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.fourthbreakfast.planner.Launch
import dev.fourthbreakfast.planner.agenda.AgendaScreen
import dev.fourthbreakfast.planner.agenda.AgendaViewModel
import dev.fourthbreakfast.planner.agenda.TaskScheduleEditor
import dev.fourthbreakfast.planner.core.binEntries
import dev.fourthbreakfast.planner.habits.HabitsScreen
import dev.fourthbreakfast.planner.habits.HabitsViewModel
import dev.fourthbreakfast.planner.notes.NotesScreen
import dev.fourthbreakfast.planner.notes.NotesViewModel
import dev.fourthbreakfast.planner.search.SearchScreen
import dev.fourthbreakfast.planner.tasks.BinScreen
import dev.fourthbreakfast.planner.tasks.TaskList
import dev.fourthbreakfast.planner.tasks.TasksViewModel
import dev.fourthbreakfast.planner.ui.LocalServices
import dev.fourthbreakfast.planner.ui.Tab
import dev.fourthbreakfast.planner.ui.rememberAnimationsOn
import kotlinx.coroutines.launch

/**
 * The app: the three tabs, the bin, the search, and the settings, which each device draws in its own [settings].
 */
@Composable
fun PlannerScreen(
    viewModel: TasksViewModel,
    agenda: AgendaViewModel,
    notes: NotesViewModel,
    habits: HabitsViewModel? = null,
    settings: @Composable (onClose: () -> Unit) -> Unit,
) {
    val tasks by viewModel.all.collectAsStateWithLifecycle(initialValue = null)
    var binOpen by rememberSaveable { mutableStateOf(false) }
    var tab by rememberSaveable { mutableStateOf(Tab.TODAY) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var schedulingTaskId by rememberSaveable { mutableStateOf<String?>(null) }
    val listState = rememberSaveableStateHolder()
    val all = tasks
    val entries = remember(all) { binEntries(all.orEmpty()) }
    val active = remember(all) { all.orEmpty().filterNot { it.inBin } }
    val openSettings = { settingsOpen = true }
    val openSearch = { searchOpen = true }
    val requests = LocalServices.current.requests
    val request = requests.pending
    // a shortcut, a notification or a share brings the app to the right tab, which then acts on it
    LaunchedEffect(request) {
        val target = when (request) {
            Launch.NewTask -> Tab.TASKS
            Launch.NewEvent, is Launch.OpenEvent -> Tab.AGENDA
            Launch.NewNote, is Launch.OpenNote, is Launch.Shared -> Tab.NOTES
            null -> null
        }
        if (target != null) {
            schedulingTaskId = null
            tab = target
            settingsOpen = false
            searchOpen = false
            binOpen = false
        }
    }
    val schedulingTask = active.firstOrNull { it.id == schedulingTaskId }
    if (schedulingTask != null) {
        TaskScheduleEditor(schedulingTask, agenda) { schedulingTaskId = null }
        return
    }
    val animated = rememberAnimationsOn()
    val typing = dev.fourthbreakfast.planner.ui.rememberKeyboardShown()
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        androidx.compose.foundation.layout.Box(Modifier.weight(1f).statusBarsPadding().imePadding()) {
            CompositionLocalProvider(dev.fourthbreakfast.planner.ui.LocalEmbeddedNavigation provides true) {
                AnimatedContent(
                    modifier = Modifier.background(MaterialTheme.colorScheme.background),
                    targetState = Place(tab, settingsOpen, binOpen, searchOpen),
                    transitionSpec = {
                        when {
                            !animated -> EnterTransition.None togetherWith ExitTransition.None
                            // settings and the bin fade in and out; moving between tabs slides the way the finger swept
                            initialState.settings != targetState.settings || initialState.bin != targetState.bin || initialState.search != targetState.search ->
                                fadeIn(tween(160)) togetherWith fadeOut(tween(120))
                            else -> {
                                val way = if (targetState.tab.ordinal > initialState.tab.ordinal) 1 else -1
                                (slideInHorizontally(tween(220)) { way * it / 5 } + fadeIn(tween(220))) togetherWith
                                    (slideOutHorizontally(tween(220)) { -way * it / 5 } + fadeOut(tween(140)))
                            }
                        }
                    },
                    label = "screen",
                ) { place ->
                    when {
                        place.search -> SearchScreen(
                            notes,
                            onOpen = { launch ->
                                requests.ask(launch)
                                searchOpen = false
                            },
                            onClose = { searchOpen = false },
                        )
                        place.settings -> settings { settingsOpen = false }
                        place.tab == Tab.TODAY -> {
                            val events by agenda.all.collectAsStateWithLifecycle(initialValue = emptyList())
                            Column(Modifier.fillMaxSize()) {
                                Row(
                                    Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 16.dp),
                                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                ) {
                                    Text("Today", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                                    dev.fourthbreakfast.planner.ui.SettingsButton(openSettings)
                                }
                                androidx.compose.material3.HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline)
                                dev.fourthbreakfast.planner.today.TodayWorkspace(
                                    active,
                                    events,
                                    viewModel,
                                    onSchedule = { schedulingTaskId = it.id },
                                    onOpenEvent = { id ->
                                        requests.ask(Launch.OpenEvent(id))
                                        tab = Tab.AGENDA
                                    },
                                )
                            }
                        }
                        place.tab == Tab.HABITS && habits != null -> HabitsScreen(
                            habits,
                            onClose = { tab = Tab.TASKS },
                            onSelectTab = { tab = it },
                            onOpenSettings = openSettings,
                        )
                        place.tab == Tab.AGENDA ->
                            AgendaScreen(
                                agenda,
                                onShowTasks = { tab = Tab.TASKS },
                                onShowNotes = { tab = Tab.NOTES },
                                onOpenSettings = openSettings,
                                onShowHabits = { tab = Tab.HABITS },
                            )
                        place.tab == Tab.NOTES ->
                            NotesScreen(notes, onShowAgenda = {
                                tab = Tab.AGENDA
                            }, onShowTasks = { tab = Tab.TASKS }, onOpenSettings = openSettings, onOpenSearch = openSearch, onShowHabits = { tab = Tab.HABITS })
                        place.bin -> BinScreen(entries, viewModel, onClose = { binOpen = false })
                        else ->
                            // keeps the aim and collapsed tasks while the bin is open
                            listState.SaveableStateProvider("list") {
                                TaskList(
                                    tasks = active,
                                    binCount = entries.size,
                                    onOpenBin = { binOpen = true },
                                    onSelectTab = { tab = it },
                                    onOpenSettings = openSettings,
                                    viewModel = viewModel,
                                    onScheduleTask = { schedulingTaskId = it.id },
                                )
                            }
                    }
                }
            }
        }
        if (!settingsOpen && !searchOpen && !binOpen && !typing) {
            dev.fourthbreakfast.planner.ui.TabBar(tab, { tab = it }, modifier = Modifier.navigationBarsPadding(), onLongPress = openSettings)
        }
    }
}

/** Which screen is showing, as one value, so a change of it can be animated. */
private data class Place(val tab: Tab, val settings: Boolean, val bin: Boolean, val search: Boolean)
