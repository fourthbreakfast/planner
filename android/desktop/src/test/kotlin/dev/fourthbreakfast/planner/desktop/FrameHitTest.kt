package dev.fourthbreakfast.planner.desktop

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Test

/** What Windows is told is under the pointer, for a window of 1000 by 600 with edges 6 pixels wide. */
class FrameHitTest {
    private val maximize = Rect(860f, 0f, 906f, 40f)
    private val bar = Rect(0f, 0f, 700f, 40f)

    private fun at(x: Int, y: Int, edge: Int = 6) = frameHit(x, y, 1000, 600, edge, maximize, bar)

    @Test
    fun theEdgesAndCornersResize() {
        assertEquals(NativeFrame.HTTOPLEFT, at(0, 0))
        assertEquals(NativeFrame.HTBOTTOMRIGHT, at(999, 599))
        assertEquals(NativeFrame.HTLEFT, at(3, 300))
        assertEquals(NativeFrame.HTRIGHT, at(994, 300))
        assertEquals(NativeFrame.HTTOP, at(300, 5))
        assertEquals(NativeFrame.HTBOTTOM, at(300, 595))
    }

    @Test
    fun theBarDragsAndTheMaximizeButtonIsItsOwn() {
        assertEquals(NativeFrame.HTCAPTION, at(300, 20))
        assertEquals(NativeFrame.HTMAXBUTTON, at(880, 20))
        assertEquals(NativeFrame.HTCLIENT, at(950, 20))
        assertEquals(NativeFrame.HTCLIENT, at(300, 300))
    }

    @Test
    fun aMaximizedWindowHasNoEdges() {
        assertEquals(NativeFrame.HTCAPTION, at(0, 0, edge = 0))
        assertEquals(NativeFrame.HTCLIENT, at(999, 599, edge = 0))
    }
}
