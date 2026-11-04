package dev.fourthbreakfast.planner.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import com.sun.jna.Callback
import com.sun.jna.CallbackReference
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.win32.StdCallLibrary
import java.awt.Window

/**
 * What Windows needs to know about a window drawn without its frame, so that dragging it to an edge snaps it, Win and
 * the arrow keys move it, double clicking the bar maximizes it, and hovering the maximize button offers the snap
 * layouts, all as for any other window. The window keeps the look it has: this only tells Windows where the bar, the
 * edges and the maximize button are.
 *
 * Windows is told by taking over the window's message handling: the window gets a frame in name (which is what makes it
 * snap), the frame is made to take up no room, and the questions Windows asks about where the pointer is are answered
 * from the rectangles the drawing reports. Anything not answered here goes to what handled it before. If any of it
 * fails, [installed] stays false and the app's own dragging and resizing keep working as they did.
 */
class NativeFrame {
    /** Where the bar can be dragged by, in the window's own pixels. */
    @Volatile
    var dragArea: Rect = Rect.Zero

    /** Where the maximize button is, in the window's own pixels. */
    @Volatile
    var maximizeButton: Rect = Rect.Zero

    /** Whether Windows has taken over the frame, so the drawn edges and drag area are not needed. */
    var installed by mutableStateOf(false)
        private set

    /** The pointer is over the maximize button, which Windows tracks as part of the frame, not the window. */
    var maximizeHovered by mutableStateOf(false)
        private set

    private var previous = 0L

    // kept as a field: the native side holds only an address, and a callback collected away would crash the window
    private var handler: Callback? = null

    fun install(window: Window, edgeDp: Int) {
        if (!System.getProperty("os.name").orEmpty().startsWith("Windows") || installed) return
        try {
            val hwnd = Native.getComponentPointer(window) ?: return
            val user32 = User32X.INSTANCE
            val proc = object : WindowProc {
                override fun callback(hwnd: Pointer, message: Int, wParam: Long, lParam: Long): Long = try {
                    handle(user32, hwnd, message, wParam, lParam, edgeDp) ?: user32.CallWindowProcW(Pointer(previous), hwnd, message, wParam, lParam)
                } catch (e: Throwable) {
                    trace("failed: $e")
                    user32.CallWindowProcW(Pointer(previous), hwnd, message, wParam, lParam)
                }
            }
            handler = proc
            previous = user32.SetWindowLongPtrW(hwnd, GWLP_WNDPROC, Pointer.nativeValue(CallbackReference.getFunctionPointer(proc)))
            if (previous == 0L) {
                handler = null
                return
            }
            val style = user32.GetWindowLongPtrW(hwnd, GWL_STYLE)
            user32.SetWindowLongPtrW(hwnd, GWL_STYLE, style or WS_CAPTION or WS_THICKFRAME or WS_MINIMIZEBOX or WS_MAXIMIZEBOX or WS_SYSMENU)
            // the new frame takes effect once Windows is asked to measure the window again
            user32.SetWindowPos(hwnd, null, 0, 0, 0, 0, SWP_NOMOVE or SWP_NOSIZE or SWP_NOZORDER or SWP_NOACTIVATE or SWP_FRAMECHANGED)
            installed = true
        } catch (_: Throwable) {
            installed = false
        }
    }

    private fun trace(line: String) {
        runCatching { java.io.File(System.getProperty("java.io.tmpdir"), "planner-frame.txt").appendText(line + System.lineSeparator()) }
    }

    /** The answer to a message, or null to leave it to what handled it before. */
    private fun handle(user32: User32X, hwnd: Pointer, message: Int, wParam: Long, lParam: Long, edgeDp: Int): Long? {
        when (message) {
            WM_NCCALCSIZE -> {
                // the whole window is the drawing: no room for a frame. a maximized window reaches past the screen by the
                // width of the frame it no longer shows, so that much is taken back
                if (wParam == 0L) return 0L
                if (user32.IsZoomed(hwnd) != 0) {
                    val rect = Pointer(lParam)
                    val inset = user32.GetSystemMetrics(SM_CXSIZEFRAME) + user32.GetSystemMetrics(SM_CXPADDEDBORDER)
                    rect.setInt(0, rect.getInt(0) + inset)
                    rect.setInt(4, rect.getInt(4) + inset)
                    rect.setInt(8, rect.getInt(8) - inset)
                    rect.setInt(12, rect.getInt(12) - inset)
                }
                return 0L
            }

            WM_NCHITTEST -> {
                val box = Box()
                user32.GetWindowRect(hwnd, box)
                val x = (lParam and 0xFFFF).toShort().toInt() - box.left
                val y = ((lParam shr 16) and 0xFFFF).toShort().toInt() - box.top
                return hit(user32, hwnd, x, y, box.right - box.left, box.bottom - box.top, edgeDp).toLong()
            }

            WM_NCMOUSEMOVE -> {
                val over = (wParam and 0xFFFF).toInt() == HTMAXBUTTON
                if (over && !maximizeHovered) {
                    val track = Track()
                    track.size = track.size()
                    track.flags = TME_LEAVE or TME_NONCLIENT
                    track.window = Pointer.nativeValue(hwnd)
                    user32.TrackMouseEvent(track)
                }
                maximizeHovered = over
                return null
            }

            WM_NCMOUSELEAVE -> {
                maximizeHovered = false
                return null
            }

            // the maximize button is done here: left to Windows it would draw a caption button over the window
            WM_NCLBUTTONDOWN -> if ((wParam and 0xFFFF).toInt() == HTMAXBUTTON) return 0L

            WM_NCLBUTTONUP -> if ((wParam and 0xFFFF).toInt() == HTMAXBUTTON) {
                user32.PostMessageW(hwnd, WM_SYSCOMMAND, if (user32.IsZoomed(hwnd) != 0) SC_RESTORE else SC_MAXIMIZE, 0)
                return 0L
            }
        }
        return null
    }

    private fun hit(user32: User32X, hwnd: Pointer, x: Int, y: Int, width: Int, height: Int, edgeDp: Int): Int {
        val maximized = user32.IsZoomed(hwnd) != 0
        if (!maximized) {
            val edge = (edgeDp * user32.GetDpiForWindow(hwnd) / 96.0).toInt().coerceAtLeast(2)
            val left = x < edge
            val right = x >= width - edge
            val top = y < edge
            val bottom = y >= height - edge
            when {
                top && left -> return HTTOPLEFT
                top && right -> return HTTOPRIGHT
                bottom && left -> return HTBOTTOMLEFT
                bottom && right -> return HTBOTTOMRIGHT
                left -> return HTLEFT
                right -> return HTRIGHT
                top -> return HTTOP
                bottom -> return HTBOTTOM
            }
        }
        val maximize = maximizeButton
        if (maximize.contains(androidx.compose.ui.geometry.Offset(x.toFloat(), y.toFloat()))) return HTMAXBUTTON
        if (dragArea.contains(androidx.compose.ui.geometry.Offset(x.toFloat(), y.toFloat()))) return HTCAPTION
        return HTCLIENT
    }

    /** What is handled for a window: its handle, what was asked, and the two numbers that come with it. */
    interface WindowProc : Callback {
        fun callback(hwnd: Pointer, message: Int, wParam: Long, lParam: Long): Long
    }

    class Box : Structure() {
        @JvmField var left = 0

        @JvmField var top = 0

        @JvmField var right = 0

        @JvmField var bottom = 0

        override fun getFieldOrder() = listOf("left", "top", "right", "bottom")
    }

    class Track : Structure() {
        @JvmField var size = 0

        @JvmField var flags = 0

        @JvmField var window = 0L

        @JvmField var hover = 0

        override fun getFieldOrder() = listOf("size", "flags", "window", "hover")
    }

    @Suppress("FunctionName")
    interface User32X : StdCallLibrary {
        fun SetWindowLongPtrW(window: Pointer, index: Int, value: Long): Long

        fun GetWindowLongPtrW(window: Pointer, index: Int): Long

        fun CallWindowProcW(previous: Pointer, window: Pointer, message: Int, wParam: Long, lParam: Long): Long

        fun SetWindowPos(window: Pointer, after: Pointer?, x: Int, y: Int, width: Int, height: Int, flags: Int): Boolean

        fun GetWindowRect(window: Pointer, rect: Box): Boolean

        fun IsZoomed(window: Pointer): Int

        fun GetSystemMetrics(index: Int): Int

        fun GetDpiForWindow(window: Pointer): Int

        fun PostMessageW(window: Pointer, message: Int, wParam: Long, lParam: Long): Boolean

        fun TrackMouseEvent(track: Track): Boolean

        companion object {
            val INSTANCE: User32X by lazy { Native.load("user32", User32X::class.java) }
        }
    }

    private companion object {
        const val GWLP_WNDPROC = -4
        const val GWL_STYLE = -16
        const val WS_CAPTION = 0x00C00000L
        const val WS_THICKFRAME = 0x00040000L
        const val WS_MINIMIZEBOX = 0x00020000L
        const val WS_MAXIMIZEBOX = 0x00010000L
        const val WS_SYSMENU = 0x00080000L
        const val SWP_NOSIZE = 0x0001
        const val SWP_NOMOVE = 0x0002
        const val SWP_NOZORDER = 0x0004
        const val SWP_NOACTIVATE = 0x0010
        const val SWP_FRAMECHANGED = 0x0020
        const val WM_NCCALCSIZE = 0x0083
        const val WM_NCHITTEST = 0x0084
        const val WM_NCMOUSEMOVE = 0x00A0
        const val WM_NCLBUTTONDOWN = 0x00A1
        const val WM_NCLBUTTONUP = 0x00A2
        const val WM_NCMOUSELEAVE = 0x02A2
        const val WM_SYSCOMMAND = 0x0112
        const val SC_MAXIMIZE = 0xF030L
        const val SC_RESTORE = 0xF120L
        const val SM_CXSIZEFRAME = 32
        const val SM_CXPADDEDBORDER = 92
        const val TME_LEAVE = 0x2
        const val TME_NONCLIENT = 0x10
        const val HTCLIENT = 1
        const val HTCAPTION = 2
        const val HTMAXBUTTON = 9
        const val HTLEFT = 10
        const val HTRIGHT = 11
        const val HTTOP = 12
        const val HTTOPLEFT = 13
        const val HTTOPRIGHT = 14
        const val HTBOTTOM = 15
        const val HTBOTTOMLEFT = 16
        const val HTBOTTOMRIGHT = 17
    }
}
