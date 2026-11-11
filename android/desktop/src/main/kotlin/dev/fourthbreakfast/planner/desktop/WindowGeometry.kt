package dev.fourthbreakfast.planner.desktop

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import dev.fourthbreakfast.planner.ui.Prefs
import java.awt.GraphicsEnvironment
import java.awt.Toolkit

internal data class WindowGeometry(val size: DpSize, val maximized: Boolean)

/** Keep the restored size separate from maximized/minimized bounds. */
internal class WindowGeometryStore(private val prefs: Prefs) {
    fun restore(available: DpSize): WindowGeometry = WindowGeometry(
        DpSize(
            prefs.getInt("window-width", 1280).coerceIn(minOf(900, available.width.value.toInt()), available.width.value.toInt()).dp,
            prefs.getInt("window-height", 820).coerceIn(minOf(560, available.height.value.toInt()), available.height.value.toInt()).dp,
        ),
        prefs.getBoolean("window-maximized", false),
    )

    fun save(size: DpSize, placement: WindowPlacement, minimized: Boolean) {
        if (minimized) return
        if (placement == WindowPlacement.Floating &&
            size.width.value.isFinite() &&
            size.height.value.isFinite() &&
            size.width.value > 0 &&
            size.height.value > 0
        ) {
            prefs.putInt("window-width", size.width.value.toInt())
            prefs.putInt("window-height", size.height.value.toInt())
        }
        if (placement != WindowPlacement.Fullscreen) prefs.putBoolean("window-maximized", placement == WindowPlacement.Maximized)
    }
}

internal fun availableWindowSize(): DpSize {
    val configuration = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration
    val bounds = configuration.bounds
    val insets = Toolkit.getDefaultToolkit().getScreenInsets(configuration)
    return DpSize((bounds.width - insets.left - insets.right).coerceAtLeast(1).dp, (bounds.height - insets.top - insets.bottom).coerceAtLeast(1).dp)
}
