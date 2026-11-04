package dev.fourthbreakfast.planner.desktop

import java.awt.Point
import java.awt.Rectangle
import org.junit.Assert.assertEquals
import org.junit.Test

/** Where the tray's menu opens, on a screen of 1920 by 1080 with the taskbar along the bottom 48 pixels. */
class MenuCornerTest {
    private val screen = Rectangle(0, 0, 1920, 1032)

    @Test
    fun inTheOpenItOpensAtTheClick() {
        assertEquals(Point(500, 400), menuCorner(Point(500, 400), 188, 200, screen))
    }

    @Test
    fun byTheTrayItOpensUpAndLeftOfTheClick() {
        // a click on the taskbar itself: the menu sits right on top of it
        assertEquals(Point(1900 - 188, 1032 - 200), menuCorner(Point(1900, 1040), 188, 200, screen))
        assertEquals(Point(1900 - 188, 1000 - 200), menuCorner(Point(1900, 1000), 188, 200, screen))
    }

    @Test
    fun itNeverLeavesTheScreen() {
        assertEquals(Point(0, 0), menuCorner(Point(-50, -50), 188, 200, screen))
    }
}
