package dev.fourthbreakfast.planner.agenda

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.fourthbreakfast.planner.R
import dev.fourthbreakfast.planner.core.Event
import dev.fourthbreakfast.planner.ui.BinList
import dev.fourthbreakfast.planner.ui.Tab

/** The bin for events: each one with its day. */
@Composable
fun AgendaBin(entries: List<Event>, viewModel: AgendaViewModel, onClose: () -> Unit) {
    val today = rememberToday()
    BinList(
        from = Tab.AGENDA,
        entries = entries,
        key = { it.id },
        onRestore = { viewModel.restore(it.id) },
        onDelete = { viewModel.deleteForever(it.id) },
        onEmpty = viewModel::emptyBin,
        onClose = onClose,
        question = { stringResource(R.string.confirm_delete_event, it.title) },
    ) { event ->
        Text(event.title, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = dateText(event.day, today),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
