package dev.fourthbreakfast.planner.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.fourthbreakfast.planner.resources.Res
import dev.fourthbreakfast.planner.resources.back
import dev.fourthbreakfast.planner.resources.bin
import dev.fourthbreakfast.planner.resources.bin_with_count
import dev.fourthbreakfast.planner.resources.cancel
import dev.fourthbreakfast.planner.resources.confirm_empty_bin
import dev.fourthbreakfast.planner.resources.delete_forever
import dev.fourthbreakfast.planner.resources.pin
import dev.fourthbreakfast.planner.resources.search
import dev.fourthbreakfast.planner.resources.settings_title
import dev.fourthbreakfast.planner.resources.unpin
import dev.fourthbreakfast.planner.ui.theme.Space
import kotlin.math.cos
import kotlin.math.sin
import org.jetbrains.compose.resources.stringResource

/** A magnifying glass, in the row of controls beside the bin. */
@Composable
fun SearchButton(onClick: () -> Unit) {
    DrawnIconButton(stringResource(Res.string.search), onClick) { ink ->
        val stroke = Space.strokeThin.toPx()
        val w = size.width
        drawCircle(ink, radius = w * 0.34f, center = Offset(w * 0.42f, w * 0.42f), style = Stroke(stroke, cap = StrokeCap.Round))
        drawLine(ink, Offset(w * 0.68f, w * 0.68f), Offset(w * 0.92f, w * 0.92f), strokeWidth = stroke, cap = StrokeCap.Round)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BinButton(binCount: Int, onClick: () -> Unit, modifier: Modifier = Modifier, onEmpty: (() -> Unit)? = null) {
    var asking by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val label = if (binCount > 0) stringResource(Res.string.bin_with_count, binCount) else stringResource(Res.string.bin)
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .heightIn(min = Space.touch)
            .widthIn(min = Space.touch)
            .combinedClickable(
                onClick = onClick,
                onLongClick = if (onEmpty != null && binCount > 0) {
                    {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        asking = true
                    }
                } else {
                    null
                },
            )
            .semantics(mergeDescendants = true) { contentDescription = label },
    ) {
        Canvas(Modifier.size(Space.icon)) { binIcon(ink) }
        if (binCount > 0) {
            Text(
                text = binCount.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = ink,
                modifier = Modifier.padding(start = Space.x1),
            )
        }
    }
    if (asking) {
        AlertDialog(
            onDismissRequest = { asking = false },
            text = { Text(stringResource(Res.string.confirm_empty_bin)) },
            confirmButton = {
                QuietButton(stringResource(Res.string.delete_forever)) {
                    asking = false
                    onEmpty?.invoke()
                }
            },
            dismissButton = { QuietButton(stringResource(Res.string.cancel)) { asking = false } },
        )
    }
}

/** A consistent heading and quiet count across the lists and bins. */
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
    DrawnIconButton(label, onClick) { ink -> plus(ink) }
}

/** An arrow pointing back, in the spot where the bin button is on the main screen. */
@Composable
fun BackButton(onClick: () -> Unit) {
    DrawnIconButton(stringResource(Res.string.back), onClick) { ink ->
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
 * A check box as a small drawing, the same one for tasks and notes: a sharp square outline, and once checked the square
 * is filled with a tick cut out of it.
 */
@Composable
fun CheckBoxMark(checked: Boolean, ink: Color, modifier: Modifier = Modifier.size(Space.check)) {
    val cutout = MaterialTheme.colorScheme.background
    Canvas(modifier) {
        // square and centred, whatever room it is given
        val side = minOf(size.width, size.height, Space.check.toPx())
        val corner = Offset((size.width - side) / 2, (size.height - side) / 2)
        val line = Space.strokeThin.toPx()
        if (checked) {
            drawRect(ink, topLeft = corner, size = Size(side, side))
            val tick = Path().apply {
                moveTo(corner.x + side * 0.24f, corner.y + side * 0.53f)
                lineTo(corner.x + side * 0.43f, corner.y + side * 0.72f)
                lineTo(corner.x + side * 0.77f, corner.y + side * 0.29f)
            }
            drawPath(tick, cutout, style = Stroke(line * 1.6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        } else {
            drawRect(ink, topLeft = corner + Offset(line / 2, line / 2), size = Size(side - line, side - line), style = Stroke(line))
        }
    }
}

/** A push pin, upright: outlined, or filled once the note is pinned. */
fun DrawScope.pinIcon(ink: Color, filled: Boolean) {
    val line = Space.strokeThin.toPx()
    val w = size.width
    val h = size.height
    val head = Path().apply {
        moveTo(w * 0.34f, h * 0.08f)
        lineTo(w * 0.66f, h * 0.08f)
        lineTo(w * 0.62f, h * 0.40f)
        lineTo(w * 0.80f, h * 0.58f)
        lineTo(w * 0.20f, h * 0.58f)
        lineTo(w * 0.38f, h * 0.40f)
        close()
    }
    if (filled) drawPath(head, ink) else drawPath(head, ink, style = Stroke(line, join = StrokeJoin.Round))
    drawLine(ink, Offset(w * 0.5f, h * 0.58f), Offset(w * 0.5f, h * 0.94f), line, StrokeCap.Round)
}

/** The pin, in a touch square: outlined to pin the note, filled while it is pinned. */
@Composable
fun PinButton(pinned: Boolean, onClick: () -> Unit) {
    DrawnIconButton(stringResource(if (pinned) Res.string.unpin else Res.string.pin), onClick) { ink -> pinIcon(ink, pinned) }
}

/** A plain cog for the settings: six blunt teeth on a body with flat sides between them, and a round hole, in one thin line. */
fun DrawScope.cog(ink: Color) {
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

/** The settings, as a cog at the right of a screen's bar. Holding a tab opens them too. */
@Composable
fun SettingsButton(onClick: () -> Unit) {
    DrawnIconButton(stringResource(Res.string.settings_title), onClick) { ink -> cog(ink) }
}

/** A small drawn icon in a touch square, quiet, read out as [label]. The drawing gets the ink to draw in. */
@Composable
fun DrawnIconButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, draw: DrawScope.(ink: Color) -> Unit) {
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(Space.touch)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
    ) {
        Canvas(Modifier.size(Space.icon)) { draw(ink) }
    }
}
