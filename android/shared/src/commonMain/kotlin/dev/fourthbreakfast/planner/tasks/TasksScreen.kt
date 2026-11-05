package dev.fourthbreakfast.planner.tasks

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.fourthbreakfast.planner.Launch
import dev.fourthbreakfast.planner.core.MAX_DEPTH
import dev.fourthbreakfast.planner.core.SortOrder
import dev.fourthbreakfast.planner.core.Task
import dev.fourthbreakfast.planner.core.subtree
import dev.fourthbreakfast.planner.resources.Res
import dev.fourthbreakfast.planner.resources.nothing_to_do
import dev.fourthbreakfast.planner.resources.open_tasks_in_all
import dev.fourthbreakfast.planner.resources.task_removed
import dev.fourthbreakfast.planner.resources.undo
import dev.fourthbreakfast.planner.ui.BinButton
import dev.fourthbreakfast.planner.ui.DiscreetMessage
import dev.fourthbreakfast.planner.ui.LocalServices
import dev.fourthbreakfast.planner.ui.RowSwipe
import dev.fourthbreakfast.planner.ui.SettingsButton
import dev.fourthbreakfast.planner.ui.Tab
import dev.fourthbreakfast.planner.ui.TabBar
import dev.fourthbreakfast.planner.ui.TitleWithCount
import dev.fourthbreakfast.planner.ui.itemAt
import dev.fourthbreakfast.planner.ui.offsetOf
import dev.fourthbreakfast.planner.ui.rememberKeyboardShown
import dev.fourthbreakfast.planner.ui.rowKeyAt
import dev.fourthbreakfast.planner.ui.showUndo
import dev.fourthbreakfast.planner.ui.sweepAndHold
import dev.fourthbreakfast.planner.ui.theme.Space
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

private const val PREF_PEEKED = "peeked"

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TaskList(
    tasks: List<Task>,
    binCount: Int,
    onOpenBin: () -> Unit,
    onSelectTab: (Tab) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: TasksViewModel,
) {
    val prefs = LocalServices.current.prefs
    val addFocus = remember { FocusRequester() }
    val requests = LocalServices.current.requests
    LaunchedEffect(requests.pending) {
        if (requests.pending == Launch.NewTask) {
            requests.clear()
            addFocus.requestFocus()
        }
    }

    var aimedId by rememberSaveable { mutableStateOf<String?>(null) }
    var renamingId by rememberSaveable { mutableStateOf<String?>(null) }
    var openIds by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    var addText by rememberSaveable { mutableStateOf("") }
    var addFocused by remember { mutableStateOf(false) }
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val binnedMessage = stringResource(Res.string.task_removed)
    val undoLabel = stringResource(Res.string.undo)

    val aimedTask = tasks.firstOrNull { it.id == aimedId }
    val aimed = aimedTask != null

    fun offerUndo(message: String, undo: () -> Unit) {
        scope.launch { if (snackbarHost.showUndo(message, undoLabel)) undo() }
    }

    // one closed task is enough for the button to open everything; otherwise it closes everything
    val parentIds = remember(tasks) { tasks.mapNotNull { it.parentId }.toSet() }
    val anyCollapsed = parentIds.any { it !in openIds }
    // siblings are always ranked by priority, and a task with subtasks starts closed
    val held by viewModel.heldPriorities.collectAsStateWithLifecycle()
    val rows = remember(tasks, parentIds, openIds, held) { arrangeHolding(tasks, held, SortOrder.PRIORITY, parentIds - openIds.toSet()) }
    val display = remember(rows) { rows + EndRow }

    val list = rememberLazyListState()
    // the list first has only its end spacer, and the tasks arrive above it: left alone, it would keep the spacer in view
    // and open scrolled down. So when the tasks come in after the first look, it goes to the top once.
    val startedEmpty = remember { tasks.isEmpty() }
    LaunchedEffect(tasks.isNotEmpty()) {
        if (startedEmpty && tasks.isNotEmpty()) list.scrollToItem(0)
    }
    val endSpace = Space.listEnd()
    val focusManager = LocalFocusManager.current
    // the add line stays in use after enter. It ends when the keyboard goes away (back) or on a touch elsewhere
    val imeVisible = rememberKeyboardShown()
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
            prefs.putBoolean(PREF_PEEKED, true)
            swipe.peek(firstKey, peekPx)
        }
    }

    fun updateDropTarget() {
        val row = list.itemAt(dragY)
        val key = row?.key as? String
        if (row == null || key == null || key == dragKey) {
            dropKey = null
            mark = null
            return
        }
        val fraction = (dragY - row.offset) / row.size
        dropZone = dropZoneAt(fraction)
        val moved = dragKey ?: return
        val drop = resolveDrop(currentRows, currentTasks, moved, key, if (key == EndRow.key) DropZone.BEFORE else dropZone)
        // a place nothing can land on shows nothing
        dropKey = key.takeIf { drop != null }
        val newMark = when {
            drop == null -> null
            // siblings are always ranked by priority, so a line between rows shows where the task will really land
            key == EndRow.key || dropZone != DropZone.INTO ->
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
            // siblings are ranked by priority, so a task snaps back among its own colour
            viewModel.move(key, rankedDestination(currentTasks, key, drop.destination))
            drop.destination.parentId?.let { parent -> openIds = ArrayList(openIds + parent) }
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
                    grabInRow = dragY - list.offsetOf(key)
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
                        list.itemAt(down.position.y - listBounds.top).let { it != null && it.key != EndRow.key }
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
                    aimedTask?.let { openIds = ArrayList(openIds + it.id) }
                    addText = ""
                },
                focus = addFocus,
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
                        openIds = ArrayList(parentIds)
                    } else {
                        openIds = arrayListOf()
                        // a target inside a closed task is out of sight, so it goes
                        if (tasks.firstOrNull { it.id == aimedId }?.parentId != null) aimedId = null
                    }
                },
                binCount = binCount,
                onOpenBin = onOpenBin,
                onEmptyBin = viewModel::emptyBin,
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
                                translationY = dragY - grabInRow - list.offsetOf(row.key)
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
                                openIds = ArrayList(if (row.collapsed) openIds + task.id else openIds - task.id)
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
                    text = stringResource(Res.string.nothing_to_do),
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

@Composable
private fun Header(
    openCount: Int,
    subtasksOpen: Int,
    showExpandAll: Boolean,
    anyCollapsed: Boolean,
    onToggleAll: () -> Unit,
    binCount: Int,
    onOpenBin: () -> Unit,
    onEmptyBin: () -> Unit,
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
                countLabel = stringResource(Res.string.open_tasks_in_all, openCount, subtasksOpen),
                modifier = Modifier.weight(1f),
            )
            if (showExpandAll) ExpandAllButton(anyCollapsed, onToggleAll)
            BinButton(binCount, onOpenBin, onEmpty = onEmptyBin)
            SettingsButton(onOpenSettings)
        }
    }
}
