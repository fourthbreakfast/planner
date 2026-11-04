package dev.fourthbreakfast.planner.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowScope
import androidx.compose.ui.window.WindowState
import dev.fourthbreakfast.planner.resources.Res
import dev.fourthbreakfast.planner.resources.window_close
import dev.fourthbreakfast.planner.resources.window_maximize
import dev.fourthbreakfast.planner.resources.window_minimize
import dev.fourthbreakfast.planner.resources.window_restore
import dev.fourthbreakfast.planner.ui.SegmentText
import dev.fourthbreakfast.planner.ui.theme.Space
import java.awt.Cursor
import java.awt.Frame
import java.awt.Window
import org.jetbrains.compose.resources.stringResource

private val closeRed = Color(0xFFC42B1C)

/**
 * The top of the window, drawn by the app: its mark and name at the left, a place to drag the window by (a double click
 * maximizes), [extras] such as the settings, and the window's own minimize, maximize and close at the right, as Windows 11
 * lays them.
 */
@Composable
fun WindowScope.TitleBar(state: WindowState, frame: NativeFrame, onClose: () -> Unit, extras: @Composable RowScope.() -> Unit = {}) {
    val colors = MaterialTheme.colorScheme
    val icon = remember { appIconPainter() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().height(Desk.titleBar).background(colors.background),
    ) {
        WindowDraggableArea(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .onGloballyPositioned { frame.dragArea = it.boundsInWindow() }
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = { state.toggleMaximized() })
                },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxSize().padding(start = Space.gutter)) {
                Image(icon, contentDescription = null, modifier = Modifier.size(Space.x5))
                SegmentText("planner", colors.onSurfaceVariant, Modifier.padding(start = Space.x3), scale = 0.8f)
            }
        }
        extras()
        WindowButton(stringResource(Res.string.window_minimize), hoverColor = colors.secondaryContainer, onClick = { state.isMinimized = true }) { ink ->
            drawLine(ink, Offset(size.width * 0.28f, size.height * 0.5f), Offset(size.width * 0.72f, size.height * 0.5f), Space.strokeThin.toPx())
        }
        WindowButton(
            stringResource(if (state.placement == WindowPlacement.Maximized) Res.string.window_restore else Res.string.window_maximize),
            hoverColor = colors.secondaryContainer,
            modifier = Modifier.onGloballyPositioned { frame.maximizeButton = it.boundsInWindow() },
            forceHover = frame.maximizeHovered,
            onClick = { state.toggleMaximized() },
        ) { ink ->
            val line = Stroke(Space.strokeThin.toPx())
            if (state.placement == WindowPlacement.Maximized) {
                drawRect(ink, Offset(size.width * 0.28f, size.height * 0.36f), Size(size.width * 0.36f, size.height * 0.36f), style = line)
                drawLine(ink, Offset(size.width * 0.38f, size.height * 0.26f), Offset(size.width * 0.74f, size.height * 0.26f), line.width)
                drawLine(ink, Offset(size.width * 0.74f, size.height * 0.26f), Offset(size.width * 0.74f, size.height * 0.62f), line.width)
            } else {
                drawRect(ink, Offset(size.width * 0.28f, size.height * 0.28f), Size(size.width * 0.44f, size.height * 0.44f), style = line)
            }
        }
        WindowButton(stringResource(Res.string.window_close), hoverColor = closeRed, hoverInk = Color.White, onClick = onClose) { ink ->
            val line = Space.strokeThin.toPx()
            drawLine(ink, Offset(size.width * 0.3f, size.height * 0.3f), Offset(size.width * 0.7f, size.height * 0.7f), line)
            drawLine(ink, Offset(size.width * 0.7f, size.height * 0.3f), Offset(size.width * 0.3f, size.height * 0.7f), line)
        }
    }
}

private fun WindowState.toggleMaximized() {
    placement = if (placement == WindowPlacement.Maximized) WindowPlacement.Floating else WindowPlacement.Maximized
}

@Composable
internal fun WindowButton(
    label: String,
    hoverColor: Color,
    hoverInk: Color? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    forceHover: Boolean = false,
    draw: androidx.compose.ui.graphics.drawscope.DrawScope.(Color) -> Unit,
) {
    val (hover, pointerOver) = rememberHover()
    val hovered = pointerOver || forceHover
    val ink = if (hovered && hoverInk != null) hoverInk else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .width(46.dp)
            .fillMaxHeight()
            .background(if (hovered) hoverColor else Color.Transparent)
            .hoverable(hover)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
    ) {
        Canvas(Modifier.size(Space.x4)) { draw(ink) }
    }
}

/**
 * A window without the system's frame cannot be resized from its edges by itself, so these thin strips along the edges
 * and corners do it, with the cursor Windows shows there.
 */
@Composable
fun BoxScope.ResizeEdges(window: Window) {
    if ((window as? Frame)?.extendedState == Frame.MAXIMIZED_BOTH) return
    val e = Desk.edge
    // which sides an edge moves: left, top, right, bottom
    Edge(window, Cursor.W_RESIZE_CURSOR, Modifier.align(Alignment.CenterStart).width(e).fillMaxHeight(), left = true)
    Edge(window, Cursor.E_RESIZE_CURSOR, Modifier.align(Alignment.CenterEnd).width(e).fillMaxHeight(), right = true)
    Edge(window, Cursor.N_RESIZE_CURSOR, Modifier.align(Alignment.TopCenter).height(e).fillMaxWidth(), top = true)
    Edge(window, Cursor.S_RESIZE_CURSOR, Modifier.align(Alignment.BottomCenter).height(e).fillMaxWidth(), bottom = true)
    Edge(window, Cursor.NW_RESIZE_CURSOR, Modifier.align(Alignment.TopStart).size(e * 2), left = true, top = true)
    Edge(window, Cursor.NE_RESIZE_CURSOR, Modifier.align(Alignment.TopEnd).size(e * 2), right = true, top = true)
    Edge(window, Cursor.SW_RESIZE_CURSOR, Modifier.align(Alignment.BottomStart).size(e * 2), left = true, bottom = true)
    Edge(window, Cursor.SE_RESIZE_CURSOR, Modifier.align(Alignment.BottomEnd).size(e * 2), right = true, bottom = true)
}

@Composable
private fun Edge(
    window: Window,
    cursor: Int,
    modifier: Modifier,
    left: Boolean = false,
    top: Boolean = false,
    right: Boolean = false,
    bottom: Boolean = false,
) {
    Box(
        modifier
            .pointerHoverIcon(PointerIcon(Cursor(cursor)))
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    val mouse = java.awt.MouseInfo.getPointerInfo().location
                    val b = window.bounds
                    val min = window.minimumSize
                    var x = b.x
                    var y = b.y
                    var w = b.width
                    var h = b.height
                    if (right) w = (mouse.x - b.x).coerceAtLeast(min.width)
                    if (bottom) h = (mouse.y - b.y).coerceAtLeast(min.height)
                    if (left) {
                        w = (b.x + b.width - mouse.x).coerceAtLeast(min.width)
                        x = b.x + b.width - w
                    }
                    if (top) {
                        h = (b.y + b.height - mouse.y).coerceAtLeast(min.height)
                        y = b.y + b.height - h
                    }
                    window.setBounds(x, y, w, h)
                }
            },
    )
}
