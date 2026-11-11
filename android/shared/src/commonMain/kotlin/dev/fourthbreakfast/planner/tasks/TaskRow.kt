package dev.fourthbreakfast.planner.tasks

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import dev.fourthbreakfast.planner.resources.Res
import dev.fourthbreakfast.planner.resources.aim_at_task
import dev.fourthbreakfast.planner.resources.remove_task
import dev.fourthbreakfast.planner.resources.rename
import dev.fourthbreakfast.planner.resources.schedule_task
import dev.fourthbreakfast.planner.resources.shortlist_add
import dev.fourthbreakfast.planner.resources.shortlist_remove
import dev.fourthbreakfast.planner.resources.task_actions
import dev.fourthbreakfast.planner.resources.task_progress
import dev.fourthbreakfast.planner.ui.BackHandler
import dev.fourthbreakfast.planner.ui.DrawnIconButton
import dev.fourthbreakfast.planner.ui.RowSwipe
import dev.fourthbreakfast.planner.ui.SwipeRow
import dev.fourthbreakfast.planner.ui.starIcon
import dev.fourthbreakfast.planner.ui.theme.PriorityMedium
import dev.fourthbreakfast.planner.ui.theme.Space
import org.jetbrains.compose.resources.stringResource

/** Shows where a drop would land: a line above or below the row, or the row lit up for nesting. */
@Composable
internal fun Modifier.dropHint(zone: DropZone?, depth: Int = 0): Modifier {
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
internal fun TaskRow(
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
    onToggleStar: () -> Unit = {},
    onSchedule: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val task = node.task
    val removeLabel = stringResource(Res.string.remove_task)
    val renameLabel = stringResource(Res.string.rename)
    val aimLabel = stringResource(Res.string.aim_at_task)
    val shortlistLabel = stringResource(if (task.starred) Res.string.shortlist_remove else Res.string.shortlist_add)
    val scheduleLabel = stringResource(Res.string.schedule_task)
    val colors = MaterialTheme.colorScheme
    var actionsOpen by remember(task.id) { mutableStateOf(false) }
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
                CustomAccessibilityAction(shortlistLabel) {
                    onToggleStar()
                    true
                },
            ) + if (onSchedule != null) {
                listOf(
                    CustomAccessibilityAction(scheduleLabel) {
                        onSchedule()
                        true
                    },
                )
            } else {
                emptyList()
            }
        },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Space.radius))
                .background(if (aimed || dragging) colors.secondaryContainer else colors.background)
                .dropHint(dropHint, dropDepth)
                .clickable(enabled = !renaming, onClickLabel = aimLabel, onClick = onAim)
                .padding(horizontal = Space.rowPad),
        ) {
            Spacer(Modifier.width(Space.indent * node.depth))
            TaskCheck(task.done, colors.onBackground, task.title, onDoneChange)
            PriorityMarker(task.priority, onCyclePriority)
            if (renaming) {
                RenameField(
                    title = task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    onRename = onRename,
                    onCancel = onCancelRename,
                    modifier = Modifier.weight(1f).padding(vertical = Space.x2),
                )
            } else {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (task.done) TextDecoration.LineThrough else null,
                    color = if (task.done) colors.onSurfaceVariant else colors.onBackground,
                    modifier = Modifier.weight(1f).padding(vertical = Space.x2),
                )
            }
            if (node.childCount > 0) {
                Text(
                    text = stringResource(Res.string.task_progress, node.doneChildCount, node.childCount),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                )
            }
            if (task.starred) Canvas(Modifier.size(Space.x3)) { starIcon(PriorityMedium, true) }
            Box {
                DrawnIconButton(stringResource(Res.string.task_actions), { actionsOpen = true }) { ink ->
                    for (i in -1..1) {
                        drawCircle(
                            ink,
                            radius = size.minDimension * 0.075f,
                            center = Offset(
                                size.width / 2 + i * size.width * 0.28f,
                                size.height / 2,
                            ),
                        )
                    }
                }
                DropdownMenu(expanded = actionsOpen, onDismissRequest = { actionsOpen = false }) {
                    DropdownMenuItem(text = { Text(stringResource(Res.string.rename)) }, onClick = {
                        actionsOpen = false
                        onStartRename()
                    })
                    DropdownMenuItem(
                        text = { Text(stringResource(if (task.starred) Res.string.shortlist_remove else Res.string.shortlist_add)) },
                        onClick = {
                            actionsOpen = false
                            onToggleStar()
                        },
                    )
                    if (onSchedule !=
                        null
                    ) {
                        DropdownMenuItem(text = { Text(stringResource(Res.string.schedule_task)) }, onClick = {
                            actionsOpen = false
                            onSchedule()
                        })
                    }
                    DropdownMenuItem(text = { Text(removeLabel) }, onClick = {
                        actionsOpen = false
                        onRemove()
                    })
                }
            }
        }
    }
}

/** Edits a title in place. Done or tapping elsewhere keeps the edit, back drops it. */
@Composable
private fun RenameField(title: String, style: TextStyle, onRename: (String) -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier) {
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
