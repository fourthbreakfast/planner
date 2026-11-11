package dev.fourthbreakfast.planner.desktop

import com.sun.jna.Native
import com.sun.jna.WString
import com.sun.jna.win32.StdCallLibrary

/** Must match the installer shortcut's AppUserModelID, and run before AWT creates a window. */
internal object WindowsIdentity {
    const val APP_ID = "fourthbreakfast.planner"
    private val windows get() = System.getProperty("os.name").orEmpty().startsWith("Windows")

    fun install() {
        if (windows) {
            runCatching {
                Native.load("shell32", Shell::class.java).SetCurrentProcessExplicitAppUserModelID(WString(APP_ID))
            }
        }
    }

    /** A user-launched second instance may transfer its foreground permission to the running instance. */
    fun allowForeground(pid: Long) {
        if (windows && pid in 1..Int.MAX_VALUE) {
            runCatching {
                Native.load("user32", Foreground::class.java).AllowSetForegroundWindow(pid.toInt())
            }
        }
    }

    @Suppress("FunctionName")
    interface Shell : StdCallLibrary {
        fun SetCurrentProcessExplicitAppUserModelID(id: WString): Int
    }

    @Suppress("FunctionName")
    interface Foreground : StdCallLibrary {
        fun AllowSetForegroundWindow(pid: Int): Boolean
    }
}
