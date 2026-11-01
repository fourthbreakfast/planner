package dev.fourthbreakfast.planner.agenda

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.fourthbreakfast.planner.R
import dev.fourthbreakfast.planner.core.Event
import dev.fourthbreakfast.planner.tasks.BinBar
import dev.fourthbreakfast.planner.tasks.QuietButton
import dev.fourthbreakfast.planner.ui.theme.Space

/** What a confirmation is waiting for: one event, or the whole bin when the event is null. */
private data class PendingDelete(val event: Event?)

/** The bin for events, laid out like the bin for tasks. */
@Composable
fun AgendaBin(entries: List<Event>, viewModel: AgendaViewModel, onClose: () -> Unit) {
    var pending by remember { mutableStateOf<PendingDelete?>(null) }
    BackHandler(onBack = onClose)
    val today = today()
    val colors = MaterialTheme.colorScheme

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { BinBar(entries.size, onEmpty = { pending = PendingDelete(null) }, onClose = onClose) },
    ) { padding ->
        if (entries.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.bin_is_empty))
            }
        } else {
            LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = Space.listEnd())) {
                items(entries, key = { it.id }) { event ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.animateItem().padding(start = Space.gutter, end = Space.rowPad),
                    ) {
                        Column(Modifier.weight(1f).padding(vertical = Space.x3)) {
                            Text(event.title, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = dateText(event.day, today),
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.onSurfaceVariant,
                            )
                        }
                        QuietButton(stringResource(R.string.restore)) { viewModel.restore(event.id) }
                        QuietButton(stringResource(R.string.delete)) { pending = PendingDelete(event) }
                    }
                }
            }
        }
    }

    pending?.let { request ->
        AlertDialog(
            onDismissRequest = { pending = null },
            text = {
                Text(
                    if (request.event != null) {
                        stringResource(R.string.confirm_delete_event, request.event.title)
                    } else {
                        stringResource(R.string.confirm_empty_bin)
                    },
                )
            },
            confirmButton = {
                QuietButton(stringResource(R.string.delete_forever)) {
                    if (request.event != null) viewModel.deleteForever(request.event.id) else viewModel.emptyBin()
                    pending = null
                }
            },
            dismissButton = { QuietButton(stringResource(R.string.cancel)) { pending = null } },
        )
    }
}
