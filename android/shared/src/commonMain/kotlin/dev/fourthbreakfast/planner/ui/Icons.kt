package dev.fourthbreakfast.planner.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import dev.fourthbreakfast.planner.ui.theme.Space

// the small drawings the phone and the computer share, each filling the square it is drawn in, in one thin line

/** A plus, for adding. */
fun DrawScope.plus(ink: Color) {
    val line = Space.strokeThin.toPx()
    drawLine(ink, Offset(size.width * 0.5f, size.height * 0.14f), Offset(size.width * 0.5f, size.height * 0.86f), line, StrokeCap.Round)
    drawLine(ink, Offset(size.width * 0.14f, size.height * 0.5f), Offset(size.width * 0.86f, size.height * 0.5f), line, StrokeCap.Round)
}

/** A bin: a body narrowing to its foot, and a lid with a handle. */
fun DrawScope.binIcon(ink: Color) {
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

/** Three lines, a list. */
fun DrawScope.listLines(ink: Color) {
    val line = Space.strokeThin.toPx()
    for (y in listOf(0.28f, 0.5f, 0.72f)) {
        drawLine(ink, Offset(size.width * 0.18f, size.height * y), Offset(size.width * 0.82f, size.height * y), line, StrokeCap.Round)
    }
}

/** A filled triangle that points down to open everything when [opens], and up to close it all. */
fun DrawScope.openAllTriangle(ink: Color, opens: Boolean) {
    val tip = if (opens) 0.66f else 0.34f
    val base = if (opens) 0.36f else 0.64f
    val triangle = Path().apply {
        moveTo(size.width * 0.24f, size.height * base)
        lineTo(size.width * 0.76f, size.height * base)
        lineTo(size.width * 0.5f, size.height * tip)
        close()
    }
    drawPath(triangle, ink)
}
