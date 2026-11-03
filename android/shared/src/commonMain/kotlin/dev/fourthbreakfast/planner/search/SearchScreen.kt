package dev.fourthbreakfast.planner.search

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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.fourthbreakfast.planner.Launch
import dev.fourthbreakfast.planner.core.matchesAll
import dev.fourthbreakfast.planner.core.noteText
import dev.fourthbreakfast.planner.core.noteTitle
import dev.fourthbreakfast.planner.core.searchWords
import dev.fourthbreakfast.planner.notes.NotesViewModel
import dev.fourthbreakfast.planner.resources.Res
import dev.fourthbreakfast.planner.resources.nothing_found
import dev.fourthbreakfast.planner.resources.search
import dev.fourthbreakfast.planner.ui.BackButton
import dev.fourthbreakfast.planner.ui.BackHandler
import dev.fourthbreakfast.planner.ui.theme.Space
import org.jetbrains.compose.resources.stringResource

private const val MOST = 40

/** A search of the notes: by title and by every line of text. Touching a result opens the note. */
@Composable
fun SearchScreen(notes: NotesViewModel, onOpen: (Launch) -> Unit, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val colors = MaterialTheme.colorScheme
    var query by rememberSaveable { mutableStateOf("") }
    val allNotes by notes.all.collectAsStateWithLifecycle(initialValue = emptyList())
    val words = searchWords(query)
    val found = remember(allNotes, query) { allNotes.filter { !it.inBin && matchesAll(noteText(it), words) }.take(MOST) }
    val focus = remember { FocusRequester() }
    val searchLabel = stringResource(Res.string.search)
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
            items(found, key = { it.id }) { note ->
                val line = noteText(note).lines().drop(1).firstOrNull { l -> words.any { it in l.lowercase() } }?.trim()
                Result(noteTitle(note), line) { onOpen(Launch.OpenNote(note.id)) }
            }
            if (words.isNotEmpty() && found.isEmpty()) {
                item(key = "none") {
                    Text(
                        text = stringResource(Res.string.nothing_found),
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
