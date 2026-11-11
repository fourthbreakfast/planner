package dev.fourthbreakfast.planner.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.fourthbreakfast.planner.resources.Res
import dev.fourthbreakfast.planner.resources.habits_title
import dev.fourthbreakfast.planner.resources.open_update_installer
import dev.fourthbreakfast.planner.resources.restart_to_update
import dev.fourthbreakfast.planner.resources.settings_title
import dev.fourthbreakfast.planner.resources.sync_now
import dev.fourthbreakfast.planner.settings.syncStatusText
import dev.fourthbreakfast.planner.sync.SyncHealth
import dev.fourthbreakfast.planner.sync.health
import dev.fourthbreakfast.planner.ui.QuietButton
import dev.fourthbreakfast.planner.ui.cog
import dev.fourthbreakfast.planner.ui.theme.PriorityLow
import dev.fourthbreakfast.planner.ui.theme.PriorityMedium
import dev.fourthbreakfast.planner.ui.theme.Space
import dev.fourthbreakfast.planner.updates.UpdateState
import dev.fourthbreakfast.planner.updates.Updates
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * What sits in the title bar just left of minimize, maximize and close: how sync is doing, a newer version when one is
 * ready, and the settings cog.
 */
@Composable
fun TitleStatus(sync: DesktopSync, updates: Updates, onUpdate: (UpdateState.Ready) -> Unit, onSettings: () -> Unit, onHabits: () -> Unit = {}) {
    val colors = MaterialTheme.colorScheme
    QuietButton(stringResource(Res.string.habits_title), onHabits)
    val state by sync.state.collectAsState()
    val scope = rememberCoroutineScope()
    val update by updates.state.collectAsState()
    val text = state.peers.joinToString("  ·  ") { peer ->
        peer.name.ifBlank { peer.id.take(7) } + " " + if (peer.connected) "●" else "○"
    }
    if (text.isNotEmpty()) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 240.dp).padding(horizontal = Space.x3),
        )
        val statusColor = when (state.health) {
            SyncHealth.CURRENT -> PriorityLow
            SyncHealth.WORKING -> PriorityMedium
            SyncHealth.ERROR -> colors.error
            else -> colors.onSurfaceVariant
        }
        Canvas(Modifier.size(Space.dot)) { drawCircle(statusColor) }
        WindowButton(
            stringResource(Res.string.sync_now) + ": " + syncStatusText(state),
            hoverColor = colors.secondaryContainer,
            onClick = { if (!sync.state.value.syncing) scope.launch { sync.syncNow() } },
        ) { ink ->
            val line = Space.strokeThin.toPx()
            val inset = size.minDimension * 0.18f
            drawArc(
                ink,
                35f,
                280f,
                false,
                Offset(inset, inset),
                Size(size.width - inset * 2, size.height - inset * 2),
                style = Stroke(line, cap = StrokeCap.Round),
            )
            drawLine(ink, Offset(size.width * 0.78f, size.height * 0.24f), Offset(size.width * 0.78f, size.height * 0.48f), line, StrokeCap.Round)
            drawLine(ink, Offset(size.width * 0.78f, size.height * 0.48f), Offset(size.width * 0.54f, size.height * 0.48f), line, StrokeCap.Round)
        }
    }
    (update as? UpdateState.Ready)?.let { ready ->
        // a newer version, offered quietly beside the settings
        Text(
            stringResource(if (DesktopPlatform.isLinux) Res.string.open_update_installer else Res.string.restart_to_update),
            style = MaterialTheme.typography.labelMedium,
            color = colors.onBackground,
            modifier = Modifier.clickable { onUpdate(ready) }.padding(horizontal = Space.x3),
        )
    }
    // the same size and hover as the window buttons beside it
    WindowButton(stringResource(Res.string.settings_title), hoverColor = colors.secondaryContainer, onClick = onSettings) { cog(it) }
}
