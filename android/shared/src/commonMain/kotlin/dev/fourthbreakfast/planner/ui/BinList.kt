package dev.fourthbreakfast.planner.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import dev.fourthbreakfast.planner.resources.Res
import dev.fourthbreakfast.planner.resources.bin
import dev.fourthbreakfast.planner.resources.bin_is_empty
import dev.fourthbreakfast.planner.resources.bin_with_count
import dev.fourthbreakfast.planner.resources.cancel
import dev.fourthbreakfast.planner.resources.confirm_empty_bin
import dev.fourthbreakfast.planner.resources.delete
import dev.fourthbreakfast.planner.resources.delete_forever
import dev.fourthbreakfast.planner.resources.empty_bin
import dev.fourthbreakfast.planner.resources.restore
import dev.fourthbreakfast.planner.ui.theme.Space
import org.jetbrains.compose.resources.stringResource

/**
 * The top of a bin. The word "bin" is in the calculator lettering, in the place of the tab it was opened from (tasks
 * at the left, agenda in the middle, notes at the right), and the row under it is the main screen's row: the count
 * at the left, the arrow where the bin button was.
 */
@Composable
fun BinBar(count: Int, from: Tab, onEmpty: () -> Unit, onClose: () -> Unit) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = Space.rowPad),
        ) {
            listOf(Tab.TASKS, Tab.AGENDA, Tab.NOTES).forEach { tab ->
                Box(
                    contentAlignment = when (tab) {
                        Tab.TASKS -> Alignment.CenterStart
                        Tab.AGENDA -> Alignment.Center
                        Tab.NOTES -> Alignment.CenterEnd
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = Space.touch)
                        .padding(horizontal = Space.gutter - Space.rowPad),
                ) {
                    if (tab == from) {
                        SegmentText(
                            text = stringResource(Res.string.bin),
                            color = MaterialTheme.colorScheme.onBackground,
                            scale = TAB_SCALE,
                            modifier = Modifier.semantics { heading() },
                        )
                    }
                }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Space.gutter, end = Space.rowPad),
        ) {
            TitleWithCount(
                title = null,
                count = count,
                countLabel = stringResource(Res.string.bin_with_count, count),
                modifier = Modifier.weight(1f),
            )
            if (count > 0) QuietButton(stringResource(Res.string.empty_bin), onEmpty)
            BackButton(onClose)
        }
    }
}

/** What a confirmation is waiting for: one entry, or the whole bin when the entry is null. */
private class BinQuestion<T>(val entry: T?)

/** A bin: its bar, then each thing in it with restore and delete. Deleting for good always asks first. */
@Composable
fun <T> BinList(
    from: Tab,
    entries: List<T>,
    key: (T) -> String,
    onRestore: (T) -> Unit,
    onDelete: (T) -> Unit,
    onEmpty: () -> Unit,
    onClose: () -> Unit,
    question: @Composable (T) -> String,
    dividers: Boolean = false,
    row: @Composable (T) -> Unit,
) {
    var asking by remember { mutableStateOf<BinQuestion<T>?>(null) }
    BackHandler(onBack = onClose)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { BinBar(entries.size, from, onEmpty = { asking = BinQuestion(null) }, onClose = onClose) },
    ) { padding ->
        if (entries.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(Res.string.bin_is_empty))
            }
        } else {
            LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = Space.listEnd())) {
                items(entries, key = key) { entry ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.animateItem().padding(start = Space.gutter, end = Space.rowPad),
                    ) {
                        Column(Modifier.weight(1f).padding(vertical = Space.x3)) { row(entry) }
                        QuietButton(stringResource(Res.string.restore)) { onRestore(entry) }
                        QuietButton(stringResource(Res.string.delete)) { asking = BinQuestion(entry) }
                    }
                    if (dividers) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }

    asking?.let { request ->
        val entry = request.entry
        AlertDialog(
            onDismissRequest = { asking = null },
            text = { Text(if (entry != null) question(entry) else stringResource(Res.string.confirm_empty_bin)) },
            confirmButton = {
                QuietButton(stringResource(Res.string.delete_forever)) {
                    if (entry != null) onDelete(entry) else onEmpty()
                    asking = null
                }
            },
            dismissButton = { QuietButton(stringResource(Res.string.cancel)) { asking = null } },
        )
    }
}
