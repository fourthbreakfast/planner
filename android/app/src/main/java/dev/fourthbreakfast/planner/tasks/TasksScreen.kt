package dev.fourthbreakfast.planner.tasks

import androidx.compose.ui.window.PopupPositionProvider
import dev.fourthbreakfast.planner.settings.SettingsScreen
import kotlinx.coroutines.coroutineScope
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntRect
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.border
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.fourthbreakfast.planner.R
import dev.fourthbreakfast.planner.agenda.AgendaScreen
import dev.fourthbreakfast.planner.notes.NotesScreen
import dev.fourthbreakfast.planner.notes.NotesViewModel
import dev.fourthbreakfast.planner.agenda.AgendaViewModel
import dev.fourthbreakfast.planner.ui.theme.Space
import dev.fourthbreakfast.planner.core.MAX_DEPTH
import dev.fourthbreakfast.planner.core.SortOrder
import dev.fourthbreakfast.planner.core.Task
import dev.fourthbreakfast.planner.core.binEntries
import dev.fourthbreakfast.planner.core.subtree
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val PREFS = "view"
private const val PREF_SORT = "sort"
private const val PREF_PEEKED = "peeked"

@Composable
fun TasksScreen(viewModel: TasksViewModel, agenda: AgendaViewModel, notes: NotesViewModel) {
    val tasks by viewModel.all.collectAsStateWithLifecycle(initialValue = null)
    var binOpen by rememberSaveable { mutableStateOf(false) }
    var tab by rememberSaveable { mutableStateOf(Tab.TASKS) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    val listState = rememberSaveableStateHolder()
    val all = tasks
    val entries = remember(all) { binEntries(all.orEmpty()) }
    val active = remember(all) { all.orEmpty().filterNot { it.inBin } }
    val openSettings = { settingsOpen = true }
    if (settingsOpen) {
        SettingsScreen(viewModel, onClose = { settingsOpen = false })
    } else if (tab == Tab.AGENDA) {
        AgendaScreen(agenda, onShowTasks = { tab = Tab.TASKS }, onShowNotes = { tab = Tab.NOTES }, onOpenSettings = openSettings)
    } else if (tab == Tab.NOTES) {
        NotesScreen(notes, onShowAgenda = { tab = Tab.AGENDA }, onShowTasks = { tab = Tab.TASKS }, onOpenSettings = openSettings)
    } else if (binOpen) {
        BinScreen(entries, viewModel, onClose = { binOpen = false })
    } else {
        // keeps the aim and collapsed tasks while the bin is open
        listState.SaveableStateProvider("list") {
            TaskList(
                tasks = active,
                binCount = entries.size,
                onOpenBin = { binOpen = true },
                onSelectTab = { tab = it },
                onOpenSettings = openSettings,
                viewModel = viewModel,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskList(
    tasks: List<Task>,
    binCount: Int,
    onOpenBin: () -> Unit,
    onSelectTab: (Tab) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: TasksViewModel,
) {
    val prefs = LocalContext.current.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    var order by remember {
        mutableStateOf(
            SortOrder.entries.firstOrNull { it.name == prefs.getString(PREF_SORT, null) } ?: SortOrder.TREE,
        )
    }
    // two modes: the tree in the order things were put, or ranked by priority among their neighbours
    val treeMode = order == SortOrder.TREE

    fun setOrder(new: SortOrder) {
        order = new
        prefs.edit().putString(PREF_SORT, new.name).apply()
    }

    var aimedId by rememberSaveable { mutableStateOf<String?>(null) }
    var renamingId by rememberSaveable { mutableStateOf<String?>(null) }
    var collapsedIds by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    var addText by rememberSaveable { mutableStateOf("") }
    var addFocused by remember { mutableStateOf(false) }
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val binnedMessage = stringResource(R.string.task_removed)
    val undoLabel = stringResource(R.string.undo)

    val aimedTask = tasks.firstOrNull { it.id == aimedId }
    val aimed = aimedTask != null

    fun offerUndo(message: String, undo: () -> Unit) {
        scope.launch { if (snackbarHost.showUndo(message, undoLabel)) undo() }
    }

    // one closed task is enough for the button to open everything; otherwise it closes everything
    val parentIds = remember(tasks) { tasks.mapNotNull { it.parentId }.toSet() }
    val anyCollapsed = parentIds.any { it in collapsedIds }
    val rows = remember(tasks, order, collapsedIds) { arrange(tasks, order, collapsedIds) }
    val display = remember(rows) { rows + EndRow }

    val list = rememberLazyListState()
    val endSpace = Space.listEnd()
    val focusManager = LocalFocusManager.current
    // the add line stays in use after enter. It ends when the keyboard goes away (back) or on a touch elsewhere
    val imeVisible = WindowInsets.isImeVisible
    var imeWasShown by remember { mutableStateOf(false) }
    LaunchedEffect(imeVisible) {
        if (imeVisible) {
            imeWasShown = true
        } else if (imeWasShown) {
            imeWasShown = false
            focusManager.clearFocus()
        }
    }
    var addBarBounds by remember { mutableStateOf(Rect.Zero) }
    var listBounds by remember { mutableStateOf(Rect.Zero) }

    // dragging: the gesture lives on the list, so it survives rows scrolling away
    var dragKey by remember { mutableStateOf<String?>(null) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var grabInRow by remember { mutableFloatStateOf(0f) }
    var dropKey by remember { mutableStateOf<String?>(null) }
    var dropZone by remember { mutableStateOf(DropZone.INTO) }
    var mark by remember { mutableStateOf<DropMark?>(null) }
    val currentRows by rememberUpdatedState(rows)
    val currentTasks by rememberUpdatedState(tasks)
    val swipe = remember(scope) { RowSwipe(scope, canGoLeft = true) }
    // the first time there is a row, it slides open and shut once, so people know rows move
    val peekPx = with(LocalDensity.current) { (Space.touch * 2).toPx() }
    val firstKey = (rows.firstOrNull() as? TaskNode)?.task?.id
    LaunchedEffect(firstKey) {
        if (firstKey != null && !prefs.getBoolean(PREF_PEEKED, false)) {
            delay(700)
            prefs.edit().putBoolean(PREF_PEEKED, true).apply()
            swipe.peek(firstKey, peekPx)
        }
    }

    fun updateDropTarget() {
        val row = list.layoutInfo.visibleItemsInfo.firstOrNull { dragY >= it.offset && dragY < it.offset + it.size }
        val key = row?.key as? String
        if (row == null || key == null || key == dragKey) {
            dropKey = null
            mark = null
            return
        }
        val fraction = (dragY - row.offset) / row.size
        dropZone = when {
            fraction < 0.28f -> DropZone.BEFORE
            fraction > 0.72f -> DropZone.AFTER
            else -> DropZone.INTO
        }
        val moved = dragKey ?: return
        val drop = resolveDrop(currentRows, currentTasks, moved, key, if (key == EndRow.key) DropZone.BEFORE else dropZone)
        // a place nothing can land on shows nothing
        dropKey = key.takeIf { drop != null }
        val newMark = when {
            drop == null -> null
            order == SortOrder.PRIORITY && (key == EndRow.key || dropZone != DropZone.INTO) ->
                rankedMark(currentRows, currentTasks, moved, drop.destination, drop.depth)
            else -> DropMark(key, if (key == EndRow.key) DropZone.BEFORE else dropZone, drop.depth)
        }
        // a small tick each time the line moves, so moving around has a feel
        if (newMark != mark) {
            mark = newMark
            if (newMark != null) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        }
    }

    LaunchedEffect(dragKey) {
        // near the top or bottom edge the list scrolls under the finger
        while (dragKey != null) {
            val height = list.layoutInfo.viewportSize.height
            val edge = height * 0.12f
            val speed = when {
                dragY < edge -> -14f
                dragY > height - edge -> 14f
                else -> 0f
            }
            if (speed != 0f) {
                list.scrollBy(speed)
                updateDropTarget()
            }
            delay(16)
        }
    }

    fun finishDrag() {
        val key = dragKey
        val target = dropKey
        dragKey = null
        dropKey = null
        mark = null
        if (key == null || target == null) return
        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        resolveDrop(currentRows, currentTasks, key, target, dropZone)?.let { drop ->
            // in priority mode a task snaps back among its own colour
            val to = if (order == SortOrder.PRIORITY) rankedDestination(currentTasks, key, drop.destination) else drop.destination
            viewModel.move(key, to)
            drop.destination.parentId?.let { parent -> collapsedIds = ArrayList(collapsedIds - parent) }
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .sweepAndHold(
                enabled = renamingId == null,
                onSweepLeft = { onSelectTab(Tab.AGENDA) },
                onSweepRight = null,
                rowAt = { at -> (list.rowKeyAt(at, listBounds) as? String)?.takeIf { it != EndRow.key } },
                swipe = swipe,
                onSwipedRight = { id ->
                    tasks.firstOrNull { it.id == id }?.let { task ->
                        viewModel.moveToBin(task)
                        offerUndo(binnedMessage) { viewModel.restore(task) }
                    }
                },
                onSwipedLeft = { id ->
                    aimedId = null
                    renamingId = id
                },
                onHold = { key, at ->
                    dragKey = key
                    dragY = at.y - listBounds.top
                    grabInRow = dragY - (list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }?.offset ?: 0)
                },
                onDrag = { dy ->
                    dragY += dy
                    updateDropTarget()
                },
                onRelease = { moved ->
                    if (!moved) dropKey = null
                    finishDrag()
                },
            )
            // the cursor only lives while the add line is in use, so a touch ends it, except on a task:
            // picking where the next task goes is part of typing it
            .pointerInput(addFocused) {
                if (!addFocused) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val inList = listBounds.contains(down.position) &&
                        list.layoutInfo.visibleItemsInfo.any {
                            val y = down.position.y - listBounds.top
                            it.key != EndRow.key && y >= it.offset && y < it.offset + it.size
                        }
                    if (!inList && !addBarBounds.contains(down.position)) focusManager.clearFocus()
                }
            },
        snackbarHost = {
            SnackbarHost(snackbarHost) { data ->
                DiscreetMessage(data.visuals.message, data.visuals.actionLabel, data::performAction)
            }
        },
        bottomBar = {
            AddBar(
                text = addText,
                onTextChange = { addText = it },
                aimedTitle = aimedTask?.title,
                onClearAim = { aimedId = null },
                onFocusChange = {
                    addFocused = it
                    // leaving input mode always drops the target
                    if (!it) aimedId = null
                },
                onAdd = {
                    viewModel.add(addText, aimedTask?.id)
                    aimedTask?.let { collapsedIds = ArrayList(collapsedIds - it.id) }
                    addText = ""
                },
                modifier = Modifier.onGloballyPositioned { addBarBounds = it.boundsInRoot() },
            )
        },
        topBar = {
            Header(
                openCount = tasks.count { !it.done && it.parentId == null },
                subtasksOpen = tasks.count { !it.done && it.parentId != null },
                showExpandAll = parentIds.isNotEmpty(),
                anyCollapsed = anyCollapsed,
                onToggleAll = {
                    if (anyCollapsed) {
                        collapsedIds = arrayListOf()
                    } else {
                        collapsedIds = ArrayList(parentIds)
                        // a target inside a closed task is out of sight, so it goes
                        if (tasks.firstOrNull { it.id == aimedId }?.parentId != null) aimedId = null
                    }
                },
                treeMode = treeMode,
                onPriorityChange = {
                    setOrder(if (it) SortOrder.PRIORITY else SortOrder.TREE)
                    // back to the top of the list, with everything left open or closed as it was
                    list.requestScrollToItem(0)
                },
                binCount = binCount,
                onOpenBin = onOpenBin,
                onSelectTab = onSelectTab,
                onOpenSettings = onOpenSettings,
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                state = list,
                // otherwise the list takes the finger's movement and cancels the drag
                userScrollEnabled = dragKey == null,
                modifier = Modifier
                    .padding(padding)
                    .consumeWindowInsets(padding)
                    .fillMaxSize()
                    .onGloballyPositioned { listBounds = it.boundsInRoot() },
            ) {
                items(display, key = { it.key }) { row ->
                    val dragging = dragKey == row.key
                    val hint = mark?.takeIf { it.key == row.key }?.zone
                    val dropDepth = mark?.depth ?: 0
                    val moved = Modifier
                        .animateItem(placementSpec = reorderSpring())
                        .then(if (dragging) Modifier.zIndex(1f) else Modifier)
                        .graphicsLayer {
                            if (dragging) {
                                val offset = list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == row.key }?.offset ?: 0
                                translationY = dragY - grabInRow - offset
                                alpha = 0.92f
                                shadowElevation = 12f
                            }
                        }
                    when (row) {
                        is EndRow -> Box(
                            Modifier
                                .fillMaxWidth()
                                .height(endSpace)
                                .animateItem()
                                .dropHint(hint, dropDepth),
                        )
                        is TaskNode -> {
                            val task = row.task
                            val toggleCollapsed = {
                                // never keep aiming at a row that is about to be hidden (the row itself stays)
                                if (!row.collapsed && subtree(tasks, task.id).any { it.id == aimedId && it.id != task.id }) {
                                    aimedId = null
                                }
                                collapsedIds = ArrayList(
                                    if (row.collapsed) collapsedIds - task.id else collapsedIds + task.id,
                                )
                            }
                            TaskRow(
                                node = row,
                                aimed = task.id == aimedId,
                                renaming = task.id == renamingId,
                                onStartRename = {
                                    aimedId = null
                                    renamingId = task.id
                                },
                                onRename = { title ->
                                    viewModel.rename(task.id, title)
                                    renamingId = null
                                },
                                onCancelRename = { renamingId = null },
                                swipe = swipe,
                                dragging = dragging,
                                dropHint = hint,
                                dropDepth = dropDepth,
                                // typing: a touch picks where the next task goes. Otherwise it opens or closes what is inside
                                onAim = {
                                    if (addFocused) {
                                        // nothing can go inside a task on the deepest level
                                        if (row.depth < MAX_DEPTH) aimedId = if (task.id == aimedId) null else task.id
                                    } else if (row.childCount > 0) {
                                        toggleCollapsed()
                                    }
                                },
                                onDoneChange = { viewModel.setDone(task, it) },
                                onCyclePriority = { viewModel.cyclePriority(task) },
                                onRemove = {
                                    viewModel.moveToBin(task)
                                    offerUndo(binnedMessage) { viewModel.restore(task) }
                                },
                                modifier = moved,
                            )
                        }
                    }
                }
            }
            if (tasks.isEmpty()) {
                Text(
                    text = stringResource(R.string.nothing_to_do),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(padding).align(Alignment.Center),
                )
            }
        }
    }
}

private fun reorderSpring() = spring<IntOffset>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMedium,
    visibilityThreshold = IntOffset(1, 1),
)

/** Shows where a drop would land: a line above or below the row, or the row lit up for nesting. */
@Composable
private fun Modifier.dropHint(zone: DropZone?, depth: Int = 0): Modifier {
    if (zone == null) return this
    val line = MaterialTheme.colorScheme.onBackground
    val fill = MaterialTheme.colorScheme.secondaryContainer
    return drawWithContent {
        if (zone == DropZone.INTO) drawRect(fill)
        drawContent()
        val thickness = Space.strokeThick.toPx()
        // the line starts where the dropped task would sit, so its depth can be seen
        val start = (Space.rowPad + Space.indent * depth).toPx()
        when (zone) {
            DropZone.BEFORE -> drawRect(line, topLeft = Offset(start, 0f), size = size.copy(width = size.width - start, height = thickness))
            DropZone.AFTER -> drawRect(
                line,
                topLeft = Offset(start, size.height - thickness),
                size = size.copy(width = size.width - start, height = thickness),
            )
            DropZone.INTO -> Unit
        }
    }
}

/** How long the undo pill stays, long enough to reach it with a thumb. */
private const val UNDO_MILLIS = 6000L

/** Shows [message] with an undo button and takes it away after a while. True when undo was touched. */
suspend fun SnackbarHostState.showUndo(message: String, undoLabel: String): Boolean = coroutineScope {
    currentSnackbarData?.dismiss()
    val timer = launch {
        delay(UNDO_MILLIS)
        currentSnackbarData?.dismiss()
    }
    val result = showSnackbar(message = message, actionLabel = undoLabel, duration = SnackbarDuration.Indefinite)
    timer.cancel()
    result == SnackbarResult.ActionPerformed
}

/** A small pill: says what happened and offers the way back, without covering the list. */
@Composable
fun DiscreetMessage(message: String, action: String? = null, onAction: () -> Unit = {}) {
    Box(Modifier.fillMaxWidth().padding(bottom = Space.x1), contentAlignment = Alignment.Center) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(percent = 50))
                .padding(start = Space.gutter, end = if (action == null) Space.gutter else Space.rowPad)
                .then(if (action == null) Modifier.padding(vertical = Space.x3) else Modifier),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (action != null) QuietButton(action, onAction)
        }
    }
}

@Composable
private fun Header(
    openCount: Int,
    subtasksOpen: Int,
    showExpandAll: Boolean,
    anyCollapsed: Boolean,
    onToggleAll: () -> Unit,
    treeMode: Boolean,
    onPriorityChange: (Boolean) -> Unit,
    binCount: Int,
    onOpenBin: () -> Unit,
    onSelectTab: (Tab) -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column {
        TabBar(
            selected = Tab.TASKS,
            onSelect = { if (it != Tab.TASKS) onSelectTab(it) },
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
                count = openCount,
                secondCount = subtasksOpen,
                countLabel = stringResource(R.string.open_tasks_in_all, openCount, subtasksOpen),
                modifier = Modifier.weight(1f),
            )
            if (showExpandAll) ExpandAllButton(anyCollapsed, onToggleAll)
            ModeDot(!treeMode, onPriorityChange)
            BinButton(binCount, onOpenBin)
        }
    }
}

/** Under a swiping row: swiping right removes it, swiping left renames it. */
@Composable
internal fun SwipeBackground(renaming: Boolean, removeLabel: String, renameLabel: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(
        contentAlignment = if (renaming) Alignment.CenterEnd else Alignment.CenterStart,
        modifier = modifier
            .background(if (renaming) colors.secondaryContainer else colors.primary)
            .padding(horizontal = Space.gutter),
    ) {
        Text(
            text = if (renaming) renameLabel else removeLabel,
            style = MaterialTheme.typography.labelLarge,
            color = if (renaming) colors.onSecondaryContainer else colors.onPrimary,
        )
    }
}

@Composable
private fun TaskRow(
    node: TaskNode,
    aimed: Boolean,
    renaming: Boolean,
    onStartRename: () -> Unit,
    onRename: (String) -> Unit,
    onCancelRename: () -> Unit,
    swipe: RowSwipe,
    dragging: Boolean,
    dropHint: DropZone?,
    dropDepth: Int,
    onAim: () -> Unit,
    onDoneChange: (Boolean) -> Unit,
    onCyclePriority: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val task = node.task
    val removeLabel = stringResource(R.string.remove_task)
    val renameLabel = stringResource(R.string.rename)
    val aimLabel = stringResource(R.string.aim_at_task)
    val colors = MaterialTheme.colorScheme
    SwipeRow(
        key = task.id,
        swipe = swipe,
        removeLabel = removeLabel,
        renameLabel = renameLabel,
        modifier = modifier.semantics {
            customActions = listOf(
                CustomAccessibilityAction(removeLabel) {
                    onRemove()
                    true
                },
                CustomAccessibilityAction(renameLabel) {
                    onStartRename()
                    true
                },
            )
        },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(if (aimed || dragging) colors.secondaryContainer else colors.background)
                .dropHint(dropHint, dropDepth)
                .clickable(enabled = !renaming, onClickLabel = aimLabel, onClick = onAim)
                .padding(horizontal = Space.rowPad),
        ) {
            Spacer(Modifier.width(Space.indent * node.depth))
            PriorityMarker(task.priority, onCyclePriority)
            if (renaming) {
                RenameField(
                    title = task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    onRename = onRename,
                    onCancel = onCancelRename,
                    modifier = Modifier.weight(1f).padding(vertical = Space.x3),
                )
            } else {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (task.done) TextDecoration.LineThrough else null,
                    color = if (task.done) colors.onSurfaceVariant else colors.onBackground,
                    modifier = Modifier.weight(1f).padding(vertical = Space.x3),
                )
            }
            if (node.childCount > 0) {
                Text(
                    text = stringResource(R.string.task_progress, node.doneChildCount, node.childCount),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                )
            }
            TaskCheck(task.done, colors.onBackground, onDoneChange)
        }
    }
}

/** Edits a title in place. Done or tapping elsewhere keeps the edit, back drops it. */
@Composable
private fun RenameField(
    title: String,
    style: TextStyle,
    onRename: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var value by remember { mutableStateOf(TextFieldValue(title, TextRange(title.length))) }
    var hadFocus by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val colors = MaterialTheme.colorScheme

    fun save() {
        if (finished) return
        finished = true
        onRename(value.text)
    }

    LaunchedEffect(Unit) {
        focus.requestFocus()
        keyboard?.show()
    }
    BackHandler {
        finished = true
        onCancel()
    }
    BasicTextField(
        value = value,
        onValueChange = { value = it },
        textStyle = style.copy(color = colors.onBackground),
        cursorBrush = SolidColor(colors.onBackground),
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { save() }),
        modifier = modifier
            .focusRequester(focus)
            .onFocusChanged {
                if (hadFocus && !it.isFocused) save()
                hadFocus = it.isFocused
            },
    )
}

/** The add line stays at the bottom of the screen. A tapped task is shown as its target above the line. */
@Composable
private fun AddBar(
    text: String,
    onTextChange: (String) -> Unit,
    aimedTitle: String?,
    onClearAim: () -> Unit,
    onFocusChange: (Boolean) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.background)
            .navigationBarsPadding()
            .imePadding(),
    ) {
        if (aimedTitle != null) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = Space.gutter)) {
                Text(
                    text = stringResource(R.string.add_inside_hint, aimedTitle),
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 14.sp),
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                QuietButton(stringResource(R.string.clear_target), onClearAim)
            }
        }
        TextField(
            value = text,
            onValueChange = onTextChange,
            placeholder = { Text(stringResource(R.string.add_task_hint), maxLines = 1) },
            // the + is an invitation, the › says the line is in use
            leadingIcon = {
                Text(if (focused || text.isNotEmpty()) "›" else "+", color = colors.onSurfaceVariant)
            },
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedTextColor = colors.onBackground,
                unfocusedTextColor = colors.onBackground,
                focusedPlaceholderColor = colors.onSurfaceVariant,
                unfocusedPlaceholderColor = colors.onSurfaceVariant,
                cursorColor = colors.onBackground,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onAdd() }),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged {
                    focused = it.isFocused
                    onFocusChange(it.isFocused)
                },
        )
    }
}
