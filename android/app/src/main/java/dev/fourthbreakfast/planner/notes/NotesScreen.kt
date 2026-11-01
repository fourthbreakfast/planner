package dev.fourthbreakfast.planner.notes

import androidx.activity.compose.BackHandler
import dev.fourthbreakfast.planner.PlannerApp
import androidx.compose.ui.platform.LocalContext
import dev.fourthbreakfast.planner.tasks.showUndo
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.fourthbreakfast.planner.R
import dev.fourthbreakfast.planner.core.Note
import dev.fourthbreakfast.planner.core.linesUnderTitle
import dev.fourthbreakfast.planner.core.noteTitle
import dev.fourthbreakfast.planner.tasks.AddButton
import dev.fourthbreakfast.planner.tasks.BinBar
import dev.fourthbreakfast.planner.tasks.BinButton
import dev.fourthbreakfast.planner.tasks.DiscreetMessage
import dev.fourthbreakfast.planner.tasks.QuietButton
import dev.fourthbreakfast.planner.tasks.RowSwipe
import dev.fourthbreakfast.planner.tasks.SwipeRow
import dev.fourthbreakfast.planner.tasks.Tab
import dev.fourthbreakfast.planner.tasks.TabBar
import dev.fourthbreakfast.planner.tasks.TitleWithCount
import dev.fourthbreakfast.planner.tasks.rowKeyAt
import dev.fourthbreakfast.planner.tasks.sweepAndHold
import dev.fourthbreakfast.planner.ui.theme.Space
import kotlinx.coroutines.launch

private const val END_KEY = "end"
private const val PREVIEW_LINES = 8

/** The notes: one on top of another in the order they were put, with a touch opening one to write in. */
@Composable
fun NotesScreen(viewModel: NotesViewModel, onShowAgenda: () -> Unit, onShowTasks: () -> Unit, onOpenSettings: () -> Unit) {
    val settings = (LocalContext.current.applicationContext as PlannerApp).settings
    val everything by viewModel.all.collectAsStateWithLifecycle(initialValue = emptyList())
    val notes = remember(everything) { everything.filterNot { it.inBin } }
    val binned = remember(everything) { everything.filter { it.inBin }.sortedByDescending { it.deletedAt } }
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    var binOpen by rememberSaveable { mutableStateOf(false) }
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val binnedMessage = stringResource(R.string.task_removed)
    val undoLabel = stringResource(R.string.undo)

    fun moveToBinWithUndo(id: String) {
        viewModel.moveToBin(id)
        scope.launch { if (snackbarHost.showUndo(binnedMessage, undoLabel)) viewModel.restore(id) }
    }

    val open = notes.firstOrNull { it.id == openId }
    when {
        open != null -> NoteEditor(
            initialTitle = open.title,
            initialBody = open.body,
            toolbarAbove = settings.toolbarAbove,
            onSave = { title, body -> viewModel.save(open.id, title, body) },
            onClose = { title, body ->
                viewModel.close(open.id, title, body)
                openId = null
            },
        )
        binOpen -> NotesBin(binned, viewModel, onClose = { binOpen = false })
        else -> NotesList(
            notes = notes,
            binCount = binned.size,
            snackbarHost = snackbarHost,
            onShowAgenda = onShowAgenda,
            onShowTasks = onShowTasks,
            onOpenSettings = onOpenSettings,
            onOpenBin = { binOpen = true },
            onNew = { viewModel.add { openId = it } },
            onOpen = { openId = it },
            onRemove = ::moveToBinWithUndo,
            onMove = viewModel::move,
        )
    }
}

@Composable
private fun NotesList(
    notes: List<Note>,
    binCount: Int,
    snackbarHost: SnackbarHostState,
    onShowAgenda: () -> Unit,
    onShowTasks: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenBin: () -> Unit,
    onNew: () -> Unit,
    onOpen: (String) -> Unit,
    onRemove: (String) -> Unit,
    onMove: (String, String?) -> Unit,
) {
    BackHandler(onBack = onShowTasks)
    val list = rememberLazyListState()
    val endSpace = Space.listEnd()
    val scope = rememberCoroutineScope()
    val swipe = remember(scope) { RowSwipe(scope, canGoLeft = false) }
    var listBounds by remember { mutableStateOf(Rect.Zero) }
    val colors = MaterialTheme.colorScheme
    // the note held by a long press, where the finger is, and the note it would go in front of (the end key for last)
    var dragKey by remember { mutableStateOf<String?>(null) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var grab by remember { mutableFloatStateOf(0f) }
    var dropKey by remember { mutableStateOf<String?>(null) }

    fun updateDrop() {
        val held = list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == dragKey } ?: return
        val centre = dragY - grab + held.size / 2f
        val target = list.layoutInfo.visibleItemsInfo
            .filter { it.key != dragKey && it.key != END_KEY }
            .firstOrNull { it.offset + it.size / 2f > centre }
        dropKey = (target?.key as? String) ?: END_KEY
    }

    fun finishDrag() {
        val key = dragKey
        val before = dropKey
        dragKey = null
        dropKey = null
        if (key != null && before != null) onMove(key, before.takeIf { it != END_KEY })
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .sweepAndHold(
                enabled = true,
                onSweepLeft = null,
                onSweepRight = onShowAgenda,
                rowAt = { at -> (list.rowKeyAt(at, listBounds) as? String)?.takeIf { it != END_KEY } },
                swipe = swipe,
                onSwipedRight = onRemove,
                onHold = { key, at ->
                    dragKey = key
                    dragY = at.y - listBounds.top
                    grab = dragY - (list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }?.offset ?: 0)
                    updateDrop()
                },
                onDrag = { dy ->
                    dragY += dy
                    updateDrop()
                },
                onRelease = { moved ->
                    if (!moved) dropKey = null
                    finishDrag()
                },
            ),
        snackbarHost = {
            SnackbarHost(snackbarHost) { data ->
                DiscreetMessage(data.visuals.message, data.visuals.actionLabel, data::performAction)
            }
        },
        floatingActionButton = { AddButton(stringResource(R.string.add_note), onNew) },
        topBar = {
            Column {
                TabBar(
                    selected = Tab.NOTES,
                    onSelect = {
                        when (it) {
                            Tab.TASKS -> onShowTasks()
                            Tab.AGENDA -> onShowAgenda()
                            Tab.NOTES -> Unit
                        }
                    },
                    onLongPress = onOpenSettings,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = Space.gutter, end = Space.rowPad),
                ) {
                    TitleWithCount(
                        title = null,
                        count = notes.size,
                        countLabel = pluralStringResource(R.plurals.notes_count, notes.size, notes.size),
                        modifier = Modifier.weight(1f),
                    )
                    BinButton(binCount, onOpenBin)
                }
            }
        },
    ) { padding ->
        if (notes.isEmpty()) {
            Box(
                Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.nothing_written),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                state = list,
                // otherwise the list takes the finger's movement and cancels the drag
                userScrollEnabled = dragKey == null,
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .onGloballyPositioned { listBounds = it.boundsInRoot() },
            ) {
                items(notes, key = { it.id }) { note ->
                    val dragging = dragKey == note.id
                    NoteRow(
                        note = note,
                        dropAbove = dropKey == note.id,
                        modifier = Modifier
                            .animateItem()
                            .then(if (dragging) Modifier.zIndex(1f) else Modifier)
                            .graphicsLayer {
                                if (dragging) {
                                    val offset = list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == note.id }?.offset ?: 0
                                    translationY = dragY - grab - offset
                                    alpha = 0.92f
                                    shadowElevation = 12f
                                }
                            },
                        swipe = swipe,
                        onOpen = { onOpen(note.id) },
                        onRemove = { onRemove(note.id) },
                    )
                }
                // a third of the screen left empty under the last note, so the list can always be pulled up
                item(key = END_KEY) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(endSpace)
                            .drawBehind {
                                if (dropKey == END_KEY) {
                                    drawLine(colors.onBackground, Offset(Space.gutter.toPx(), 0f), Offset(size.width - Space.gutter.toPx(), 0f), Space.strokeThick.toPx())
                                }
                            },
                    )
                }
            }
        }
    }
}

/** One note as it will read: its first lines, marked and pushed in as written. A touch opens it, a swipe right bins it. */
@Composable
private fun NoteRow(
    note: Note,
    dropAbove: Boolean,
    swipe: RowSwipe,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val removeLabel = stringResource(R.string.remove_task)
    val title = noteTitle(note)
    val preview = remember(note.title, note.body) { previewOf(linesUnderTitle(note), PREVIEW_LINES - 1) }
    SwipeRow(
        key = note.id,
        swipe = swipe,
        removeLabel = removeLabel,
        renameLabel = "",
        modifier = modifier
            .drawBehind {
                if (dropAbove) {
                    drawLine(colors.onBackground, Offset(Space.gutter.toPx(), 0f), Offset(size.width - Space.gutter.toPx(), 0f), Space.strokeThick.toPx())
                }
            }
            .semantics { customActions = listOf(CustomAccessibilityAction(removeLabel) { onRemove(); true }) },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.background)
                .clickable(onClickLabel = stringResource(R.string.open_note), onClick = onOpen)
                .heightIn(min = Space.touch)
                .padding(horizontal = Space.gutter, vertical = Space.x5),
        ) {
            Column {
                if (title.isNotEmpty()) {
                    Text(text = title, style = MaterialTheme.typography.titleLarge, color = colors.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (preview.isNotEmpty()) {
                    Text(text = preview, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** What a confirmation is waiting for: one note, or the whole bin when the note is null. */
private data class PendingDelete(val note: Note?)

/** The bin for notes, laid out like the bin for events. */
@Composable
private fun NotesBin(entries: List<Note>, viewModel: NotesViewModel, onClose: () -> Unit) {
    var pending by remember { mutableStateOf<PendingDelete?>(null) }
    BackHandler(onBack = onClose)
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
                items(entries, key = { it.id }) { note ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.animateItem().padding(start = Space.gutter, end = Space.rowPad),
                    ) {
                        Text(
                            text = noteTitle(note),
                            maxLines = 2,
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.onBackground,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(vertical = Space.x3),
                        )
                        QuietButton(stringResource(R.string.restore)) { viewModel.restore(note.id) }
                        QuietButton(stringResource(R.string.delete)) { pending = PendingDelete(note) }
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
                    if (request.note != null) stringResource(R.string.confirm_delete_note) else stringResource(R.string.confirm_empty_bin),
                )
            },
            confirmButton = {
                QuietButton(stringResource(R.string.delete_forever)) {
                    if (request.note != null) viewModel.deleteForever(request.note.id) else viewModel.emptyBin()
                    pending = null
                }
            },
            dismissButton = { QuietButton(stringResource(R.string.cancel)) { pending = null } },
        )
    }
}
