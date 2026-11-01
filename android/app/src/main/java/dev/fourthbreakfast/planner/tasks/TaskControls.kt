package dev.fourthbreakfast.planner.tasks

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
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
import androidx.compose.ui.semantics.semantics
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
