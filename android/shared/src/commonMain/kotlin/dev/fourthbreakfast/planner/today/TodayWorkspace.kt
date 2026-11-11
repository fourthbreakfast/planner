package dev.fourthbreakfast.planner.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.fourthbreakfast.planner.agenda.timeText
import dev.fourthbreakfast.planner.agenda.today
import dev.fourthbreakfast.planner.core.Event
import dev.fourthbreakfast.planner.core.Priority
import dev.fourthbreakfast.planner.core.SortOrder
import dev.fourthbreakfast.planner.core.Task
import dev.fourthbreakfast.planner.core.shortlisted
import dev.fourthbreakfast.planner.tasks.PriorityDot
import dev.fourthbreakfast.planner.tasks.TaskCheck
import dev.fourthbreakfast.planner.tasks.TaskNode
import dev.fourthbreakfast.planner.tasks.TasksViewModel
import dev.fourthbreakfast.planner.tasks.arrangeHolding
import dev.fourthbreakfast.planner.ui.QuietButton
import dev.fourthbreakfast.planner.ui.rememberIs24Hour
import dev.fourthbreakfast.planner.ui.theme.Space
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.delay

/** One document of tasks and upcoming appointments, shared by both application shells. */
@Composable
fun TodayWorkspace(
    tasks: List<Task>,
    events: List<Event>,
    viewModel: TasksViewModel,
    onSchedule: (Task) -> Unit,
    onOpenEvent: (String) -> Unit,
    onTaskPanelEnd: (Float) -> Unit = {},
    onEditingChange: (Boolean) -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    var shortlist by rememberSaveable { mutableStateOf(false) }
    var closed by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var text by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<Task?>(null) }
    var rename by remember { mutableStateOf("") }
    var addInside by remember { mutableStateOf<Task?>(null) }
    var adding by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val addFocus = remember { FocusRequester() }
    DisposableEffect(Unit) { onDispose { onEditingChange(false) } }
    val active = remember(tasks) { tasks.filterNot { it.inBin } }
    val displayed = remember(active, shortlist) { if (shortlist) shortlisted(active) else active }
    val held by viewModel.heldPriorities.collectAsState()
    val rows = remember(displayed, closed, held) { arrangeHolding(displayed, held, SortOrder.PRIORITY, closed).filterIsInstance<TaskNode>() }
    LaunchedEffect(addInside?.id) {
        if (addInside != null) {
            listState.animateScrollToItem(rows.size + 1)
            addFocus.requestFocus()
        }
    }
    LaunchedEffect(rows.size, adding) {
        if (adding) listState.animateScrollToItem(rows.size + 1)
    }
    var clockTick by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            clockTick++
        }
    }
    val day = remember(clockTick) { today() }
    val minute = remember(clockTick) { LocalTime.now().let { it.hour * 60 + it.minute } }
    val clock24 = rememberIs24Hour()
    val upcoming = remember(events, day, minute, tasks) {
        upNext(events, tasks, day, minute)
    }
    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(horizontal = Space.gutter, vertical = Space.x3)) {
        item(key = "today:filters") {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = Space.x3)) {
                androidx.compose.material3.TextButton(onClick = { shortlist = false }, modifier = Modifier.semantics { selected = !shortlist }) {
                    Text("All", style = MaterialTheme.typography.bodyMedium, color = if (!shortlist) colors.onBackground else colors.onSurfaceVariant)
                }
                Text(" / ", color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                androidx.compose.material3.TextButton(onClick = { shortlist = true }, modifier = Modifier.semantics { selected = shortlist }) {
                    Text("Do now", style = MaterialTheme.typography.bodyMedium, color = if (shortlist) colors.onBackground else colors.onSurfaceVariant)
                }
                if (shortlist) Text("·", color = dev.fourthbreakfast.planner.ui.theme.PriorityMedium)
            }
        }
        if (rows.isEmpty()) {
            item(key = "today:empty") {
                Text(
                    if (shortlist) "Your shortlist is clear." else "Add a task to get started.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = Space.x4),
                )
            }
        }
        items(rows, key = { "task:" + it.task.id }) { node ->
            val task = node.task
            var menu by remember(task.id) { mutableStateOf(false) }
            Row(
                Modifier.fillMaxWidth().heightIn(min = Space.touch).padding(start = Space.indent * node.depth),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TaskCheck(task.done, colors.onBackground, task.title) { viewModel.setDone(task, it) }
                if (node.childCount > 0) {
                    dev.fourthbreakfast.planner.ui.DrawnIconButton(if (node.collapsed) "Expand task" else "Collapse task", onClick = {
                        closed = if (node.collapsed) closed - task.id else closed + task.id
                    }) { ink ->
                        val a = if (node.collapsed) Offset(size.width * .35f, size.height * .2f) else Offset(size.width * .2f, size.height * .35f)
                        val b = if (node.collapsed) Offset(size.width * .65f, size.height * .5f) else Offset(size.width * .5f, size.height * .65f)
                        val c = if (node.collapsed) Offset(size.width * .35f, size.height * .8f) else Offset(size.width * .8f, size.height * .35f)
                        drawLine(ink, a, b, Space.strokeThin.toPx())
                        drawLine(ink, b, c, Space.strokeThin.toPx())
                    }
                } else {
                    Spacer(Modifier.width(Space.x2))
                }
                Column(
                    Modifier.weight(1f).clickable {
                        editing = task
                        rename = task.title
                    }.padding(vertical = Space.x2),
                ) {
                    Text(
                        task.title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (task.done) colors.onSurfaceVariant else colors.onBackground,
                        textDecoration = if (task.done) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                    )
                }
                if (task.priority != Priority.NONE) PriorityDot(task.priority)
                Box {
                    dev.fourthbreakfast.planner.ui.DrawnIconButton("Task actions", onClick = { menu = true }) { ink ->
                        for (x in listOf(.25f, .5f, .75f)) drawCircle(ink, 1.2.dp.toPx(), Offset(size.width * x, size.height / 2))
                    }
                    DropdownMenu(menu, { menu = false }) {
                        fun close(action: () -> Unit) {
                            menu = false
                            action()
                        }
                        DropdownMenuItem(text = { Text("Rename") }, onClick = {
                            close {
                                editing = task
                                rename = task.title
                            }
                        })
                        DropdownMenuItem(text = { Text("Schedule") }, onClick = { close { onSchedule(task) } })
                        DropdownMenuItem(text = { Text("Add subtask") }, onClick = {
                            close {
                                addInside = task
                                closed = closed - task.id
                            }
                        })
                        DropdownMenuItem(
                            text = { Text(if (task.starred) "Remove from Do now" else "Add to Do now") },
                            onClick = { close { viewModel.setStarred(task, !task.starred) } },
                        )
                        Priority.entries.forEach { priority ->
                            DropdownMenuItem(
                                text = { Text("Priority: " + priority.name.lowercase().replaceFirstChar { it.uppercase() }) },
                                onClick = { close { viewModel.setPriority(task, priority) } },
                            )
                        }
                        DropdownMenuItem(text = { Text("Move to bin") }, onClick = { close { viewModel.moveToBin(task) } })
                    }
                }
            }
        }
        item(key = "today:add") {
            if (addInside != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Inside: " + addInside!!.title,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    QuietButton("Clear", onClick = { addInside = null })
                }
            }
            Row(Modifier.fillMaxWidth().heightIn(min = Space.touch), verticalAlignment = Alignment.CenterVertically) {
                Text("+", color = colors.onSurfaceVariant, modifier = Modifier.padding(horizontal = Space.x3))
                fun add() {
                    if (text.isNotBlank()) {
                        viewModel.add(text.trim(), addInside?.id, starred = shortlist)
                        text = ""
                    }
                }
                BasicTextField(
                    text, { text = it },
                    Modifier.weight(1f).padding(vertical = Space.x3).focusRequester(addFocus).onFocusChanged {
                        adding = it.isFocused
                        onEditingChange(it.isFocused)
                    }
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown && (event.key == Key.Enter || event.key == Key.NumPadEnter)) {
                                add()
                                true
                            } else {
                                false
                            }
                        }.semantics { contentDescription = "Add task" },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onBackground),
                    cursorBrush = SolidColor(colors.onBackground), singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { add() }),
                    decorationBox = { inner ->
                        if (text.isEmpty()) Text("Add task", color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                        inner()
                    },
                )
                if (text.isNotBlank()) QuietButton("Add", onClick = { add() })
            }
        }
        item(key = "today:up-next") {
            Column(
                Modifier.fillMaxWidth().padding(top = Space.x6)
                    .then(
                        Modifier.reportPanelEnd {
                            if (listState.firstVisibleItemIndex == 0 &&
                                listState.firstVisibleItemScrollOffset == 0
                            ) {
                                onTaskPanelEnd(it)
                            }
                        },
                    ),
            ) {
                HorizontalDivider(color = colors.outlineVariant, thickness = 1.dp)
                Text(
                    "Up next",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = Space.x4, bottom = Space.x2),
                )
            }
        }
        if (upcoming.isEmpty()) {
            item(key = "today:no-events") {
                Text(
                    "No upcoming events.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = Space.x2),
                )
            }
        }
        items(upcoming, key = { "event:" + it.event.id + "@" + it.start }) { occurrence ->
            val event = occurrence.event
            Row(
                Modifier.fillMaxWidth().heightIn(min = Space.touch).clickable { onOpenEvent(event.id + "@" + occurrence.start) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (occurrence.start > day) {
                        LocalDate.ofEpochDay(occurrence.start).let { "${it.dayOfMonth}/${it.monthValue}" }
                    } else {
                        event.minute?.let { timeText(it, clock24) } ?: "Today"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.width(72.dp),
                )
                Text(event.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            }
        }
        item(key = "today:end") { Spacer(Modifier.height(Space.x6)) }
    }
    if (editing != null) {
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Rename task", style = MaterialTheme.typography.bodyLarge) },
            text = { OutlinedTextField(rename, { rename = it }, singleLine = false, modifier = Modifier.onFocusChanged { onEditingChange(it.isFocused) }) },
            confirmButton = {
                QuietButton("Save", onClick = {
                    if (rename.isNotBlank()) {
                        editing?.let { viewModel.rename(it.id, rename.trim()) }
                        editing = null
                    }
                })
            },
            dismissButton = { QuietButton("Cancel", onClick = { editing = null }) },
        )
    }
}

private fun Modifier.reportPanelEnd(onEnd: (Float) -> Unit): Modifier = onGloballyPositioned { onEnd(it.positionInRoot().y) }
