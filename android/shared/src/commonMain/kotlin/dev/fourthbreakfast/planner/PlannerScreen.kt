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
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.fourthbreakfast.planner.Launch
import dev.fourthbreakfast.planner.agenda.AgendaScreen
import dev.fourthbreakfast.planner.agenda.AgendaViewModel
import dev.fourthbreakfast.planner.core.binEntries
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
fun PlannerScreen(viewModel: TasksViewModel, agenda: AgendaViewModel, notes: NotesViewModel, settings: @Composable (onClose: () -> Unit) -> Unit) {
    val tasks by viewModel.all.collectAsStateWithLifecycle(initialValue = null)
    var binOpen by rememberSaveable { mutableStateOf(false) }
    var tab by rememberSaveable { mutableStateOf(Tab.TASKS) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
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
            tab = target
            settingsOpen = false
            searchOpen = false
            if (target == Tab.TASKS) binOpen = false
        }
    }
    val animated = rememberAnimationsOn()
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
            place.tab == Tab.AGENDA ->
                AgendaScreen(agenda, onShowTasks = { tab = Tab.TASKS }, onShowNotes = { tab = Tab.NOTES }, onOpenSettings = openSettings)
            place.tab == Tab.NOTES ->
                NotesScreen(notes, onShowAgenda = {
                    tab = Tab.AGENDA
                }, onShowTasks = { tab = Tab.TASKS }, onOpenSettings = openSettings, onOpenSearch = openSearch)
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
                    )
                }
        }
    }
}

/** Which screen is showing, as one value, so a change of it can be animated. */
private data class Place(val tab: Tab, val settings: Boolean, val bin: Boolean, val search: Boolean)
