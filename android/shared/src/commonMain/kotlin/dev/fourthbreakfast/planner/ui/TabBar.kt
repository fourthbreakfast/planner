package dev.fourthbreakfast.planner.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import dev.fourthbreakfast.planner.resources.Res
import dev.fourthbreakfast.planner.resources.agenda_title
import dev.fourthbreakfast.planner.resources.habits_title
import dev.fourthbreakfast.planner.resources.notes_title
import dev.fourthbreakfast.planner.resources.tasks_title
import dev.fourthbreakfast.planner.resources.today_title
import dev.fourthbreakfast.planner.ui.theme.PriorityHigh
import dev.fourthbreakfast.planner.ui.theme.Space
import org.jetbrains.compose.resources.stringResource

/** Shared reading order for desktop navigation and the phone's bottom bar. */
enum class Tab(val alignment: Alignment) {
    TODAY(Alignment.CenterStart),
    TASKS(Alignment.CenterStart),
    HABITS(Alignment.Center),
    AGENDA(Alignment.Center),
    NOTES(Alignment.CenterEnd),
}

/** The application shell supplies navigation once; retained feature screens can suppress their legacy bars. */
val LocalEmbeddedNavigation = compositionLocalOf { false }

@Composable
fun tabLabel(tab: Tab): String = stringResource(
    when (tab) {
        Tab.TODAY -> Res.string.today_title
        Tab.TASKS -> Res.string.tasks_title
        Tab.HABITS -> Res.string.habits_title
        Tab.AGENDA -> Res.string.agenda_title
        Tab.NOTES -> Res.string.notes_title
    },
).replaceFirstChar { it.uppercase() }

/** Five labeled destinations, ready for the shell's bottom bar. The shell owns system insets. */
@Composable
fun TabBar(selected: Tab, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier, onLongPress: (() -> Unit)? = null) {
    if (LocalEmbeddedNavigation.current) return
    val colors = MaterialTheme.colorScheme
    Row(
        modifier.fillMaxWidth().background(colors.background)
            .padding(horizontal = Space.x2, vertical = Space.x1).height(IntrinsicSize.Min).selectableGroup(),
    ) {
        Tab.entries.forEach { tab ->
            TabWord(tab, selected == tab, { onSelect(tab) }, onLongPress, Modifier.weight(1f))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TabWord(tab: Tab, active: Boolean, onClick: () -> Unit, onLongClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    val label = tabLabel(tab)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.x1, Alignment.CenterVertically),
        modifier = modifier.fillMaxHeight().heightIn(min = Space.touch + Space.x2)
            .clip(RoundedCornerShape(Space.radius))
            .background(
                when {
                    pressed -> colors.secondaryContainer
                    hovered -> colors.surfaceVariant
                    else -> colors.background
                },
            )
            .drawBehind {
                if (focused) {
                    val line = Space.strokeThin.toPx()
                    drawRoundRect(
                        colors.onSurface,
                        topLeft = Offset(line / 2, line / 2),
                        size = Size(size.width - line, size.height - line),
                        cornerRadius = CornerRadius(Space.radius.toPx()),
                        style = Stroke(line),
                    )
                }
            }
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
                onLongClick = onLongClick?.let { action ->
                    {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        action()
                    }
                },
            )
            .semantics { selected = active }
            .padding(horizontal = Space.x1, vertical = Space.x1),
    ) {
        NavigationIcon(tab, active = active)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) colors.onSurface else colors.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
        )
        Canvas(Modifier.size(Space.x1)) {
            if (active) drawCircle(PriorityHigh)
        }
    }
}

/** Small outline drawings shared by the desktop rail and mobile navigation; labels carry their meaning. */
@Composable
fun NavigationIcon(tab: Tab, modifier: Modifier = Modifier, active: Boolean = false) {
    val ink = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier.size(Space.icon)) {
        val unit = size.minDimension / 24f
        fun point(x: Float, y: Float) = Offset(x * unit, y * unit)
        fun line(x: Float, y: Float, endX: Float, endY: Float) = drawLine(ink, point(x, y), point(endX, endY), Space.strokeThin.toPx(), StrokeCap.Round)
        val stroke = Stroke(Space.strokeThin.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (tab) {
            Tab.TODAY -> {
                drawCircle(ink, 4f * unit, point(12f, 12f), style = stroke)
                line(12f, 2f, 12f, 4f)
                line(12f, 20f, 12f, 22f)
                line(2f, 12f, 4f, 12f)
                line(20f, 12f, 22f, 12f)
                line(5f, 5f, 6.5f, 6.5f)
                line(17.5f, 17.5f, 19f, 19f)
                line(5f, 19f, 6.5f, 17.5f)
                line(17.5f, 6.5f, 19f, 5f)
            }
            Tab.TASKS -> {
                line(3f, 6f, 5f, 8f)
                line(5f, 8f, 8f, 4f)
                line(11f, 6f, 21f, 6f)
                line(3f, 15f, 5f, 17f)
                line(5f, 17f, 8f, 13f)
                line(11f, 15f, 21f, 15f)
            }
            Tab.HABITS -> {
                drawRoundRect(ink, point(3f, 4f), Size(18f * unit, 16f * unit), CornerRadius(2f * unit), style = stroke)
                for (x in listOf(7f, 12f, 17f)) {
                    for (y in listOf(9f, 15f)) {
                        drawCircle(ink, unit, point(x, y))
                    }
                }
            }
            Tab.AGENDA -> {
                drawRoundRect(ink, point(3f, 5f), Size(18f * unit, 16f * unit), CornerRadius(2f * unit), style = stroke)
                line(7f, 3f, 7f, 7f)
                line(17f, 3f, 17f, 7f)
                line(3f, 10f, 21f, 10f)
                line(7f, 15f, 11f, 15f)
            }
            Tab.NOTES -> {
                val page = Path().apply {
                    moveTo(5f * unit, 3f * unit)
                    lineTo(15f * unit, 3f * unit)
                    lineTo(20f * unit, 8f * unit)
                    lineTo(20f * unit, 21f * unit)
                    lineTo(5f * unit, 21f * unit)
                    close()
                }
                drawPath(page, ink, style = stroke)
                line(15f, 3f, 15f, 8f)
                line(15f, 8f, 20f, 8f)
                line(9f, 12f, 16f, 12f)
                line(9f, 16f, 16f, 16f)
            }
        }
    }
}

/** Retained for callers that used the old scale constant; headings now follow the shared text scale. */
const val TAB_SCALE = 1.0f

@Composable
fun ScreenHeading(text: String, modifier: Modifier = Modifier) {
    Box(modifier.heightIn(min = Space.touch).semantics { heading() }, contentAlignment = Alignment.CenterStart) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}
