package dev.fourthbreakfast.planner.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = Space.rowPad)
            .selectableGroup(),
    ) {
        for (tab in Tab.entries) {
            val title = when (tab) {
                Tab.TASKS -> Res.string.tasks_title
                Tab.AGENDA -> Res.string.agenda_title
                Tab.NOTES -> Res.string.notes_title
            }
            Box(Modifier.weight(1f)) {
                TabWord(stringResource(title), selected == tab, { onSelect(tab) }, onLongPress)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TabWord(text: String, active: Boolean, onClick: () -> Unit, onLongClick: (() -> Unit)?) {
    val haptics = LocalHapticFeedback.current
    val colors = MaterialTheme.colorScheme
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    Box(
        contentAlignment = Alignment.Center,
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
        Text(
            text = text,
            color = if (active) colors.primary else colors.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
        )
        if (active) {
            Box(Modifier.align(Alignment.BottomCenter).width(Space.x6).height(Space.strokeThick).background(colors.primary))
        }
    }
}

/** A heading for a screen that is a tab of its own. */
@Composable
fun ScreenHeading(text: String, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = modifier
            .heightIn(min = Space.touch)
            .semantics { heading() },
    ) {
        Text(text = text, color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.titleLarge)
    }
}
