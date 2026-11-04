package dev.fourthbreakfast.planner.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
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
import dev.fourthbreakfast.planner.resources.Res
import dev.fourthbreakfast.planner.resources.bin
import dev.fourthbreakfast.planner.resources.bin_with_count
import dev.fourthbreakfast.planner.ui.SegmentNumber
import dev.fourthbreakfast.planner.ui.SegmentText
import dev.fourthbreakfast.planner.ui.theme.Space
import org.jetbrains.compose.resources.stringResource

/** The desktop's own sizes: a mouse needs less room than a thumb. */
object Desk {
    /** A computer screen is read from further and pointed at finely, so its type is a little smaller than the phone's. */
    val textScale = 0.9f

    val titleBar = 40.dp
    val row = 36.dp

    // every control in a column is a 32dp square with a 16dp drawing, its edge 12dp in from the column's right side
    val button = 32.dp
    val icon = 16.dp
    val inset = 12.dp
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
        modifier = Modifier.fillMaxWidth().height(Space.touch).padding(start = Space.gutter, end = Desk.inset),
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
        Canvas(Modifier.size(Desk.icon)) { draw(ink) }
    }
}

/** A plus, for adding, drawn as the phone's. */
fun androidx.compose.ui.graphics.drawscope.DrawScope.plus(ink: Color) {
    val line = Space.strokeThin.toPx()
    drawLine(ink, Offset(size.width * 0.5f, size.height * 0.14f), Offset(size.width * 0.5f, size.height * 0.86f), line, StrokeCap.Round)
    drawLine(ink, Offset(size.width * 0.14f, size.height * 0.5f), Offset(size.width * 0.86f, size.height * 0.5f), line, StrokeCap.Round)
}

/** A plain calendar page: two rings at the top, a line under them, nothing inside. */
fun androidx.compose.ui.graphics.drawscope.DrawScope.calendar(ink: Color) {
    val line = Space.strokeThin.toPx()
    val w = size.width
    val h = size.height
    drawRect(
        ink,
        Offset(w * 0.16f, h * 0.22f),
        androidx.compose.ui.geometry.Size(w * 0.68f, h * 0.62f),
        style = androidx.compose.ui.graphics.drawscope.Stroke(line),
    )
    drawLine(ink, Offset(w * 0.16f, h * 0.42f), Offset(w * 0.84f, h * 0.42f), line)
    for (x in listOf(0.36f, 0.64f)) drawLine(ink, Offset(w * x, h * 0.12f), Offset(w * x, h * 0.30f), line, StrokeCap.Round)
}

/** Three lines, a list. */
fun androidx.compose.ui.graphics.drawscope.DrawScope.listLines(ink: Color) {
    val line = Space.strokeThin.toPx()
    for (y in listOf(
        0.28f,
        0.5f,
        0.72f,
    )) {
        drawLine(ink, Offset(size.width * 0.18f, size.height * y), Offset(size.width * 0.82f, size.height * y), line, StrokeCap.Round)
    }
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

/** The + at the bottom right of a column, level with the line that adds a task, as on the phone. */
@Composable
fun FootAdd(label: String, onClick: () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().height(Space.touch).padding(start = Space.gutter, end = Desk.inset),
    ) {
        HeaderButton(label, onClick) { plus(it) }
    }
}

/** The bin, with how many things are in it written just before it when there are any. */
@Composable
fun DeskBin(count: Int, onClick: () -> Unit) {
    val label = if (count > 0) stringResource(Res.string.bin_with_count, count) else stringResource(Res.string.bin)
    if (count > 0) {
        Text(
            count.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = Space.x2),
        )
    }
    HeaderButton(label, onClick) { binIcon(it) }
}

/** A bin: a body narrowing to its foot, a lid with a handle, as the phone draws it. */
fun androidx.compose.ui.graphics.drawscope.DrawScope.binIcon(ink: Color) {
    val line = androidx.compose.ui.graphics.drawscope.Stroke(
        Space.strokeThin.toPx(),
        cap = StrokeCap.Round,
        join = androidx.compose.ui.graphics.StrokeJoin.Round,
    )
    val w = size.width
    val h = size.height
    val body = androidx.compose.ui.graphics.Path().apply {
        moveTo(w * 0.22f, h * 0.28f)
        lineTo(w * 0.28f, h * 0.92f)
        lineTo(w * 0.72f, h * 0.92f)
        lineTo(w * 0.78f, h * 0.28f)
    }
    drawPath(body, ink, style = line)
    val lid = androidx.compose.ui.graphics.Path().apply {
        moveTo(w * 0.12f, h * 0.28f)
        lineTo(w * 0.88f, h * 0.28f)
        moveTo(w * 0.38f, h * 0.28f)
        lineTo(w * 0.38f, h * 0.10f)
        lineTo(w * 0.62f, h * 0.10f)
        lineTo(w * 0.62f, h * 0.28f)
    }
    drawPath(lid, ink, style = line)
}

/** A › pointing right, for a line in use. */
fun androidx.compose.ui.graphics.drawscope.DrawScope.chevron(ink: Color) {
    val line = Space.strokeThin.toPx()
    drawLine(ink, Offset(size.width * 0.36f, size.height * 0.18f), Offset(size.width * 0.68f, size.height * 0.5f), line, StrokeCap.Round)
    drawLine(ink, Offset(size.width * 0.68f, size.height * 0.5f), Offset(size.width * 0.36f, size.height * 0.82f), line, StrokeCap.Round)
}
