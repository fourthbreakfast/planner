package dev.fourthbreakfast.planner.desktop

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import dev.fourthbreakfast.planner.ui.DesktopPrefs
import java.util.UUID
import java.util.prefs.Preferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowGeometryTest {
    private fun withStore(test: (WindowGeometryStore, DesktopPrefs) -> Unit) {
        val node = "dev/fourthbreakfast/planner-test/${UUID.randomUUID()}"
        try {
            val prefs = DesktopPrefs(node)
            test(WindowGeometryStore(prefs), prefs)
        } finally {
            Preferences.userRoot().node(node).removeNode()
        }
    }

    @Test
    fun maximizingAndMinimizingPreserveTheRestoredSize() = withStore { store, _ ->
        val normal = DpSize(1100.dp, 700.dp)
        store.save(normal, WindowPlacement.Floating, false)
        store.save(DpSize(1920.dp, 1040.dp), WindowPlacement.Maximized, false)
        store.save(DpSize(0.dp, 0.dp), WindowPlacement.Floating, true)
        val restored = store.restore(DpSize(1920.dp, 1040.dp))
        assertEquals(normal, restored.size)
        assertTrue(restored.maximized)
    }

    @Test
    fun changingMonitorsKeepsTheWholeWindowAccessible() = withStore { store, _ ->
        store.save(DpSize(1800.dp, 1000.dp), WindowPlacement.Floating, false)
        assertEquals(DpSize(800.dp, 500.dp), store.restore(DpSize(800.dp, 500.dp)).size)
    }

    @Test
    fun corruptPreferencesCannotCreateAnUnusableWindow() = withStore { store, prefs ->
        prefs.putInt("window-width", -1)
        prefs.putInt("window-height", Int.MAX_VALUE)
        assertEquals(DpSize(900.dp, 900.dp), store.restore(DpSize(1600.dp, 900.dp)).size)
    }
}
