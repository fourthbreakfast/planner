package dev.fourthbreakfast.planner.tasks

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import dev.fourthbreakfast.planner.ui.theme.Space

// the fourteen bars of a letter: top, upper right, lower right, bottom, lower left, upper left,
// the middle in two halves, then the upper centre, the lower centre and the four diagonals
private const val A = 1
private const val B = 2
private const val C = 4
private const val D = 8
private const val E = 16
private const val F = 32
private const val G1 = 64
private const val G2 = 128
private const val I = 256
private const val L = 512
private const val H = 1024
private const val J = 2048
private const val K = 4096
private const val M = 8192

private val ALL_BARS = intArrayOf(A, B, C, D, E, F, G1, G2, I, L, H, J, K, M)

private val GLYPHS: Map<Char, Int> = mapOf(
    'a' to (A or B or C or E or F or G1 or G2),
    'b' to (A or B or C or D or G2 or I or L),
    'c' to (A or D or E or F),
    'd' to (A or B or C or D or I or L),
    'e' to (A or D or E or F or G1 or G2),
    'f' to (A or E or F or G1 or G2),
    'g' to (A or C or D or E or F or G2),
    'h' to (B or C or E or F or G1 or G2),
    'i' to (A or D or I or L),
    'j' to (B or C or D or E),
    'k' to (E or F or G1 or J or M),
    'l' to (D or E or F),
    'm' to (B or C or E or F or H or J),
    'n' to (B or C or E or F or H or M),
    'o' to (A or B or C or D or E or F),
    'p' to (A or B or E or F or G1 or G2),
    'q' to (A or B or C or D or E or F or M),
    'r' to (A or B or E or F or G1 or G2 or M),
    's' to (A or C or D or F or G1 or G2),
    't' to (A or I or L),
    'u' to (B or C or D or E or F),
    'v' to (E or F or K or J),
    'w' to (B or C or E or F or K or M),
    'x' to (H or J or K or M),
    'y' to (H or J or L),
    'z' to (A or D or J or K),
    '0' to (A or B or C or D or E or F),
    '1' to (B or C),
    '2' to (A or B or D or E or G1 or G2),
    '3' to (A or B or C or D or G1 or G2),
    '4' to (B or C or F or G1 or G2),
    '5' to (A or C or D or F or G1 or G2),
    '6' to (A or C or D or E or F or G1 or G2),
    '7' to (A or B or C),
    '8' to (A or B or C or D or E or F or G1 or G2),
    '9' to (A or B or C or D or F or G1 or G2),
    '-' to (G1 or G2),
    '–' to (G1 or G2),
)

private val CELL_W = 10f
private val CELL_H = 18f
private const val BAR = 2.4f
private const val GAP = 0.45f
private const val SLANT = 0.12f
private const val LETTER_GAP = 4f
private const val NARROW = 4f
private const val SPACE = 6f

private fun cellWidth(char: Char) = when (char) {
    ':', '.' -> NARROW
    ' ' -> SPACE
    else -> CELL_W
}

/**
 * Words and numbers drawn like the display of a pocket calculator watch, on fourteen slanted bars a letter,
 * with the unlit bars faintly showing. The same bars and slant as the seven-segment counts. [lit] false draws
 * only the faint bars, an empty display waiting for something. Letters are drawn as capitals, which is all
 * such a display can do.
 */
@Composable
fun SegmentText(text: String, color: Color, modifier: Modifier = Modifier, scale: Float = 1f, lit: Boolean = true) {
    val lower = text.lowercase()
    val units = lower.sumOf { cellWidth(it).toDouble() }.toFloat() + LETTER_GAP * (lower.length - 1).coerceAtLeast(0) + CELL_H * SLANT
    Canvas(
        modifier
            .width(Dp(units * scale))
            .height(Dp(CELL_H * scale))
            .semantics { contentDescription = text },
    ) {
        var left = 0f
        lower.forEach { char ->
            val mask = if (lit) GLYPHS[char] ?: 0 else 0
            when (char) {
                ':' -> drawDots(left, scale, color, lit)
                '.' -> drawDots(left, scale, color, lit, lowOnly = true)
                ' ' -> Unit
                else -> ALL_BARS.forEach { bar ->
                    drawBar(bar, left, scale, color.copy(alpha = if (mask and bar != 0) 1f else 0.13f))
                }
            }
            left += (cellWidth(char) + LETTER_GAP) * Space.digitBar.toPx() / 2.4f * scale
        }
    }
}

private fun DrawScope.drawDots(left: Float, scale: Float, color: Color, lit: Boolean, lowOnly: Boolean = false) {
    val px = Space.digitBar.toPx() / 2.4f * scale
    val radius = BAR * 0.62f * px
    val color2 = color.copy(alpha = if (lit) 1f else 0.13f)
    val x = left + NARROW / 2 * px + (CELL_H - CELL_H * 0.28f) * SLANT * px
    val xHigh = left + NARROW / 2 * px + (CELL_H - CELL_H * 0.28f) * SLANT * px
    if (!lowOnly) drawCircle(color2, radius, Offset(xHigh, CELL_H * 0.3f * px))
    drawCircle(color2, radius, Offset(x + if (lowOnly) 0f else 0f, CELL_H * (if (lowOnly) 0.88f else 0.7f) * px))
}

private fun DrawScope.drawBar(bar: Int, left: Float, scale: Float, color: Color) {
    val px = Space.digitBar.toPx() / 2.4f * scale
    val w = CELL_W * px
    val h = CELL_H * px
    val t = BAR * px
    val gap = GAP * px
    val half = t / 2
    val mid = h / 2
    fun x(px0: Float, py: Float) = left + px0 + (h - py) * SLANT
    val path = Path()
    fun polygon(points: List<Pair<Float, Float>>) {
        points.forEachIndexed { i, (xx, yy) -> if (i == 0) path.moveTo(x(xx, yy), yy) else path.lineTo(x(xx, yy), yy) }
        path.close()
    }
    fun horizontal(x0: Float, x1: Float, yc: Float) = polygon(
        listOf(x0 to yc, x0 + half to yc - half, x1 - half to yc - half, x1 to yc, x1 - half to yc + half, x0 + half to yc + half),
    )
    fun vertical(xc: Float, y0: Float, y1: Float) = polygon(
        listOf(
            xc to y0 + gap,
            xc + half to y0 + half + gap,
            xc + half to y1 - half - gap,
            xc to y1 - gap,
            xc - half to y1 - half - gap,
            xc - half to y0 + half + gap,
        ),
    )
    fun diagonal(x0: Float, y0: Float, x1: Float, y1: Float) =
        drawLine(color, Offset(x(x0, y0), y0), Offset(x(x1, y1), y1), strokeWidth = t * 0.85f, cap = StrokeCap.Butt)

    val centre = w / 2
    val inner = half + gap
    when (bar) {
        A -> horizontal(inner, w - inner, half)
        D -> horizontal(inner, w - inner, h - half)
        G1 -> horizontal(inner, centre - gap * 0.8f, mid)
        G2 -> horizontal(centre + gap * 0.8f, w - inner, mid)
        F -> vertical(half, half, mid + half * 0.2f)
        B -> vertical(w - half, half, mid + half * 0.2f)
        E -> vertical(half, mid - half * 0.2f, h - half)
        C -> vertical(w - half, mid - half * 0.2f, h - half)
        I -> vertical(centre, t, mid - half)
        L -> vertical(centre, mid + half, h - t)
        H -> diagonal(t * 1.2f, t * 1.2f, centre - half * 1.3f, mid - half * 1.3f)
        J -> diagonal(w - t * 1.2f, t * 1.2f, centre + half * 1.3f, mid - half * 1.3f)
        K -> diagonal(t * 1.2f, h - t * 1.2f, centre - half * 1.3f, mid + half * 1.3f)
        M -> diagonal(w - t * 1.2f, h - t * 1.2f, centre + half * 1.3f, mid + half * 1.3f)
    }
    if (bar in intArrayOf(H, J, K, M)) return
    drawPath(path, color)
}
