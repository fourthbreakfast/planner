package dev.fourthbreakfast.planner.notes

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import dev.fourthbreakfast.planner.R
import dev.fourthbreakfast.planner.core.Note
import dev.fourthbreakfast.planner.core.noteTitle
import dev.fourthbreakfast.planner.ui.BinList
import dev.fourthbreakfast.planner.ui.Tab

/** The bin for notes: each one by its title. */
@Composable
fun NotesBin(entries: List<Note>, viewModel: NotesViewModel, onClose: () -> Unit) {
    BinList(
        from = Tab.NOTES,
        entries = entries,
        key = { it.id },
        onRestore = { viewModel.restore(it.id) },
        onDelete = { viewModel.deleteForever(it.id) },
        onEmpty = viewModel::emptyBin,
        onClose = onClose,
        question = { stringResource(R.string.confirm_delete_note) },
    ) { note ->
        Text(
            text = noteTitle(note),
            maxLines = 2,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
