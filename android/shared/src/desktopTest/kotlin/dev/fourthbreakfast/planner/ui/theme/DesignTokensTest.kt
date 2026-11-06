package dev.fourthbreakfast.planner.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import java.io.File
import kotlin.math.pow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The reference palette must describe the shipped app, and readable roles must retain their contrast. */
class DesignTokensTest {
    private val tokens by lazy {
        val path = requireNotNull(System.getProperty("planner.designTokens"))
        Json.parseToJsonElement(File(path).readText()).jsonObject
    }

    private fun colorValue(name: String) = tokens.getValue("color").jsonObject.getValue("tokens").jsonArray
        .map { it.jsonObject }.single { it.getValue("name").jsonPrimitive.content == name }.getValue("value")

    private fun hex(color: Color) = "#%06x".format(color.toArgb() and 0xffffff)

    @Test
    fun referenceColorsMatchTheRuntimeTheme() {
        val roles = mapOf(
            "page" to (PageLight to PageDark),
            "ink" to (InkLight to InkDark),
            "quiet" to (QuietLight to QuietDark),
            "line" to (LineLight to LineDark),
            "panel" to (PanelLight to PanelDark),
            "aim" to (AimLight to AimDark),
            "accent" to (AccentLight to AccentDark),
        )
        for ((name, colors) in roles) {
            val value = colorValue(name) as JsonObject
            assertEquals(name, hex(colors.first), value.getValue("light").jsonPrimitive.content)
            assertEquals(name, hex(colors.second), value.getValue("dark").jsonPrimitive.content)
        }
        for ((name, color) in mapOf("priority-high" to PriorityHigh, "priority-medium" to PriorityMedium, "priority-low" to PriorityLow)) {
            assertEquals(name, hex(color), colorValue(name).jsonPrimitive.content)
        }
    }

    @Test
    fun priorityDiameterAndControlSizesMatchTheReference() {
        val sizes = tokens.getValue("spacing").jsonObject.getValue("tokens").jsonArray.map { it.jsonObject }
            .associate { it.getValue("name").jsonPrimitive.content to it.getValue("value").jsonPrimitive.content.removeSuffix("px").toFloat() }
        val actual = mapOf(
            "touch" to Space.touch.value,
            "check" to Space.check.value,
            "mark-width" to Space.markWidth.value,
            "dot" to Space.dot.value,
            "dot-high" to Space.dotHighRadius.value * 2,
            "dot-medium" to Space.dotMediumRadius.value * 2,
            "dot-low" to Space.dotLowRadius.value * 2,
        )
        for ((name, value) in actual) assertEquals(name, value, requireNotNull(sizes[name]), 0.001f)
        assertTrue("removed check-inset must not return to the reference", "check-inset" !in sizes)
    }

    @Test
    fun readableRolesMeetNormalTextContrastInBothThemes() {
        val pairs = listOf(
            InkLight to PageLight,
            QuietLight to PageLight,
            AccentLight to PageLight,
            InkDark to PageDark,
            QuietDark to PageDark,
            AccentDark to PageDark,
            Color.White to ErrorRed,
        )
        for ((foreground, background) in pairs) {
            val a = luminance(foreground)
            val b = luminance(background)
            val contrast = (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
            assertTrue("${hex(foreground)} on ${hex(background)} has contrast $contrast", contrast >= 4.5)
        }
    }

    private fun luminance(color: Color): Double {
        fun channel(value: Float): Double = value.toDouble().let { if (it <= 0.04045) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
        return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
    }
}
