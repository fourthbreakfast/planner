package dev.fourthbreakfast.planner.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.fourthbreakfast.planner.ui.SegmentNumber
import dev.fourthbreakfast.planner.ui.SegmentText
import dev.fourthbreakfast.planner.ui.theme.Space

/** The desktop's own sizes: a mouse needs less room than a thumb. */
object Desk {
    val titleBar = 40.dp
    val row = 36.dp
    val button = 32.dp
    val columnMin = 280.dp
    val panel = 560.dp
    val edge = 6.dp
}

/** The top of a column: its name in the calculator lettering, a count, then its buttons at the right. */
@Composable
fun ColumnHeader(name: String, count: Int?, focused: Boolean, actions: @Composable RowScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().height(Space.touch).padding(start = Space.gutter, end = Space.x1),
    ) {
        SegmentText(name, if (focused) colors.onBackground else colors.onSurfaceVariant)
        if (count != null) SegmentNumber(count, colors.onSurfaceVariant, Modifier.padding(start = Space.x3))
        Box(Modifier.weight(1f))
        actions()
    }
}

/** A small drawn button in the column header, the same quiet ink as the phone's icons. */
@Composable
fun HeaderButton(label: String, onClick: () -> Unit, draw: androidx.compose.ui.graphics.drawscope.DrawScope.(Color) -> Unit) {
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(Desk.button)
            .background(if (hovered) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .hoverable(hover)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
    ) {
        Canvas(Modifier.size(Space.icon)) { draw(ink) }
    }
}

/** A plus, for adding. */
fun androidx.compose.ui.graphics.drawscope.DrawScope.plus(ink: Color) {
    val line = Space.strokeThin.toPx()
    drawLine(ink, Offset(size.width * 0.5f, size.height * 0.2f), Offset(size.width * 0.5f, size.height * 0.8f), line, StrokeCap.Round)
    drawLine(ink, Offset(size.width * 0.2f, size.height * 0.5f), Offset(size.width * 0.8f, size.height * 0.5f), line, StrokeCap.Round)
}

/** A magnifying glass, for search. */
fun androidx.compose.ui.graphics.drawscope.DrawScope.magnifier(ink: Color) {
    val line = Space.strokeThin.toPx()
    drawCircle(
        ink,
        radius = size.width * 0.28f,
        center = Offset(size.width * 0.44f, size.height * 0.44f),
        style = androidx.compose.ui.graphics.drawscope.Stroke(line),
    )
    drawLine(ink, Offset(size.width * 0.64f, size.height * 0.64f), Offset(size.width * 0.84f, size.height * 0.84f), line, StrokeCap.Round)
}

/** A cog for the settings: eight square teeth around a ring, with a hole in the middle, all in one thin line. */
fun androidx.compose.ui.graphics.drawscope.DrawScope.cog(ink: Color) {
    val line = Space.strokeThin.toPx()
    val c = Offset(size.width / 2, size.height / 2)
    val outer = size.minDimension * 0.46f
    val inner = size.minDimension * 0.34f
    val teeth = 8
    val path = androidx.compose.ui.graphics.Path()
    for (i in 0 until teeth * 2) {
        // each tooth and each gap takes the same turn, the tooth standing out to the outer ring
        val start = (i / (teeth * 2f)) * 2 * Math.PI - Math.PI / (teeth * 2)
        val end = start + 2 * Math.PI / (teeth * 2)
        val r = if (i % 2 == 0) outer else inner
        val a = Offset(c.x + r * kotlin.math.cos(start).toFloat(), c.y + r * kotlin.math.sin(start).toFloat())
        val b = Offset(c.x + r * kotlin.math.cos(end).toFloat(), c.y + r * kotlin.math.sin(end).toFloat())
        if (i == 0) path.moveTo(a.x, a.y) else path.lineTo(a.x, a.y)
        path.lineTo(b.x, b.y)
    }
    path.close()
    drawPath(path, ink, style = androidx.compose.ui.graphics.drawscope.Stroke(line, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    drawCircle(ink, radius = size.minDimension * 0.13f, center = c, style = androidx.compose.ui.graphics.drawscope.Stroke(line))
}

/** A short line of quiet text, for an empty column. */
@Composable
fun Quiet(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = Space.gutter, top = Space.x3),
    )
}

/** Whether the pointer is over something, for rows that show their buttons on hover. */
@Composable
fun rememberHover(): Pair<MutableInteractionSource, Boolean> {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    return source to hovered
}
