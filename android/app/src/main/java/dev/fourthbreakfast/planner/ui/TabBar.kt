package dev.fourthbreakfast.planner.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import dev.fourthbreakfast.planner.R
import dev.fourthbreakfast.planner.ui.theme.Space

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
