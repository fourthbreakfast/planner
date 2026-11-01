package dev.fourthbreakfast.planner.search

import androidx.activity.compose.BackHandler
import dev.fourthbreakfast.planner.tasks.Tab
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.fourthbreakfast.planner.Launch
import dev.fourthbreakfast.planner.R
import dev.fourthbreakfast.planner.agenda.AgendaViewModel
import dev.fourthbreakfast.planner.agenda.dateText
import dev.fourthbreakfast.planner.agenda.rememberIs24Hour
import dev.fourthbreakfast.planner.agenda.rememberToday
import dev.fourthbreakfast.planner.agenda.timeText
import dev.fourthbreakfast.planner.core.eventText
import dev.fourthbreakfast.planner.core.matchesAll
import dev.fourthbreakfast.planner.core.noteText
import dev.fourthbreakfast.planner.core.noteTitle
import dev.fourthbreakfast.planner.core.searchWords
import dev.fourthbreakfast.planner.notes.NotesViewModel
import dev.fourthbreakfast.planner.tasks.BackButton
import dev.fourthbreakfast.planner.tasks.TasksViewModel
import dev.fourthbreakfast.planner.ui.theme.Space

private const val MOST = 40

/** A search of the tab it was opened from: its tasks, its events or its notes. Touching a result goes to it. */
@Composable
fun SearchScreen(
    scope: Tab,
    tasks: TasksViewModel,
    agenda: AgendaViewModel,
    notes: NotesViewModel,
    onOpen: (Launch) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    val colors = MaterialTheme.colorScheme
    var query by rememberSaveable { mutableStateOf("") }
    val allTasks by tasks.all.collectAsStateWithLifecycle(initialValue = emptyList())
    val allEvents by agenda.all.collectAsStateWithLifecycle(initialValue = emptyList())
    val allNotes by notes.all.collectAsStateWithLifecycle(initialValue = emptyList())
    val today = rememberToday()
    val is24Hour = rememberIs24Hour()
    val words = searchWords(query)
    val foundTasks = remember(allTasks, query) {
        if (scope != Tab.TASKS) emptyList() else allTasks.filter { !it.inBin && matchesAll(it.title, words) }.take(MOST)
    }
    val foundEvents = remember(allEvents, query) {
        if (scope != Tab.AGENDA) emptyList() else allEvents.filter { !it.inBin && matchesAll(eventText(it), words) }.take(MOST)
    }
    val foundNotes = remember(allNotes, query) {
        if (scope != Tab.NOTES) emptyList() else allNotes.filter { !it.inBin && matchesAll(noteText(it), words) }.take(MOST)
    }
    val focus = remember { FocusRequester() }
    val searchLabel = stringResource(R.string.search)
    LaunchedEffect(Unit) { focus.requestFocus() }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = Space.gutter, end = Space.rowPad),
            ) {
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.headlineSmall.copy(color = colors.onBackground),
                    cursorBrush = SolidColor(colors.onBackground),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Search),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = Space.touch)
                        .semantics { if (query.isEmpty()) contentDescription = searchLabel }
                        .focusRequester(focus),
                    decorationBox = { field ->
                        Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.heightIn(min = Space.touch)) { field() }
                    },
                )
                BackButton(onClose)
            }
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            if (foundTasks.isNotEmpty()) {
                items(foundTasks, key = { "t-" + it.id }) { task ->
                    val parent = allTasks.firstOrNull { it.id == task.parentId }?.title
                    Result(task.title, parent) { onOpen(Launch.ShowTask(task.id)) }
                }
            }
            if (foundEvents.isNotEmpty()) {
                items(foundEvents, key = { "e-" + it.id }) { event ->
                    val when_ = dateText(event.day, today) + (event.minute?.let { " · " + timeText(it, is24Hour) } ?: "")
                    Result(event.title, when_) { onOpen(Launch.OpenEvent(event.id)) }
                }
            }
            if (foundNotes.isNotEmpty()) {
                items(foundNotes, key = { "n-" + it.id }) { note ->
                    val line = noteText(note).lines().drop(1).firstOrNull { l -> words.any { it in l.lowercase() } }?.trim()
                    Result(noteTitle(note), line) { onOpen(Launch.OpenNote(note.id)) }
                }
            }
            if (words.isNotEmpty() && foundTasks.isEmpty() && foundEvents.isEmpty() && foundNotes.isEmpty()) {
                item(key = "none") {
                    Text(
                        text = stringResource(R.string.nothing_found),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(start = Space.gutter, top = Space.x4),
                    )
                }
            }
            item(key = "end") { Spacer(Modifier.height(Space.listEnd())) }
        }
    }
}

@Composable
private fun Result(title: String, detail: String?, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = Space.touch)
            .padding(horizontal = Space.gutter, vertical = Space.x2),
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge, color = colors.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (!detail.isNullOrEmpty()) {
            Text(text = detail, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
