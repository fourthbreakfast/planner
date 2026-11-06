package dev.fourthbreakfast.planner.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import dev.fourthbreakfast.planner.resources.Res
import dev.fourthbreakfast.planner.resources.agenda_title
import dev.fourthbreakfast.planner.resources.notes_title
import dev.fourthbreakfast.planner.resources.tasks_title
import dev.fourthbreakfast.planner.ui.theme.Space
import org.jetbrains.compose.resources.stringResource

/** The three tabs, in their order along the top, each with the side of the bar its word sits at. */
enum class Tab(val alignment: Alignment) {
    TASKS(Alignment.CenterStart),
    AGENDA(Alignment.Center),
    NOTES(Alignment.CenterEnd),
}

/**
 * The three tabs along the top: tasks at the left, agenda in the middle, notes at the right. The one you are on is in
 * full ink, the other is quiet, and a tab dims a little while it is pressed. Holding any tab opens the settings.
 */
@Composable
fun TabBar(selected: Tab, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier, onLongPress: (() -> Unit)? = null) {
    val labels = listOf(
        stringResource(Res.string.tasks_title),
        stringResource(Res.string.agenda_title),
        stringResource(Res.string.notes_title),
    )
    val density = LocalDensity.current
    val scroll = rememberScrollState()
    BoxWithConstraints(
        modifier = modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = Space.rowPad),
    ) {
        val wordWidth = labels.maxOf { segmentTextWidth(it, TAB_SCALE * density.fontScale) }
        val cellWidth = maxOf(maxWidth / Tab.entries.size, wordWidth + (Space.gutter - Space.rowPad) * 2)
        val viewportPx = with(density) { maxWidth.toPx() }
        val cellPx = with(density) { cellWidth.toPx() }
        LaunchedEffect(selected, viewportPx, cellPx, scroll.maxValue) {
            val target = (selected.ordinal * cellPx - (viewportPx - cellPx) / 2).toInt().coerceIn(0, scroll.maxValue)
            scroll.scrollTo(target)
        }
        Row(Modifier.horizontalScroll(scroll).selectableGroup()) {
            Tab.entries.forEachIndexed { index, tab ->
                Box(Modifier.width(cellWidth)) {
                    TabWord(labels[index], selected == tab, tab.alignment, { onSelect(tab) }, onLongPress)
                }
            }
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
fun ScreenHeading(text: String, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = modifier
            .heightIn(min = Space.touch)
            .semantics { heading() },
    ) {
        SegmentText(text = text, color = MaterialTheme.colorScheme.onBackground, scale = TAB_SCALE)
    }
}
