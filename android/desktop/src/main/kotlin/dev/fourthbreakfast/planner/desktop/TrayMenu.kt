package dev.fourthbreakfast.planner.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberDialogState
import dev.fourthbreakfast.planner.ui.theme.PlannerTheme
import java.awt.GraphicsEnvironment
import java.awt.MouseInfo
import java.awt.Point
import java.awt.SystemTray
import java.awt.Toolkit
import java.awt.TrayIcon
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent

/** What the tray's menu holds: something to choose, or a line between groups. */
sealed interface TrayEntry {
    class Item(val label: String, val onClick: () -> Unit) : TrayEntry

    data object Line : TrayEntry
}

/**
 * The app's icon in the notification area. Windows draws a menu of its own for the icon, grey and square, so a right
 * click is only reported here, with where it happened, and the menu is drawn by [TrayMenu] in the app's own look.
 */
class AppTray(private val onOpen: () -> Unit, private val onMenu: (Point) -> Unit) {
    private var icon: TrayIcon? = null

    fun install(tooltip: String) {
        if (!SystemTray.isSupported() || icon != null) return
        val shown = TrayIcon(appIcon(64), tooltip)
        shown.isImageAutoSize = true
        shown.addActionListener { onOpen() }
        shown.addMouseListener(
            object : MouseAdapter() {
                override fun mouseReleased(e: MouseEvent) {
                    if (e.isPopupTrigger || e.button == MouseEvent.BUTTON3) onMenu(Point(e.xOnScreen, e.yOnScreen))
                }
            },
        )
        SystemTray.getSystemTray().add(shown)
        icon = shown
    }

    fun remove() {
        icon?.let { SystemTray.getSystemTray().remove(it) }
        icon = null
    }

    /** A message from the icon, as the reminders use. */
    fun say(title: String, text: String) {
        icon?.displayMessage(title, text, TrayIcon.MessageType.INFO)
    }
}

private val menuWidth = 156.dp
private val menuPad = 2.dp
private val lineHeight = 7.dp
private val rowHeight = 24.dp

/** The tray's menu, opened beside where the icon was clicked and closed by choosing, Escape, or going elsewhere. */
@Composable
fun TrayMenu(at: Point?, entries: List<TrayEntry>, dark: Boolean, onDismiss: () -> Unit) {
    val state = rememberDialogState(size = DpSize(menuWidth, 100.dp))
    val height = menuPad * 2 + 2.dp + entries.fold(0.dp) { sum, entry -> sum + if (entry is TrayEntry.Item) rowHeight else lineHeight }
    LaunchedEffect(at, height) {
        if (at != null) {
            state.size = DpSize(menuWidth, height)
            state.position = placeMenu(at, menuWidth.value.toInt(), height.value.toInt())
        }
    }
    DialogWindow(
        onCloseRequest = onDismiss,
        state = state,
        visible = at != null,
        title = "planner",
        undecorated = true,
        resizable = false,
        alwaysOnTop = true,
        onPreviewKeyEvent = { event ->
            (event.key == Key.Escape && event.type == KeyEventType.KeyDown).also { if (it) onDismiss() }
        },
    ) {
        DisposableEffect(window) {
            val listener = object : WindowAdapter() {
                override fun windowLostFocus(e: WindowEvent) = onDismiss()
            }
            window.addWindowFocusListener(listener)
            onDispose { window.removeWindowFocusListener(listener) }
        }
        LaunchedEffect(at) {
            if (at != null) {
                window.toFront()
                window.requestFocus()
            }
        }
        PlannerTheme(darkTheme = dark, textScale = Desk.textScale) {
            val colors = MaterialTheme.colorScheme
            Column(
                Modifier
                    .fillMaxSize()
                    .background(colors.surfaceVariant)
                    .border(1.dp, colors.outline)
                    .padding(menuPad),
            ) {
                for (entry in entries) {
                    when (entry) {
                        TrayEntry.Line -> Box(Modifier.height(lineHeight).fillMaxWidth().padding(vertical = 3.dp).background(colors.outline.copy(alpha = 0.5f)))
                        is TrayEntry.Item -> TrayRow(entry.label) {
                            entry.onClick()
                            onDismiss()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrayRow(label: String, onClick: () -> Unit) {
    val (source, hovered) = rememberHover()
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = Modifier
            .fillMaxWidth()
            .height(rowHeight)
            .background(if (hovered) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .hoverable(source)
            .clickable(interactionSource = source, indication = null, onClick = onClick),
    ) {
        Text(
            label,
            // the size of Windows' own menus
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp, lineHeight = 16.sp),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
    }
}

/** Opens the menu's corner at the click, flipped up or left where the screen would cut it off, clear of the taskbar. */
private fun placeMenu(click: Point, width: Int, height: Int): WindowPosition {
    val device = GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices
        .firstOrNull { it.defaultConfiguration.bounds.contains(click) }
        ?: MouseInfo.getPointerInfo().device
    val configuration = device.defaultConfiguration
    val bounds = configuration.bounds
    val insets = Toolkit.getDefaultToolkit().getScreenInsets(configuration)
    val left = bounds.x + insets.left
    val right = bounds.x + bounds.width - insets.right
    val top = bounds.y + insets.top
    val bottom = bounds.y + bounds.height - insets.bottom
    val x = (if (click.x + width > right) click.x - width else click.x).coerceIn(left, maxOf(left, right - width))
    val y = (if (click.y + height > bottom) click.y - height else click.y).coerceIn(top, maxOf(top, bottom - height))
    return WindowPosition(x.dp, y.dp)
}
