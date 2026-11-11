package dev.fourthbreakfast.planner.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import dev.fourthbreakfast.planner.core.Priority
import dev.fourthbreakfast.planner.core.SortOrder
import dev.fourthbreakfast.planner.core.Task
import dev.fourthbreakfast.planner.core.shortlisted
import dev.fourthbreakfast.planner.resources.Res
import dev.fourthbreakfast.planner.resources.add_inside_hint
import dev.fourthbreakfast.planner.resources.add_subtask
import dev.fourthbreakfast.planner.resources.add_task_hint
import dev.fourthbreakfast.planner.resources.bin
import dev.fourthbreakfast.planner.resources.clear_target
import dev.fourthbreakfast.planner.resources.close_task
import dev.fourthbreakfast.planner.resources.collapse_all
import dev.fourthbreakfast.planner.resources.drag_depth_hint
import dev.fourthbreakfast.planner.resources.drop_after
import dev.fourthbreakfast.planner.resources.drop_at_root
import dev.fourthbreakfast.planner.resources.drop_before
import dev.fourthbreakfast.planner.resources.drop_inside
import dev.fourthbreakfast.planner.resources.expand_all
import dev.fourthbreakfast.planner.resources.nothing_to_do
import dev.fourthbreakfast.planner.resources.open_task
import dev.fourthbreakfast.planner.resources.priority_high
import dev.fourthbreakfast.planner.resources.priority_low
import dev.fourthbreakfast.planner.resources.priority_medium
import dev.fourthbreakfast.planner.resources.priority_none
import dev.fourthbreakfast.planner.resources.remove_task
import dev.fourthbreakfast.planner.resources.rename
import dev.fourthbreakfast.planner.resources.schedule_task
import dev.fourthbreakfast.planner.resources.shortlist_add
import dev.fourthbreakfast.planner.resources.shortlist_all
import dev.fourthbreakfast.planner.resources.shortlist_remove
import dev.fourthbreakfast.planner.resources.shortlist_show
import dev.fourthbreakfast.planner.resources.task_progress
import dev.fourthbreakfast.planner.tasks.DropZone
import dev.fourthbreakfast.planner.tasks.EndRow
import dev.fourthbreakfast.planner.tasks.PriorityDot
import dev.fourthbreakfast.planner.tasks.TaskCheck
import dev.fourthbreakfast.planner.tasks.TaskNode
import dev.fourthbreakfast.planner.tasks.TasksViewModel
import dev.fourthbreakfast.planner.tasks.arrangeHolding
import dev.fourthbreakfast.planner.tasks.dragDepth
import dev.fourthbreakfast.planner.tasks.dragScrollSpeed
import dev.fourthbreakfast.planner.tasks.rankedDestination
import dev.fourthbreakfast.planner.tasks.rankedMark
import dev.fourthbreakfast.planner.tasks.resolveDrop
import dev.fourthbreakfast.planner.tasks.stableDropZone
import dev.fourthbreakfast.planner.ui.LocalServices
import dev.fourthbreakfast.planner.ui.QuietButton
import dev.fourthbreakfast.planner.ui.itemAt
import dev.fourthbreakfast.planner.ui.openAllTriangle
import dev.fourthbreakfast.planner.ui.plus
import dev.fourthbreakfast.planner.ui.starIcon
import dev.fourthbreakfast.planner.ui.theme.Space
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

private data class TaskDropTarget(val key: String, val zone: DropZone, val depth: Int)

/**
 * The tasks, as on the phone but for a mouse and a keyboard: a click selects a row, the small triangle opens or closes
 * what is inside, a double click or F2 renames, the dot changes the priority, and a right click offers the rest. A row
 * dragged onto the middle of another goes inside it, onto its top or bottom edge beside it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TaskColumn(tasks: List<Task>, binCount: Int, state: DashboardState, viewModel: TasksViewModel) {
    val renameLabel = stringResource(Res.string.rename)
    val openLabel = stringResource(Res.string.open_task)
    val closeLabel = stringResource(Res.string.close_task)
    val addSubtaskLabel = stringResource(Res.string.add_subtask)
    val priorityHighLabel = stringResource(Res.string.priority_high)
    val priorityMediumLabel = stringResource(Res.string.priority_medium)
    val priorityLowLabel = stringResource(Res.string.priority_low)
    val priorityNoneLabel = stringResource(Res.string.priority_none)
    val removeTaskLabel = stringResource(Res.string.remove_task)
    val shortlistAddLabel = stringResource(Res.string.shortlist_add)
    val shortlistRemoveLabel = stringResource(Res.string.shortlist_remove)
    val scheduleLabel = stringResource(Res.string.schedule_task)
    val prefs = LocalServices.current.prefs
    val colors = MaterialTheme.colorScheme
    val active = remember(tasks) { tasks.filterNot { it.inBin } }
    val displayed = remember(active, state.shortlistOnly) { if (state.shortlistOnly) shortlisted(active) else active }
    val parentIds = remember(active) { active.mapNotNull { it.parentId }.toSet() }
    val held by viewModel.heldPriorities.collectAsState()
    val rows = remember(displayed, state.openTasks, held) {
        arrangeHolding(displayed, held, SortOrder.PRIORITY, parentIds - state.openTasks).filterIsInstance<TaskNode>()
    }
    state.visibleTasks = rows.map { it.task.id }
    val list = rememberLazyListState()
    val density = LocalDensity.current
    val indentation = with(density) { Space.indent.toPx() }
    val edgeBand = with(density) { Space.touch.toPx() }
    val currentRows by rememberUpdatedState(rows)
    val currentActive by rememberUpdatedState(active)
    var dragging by remember { mutableStateOf<String?>(null) }
    var dragPosition by remember { mutableStateOf(Offset.Zero) }
    var sourceDepth by remember { mutableIntStateOf(0) }
    var startX by remember { mutableStateOf(0f) }
    var grabY by remember { mutableStateOf(0f) }
    var drop by remember { mutableStateOf<TaskDropTarget?>(null) }
    val landing = drop?.let { target ->
        dragging?.let { moved -> resolveDrop(rows, active, moved, target.key, target.zone, target.depth) }
    }
    val line = drop?.let { target ->
        val moved = dragging
        if (moved == null || (target.zone == DropZone.INTO && target.key != EndRow.key)) {
            null
        } else {
            landing?.let { destination ->
                rankedMark(rows, active, moved, destination.destination, destination.depth)
            }
        }
    }

    fun targetAt(y: Float): Pair<String, DropZone>? {
        if (y < 0 || y >= list.layoutInfo.viewportSize.height) return null
        // Item offsets are measured before content padding; pointer coordinates include that padding.
        val contentY = y + list.layoutInfo.viewportStartOffset
        val item = list.itemAt(contentY)
        if (item == null) {
            val last = list.layoutInfo.visibleItemsInfo.lastOrNull() ?: return null
            return if (isBelowEndRow(contentY, last.key, last.offset + last.size, list.layoutInfo.viewportEndOffset, list.canScrollForward)) {
                EndRow.key to DropZone.AFTER
            } else {
                null
            }
        }
        val previous = drop?.takeIf { it.key == item.key }?.zone
        val zone = stableDropZone((contentY - item.offset) / item.size, previous)
        return (item.key as? String)?.let { it to zone }
    }

    fun updateTarget() {
        val moved = dragging ?: return
        val target = targetAt(dragPosition.y)
        val wanted = dragDepth(sourceDepth, dragPosition.x - startX, indentation, drop?.depth)
        drop = target?.let { (key, zone) ->
            TaskDropTarget(key, zone, wanted).takeIf {
                resolveDrop(currentRows, currentActive, moved, key, zone, wanted) != null
            }
        }
    }

    fun clearDrag() {
        dragging = null
        drop = null
        state.draggingTask = false
    }

    DisposableEffect(state) { onDispose { state.draggingTask = false } }
    LaunchedEffect(dragging) {
        while (dragging != null) {
            val speed = dragScrollSpeed(dragPosition.y, list.layoutInfo.viewportSize.height.toFloat(), edgeBand, edgeBand * 0.25f)
            if (speed != 0f) {
                list.scrollBy(speed)
                updateTarget()
            }
            delay(16)
        }
    }

    Column(
        Modifier.fillMaxSize().onPreviewKeyEvent { event ->
            if (dragging != null && event.key == Key.Escape && event.type == KeyEventType.KeyDown) {
                clearDrag()
                true
            } else {
                false
            }
        },
    ) {
        val anyClosed = parentIds.any { it !in state.openTasks }
        ColumnHeader(if (state.shortlistOnly) "do now" else "tasks", rows.count { !it.task.done && it.depth == 0 }, state.focused == Section.TASKS) {
            HeaderButton(stringResource(if (state.shortlistOnly) Res.string.shortlist_all else Res.string.shortlist_show), {
                state.shortlistOnly = !state.shortlistOnly
                state.selectedTask = null
                prefs.putBoolean("tasks_shortlist", state.shortlistOnly)
            }) { starIcon(it, state.shortlistOnly) }
            if (parentIds.isNotEmpty()) {
                HeaderButton(stringResource(if (anyClosed) Res.string.expand_all else Res.string.collapse_all), {
                    state.openTasks = if (anyClosed) parentIds else emptySet()
                }) { openAllTriangle(it, opens = anyClosed) }
            }
            DeskBin(binCount, { state.panel = Panel.TaskBin }, viewModel::emptyBin)
        }
        if (rows.isEmpty()) Quiet(stringResource(Res.string.nothing_to_do))
        Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
            LazyColumn(
                state = list,
                modifier = Modifier.fillMaxSize()
                    .pointerInput(state.shortlistOnly) {
                        if (state.shortlistOnly) return@pointerInput
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            // Mouse movement reorders; touch and wheel gestures remain available to scroll the list.
                            if (down.type != PointerType.Mouse || !currentEvent.buttons.isPrimaryPressed) return@awaitEachGesture
                            val item = list.itemAt(down.position.y + list.layoutInfo.viewportStartOffset) ?: return@awaitEachGesture
                            val source = currentRows.firstOrNull { it.key == item.key } ?: return@awaitEachGesture
                            sourceDepth = source.depth
                            startX = down.position.x
                            grabY = down.position.y - (item.offset - list.layoutInfo.viewportStartOffset)
                            var started = false
                            try {
                                while (true) {
                                    val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) {
                                        if (started) {
                                            val target = drop
                                            val moved = dragging
                                            if (target != null && moved != null) {
                                                resolveDrop(currentRows, currentActive, moved, target.key, target.zone, target.depth)?.let { destination ->
                                                    viewModel.move(moved, rankedDestination(currentActive, moved, destination.destination))
                                                    destination.destination.parentId?.let { state.openTasks = state.openTasks + it }
                                                }
                                            }
                                            change.consume()
                                        }
                                        break
                                    }
                                    if (!started && (change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                                        started = true
                                        dragging = source.task.id
                                        state.draggingTask = true
                                    }
                                    if (started) {
                                        // Claim slop before LazyColumn's scroll handler; the gesture survives row recycling.
                                        change.consume()
                                        dragPosition = change.position
                                        updateTarget()
                                    }
                                }
                            } finally {
                                clearDrag()
                            }
                        }
                    },
                contentPadding = PaddingValues(top = Space.x2),
            ) {
                items(rows, key = { it.task.id }) { node ->
                    val task = node.task
                    val (hover, hovered) = rememberHover()
                    val selected = state.focused == Section.TASKS && state.selectedTask == task.id
                    val mark = line?.takeIf { it.key == task.id }?.zone
                        ?: drop?.takeIf { it.key == task.id && it.zone == DropZone.INTO && landing != null }?.zone
                    ContextMenuArea(items = {
                        listOf(
                            ContextMenuItem(renameLabel) { state.renamingTask = task.id },
                            ContextMenuItem(scheduleLabel) { state.panel = Panel.TaskSchedule(task.id) },
                            ContextMenuItem(if (task.starred) shortlistRemoveLabel else shortlistAddLabel) { viewModel.setStarred(task, !task.starred) },
                            ContextMenuItem(addSubtaskLabel) {
                                state.aimedTask = task.id
                                state.focusAdd++
                            },
                            ContextMenuItem(priorityHighLabel) { viewModel.setPriority(task, Priority.HIGH) },
                            ContextMenuItem(priorityMediumLabel) { viewModel.setPriority(task, Priority.MEDIUM) },
                            ContextMenuItem(priorityLowLabel) { viewModel.setPriority(task, Priority.LOW) },
                            ContextMenuItem(priorityNoneLabel) { viewModel.setPriority(task, Priority.NONE) },
                            ContextMenuItem(removeTaskLabel) { viewModel.moveToBin(task) },
                        )
                    }) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer { alpha = if (dragging == task.id) 0.3f else 1f }
                                .heightIn(min = Desk.row)
                                .background(
                                    when {
                                        mark == DropZone.INTO || selected -> colors.secondaryContainer
                                        hovered -> colors.secondaryContainer.copy(alpha = 0.5f)
                                        else -> colors.background
                                    },
                                )
                                .drawBehind {
                                    // a line where a dragged task would land beside this one
                                    if (state.aimedTask == task.id) {
                                        drawLine(colors.onBackground, Offset(0f, 0f), Offset(0f, size.height), Space.strokeThick.toPx())
                                    }
                                }
                                .hoverable(hover)
                                .combinedClickable(
                                    onClick = {
                                        state.focused = Section.TASKS
                                        state.selectedTask = task.id
                                        if (node.childCount > 0) {
                                            state.openTasks = if (node.collapsed) state.openTasks + task.id else state.openTasks - task.id
                                        }
                                    },
                                    onDoubleClick = { state.renamingTask = task.id },
                                )
                                .padding(start = Space.x2 + Space.indent * node.depth, end = Desk.inset),
                        ) {
                            Box(Modifier.size(Desk.button), contentAlignment = Alignment.Center) {
                                TaskCheck(task.done, colors.onBackground, task.title) { viewModel.setDone(task, it) }
                            }
                            // the triangle opens and closes a task with something inside
                            Box(Modifier.size(Space.x5), contentAlignment = Alignment.Center) {
                                if (node.childCount > 0) {
                                    HeaderButton(if (node.collapsed) openLabel else closeLabel, {
                                        state.openTasks =
                                            if (node.collapsed) state.openTasks + task.id else state.openTasks - task.id
                                    }) { ink ->
                                        val open = !node.collapsed
                                        drawPath(
                                            Path().apply {
                                                if (open) {
                                                    moveTo(size.width * 0.3f, size.height * 0.38f)
                                                    lineTo(size.width * 0.7f, size.height * 0.38f)
                                                    lineTo(size.width * 0.5f, size.height * 0.64f)
                                                } else {
                                                    moveTo(size.width * 0.38f, size.height * 0.3f)
                                                    lineTo(size.width * 0.38f, size.height * 0.7f)
                                                    lineTo(size.width * 0.64f, size.height * 0.5f)
                                                }
                                                close()
                                            },
                                            ink,
                                        )
                                    }
                                }
                            }
                            if (state.renamingTask == task.id) {
                                RenameField(task.title, Modifier.weight(1f)) { title ->
                                    if (title != null && title.isNotBlank()) viewModel.rename(task.id, title)
                                    state.renamingTask = null
                                }
                            } else {
                                Text(
                                    text = task.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (task.done) colors.onSurfaceVariant else colors.onBackground,
                                    textDecoration = if (task.done) TextDecoration.LineThrough else null,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f).padding(start = Space.x2),
                                )
                            }
                            if (node.childCount > 0) {
                                Text(
                                    stringResource(Res.string.task_progress, node.doneChildCount, node.childCount),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colors.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = Space.x1),
                                )
                            }
                            HeaderButton(if (task.starred) shortlistRemoveLabel else shortlistAddLabel, { viewModel.setStarred(task, !task.starred) }) {
                                starIcon(it, task.starred)
                            }
                            Box(
                                Modifier.size(Space.x6).combinedClickable(onClick = { viewModel.cyclePriority(task) }),
                                contentAlignment = Alignment.Center,
                            ) { PriorityDot(task.priority) }
                            HoverBin(hovered, stringResource(Res.string.bin)) { viewModel.moveToBin(task) }
                        }
                    }
                }
                if (rows.isNotEmpty()) {
                    item(key = EndRow.key) { Box(Modifier.fillMaxWidth().height(Desk.row * 1.5f)) }
                }
            }
            dragging?.let { id ->
                currentActive.firstOrNull { it.id == id }?.let { task ->
                    Text(
                        task.title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.offset { IntOffset(0, (dragPosition.y - grabY).roundToInt()) }
                            .fillMaxWidth().background(colors.surfaceVariant.copy(alpha = 0.88f)).padding(Space.x3).clearAndSetSemantics {},
                    )
                }
            }
            // This layer sits above the moving preview, so the exact insertion line always remains visible.
            Canvas(Modifier.matchParentSize()) {
                line?.let { mark ->
                    val item = list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == mark.key }
                    if (item != null) {
                        val y = (item.offset - list.layoutInfo.viewportStartOffset + if (mark.zone == DropZone.AFTER) item.size else 0).toFloat()
                        val x = (Space.x2 + Space.indent * mark.depth).toPx()
                        drawLine(colors.background, Offset(x, y), Offset(size.width, y), Space.strokeThick.toPx() * 3)
                        drawLine(colors.onBackground, Offset(x, y), Offset(size.width, y), Space.strokeThick.toPx())
                        drawCircle(colors.onBackground, Space.strokeThick.toPx() * 1.75f, Offset(x, y))
                    }
                }
            }
            if (dragging != null && landing != null) {
                val destination = rankedDestination(active, dragging!!, landing.destination)
                fun title(id: String?) = active.firstOrNull { it.id == id }?.title.orEmpty()
                val label = when {
                    drop?.zone == DropZone.INTO -> stringResource(Res.string.drop_inside, title(destination.parentId))
                    destination.beforeId != null -> stringResource(Res.string.drop_before, title(destination.beforeId))
                    destination.afterId != null -> stringResource(Res.string.drop_after, title(destination.afterId))
                    destination.parentId != null -> stringResource(Res.string.drop_inside, title(destination.parentId))
                    else -> stringResource(Res.string.drop_at_root)
                }
                Column(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth().background(colors.surfaceVariant).padding(Space.x2)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                ) {
                    Text(label, style = MaterialTheme.typography.labelMedium, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(stringResource(Res.string.drag_depth_hint), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
            }
        }
        AddLine(state, active, viewModel)
    }
}

/** Blank space after the end row is also a drop target, but only at the actual end of the list. */
internal fun isBelowEndRow(y: Float, lastKey: Any?, lastBottom: Int, viewportEnd: Int, canScrollForward: Boolean): Boolean =
    !canScrollForward && lastKey == EndRow.key && y >= lastBottom && y < viewportEnd

/** The line at the foot of the column that adds a task, inside the aimed one when there is one. */
@Composable
private fun AddLine(state: DashboardState, tasks: List<Task>, viewModel: TasksViewModel) {
    val colors = MaterialTheme.colorScheme
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val aimed = tasks.firstOrNull { it.id == state.aimedTask }
    DisposableEffect(state) { onDispose { state.editingTask = false } }
    LaunchedEffect(state.focusAdd) { if (state.focusAdd > 0) focus.requestFocus() }
    Column(Modifier.fillMaxWidth().padding(end = Desk.inset, bottom = Desk.inset)) {
        if (aimed != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = addStart + Space.x6 + Space.x2, top = Space.x2),
            ) {
                Text(
                    stringResource(Res.string.add_inside_hint, aimed.title),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                QuietButton(stringResource(Res.string.clear_target)) { state.aimedTask = null }
            }
        }
        var typing by remember { mutableStateOf(false) }
        Row(Modifier.fillMaxWidth().height(Desk.row).padding(start = addStart), verticalAlignment = Alignment.CenterVertically) {
            // the + is an invitation, the › says the line is in use, as on the phone. it sits where the priority dots
            // are not, but flush with the column's text; the words follow it
            Box(Modifier.size(Space.x6), contentAlignment = Alignment.Center) {
                val ink = colors.onSurfaceVariant
                val inUse = typing || text.isNotEmpty()
                Canvas(Modifier.size(Space.x3)) { if (inUse) chevron(ink) else plus(ink) }
            }
            Box(Modifier.weight(1f).padding(start = Space.x2), contentAlignment = Alignment.CenterStart) {
                if (text.isEmpty()) Text(stringResource(Res.string.add_task_hint), style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
                BasicTextField(
                    value = text,
                    onValueChange = { text = it.replace("\n", "") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onBackground),
                    cursorBrush = SolidColor(colors.onBackground),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (text.isNotBlank()) viewModel.add(text.trim(), aimed?.id, starred = state.shortlistOnly)
                        aimed?.let { state.openTasks = state.openTasks + it.id }
                        text = ""
                    }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus)
                        .onFocusChanged {
                            typing = it.isFocused
                            state.editingTask = it.isFocused
                        }
                        .onPreviewKeyEvent { event ->
                            if (event.key == Key.Escape && event.type == KeyEventType.KeyDown && (text.isNotEmpty() || aimed != null)) {
                                text = ""
                                state.aimedTask = null
                                true
                            } else {
                                false
                            }
                        },
                )
            }
        }
    }
}

/**
 * A text field in place of a title: enter or clicking elsewhere keeps the new words, escape the old ones, as renaming does
 * on the phone. [onDone] hears once, with the words or null for none.
 */
@Composable
private fun RenameField(initial: String, modifier: Modifier = Modifier, onDone: (String?) -> Unit) {
    val colors = MaterialTheme.colorScheme
    var text by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length))) }
    val focus = remember { FocusRequester() }
    var hadFocus by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }

    fun finish(words: String?) {
        if (finished) return
        finished = true
        onDone(words)
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
    BasicTextField(
        value = text,
        onValueChange = { text = it.copy(text = it.text.replace("\n", "")) },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onBackground),
        cursorBrush = SolidColor(colors.onBackground),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { finish(text.text.trim()) }),
        modifier = modifier
            .padding(start = Space.x2)
            .focusRequester(focus)
            .onFocusChanged {
                if (hadFocus && !it.isFocused) finish(text.text.trim())
                hadFocus = it.isFocused
            }
            .onPreviewKeyEvent { event ->
                if (event.key == Key.Escape && event.type == KeyEventType.KeyDown) {
                    finish(null)
                    true
                } else {
                    false
                }
            },
    )
}

/** Where the add line's box starts, so that the + drawn in its middle is flush with the column's text edge. */
private val addStart = Space.gutter - (Space.x6 - Space.x3) / 2
