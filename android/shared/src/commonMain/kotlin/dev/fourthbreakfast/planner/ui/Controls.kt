package dev.fourthbreakfast.planner.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.fourthbreakfast.planner.resources.Res
import dev.fourthbreakfast.planner.resources.back
import dev.fourthbreakfast.planner.resources.bin
import dev.fourthbreakfast.planner.resources.bin_with_count
import dev.fourthbreakfast.planner.resources.search
import dev.fourthbreakfast.planner.resources.settings_title
import dev.fourthbreakfast.planner.ui.theme.Space
import org.jetbrains.compose.resources.stringResource

/** A magnifying glass, in the row of controls beside the bin. */
@Composable
fun SearchButton(onClick: () -> Unit) {
    val label = stringResource(Res.string.search)
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(Space.touch)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
    ) {
        Canvas(Modifier.size(Space.icon)) {
            val stroke = Space.strokeThin.toPx()
            val w = size.width
            drawCircle(ink, radius = w * 0.34f, center = Offset(w * 0.42f, w * 0.42f), style = Stroke(stroke, cap = StrokeCap.Round))
            drawLine(ink, Offset(w * 0.68f, w * 0.68f), Offset(w * 0.92f, w * 0.92f), strokeWidth = stroke, cap = StrokeCap.Round)
        }
    }
}

@Composable
fun BinButton(binCount: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = if (binCount > 0) stringResource(Res.string.bin_with_count, binCount) else stringResource(Res.string.bin)
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .heightIn(min = Space.touch)
            .widthIn(min = Space.touch)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = label },
    ) {
        Canvas(Modifier.size(Space.icon)) {
            val line = Stroke(Space.strokeThin.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            val w = size.width
            val h = size.height
            val body = Path().apply {
                moveTo(w * 0.22f, h * 0.28f)
                lineTo(w * 0.28f, h * 0.92f)
                lineTo(w * 0.72f, h * 0.92f)
                lineTo(w * 0.78f, h * 0.28f)
            }
            drawPath(body, ink, style = line)
            val lid = Path().apply {
                moveTo(w * 0.12f, h * 0.28f)
                lineTo(w * 0.88f, h * 0.28f)
                moveTo(w * 0.38f, h * 0.28f)
                lineTo(w * 0.38f, h * 0.10f)
                lineTo(w * 0.62f, h * 0.10f)
                lineTo(w * 0.62f, h * 0.28f)
            }
            drawPath(lid, ink, style = line)
        }
        if (binCount > 0) {
            Text(
                text = binCount.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = ink,
                modifier = Modifier.padding(start = Space.x1),
            )
        }
    }
}

/** The title of a screen with a little calculator-style number beside it. Both screens use it so they match. */
@Composable
fun TitleWithCount(title: String?, count: Int, countLabel: String, modifier: Modifier = Modifier, secondCount: Int? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(vertical = Space.x2),
    ) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.semantics { heading() },
            )
        }
        SegmentNumber(
            value = count,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(start = if (title != null) Space.x3 else 0.dp)
                .semantics { contentDescription = countLabel },
        )
        // a second number, the same size but fainter: everything open, subtasks included
        if (secondCount != null) {
            SegmentNumber(
                value = secondCount,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                modifier = Modifier.padding(start = Space.x3).semantics { contentDescription = countLabel },
            )
        }
    }
}

/** A plus, to add something: drawn like the other icons, quiet, in a touch square at the bottom right. */
@Composable
fun AddButton(label: String, onClick: () -> Unit) {
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(Space.touch)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
    ) {
        Canvas(Modifier.size(Space.icon)) {
            val width = Space.strokeThin.toPx()
            drawLine(ink, Offset(size.width * 0.5f, size.height * 0.14f), Offset(size.width * 0.5f, size.height * 0.86f), width, StrokeCap.Round)
            drawLine(ink, Offset(size.width * 0.14f, size.height * 0.5f), Offset(size.width * 0.86f, size.height * 0.5f), width, StrokeCap.Round)
        }
    }
}

/** An arrow pointing back, in the spot where the bin button is on the main screen. */
@Composable
fun BackButton(onClick: () -> Unit) {
    val label = stringResource(Res.string.back)
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(Space.touch)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
    ) {
        Canvas(Modifier.size(Space.icon)) {
            val line = Stroke(Space.strokeThin.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            val w = size.width
            val h = size.height
            val arrow = Path().apply {
                moveTo(w * 0.92f, h * 0.5f)
                lineTo(w * 0.10f, h * 0.5f)
                moveTo(w * 0.42f, h * 0.18f)
                lineTo(w * 0.10f, h * 0.5f)
                lineTo(w * 0.42f, h * 0.82f)
            }
            drawPath(arrow, ink, style = line)
        }
    }
}

@Composable
fun QuietButton(label: String, onClick: () -> Unit) = QuietButton(label, true, onClick)

/** A quiet button that can be switched off, shown fainter while it is. */
@Composable
fun QuietButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = if (enabled) 1f else 0.38f),
        )
    }
}

/**
 * A check box as a small drawing, the same one for tasks and notes: a sharp square outline, and once checked a smaller
 * solid square inside it, lit like a segment.
 */
@Composable
fun CheckBoxMark(checked: Boolean, ink: Color, modifier: Modifier = Modifier.size(Space.check)) {
    Canvas(modifier) {
        // square and centred, whatever room it is given
        val side = minOf(size.width, size.height, Space.check.toPx())
        val corner = Offset((size.width - side) / 2, (size.height - side) / 2)
        val line = Space.strokeThin.toPx()
        drawRect(ink, topLeft = corner + Offset(line / 2, line / 2), size = Size(side - line, side - line), style = Stroke(line))
        if (checked) {
            val inset = Space.checkInset.toPx()
            drawRect(ink, topLeft = corner + Offset(inset, inset), size = Size(side - 2 * inset, side - 2 * inset))
        }
    }
}

/** A plain cog for the settings: six blunt teeth on a body with flat sides between them, and a round hole, in one thin line. */
fun DrawScope.cog(ink: Color) {
    val line = Space.strokeThin.toPx()
    val c = Offset(size.width / 2, size.height / 2)
    val outer = size.minDimension * 0.46f
    val inner = size.minDimension * 0.33f
    fun at(r: Float, turn: Double) = Offset(c.x + r * kotlin.math.cos(turn).toFloat(), c.y + r * kotlin.math.sin(turn).toFloat())
    val path = androidx.compose.ui.graphics.Path()
    for (i in 0 until 6) {
        val middle = i * Math.PI / 3 - Math.PI / 2
        // a tooth narrows a little as it stands out, so it reads as a cog and not a star
        val points = listOf(at(inner, middle - 0.30), at(outer, middle - 0.20), at(outer, middle + 0.20), at(inner, middle + 0.30))
        points.forEachIndexed { n, p -> if (i == 0 && n == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y) }
    }
    path.close()
    drawPath(path, ink, style = androidx.compose.ui.graphics.drawscope.Stroke(line, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    drawCircle(ink, radius = size.minDimension * 0.13f, center = c, style = androidx.compose.ui.graphics.drawscope.Stroke(line))
}

/** The settings, as a cog at the right of a screen's bar. Holding a tab opens them too. */
@Composable
fun SettingsButton(onClick: () -> Unit) {
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    val label = stringResource(Res.string.settings_title)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(Space.touch)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
    ) {
        Canvas(Modifier.size(Space.icon)) { cog(ink) }
    }
}
