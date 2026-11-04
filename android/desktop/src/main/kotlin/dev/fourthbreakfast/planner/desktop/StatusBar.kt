package dev.fourthbreakfast.planner.desktop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import dev.fourthbreakfast.planner.resources.Res
import dev.fourthbreakfast.planner.resources.restart_to_update
import dev.fourthbreakfast.planner.resources.settings_title
import dev.fourthbreakfast.planner.ui.cog
import dev.fourthbreakfast.planner.ui.theme.Space
import dev.fourthbreakfast.planner.updates.UpdateState
import dev.fourthbreakfast.planner.updates.Updates
import org.jetbrains.compose.resources.stringResource

/**
 * What sits in the title bar just left of minimize, maximize and close: how sync is doing, a newer version when one is
 * ready, and the settings cog.
 */
@Composable
fun TitleStatus(sync: DesktopSync, updates: Updates, onUpdate: (UpdateState.Ready) -> Unit, onSettings: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val state by sync.state.collectAsState()
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
            modifier = Modifier.padding(horizontal = Space.x3),
        )
    }
    (update as? UpdateState.Ready)?.let { ready ->
        // a newer version, offered quietly beside the settings
        Text(
            stringResource(Res.string.restart_to_update),
            style = MaterialTheme.typography.labelMedium,
            color = colors.onBackground,
            modifier = Modifier.clickable { onUpdate(ready) }.padding(horizontal = Space.x3),
        )
    }
    HeaderButton(stringResource(Res.string.settings_title), onSettings) { cog(it) }
}
