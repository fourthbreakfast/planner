package dev.fourthbreakfast.planner.desktop

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import dev.fourthbreakfast.planner.core.Priority
import dev.fourthbreakfast.planner.core.SortOrder
import dev.fourthbreakfast.planner.core.Task
import dev.fourthbreakfast.planner.resources.Res
import dev.fourthbreakfast.planner.resources.add_inside_hint
import dev.fourthbreakfast.planner.resources.add_subtask
import dev.fourthbreakfast.planner.resources.add_task_hint
import dev.fourthbreakfast.planner.resources.close_task
import dev.fourthbreakfast.planner.resources.collapse_all
import dev.fourthbreakfast.planner.resources.expand_all
import dev.fourthbreakfast.planner.resources.nothing_to_do
import dev.fourthbreakfast.planner.resources.open_task
import dev.fourthbreakfast.planner.resources.priority_high
import dev.fourthbreakfast.planner.resources.priority_low
import dev.fourthbreakfast.planner.resources.priority_medium
import dev.fourthbreakfast.planner.resources.priority_none
import dev.fourthbreakfast.planner.resources.remove_task
import dev.fourthbreakfast.planner.resources.rename
import dev.fourthbreakfast.planner.resources.task_progress
import dev.fourthbreakfast.planner.tasks.DropZone
import dev.fourthbreakfast.planner.tasks.PriorityDot
import dev.fourthbreakfast.planner.tasks.TaskCheck
import dev.fourthbreakfast.planner.tasks.TaskNode
import dev.fourthbreakfast.planner.tasks.TasksViewModel
import dev.fourthbreakfast.planner.tasks.arrange
import dev.fourthbreakfast.planner.tasks.rankedDestination
import dev.fourthbreakfast.planner.tasks.resolveDrop
import dev.fourthbreakfast.planner.ui.BinButton
import dev.fourthbreakfast.planner.ui.theme.Space
import org.jetbrains.compose.resources.stringResource

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
    val colors = MaterialTheme.colorScheme
    val active = remember(tasks) { tasks.filterNot { it.inBin } }
    val parentIds = remember(active) { active.mapNotNull { it.parentId }.toSet() }
    val rows = remember(active, state.openTasks) {
        arrange(active, SortOrder.PRIORITY, parentIds - state.openTasks).filterIsInstance<TaskNode>()
    }
    state.visibleTasks = rows.map { it.task.id }
    val list = rememberLazyListState()
    var dragging by remember { mutableStateOf<String?>(null) }
    var dragY by remember { mutableStateOf(0f) }
    var drop by remember { mutableStateOf<Pair<String, DropZone>?>(null) }

    fun targetAt(y: Float): Pair<String, DropZone>? {
        val item = list.layoutInfo.visibleItemsInfo.firstOrNull { y >= it.offset && y < it.offset + it.size } ?: return null
        val fraction = (y - item.offset) / item.size
        val zone = when {
            fraction < 0.28f -> DropZone.BEFORE
            fraction > 0.72f -> DropZone.AFTER
            else -> DropZone.INTO
        }
        return (item.key as? String)?.let { it to zone }
    }

    Column(Modifier.fillMaxSize()) {
        val anyClosed = parentIds.any { it !in state.openTasks }
        ColumnHeader("tasks", active.count { !it.done && it.parentId == null }, state.focused == Section.TASKS) {
            if (parentIds.isNotEmpty()) {
                HeaderButton(stringResource(if (anyClosed) Res.string.expand_all else Res.string.collapse_all), {
                    state.openTasks = if (anyClosed) parentIds else emptySet()
                }) { ink ->
                    val tip = if (anyClosed) 0.66f else 0.34f
                    val base = if (anyClosed) 0.36f else 0.64f
                    drawPath(
                        androidx.compose.ui.graphics.Path().apply {
                            moveTo(size.width * 0.24f, size.height * base)
                            lineTo(size.width * 0.76f, size.height * base)
                            lineTo(size.width * 0.5f, size.height * tip)
                            close()
                        },
                        ink,
                    )
                }
            }
            BinButton(binCount, { state.panel = Panel.TaskBin })
        }
        if (rows.isEmpty()) Quiet(stringResource(Res.string.nothing_to_do))
        LazyColumn(state = list, modifier = Modifier.weight(1f).fillMaxWidth()) {
            items(rows, key = { it.task.id }) { node ->
                val task = node.task
                val (hover, hovered) = rememberHover()
                val selected = state.focused == Section.TASKS && state.selectedTask == task.id
                val mark = drop?.takeIf { it.first == task.id }?.second
                ContextMenuArea(items = {
                    listOf(
                        ContextMenuItem(renameLabel) { state.renamingTask = task.id },
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
                                val y = when (mark) {
                                    DropZone.BEFORE -> 0f
                                    DropZone.AFTER -> size.height
                                    else -> null
                                }
                                if (y != null) drawLine(colors.onBackground, Offset(0f, y), Offset(size.width, y), Space.strokeThick.toPx())
                            }
                            .hoverable(hover)
                            .combinedClickable(
                                onClick = {
                                    state.focused = Section.TASKS
                                    state.selectedTask = task.id
                                },
                                onDoubleClick = { state.renamingTask = task.id },
                            )
                            .pointerInput(task.id) {
                                detectDragGestures(
                                    onDragStart = { at ->
                                        val item = list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == task.id }
                                        dragging = task.id
                                        dragY = (item?.offset ?: 0) + at.y
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragY += amount.y
                                        drop = targetAt(dragY)?.takeIf { it.first != task.id }
                                    },
                                    onDragEnd = {
                                        val target = drop
                                        val moved = dragging
                                        dragging = null
                                        drop = null
                                        if (target != null && moved != null) {
                                            resolveDrop(rows, active, moved, target.first, target.second)?.let { landing ->
                                                viewModel.move(moved, rankedDestination(active, moved, landing.destination))
                                                landing.destination.parentId?.let { state.openTasks = state.openTasks + it }
                                            }
                                        }
                                    },
                                    onDragCancel = {
                                        dragging = null
                                        drop = null
                                    },
                                )
                            }
                            .padding(start = Space.x2 + Space.indent * node.depth, end = Space.x1),
                    ) {
                        // the triangle opens and closes a task with something inside
                        Box(Modifier.size(Space.x5), contentAlignment = Alignment.Center) {
                            if (node.childCount > 0) {
                                HeaderButton(if (node.collapsed) openLabel else closeLabel, {
                                    state.openTasks =
                                        if (node.collapsed) state.openTasks + task.id else state.openTasks - task.id
                                }) { ink ->
                                    val open = !node.collapsed
                                    drawPath(
                                        androidx.compose.ui.graphics.Path().apply {
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
                        Box(
                            Modifier.size(Space.x6).combinedClickable(onClick = { viewModel.cyclePriority(task) }),
                            contentAlignment = Alignment.Center,
                        ) { PriorityDot(task.priority) }
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
                        if (hovered) BinButton(0, { viewModel.moveToBin(task) })
                        TaskCheck(task.done, colors.onBackground) { viewModel.setDone(task, it) }
                    }
                }
            }
        }
        AddLine(state, active, viewModel)
    }
}

/** The line at the foot of the column that adds a task, inside the aimed one when there is one. */
@Composable
private fun AddLine(state: DashboardState, tasks: List<Task>, viewModel: TasksViewModel) {
    val colors = MaterialTheme.colorScheme
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val aimed = tasks.firstOrNull { it.id == state.aimedTask }
    LaunchedEffect(state.focusAdd) { if (state.focusAdd > 0) focus.requestFocus() }
    Column(Modifier.fillMaxWidth().padding(horizontal = Space.gutter, vertical = Space.x2)) {
        if (aimed != null) {
            Text(
                stringResource(Res.string.add_inside_hint, aimed.title),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.combinedClickableCompat { state.aimedTask = null },
            )
        }
        Box(Modifier.fillMaxWidth().height(Desk.row), contentAlignment = Alignment.CenterStart) {
            if (text.isEmpty()) Text(stringResource(Res.string.add_task_hint), style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
            BasicTextField(
                value = text,
                onValueChange = { text = it.replace("\n", "") },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onBackground),
                cursorBrush = SolidColor(colors.onBackground),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    if (text.isNotBlank()) viewModel.add(text.trim(), aimed?.id)
                    aimed?.let { state.openTasks = state.openTasks + it.id }
                    text = ""
                }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus)
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

/** A text field in place of a title: enter keeps the new words, escape the old ones. */
@Composable
fun RenameField(initial: String, modifier: Modifier = Modifier, onDone: (String?) -> Unit) {
    val colors = MaterialTheme.colorScheme
    var text by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(initial, androidx.compose.ui.text.TextRange(0, initial.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    BasicTextField(
        value = text,
        onValueChange = { text = it.copy(text = it.text.replace("\n", "")) },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onBackground),
        cursorBrush = SolidColor(colors.onBackground),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone(text.text.trim()) }),
        modifier = modifier
            .padding(start = Space.x2)
            .focusRequester(focus)
            .onPreviewKeyEvent { event ->
                if (event.key == Key.Escape && event.type == KeyEventType.KeyDown) {
                    onDone(null)
                    true
                } else {
                    false
                }
            },
    )
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableCompat(onClick: () -> Unit): Modifier = this.combinedClickable(onClick = onClick)
