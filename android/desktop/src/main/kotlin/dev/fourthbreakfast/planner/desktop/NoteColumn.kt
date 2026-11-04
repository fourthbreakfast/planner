package dev.fourthbreakfast.planner.desktop

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import dev.fourthbreakfast.planner.core.Note
import dev.fourthbreakfast.planner.core.linesUnderTitle
import dev.fourthbreakfast.planner.core.noteTitle
import dev.fourthbreakfast.planner.notes.NotesViewModel
import dev.fourthbreakfast.planner.notes.previewOf
import dev.fourthbreakfast.planner.notes.rememberPreviewBoxes
import dev.fourthbreakfast.planner.resources.Res
import dev.fourthbreakfast.planner.resources.add_note
import dev.fourthbreakfast.planner.resources.nothing_written
import dev.fourthbreakfast.planner.resources.pin
import dev.fourthbreakfast.planner.resources.pinned
import dev.fourthbreakfast.planner.resources.remove_task
import dev.fourthbreakfast.planner.resources.search
import dev.fourthbreakfast.planner.resources.unpin
import dev.fourthbreakfast.planner.ui.BinButton
import dev.fourthbreakfast.planner.ui.theme.Space
import org.jetbrains.compose.resources.stringResource

/** The notes, each with its first lines, pinned ones first. A click opens one beside the dashboard; dragging reorders. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteColumn(notes: List<Note>, binCount: Int, state: DashboardState, viewModel: NotesViewModel) {
    val removeTaskLabel = stringResource(Res.string.remove_task)
    val unpinLabel = stringResource(Res.string.unpin)
    val pinLabel = stringResource(Res.string.pin)
    val colors = MaterialTheme.colorScheme
    val boxes = rememberPreviewBoxes(colors.onSurfaceVariant)
    state.visibleNotes = notes.map { it.id }
    val list = rememberLazyListState()
    var dragY by remember { mutableStateOf(0f) }
    var target by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        ColumnHeader("notes", notes.size, state.focused == Section.NOTES) {
            HeaderButton(stringResource(Res.string.search), { state.panel = Panel.Search }) { magnifier(it) }
            BinButton(binCount, { state.panel = Panel.NoteBin })
        }
        if (notes.isEmpty()) Quiet(stringResource(Res.string.nothing_written))
        LazyColumn(state = list, modifier = Modifier.weight(1f).fillMaxWidth()) {
            items(notes, key = { it.id }) { note ->
                val (hover, hovered) = rememberHover()
                val selected = (state.focused == Section.NOTES && state.selectedNote == note.id) || (state.panel as? Panel.NoteOpen)?.id == note.id
                ContextMenuArea(items = {
                    listOf(
                        ContextMenuItem(if (note.pinned) unpinLabel else pinLabel) { viewModel.setPinned(note.id, !note.pinned) },
                        ContextMenuItem(removeTaskLabel) { viewModel.moveToBin(note.id) },
                    )
                }) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(
                                when {
                                    selected -> colors.secondaryContainer
                                    hovered -> colors.secondaryContainer.copy(alpha = 0.5f)
                                    else -> colors.background
                                },
                            )
                            .drawBehind {
                                if (target == note.id) drawLine(colors.onBackground, Offset(0f, 0f), Offset(size.width, 0f), Space.strokeThick.toPx())
                            }
                            .hoverable(hover)
                            .combinedClickable(
                                onClick = {
                                    state.focused = Section.NOTES
                                    state.selectedNote = note.id
                                    state.openNote(note.id)
                                },
                            )
                            .pointerInput(note.id) {
                                detectDragGestures(
                                    onDragStart = { at ->
                                        dragY = (list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == note.id }?.offset ?: 0) + at.y
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragY += amount.y
                                        target = list.layoutInfo.visibleItemsInfo
                                            .firstOrNull { dragY >= it.offset && dragY < it.offset + it.size }
                                            ?.key?.let { it as? String }?.takeIf { it != note.id }
                                    },
                                    onDragEnd = {
                                        // dropped on a note, it goes just above it
                                        target?.let { viewModel.move(note.id, it) }
                                        target = null
                                    },
                                    onDragCancel = { target = null },
                                )
                            }
                            .padding(start = Space.gutter, end = Space.gutter, top = Space.x3, bottom = Space.x3),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                noteTitle(note),
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (note.pinned) {
                                Text(
                                    stringResource(Res.string.pinned),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colors.onSurfaceVariant,
                                )
                            }
                            if (hovered) BinButton(0, { viewModel.moveToBin(note.id) })
                        }
                        val lines = remember(note) { linesUnderTitle(note) }
                        if (lines.isNotEmpty()) {
                            Text(
                                previewOf(lines, 4),
                                inlineContent = boxes,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurfaceVariant,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
        FootAdd(stringResource(Res.string.add_note)) { viewModel.add { id -> state.openNote(id) } }
    }
}
