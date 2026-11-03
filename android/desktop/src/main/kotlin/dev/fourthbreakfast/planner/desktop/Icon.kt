package dev.fourthbreakfast.planner.desktop

import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.awt.BasicStroke
import java.awt.Color
import java.awt.RenderingHints
import java.awt.geom.Ellipse2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage

/**
 * The app's icon, drawn as on the phone: the four priority levels as a two by two grid of dots on the dark page,
 * high, medium and low filled and none an outline ring.
 */
fun appIcon(size: Int): BufferedImage {
    val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
    val g = image.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    val s = size.toDouble()
    g.color = Color(0x19, 0x19, 0x19)
    g.fill(RoundRectangle2D.Double(0.0, 0.0, s, s, s * 0.22, s * 0.22))
    val radius = s * 0.13
    val near = s * 0.33
    val far = s * 0.67
    fun dot(cx: Double, cy: Double, color: Color) {
        g.color = color
        g.fill(Ellipse2D.Double(cx - radius, cy - radius, radius * 2, radius * 2))
    }
    dot(near, near, Color(0xd9, 0x3a, 0x3a))
    dot(far, near, Color(0xd9, 0xa3, 0x00))
    dot(near, far, Color(0x44, 0x83, 0x61))
    val line = s * 0.05
    g.color = Color(0x9b, 0x9b, 0x9b)
    g.stroke = BasicStroke(line.toFloat())
    val ring = radius - line / 2
    g.draw(Ellipse2D.Double(far - ring, far - ring, ring * 2, ring * 2))
    g.dispose()
    return image
}

fun appIconPainter(): Painter = BitmapPainter(appIcon(256).toComposeImageBitmap())
