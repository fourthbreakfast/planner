package dev.fourthbreakfast.planner.tasks

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.fourthbreakfast.planner.R
import dev.fourthbreakfast.planner.ui.theme.Space
import dev.fourthbreakfast.planner.core.BinEntry
import dev.fourthbreakfast.planner.core.Task

/** What a confirmation is waiting for: one task, or the whole bin when the task is null. */
private data class PendingDelete(val task: Task?)

@Composable
fun BinScreen(entries: List<BinEntry>, viewModel: TasksViewModel, onClose: () -> Unit) {
    var pending by remember { mutableStateOf<PendingDelete?>(null) }
    BackHandler(onBack = onClose)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            // the same bar as the main screen: title and number on the left, the controls on the right
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = Space.gutter, end = Space.rowPad),
            ) {
                TitleWithCount(
                    title = stringResource(R.string.bin),
                    count = entries.size,
                    countLabel = stringResource(R.string.bin_with_count, entries.size),
                    modifier = Modifier.weight(1f),
                )
                if (entries.isNotEmpty()) {
                    QuietButton(stringResource(R.string.empty_bin)) { pending = PendingDelete(null) }
                }
                BackButton(onClose)
            }
        },
    ) { padding ->
        if (entries.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.bin_is_empty))
            }
        } else {
            LazyColumn(Modifier.padding(padding).fillMaxSize()) {
                items(entries, key = { it.task.id }) { entry ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.animateItem().padding(start = Space.gutter, end = Space.rowPad),
                    ) {
                        Column(Modifier.weight(1f).padding(vertical = Space.x3)) {
                            Text(entry.task.title, style = MaterialTheme.typography.bodyLarge)
                            if (entry.insideCount > 0) {
                                Text(
                                    text = pluralStringResource(
                                        R.plurals.tasks_inside,
                                        entry.insideCount,
                                        entry.insideCount,
                                    ),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        QuietButton(stringResource(R.string.restore)) { viewModel.restore(entry.task) }
                        QuietButton(stringResource(R.string.delete)) { pending = PendingDelete(entry.task) }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }

    pending?.let { request ->
        AlertDialog(
            onDismissRequest = { pending = null },
            text = {
                Text(
                    if (request.task != null) {
                        stringResource(R.string.confirm_delete_task, request.task.title)
                    } else {
                        stringResource(R.string.confirm_empty_bin)
                    },
                )
            },
            confirmButton = {
                QuietButton(stringResource(R.string.delete_forever)) {
                    if (request.task != null) viewModel.deleteForever(request.task) else viewModel.emptyBin()
                    pending = null
                }
            },
            dismissButton = {
                QuietButton(stringResource(R.string.cancel)) { pending = null }
            },
        )
    }
}

@Composable
fun QuietButton(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}
