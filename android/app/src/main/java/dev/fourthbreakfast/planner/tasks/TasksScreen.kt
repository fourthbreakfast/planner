package dev.fourthbreakfast.planner.tasks

import androidx.compose.ui.window.PopupPositionProvider
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
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
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
import dev.fourthbreakfast.planner.agenda.AgendaViewModel
import dev.fourthbreakfast.planner.ui.theme.Space
import kotlin.math.roundToInt
import dev.fourthbreakfast.planner.core.MAX_DEPTH
import dev.fourthbreakfast.planner.core.SortOrder
import dev.fourthbreakfast.planner.core.Task
import dev.fourthbreakfast.planner.core.binEntries
import dev.fourthbreakfast.planner.core.subtree
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val PREFS = "view"
private const val PREF_SORT = "sort"

@Composable
fun TasksScreen(viewModel: TasksViewModel, agenda: AgendaViewModel) {
    val tasks by viewModel.all.collectAsStateWithLifecycle(initialValue = null)
    var binOpen by rememberSaveable { mutableStateOf(false) }
    var agendaOpen by rememberSaveable { mutableStateOf(false) }
    val listState = rememberSaveableStateHolder()
    val all = tasks
    val entries = remember(all) { binEntries(all.orEmpty()) }
    val active = remember(all) { all.orEmpty().filterNot { it.inBin } }
    if (agendaOpen) {
        AgendaScreen(agenda, onShowTasks = { agendaOpen = false })
    } else if (binOpen) {
        BinScreen(entries, viewModel, onClose = { binOpen = false })
    } else {
        // keeps the aim and collapsed tasks while the bin is open
        listState.SaveableStateProvider("list") {
            TaskList(
                tasks = active,
                binCount = entries.size,
                onOpenBin = { binOpen = true },
                onOpenAgenda = { agendaOpen = true },
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
    onOpenAgenda: () -> Unit,
    viewModel: TasksViewModel,
) {
    val prefs = LocalContext.current.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    var order by remember {
        mutableStateOf(
            SortOrder.entries.firstOrNull { it.name == prefs.getString(PREF_SORT, null) } ?: SortOrder.TREE,
        )
    }
    // two modes: the tree, the only one where things can be dragged, or everything ranked by priority
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
    val backup = rememberBackupFiles(viewModel) { snackbarHost.showSnackbar(it) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val binnedMessage = stringResource(R.string.task_removed)
    val undoLabel = stringResource(R.string.undo)

    val aimedTask = tasks.firstOrNull { it.id == aimedId }
    val aimed = aimedTask != null

    fun offerUndo(message: String, undo: () -> Unit) {
        scope.launch {
            snackbarHost.currentSnackbarData?.dismiss()
            val result = snackbarHost.showSnackbar(
                message = message,
                actionLabel = undoLabel,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) undo()
        }
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
    var dragDx by remember { mutableFloatStateOf(0f) }
    var dropDepth by remember { mutableIntStateOf(0) }
    var lastDrop by remember { mutableStateOf<Drop?>(null) }
    val indentPx = with(LocalDensity.current) { Space.indent.toPx() }
    val currentRows by rememberUpdatedState(rows)
    val currentTasks by rememberUpdatedState(tasks)
    var ignoreTaps by remember { mutableStateOf(false) }

    fun updateDropTarget() {
        val row = list.layoutInfo.visibleItemsInfo.firstOrNull { dragY >= it.offset && dragY < it.offset + it.size }
        val key = row?.key as? String
        if (row == null || key == null || key == dragKey) {
            dropKey = null
            return
        }
        val fraction = (dragY - row.offset) / row.size
        dropKey = key
        dropZone = when {
            fraction < 0.28f -> DropZone.BEFORE
            fraction > 0.72f -> DropZone.AFTER
            else -> DropZone.INTO
        }
        val moved = dragKey ?: return
        val drop = resolveDrop(
            currentRows, currentTasks, moved, key,
            if (key == EndRow.key) DropZone.BEFORE else dropZone,
            (dragDx / indentPx).roundToInt(),
        )
        dropDepth = drop?.depth ?: 0
        // a small tick each time the drop spot changes, so moving around has a feel
        if (drop != lastDrop) {
            lastDrop = drop
            if (drop != null) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
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
        ignoreTaps = true
        scope.launch {
            delay(250)
            ignoreTaps = false
        }
        if (key == null || target == null) return
        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        resolveDrop(currentRows, currentTasks, key, target, dropZone, (dragDx / indentPx).roundToInt())?.let { drop ->
            viewModel.move(key, drop.destination)
            drop.destination.parentId?.let { parent -> collapsedIds = ArrayList(collapsedIds - parent) }
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
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
                onPriorityChange = { setOrder(if (it) SortOrder.PRIORITY else SortOrder.TREE) },
                binCount = binCount,
                onOpenBin = onOpenBin,
                onOpenAgenda = onOpenAgenda,
                onExport = backup.export,
                onImport = backup.import,
            )
        },
    ) { padding ->
        LazyColumn(
            state = list,
            // otherwise the list takes the finger's movement and cancels the drag
            userScrollEnabled = dragKey == null,
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding)
                .fillMaxSize()
                .onGloballyPositioned { listBounds = it.boundsInRoot() }
                // a sweep to the left across empty space opens the agenda; rows keep their own swipes
                .switchTabBySwiping(
                    towardsLeft = true,
                    onRow = { at ->
                        list.layoutInfo.visibleItemsInfo.any {
                            it.key != EndRow.key && at.y >= it.offset && at.y < it.offset + it.size
                        }
                    },
                    onSwitch = onOpenAgenda,
                )
                .pointerInput(order == SortOrder.TREE && renamingId == null) {
                    if (order != SortOrder.TREE || renamingId != null) return@pointerInput
                    detectDragGesturesAfterLongPress(
                        onDragStart = { start: Offset ->
                            ignoreTaps = true
                            val row = list.layoutInfo.visibleItemsInfo
                                .firstOrNull { start.y >= it.offset && start.y < it.offset + it.size }
                            val key = row?.key as? String
                            if (row != null && key != null && key != EndRow.key) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                dragKey = key
                                dragY = start.y
                                dragDx = 0f
                                lastDrop = null
                                grabInRow = start.y - row.offset
                            }
                        },
                        onDrag = { change, amount ->
                            if (dragKey != null) {
                                change.consume()
                                dragY += amount.y
                                dragDx += amount.x
                                updateDropTarget()
                            }
                        },
                        onDragEnd = { finishDrag() },
                        // a row can claim the finger's release, so this also ends a normal drag
                        onDragCancel = { finishDrag() },
                    )
                },
        ) {
            items(display, key = { it.key }) { row ->
                val dragging = dragKey == row.key
                val hint = if (dropKey == row.key) (if (row is EndRow) DropZone.BEFORE else dropZone) else null
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
                            dragging = dragging,
                            dragActive = dragKey != null,
                            dropHint = hint,
                            dropDepth = dropDepth,
                            // typing: a touch picks where the next task goes. Otherwise it opens or closes what is inside
                            onAim = {
                                if (!ignoreTaps) {
                                    if (addFocused) {
                                        // nothing can go inside a task on the deepest level
                                        if (row.depth < MAX_DEPTH) aimedId = if (task.id == aimedId) null else task.id
                                    } else if (row.childCount > 0) {
                                        toggleCollapsed()
                                    }
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

@Composable
private fun TasksMenu(open: Boolean, onDismiss: () -> Unit, onExport: () -> Unit, onImport: () -> Unit) {
    if (!open) return
    val colors = MaterialTheme.colorScheme
    val gap = with(LocalDensity.current) { Space.rowPad.roundToPx() }
    // under the tab it belongs to, as wide as its words and no wider
    val below = remember(gap) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize,
            ) = IntOffset(anchorBounds.left + gap, anchorBounds.bottom)
        }
    }

    @Composable
    fun Entry(label: String, onClick: () -> Unit) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = colors.onBackground,
            modifier = Modifier
                .clickable {
                    onDismiss()
                    onClick()
                }
                .padding(horizontal = Space.x3, vertical = Space.x3),
        )
    }
    // focusable, so a touch outside or the back button closes it instead of reaching the screen behind
    Popup(popupPositionProvider = below, onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        Column(
            Modifier
                .width(IntrinsicSize.Max)
                .background(colors.surfaceVariant, RoundedCornerShape(Space.radiusCheck))
                .border(Space.strokeThin, colors.outline, RoundedCornerShape(Space.radiusCheck)),
        ) {
            Entry(stringResource(R.string.export_tasks), onExport)
            Entry(stringResource(R.string.import_tasks), onImport)
        }
    }
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
    onOpenAgenda: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Column {
        TabBar(
            selected = Tab.TASKS,
            onSelect = { if (it == Tab.AGENDA) onOpenAgenda() },
            // holding the tasks tab opens the little menu with export and import
            onTasksLongPress = { menuOpen = true },
            tasksExtra = {
                TasksMenu(
                    open = menuOpen,
                    onDismiss = { menuOpen = false },
                    onExport = onExport,
                    onImport = onImport,
                )
            },
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

// not rememberSwipeToDismissBoxState: it saves by row key, so something brought back
// by undo would still be dismissed and remove itself again
@Composable
internal fun rememberUnsavedSwipe() = remember {
    SwipeToDismissBoxState(
        initialValue = SwipeToDismissBoxValue.Settled,
        positionalThreshold = { distance -> distance * 0.5f },
    )
}

/** Under a swiping row: swiping right removes it, swiping left renames it. */
@Composable
internal fun SwipeBackground(direction: SwipeToDismissBoxValue, removeLabel: String, renameLabel: String) {
    val colors = MaterialTheme.colorScheme
    val renaming = direction == SwipeToDismissBoxValue.EndToStart
    Box(
        contentAlignment = if (renaming) Alignment.CenterEnd else Alignment.CenterStart,
        modifier = Modifier
            .fillMaxSize()
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
    dragging: Boolean,
    dragActive: Boolean,
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
    val swipe = rememberUnsavedSwipe()
    val scope = rememberCoroutineScope()
    SwipeToDismissBox(
        state = swipe,
        gesturesEnabled = !dragActive && !renaming,
        backgroundContent = { if (!dragging) SwipeBackground(swipe.dismissDirection, removeLabel, renameLabel) },
        onDismiss = { direction ->
            if (direction == SwipeToDismissBoxValue.EndToStart) {
                // renaming does not take the row away, so it comes straight back
                onStartRename()
                scope.launch { swipe.snapTo(SwipeToDismissBoxValue.Settled) }
            } else {
                onRemove()
            }
        },
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
