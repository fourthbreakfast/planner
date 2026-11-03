package dev.fourthbreakfast.planner.notes

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.fourthbreakfast.planner.Launch
import dev.fourthbreakfast.planner.core.Note
import dev.fourthbreakfast.planner.core.linesUnderTitle
import dev.fourthbreakfast.planner.core.noteTitle
import dev.fourthbreakfast.planner.resources.Res
import dev.fourthbreakfast.planner.resources.add_note
import dev.fourthbreakfast.planner.resources.notes_count
import dev.fourthbreakfast.planner.resources.nothing_written
import dev.fourthbreakfast.planner.resources.open_note
import dev.fourthbreakfast.planner.resources.pinned
import dev.fourthbreakfast.planner.resources.remove_task
import dev.fourthbreakfast.planner.resources.task_removed
import dev.fourthbreakfast.planner.resources.undo
import dev.fourthbreakfast.planner.ui.AddButton
import dev.fourthbreakfast.planner.ui.BackHandler
import dev.fourthbreakfast.planner.ui.BinButton
import dev.fourthbreakfast.planner.ui.DiscreetMessage
import dev.fourthbreakfast.planner.ui.LocalServices
import dev.fourthbreakfast.planner.ui.RowSwipe
import dev.fourthbreakfast.planner.ui.SearchButton
import dev.fourthbreakfast.planner.ui.SettingsButton
import dev.fourthbreakfast.planner.ui.SwipeRow
import dev.fourthbreakfast.planner.ui.Tab
import dev.fourthbreakfast.planner.ui.TabBar
import dev.fourthbreakfast.planner.ui.TitleWithCount
import dev.fourthbreakfast.planner.ui.rowKeyAt
import dev.fourthbreakfast.planner.ui.showUndo
import dev.fourthbreakfast.planner.ui.sweepAndHold
import dev.fourthbreakfast.planner.ui.theme.Space
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

private const val END_KEY = "end"
private const val PREVIEW_LINES = 8

/** The notes: one on top of another in the order they were put, with a touch opening one to write in. */
@Composable
fun NotesScreen(viewModel: NotesViewModel, onShowAgenda: () -> Unit, onShowTasks: () -> Unit, onOpenSettings: () -> Unit, onOpenSearch: () -> Unit) {
    val settings = LocalServices.current.settings
    val everything by viewModel.all.collectAsStateWithLifecycle(initialValue = emptyList())
    val notes = remember(everything) { everything.filterNot { it.inBin } }
    val binned = remember(everything) { everything.filter { it.inBin }.sortedByDescending { it.deletedAt } }
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    var binOpen by rememberSaveable { mutableStateOf(false) }
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val binnedMessage = stringResource(Res.string.task_removed)
    val undoLabel = stringResource(Res.string.undo)

    fun moveToBinWithUndo(id: String) {
        viewModel.moveToBin(id)
        scope.launch { if (snackbarHost.showUndo(binnedMessage, undoLabel)) viewModel.restore(id) }
    }

    val requests = LocalServices.current.requests
    val request = requests.pending
    LaunchedEffect(request, notes) {
        when (request) {
            Launch.NewNote -> {
                requests.clear()
                binOpen = false
                viewModel.add { openId = it }
            }
            is Launch.Shared -> {
                requests.clear()
                binOpen = false
                viewModel.addShared(request.text, request.subject) { openId = it }
            }
            is Launch.OpenNote -> if (notes.any { it.id == request.id }) {
                requests.clear()
                binOpen = false
                openId = request.id
            }
            else -> Unit
        }
    }

    // a note made here that nobody wrote in does not stay: one left behind when the app was killed is taken away
    LaunchedEffect(Unit) { viewModel.dropLeftDrafts(openId) }

    val open = notes.firstOrNull { it.id == openId }
    // the list, a note and the bin fade into one another
    val mode = if (open != null) {
        1
    } else if (binOpen) {
        2
    } else {
        0
    }
    Crossfade(targetState = mode, modifier = Modifier.background(MaterialTheme.colorScheme.background), animationSpec = tween(150), label = "notes") { shown ->
        when {
            shown == 1 && open != null -> key(open.id) {
                NoteEditor(
                    note = open,
                    onPin = { viewModel.setPinned(open.id, !open.pinned) },
                    toolbarAbove = settings.toolbarAbove,
                    onSave = { title, body, base, onSaved -> viewModel.save(open.id, title, body, base, onSaved) },
                    onClose = {
                        viewModel.closed(open.id)
                        openId = null
                    },
                )
            }
            shown == 2 -> NotesBin(binned, viewModel, onClose = { binOpen = false })
            else -> NotesList(
                notes = notes,
                binCount = binned.size,
                snackbarHost = snackbarHost,
                onShowAgenda = onShowAgenda,
                onShowTasks = onShowTasks,
                onOpenSettings = onOpenSettings,
                onOpenSearch = onOpenSearch,
                onOpenBin = { binOpen = true },
                onNew = { viewModel.add { openId = it } },
                onOpen = { openId = it },
                onRemove = ::moveToBinWithUndo,
                onMove = viewModel::move,
            )
        }
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
    onOpenSearch: () -> Unit,
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
        floatingActionButton = { AddButton(stringResource(Res.string.add_note), onNew) },
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
                        countLabel = pluralStringResource(Res.plurals.notes_count, notes.size, notes.size),
                        modifier = Modifier.weight(1f),
                    )
                    SearchButton(onOpenSearch)
                    BinButton(binCount, onOpenBin)
                    SettingsButton(onOpenSettings)
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
                    text = stringResource(Res.string.nothing_written),
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
                                    drawLine(
                                        colors.onBackground,
                                        Offset(Space.gutter.toPx(), 0f),
                                        Offset(size.width - Space.gutter.toPx(), 0f),
                                        Space.strokeThick.toPx(),
                                    )
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
private fun NoteRow(note: Note, dropAbove: Boolean, swipe: RowSwipe, onOpen: () -> Unit, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val removeLabel = stringResource(Res.string.remove_task)
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
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(removeLabel) {
                        onRemove()
                        true
                    },
                )
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.background)
                .clickable(onClickLabel = stringResource(Res.string.open_note), onClick = onOpen)
                .heightIn(min = Space.touch)
                .padding(horizontal = Space.gutter, vertical = Space.x5),
        ) {
            Column {
                if (title.isNotEmpty() || note.pinned) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            color = colors.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (note.pinned) {
                            Text(
                                text = stringResource(Res.string.pinned),
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.onSurfaceVariant,
                                modifier = Modifier.padding(start = Space.x2),
                            )
                        }
                    }
                }
                if (preview.isNotEmpty()) {
                    Text(
                        text = preview,
                        inlineContent = rememberPreviewBoxes(colors.onSurfaceVariant),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
