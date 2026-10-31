package dev.fourthbreakfast.planner.tasks

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import dev.fourthbreakfast.planner.ui.theme.Space
import androidx.compose.ui.unit.dp

// which of the seven bars light up for each digit: top, top right, bottom right, bottom, bottom left, top left, middle
private val DIGITS = listOf(
    "abcdef", "bc", "abdeg", "abcdg", "bcfg", "acdfg", "acdefg", "abc", "abcdefg", "abcdfg",
)

/** A number drawn like the digits of a pocket calculator watch: seven slanted bars per digit, unlit bars faintly visible. */
@Composable
fun SegmentNumber(value: Int, color: Color, modifier: Modifier = Modifier, scale: Float = 1f) {
    val digits = value.coerceAtLeast(0).toString().map { it - '0' }
    Canvas(modifier.width((Space.digitWidth * digits.size + Space.digitGap * (digits.size - 1)) * scale).height(Space.digitHeight * scale)) {
        digits.forEachIndexed { index, digit ->
            val left = (Space.digitWidth + Space.digitGap).toPx() * scale * index
            "abcdefg".forEach { bar ->
                drawBar(bar, left, scale, color.copy(alpha = if (bar in DIGITS[digit]) 1f else 0.13f))
            }
        }
    }
}

private fun DrawScope.drawBar(bar: Char, left: Float, scale: Float, color: Color) {
    val w = Space.digitWidth.toPx() * scale
    val h = Space.digitHeight.toPx() * scale
    val t = Space.digitBar.toPx() * scale
    val gap = 0.45.dp.toPx() * scale
    val slant = 0.12f
    val half = t / 2
    // the bars lean right, the higher the further
    fun x(px: Float, py: Float) = left + px + (h - py) * slant
    val path = Path()
    fun hexagon(points: List<Pair<Float, Float>>) {
        points.forEachIndexed { i, (px, py) ->
            if (i == 0) path.moveTo(x(px, py), py) else path.lineTo(x(px, py), py)
        }
        path.close()
    }
    fun horizontal(yc: Float) = hexagon(
        listOf(
            half + gap to yc,
            t + gap to yc - half,
            w - t - gap to yc - half,
            w - half - gap to yc,
            w - t - gap to yc + half,
            t + gap to yc + half,
        ),
    )
    fun vertical(xc: Float, top: Float, bottom: Float) = hexagon(
        listOf(
            xc to top + gap,
            xc + half to top + half + gap,
            xc + half to bottom - half - gap,
            xc to bottom - gap,
            xc - half to bottom - half - gap,
            xc - half to top + half + gap,
        ),
    )
    when (bar) {
        'a' -> horizontal(half)
        'g' -> horizontal(h / 2)
        'd' -> horizontal(h - half)
        'f' -> vertical(half, half, h / 2 + half * 0.2f)
        'b' -> vertical(w - half, half, h / 2 + half * 0.2f)
        'e' -> vertical(half, h / 2 - half * 0.2f, h - half)
        'c' -> vertical(w - half, h / 2 - half * 0.2f, h - half)
    }
    drawPath(path, color)
}
