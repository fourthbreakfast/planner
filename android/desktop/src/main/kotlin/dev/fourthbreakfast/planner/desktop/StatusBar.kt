package dev.fourthbreakfast.planner.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.fourthbreakfast.planner.resources.Res
import dev.fourthbreakfast.planner.resources.settings_title
import dev.fourthbreakfast.planner.ui.theme.Space
import org.jetbrains.compose.resources.stringResource

/** A thin bar along the bottom of the window: how sync is doing at the left, the settings cog at the right. */
@Composable
fun StatusBar(sync: DesktopSync, onSettings: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val state by sync.state.collectAsState()
    Box(Modifier.fillMaxWidth().height(Space.strokeThin).background(colors.outline))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().height(32.dp).background(colors.background).padding(start = Space.gutter, end = Space.x1),
    ) {
        val peers = state.peers
        val text = when {
            peers.isEmpty() -> ""
            else -> peers.joinToString("  ·  ") { peer ->
                peer.name.ifBlank { peer.id.take(7) } + " " + if (peer.connected) "●" else "○"
            }
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant, modifier = Modifier.weight(1f))
        HeaderButton(stringResource(Res.string.settings_title), onSettings) { cog(it) }
    }
}
