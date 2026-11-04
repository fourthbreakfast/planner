package dev.fourthbreakfast.planner.desktop

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import dev.fourthbreakfast.planner.resources.Res
import dev.fourthbreakfast.planner.resources.restart_to_update
import dev.fourthbreakfast.planner.resources.settings_title
import dev.fourthbreakfast.planner.ui.theme.Space
import dev.fourthbreakfast.planner.updates.UpdateState
import dev.fourthbreakfast.planner.updates.Updates
import kotlin.math.cos
import kotlin.math.sin
import org.jetbrains.compose.resources.stringResource

/**
 * The foot of the notes column, level with the line that adds a task: how sync is doing, a newer version when one is
 * ready, and the settings cog at the far right. No bar of its own across the window.
 */
@Composable
fun StatusLine(sync: DesktopSync, updates: Updates, onUpdate: (UpdateState.Ready) -> Unit, onSettings: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val state by sync.state.collectAsState()
    val update by updates.state.collectAsState()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = Space.gutter, end = Space.x1, top = Space.x2, bottom = Space.x2).height(Desk.row),
    ) {
        val text = state.peers.joinToString("  ·  ") { peer ->
            peer.name.ifBlank { peer.id.take(7) } + " " + if (peer.connected) "●" else "○"
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant, maxLines = 1, modifier = Modifier.weight(1f))
        (update as? UpdateState.Ready)?.let { ready ->
            // a newer version, offered quietly beside the settings
            Text(
                stringResource(Res.string.restart_to_update),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onBackground,
                modifier = Modifier.clickable { onUpdate(ready) }.padding(horizontal = Space.x3),
            )
        }
        HeaderButton(stringResource(Res.string.settings_title), onSettings) { plainCog(it) }
    }
}

/** A plain cog: six blunt teeth on a body with flat sides between them, and a round hole, all in the one thin line. */
fun DrawScope.plainCog(ink: Color) {
    val line = Space.strokeThin.toPx()
    val c = Offset(size.width / 2, size.height / 2)
    val outer = size.minDimension * 0.46f
    val inner = size.minDimension * 0.33f
    fun at(r: Float, turn: Double) = Offset(c.x + r * cos(turn).toFloat(), c.y + r * sin(turn).toFloat())
    val path = Path()
    for (i in 0 until 6) {
        val middle = i * Math.PI / 3 - Math.PI / 2
        // a tooth narrows a little as it stands out, so it reads as a cog and not a star
        val points = listOf(at(inner, middle - 0.30), at(outer, middle - 0.20), at(outer, middle + 0.20), at(inner, middle + 0.30))
        points.forEachIndexed { n, p -> if (i == 0 && n == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y) }
    }
    path.close()
    drawPath(path, ink, style = Stroke(line, join = StrokeJoin.Round))
    drawCircle(ink, radius = size.minDimension * 0.13f, center = c, style = Stroke(line))
}
