package dev.fourthbreakfast.planner.tasks

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.fourthbreakfast.planner.core.Priority
import dev.fourthbreakfast.planner.resources.Res
import dev.fourthbreakfast.planner.resources.change_priority
import dev.fourthbreakfast.planner.resources.collapse_all
import dev.fourthbreakfast.planner.resources.expand_all
import dev.fourthbreakfast.planner.resources.priority_high
import dev.fourthbreakfast.planner.resources.priority_low
import dev.fourthbreakfast.planner.resources.priority_medium
import dev.fourthbreakfast.planner.resources.priority_none
import dev.fourthbreakfast.planner.ui.CheckBoxMark
import dev.fourthbreakfast.planner.ui.DrawnIconButton
import dev.fourthbreakfast.planner.ui.openAllTriangle
import dev.fourthbreakfast.planner.ui.theme.PriorityHigh
import dev.fourthbreakfast.planner.ui.theme.PriorityLow
import dev.fourthbreakfast.planner.ui.theme.PriorityMedium
import dev.fourthbreakfast.planner.ui.theme.Space
import org.jetbrains.compose.resources.stringResource

@Composable
fun priorityLabel(priority: Priority): String = stringResource(
    when (priority) {
        Priority.HIGH -> Res.string.priority_high
        Priority.MEDIUM -> Res.string.priority_medium
        Priority.LOW -> Res.string.priority_low
        Priority.NONE -> Res.string.priority_none
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
        CheckBoxMark(
            checked,
            ink,
            Modifier
                .size(Space.check)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
        )
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
            Priority.HIGH -> Space.dotHighRadius
            Priority.MEDIUM -> Space.dotMediumRadius
            Priority.LOW -> Space.dotLowRadius
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
    val action = stringResource(Res.string.change_priority)
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

/** Opens everything or closes everything. A triangle pointing down opens, pointing up closes. */
@Composable
fun ExpandAllButton(anyCollapsed: Boolean, onClick: () -> Unit) {
    DrawnIconButton(stringResource(if (anyCollapsed) Res.string.expand_all else Res.string.collapse_all), onClick) { ink ->
        openAllTriangle(ink, opens = anyCollapsed)
    }
}
