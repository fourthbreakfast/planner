package dev.fourthbreakfast.planner.tasks

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.fourthbreakfast.planner.R
import dev.fourthbreakfast.planner.core.Priority
import dev.fourthbreakfast.planner.ui.theme.PriorityHigh
import dev.fourthbreakfast.planner.ui.theme.PriorityLow
import dev.fourthbreakfast.planner.ui.theme.PriorityMedium
import dev.fourthbreakfast.planner.ui.theme.Space

@Composable
fun priorityLabel(priority: Priority): String = stringResource(
    when (priority) {
        Priority.HIGH -> R.string.priority_high
        Priority.MEDIUM -> R.string.priority_medium
        Priority.LOW -> R.string.priority_low
        Priority.NONE -> R.string.priority_none
    },
)

private val pressSpring = spring<Float>(Spring.DampingRatioNoBouncy, Spring.StiffnessMedium)

/** A small square box that gives a little when pressed and fills when done. */
@Composable
fun TaskCheck(checked: Boolean, ink: Color, onCheckedChange: (Boolean) -> Unit) {
    val haptics = LocalHapticFeedback.current
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.88f else 1f, pressSpring)
    val tick = MaterialTheme.colorScheme.background
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(Space.touch)
            .toggleable(
                value = checked,
                interactionSource = press,
                indication = null,
                role = Role.Checkbox,
                onValueChange = {
                    haptics.performHapticFeedback(
                        if (it) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff,
                    )
                    onCheckedChange(it)
                },
            ),
    ) {
        Canvas(
            Modifier
                .size(Space.check)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
        ) {
            val corner = CornerRadius(Space.radiusCheck.toPx())
            if (checked) {
                drawRoundRect(ink, cornerRadius = corner)
                val path = Path().apply {
                    moveTo(size.width * 0.24f, size.height * 0.52f)
                    lineTo(size.width * 0.43f, size.height * 0.72f)
                    lineTo(size.width * 0.77f, size.height * 0.30f)
                }
                drawPath(path, tick, style = Stroke(Space.strokeThick.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            } else {
                drawRoundRect(ink, cornerRadius = corner, style = Stroke(Space.strokeThin.toPx()))
            }
        }
    }
}

private fun priorityColor(priority: Priority): Color = when (priority) {
    Priority.HIGH -> PriorityHigh
    Priority.MEDIUM -> PriorityMedium
    Priority.LOW -> PriorityLow
    Priority.NONE -> Color.Transparent
}

/** A dot that shows the level twice, by color and by size, so it reads without color. A grey ring means no priority. */
@Composable
fun PriorityDot(priority: Priority, modifier: Modifier = Modifier) {
    val outline = MaterialTheme.colorScheme.onSurfaceVariant
    val color by animateColorAsState(priorityColor(priority))
    val radius by animateDpAsState(
        when (priority) {
            Priority.HIGH -> Space.dotHigh
            Priority.MEDIUM -> Space.dotMedium
            Priority.LOW -> Space.dotLow
            Priority.NONE -> Space.dot / 2
        },
    )
    Canvas(modifier.size(Space.modeDot)) {
        val r = radius.toPx()
        drawCircle(color, radius = r)
        if (priority == Priority.NONE) {
            drawCircle(outline, radius = r - (Space.strokeThin / 2).toPx(), style = Stroke(Space.strokeThin.toPx()))
        }
    }
}

@Composable
fun PriorityMarker(priority: Priority, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = priorityLabel(priority)
    val action = stringResource(R.string.change_priority)
    val haptics = LocalHapticFeedback.current
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.8f else 1f, pressSpring)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(Space.dotLane, Space.touch)
            .clickable(
                interactionSource = press,
                indication = null,
                onClickLabel = action,
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                onClick()
            }
            .semantics { contentDescription = label }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
    ) {
        PriorityDot(priority)
    }
}

/** One dot switches the mode: grey is tree mode, red is priority mode. */
@Composable
fun ModeDot(priority: Boolean, onChange: (Boolean) -> Unit) {
    val label = stringResource(R.string.priority_mode)
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(Space.touch)
            .toggleable(value = priority, role = Role.Switch, onValueChange = onChange)
            .semantics { contentDescription = label },
    ) {
        Canvas(Modifier.size(Space.icon)) {
            drawCircle(if (priority) PriorityHigh else ink, radius = (Space.modeDot / 2).toPx())
        }
    }
}

/** A magnifying glass, in the row of controls beside the bin. */
@Composable
fun SearchButton(onClick: () -> Unit) {
    val label = stringResource(R.string.search)
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(Space.touch)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
    ) {
        Canvas(Modifier.size(Space.icon)) {
            val stroke = Space.strokeThin.toPx()
            val w = size.width
            drawCircle(ink, radius = w * 0.34f, center = Offset(w * 0.42f, w * 0.42f), style = Stroke(stroke, cap = StrokeCap.Round))
            drawLine(ink, Offset(w * 0.68f, w * 0.68f), Offset(w * 0.92f, w * 0.92f), strokeWidth = stroke, cap = StrokeCap.Round)
        }
    }
}

@Composable
fun BinButton(binCount: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = if (binCount > 0) stringResource(R.string.bin_with_count, binCount) else stringResource(R.string.bin)
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .heightIn(min = Space.touch)
            .widthIn(min = Space.touch)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = label },
    ) {
        Canvas(Modifier.size(Space.icon)) {
            val line = Stroke(Space.strokeThin.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            val w = size.width
            val h = size.height
            val body = Path().apply {
                moveTo(w * 0.22f, h * 0.28f)
                lineTo(w * 0.28f, h * 0.92f)
                lineTo(w * 0.72f, h * 0.92f)
                lineTo(w * 0.78f, h * 0.28f)
            }
            drawPath(body, ink, style = line)
            val lid = Path().apply {
                moveTo(w * 0.12f, h * 0.28f)
                lineTo(w * 0.88f, h * 0.28f)
                moveTo(w * 0.38f, h * 0.28f)
                lineTo(w * 0.38f, h * 0.10f)
                lineTo(w * 0.62f, h * 0.10f)
                lineTo(w * 0.62f, h * 0.28f)
            }
            drawPath(lid, ink, style = line)
        }
        if (binCount > 0) {
            Text(
                text = binCount.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = ink,
                modifier = Modifier.padding(start = Space.x1),
            )
        }
    }
}

/** The title of a screen with a little calculator-style number beside it. Both screens use it so they match. */
@Composable
fun TitleWithCount(title: String?, count: Int, countLabel: String, modifier: Modifier = Modifier, secondCount: Int? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(vertical = Space.x2),
    ) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.semantics { heading() },
            )
        }
        SegmentNumber(
            value = count,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(start = if (title != null) Space.x3 else 0.dp)
                .semantics { contentDescription = countLabel },
        )
        // a second number, the same size but fainter: everything open, subtasks included
        if (secondCount != null) {
            SegmentNumber(
                value = secondCount,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                modifier = Modifier.padding(start = Space.x3).semantics { contentDescription = countLabel },
            )
        }
    }
}

enum class Tab { TASKS, AGENDA, NOTES }

/**
 * The three tabs along the top: tasks at the left, agenda in the middle, notes at the right. The one you are on is in
 * full ink, the other is quiet, and a tab dims a little while it is pressed. Holding any tab opens the settings.
 */
@Composable
fun TabBar(selected: Tab, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier, onLongPress: (() -> Unit)? = null) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = Space.rowPad)
            .selectableGroup(),
    ) {
        Box(Modifier.weight(1f)) {
            TabWord(stringResource(R.string.tasks_title), selected == Tab.TASKS, Alignment.CenterStart, { onSelect(Tab.TASKS) }, onLongPress)
        }
        Box(Modifier.weight(1f)) {
            TabWord(stringResource(R.string.agenda_title), selected == Tab.AGENDA, Alignment.Center, { onSelect(Tab.AGENDA) }, onLongPress)
        }
        Box(Modifier.weight(1f)) {
            TabWord(stringResource(R.string.notes_title), selected == Tab.NOTES, Alignment.CenterEnd, { onSelect(Tab.NOTES) }, onLongPress)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TabWord(text: String, active: Boolean, alignment: Alignment, onClick: () -> Unit, onLongClick: (() -> Unit)?) {
    val haptics = LocalHapticFeedback.current
    val colors = MaterialTheme.colorScheme
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    Box(
        contentAlignment = alignment,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Space.touch)
            .graphicsLayer { alpha = if (pressed) 0.55f else 1f }
            .combinedClickable(
                interactionSource = press,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
                onLongClick = onLongClick?.let { long ->
                    {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        long()
                    }
                },
            )
            .padding(horizontal = Space.gutter - Space.rowPad)
            .semantics { selected = active },
    ) {
        SegmentText(
            text = text,
            color = if (active) colors.onBackground else colors.onSurfaceVariant,
            scale = TAB_SCALE,
        )
    }
}

/** How large the calculator lettering is for tab words and screen titles. */
const val TAB_SCALE = 1.0f

/** A word in the same lettering and size as the tabs, for a screen that is a tab of its own. */
@Composable
fun SegmentTab(text: String, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = modifier
            .heightIn(min = Space.touch)
            .semantics { heading() },
    ) {
        SegmentText(text = text, color = MaterialTheme.colorScheme.onBackground, scale = TAB_SCALE)
    }
}

/** A plus, to add something: drawn like the other icons, quiet, in a touch square at the bottom right. */
@Composable
fun AddButton(label: String, onClick: () -> Unit) {
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(Space.touch)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
    ) {
        Canvas(Modifier.size(Space.icon)) {
            val width = Space.strokeThin.toPx()
            drawLine(ink, Offset(size.width * 0.5f, size.height * 0.14f), Offset(size.width * 0.5f, size.height * 0.86f), width, StrokeCap.Round)
            drawLine(ink, Offset(size.width * 0.14f, size.height * 0.5f), Offset(size.width * 0.86f, size.height * 0.5f), width, StrokeCap.Round)
        }
    }
}

/** An arrow pointing back, in the spot where the bin button is on the main screen. */
@Composable
fun BackButton(onClick: () -> Unit) {
    val label = stringResource(R.string.back)
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(Space.touch)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
    ) {
        Canvas(Modifier.size(Space.icon)) {
            val line = Stroke(Space.strokeThin.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            val w = size.width
            val h = size.height
            val arrow = Path().apply {
                moveTo(w * 0.92f, h * 0.5f)
                lineTo(w * 0.10f, h * 0.5f)
                moveTo(w * 0.42f, h * 0.18f)
                lineTo(w * 0.10f, h * 0.5f)
                lineTo(w * 0.42f, h * 0.82f)
            }
            drawPath(arrow, ink, style = line)
        }
    }
}

/** Opens everything or closes everything. A triangle pointing down opens, pointing up closes. */
@Composable
fun ExpandAllButton(anyCollapsed: Boolean, onClick: () -> Unit) {
    val label = stringResource(if (anyCollapsed) R.string.expand_all else R.string.collapse_all)
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(Space.touch)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
    ) {
        Canvas(Modifier.size(Space.icon)) {
            val w = size.width
            val h = size.height
            val tip = if (anyCollapsed) 0.66f else 0.34f
            val base = if (anyCollapsed) 0.36f else 0.64f
            val triangle = Path().apply {
                moveTo(w * 0.24f, h * base)
                lineTo(w * 0.76f, h * base)
                lineTo(w * 0.5f, h * tip)
                close()
            }
            drawPath(triangle, ink)
        }
    }
}
