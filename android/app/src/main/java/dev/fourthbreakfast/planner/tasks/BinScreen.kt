package dev.fourthbreakfast.planner.tasks

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import dev.fourthbreakfast.planner.R
import dev.fourthbreakfast.planner.core.BinEntry
import dev.fourthbreakfast.planner.ui.BinList
import dev.fourthbreakfast.planner.ui.Tab

/** The bin for tasks: each entry says how many tasks went with it. */
@Composable
fun BinScreen(entries: List<BinEntry>, viewModel: TasksViewModel, onClose: () -> Unit) {
    BinList(
        from = Tab.TASKS,
        entries = entries,
        key = { it.task.id },
        onRestore = { viewModel.restore(it.task) },
        onDelete = { viewModel.deleteForever(it.task) },
        onEmpty = viewModel::emptyBin,
        onClose = onClose,
        question = { stringResource(R.string.confirm_delete_task, it.task.title) },
        dividers = true,
    ) { entry ->
        Text(entry.task.title, style = MaterialTheme.typography.bodyLarge)
        if (entry.insideCount > 0) {
            Text(
                text = pluralStringResource(R.plurals.tasks_inside, entry.insideCount, entry.insideCount),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
