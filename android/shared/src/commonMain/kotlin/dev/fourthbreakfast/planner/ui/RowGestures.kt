package dev.fourthbreakfast.planner.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.platform.LocalHapticFeedback
import dev.fourthbreakfast.planner.ui.theme.Space
import kotlin.math.abs
import kotlin.math.hypot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// a swipe counts once it has gone this part of the screen's width
private const val SWIPE_FRACTION = 4

/** The key of the list row under a point of the screen, if there is one. */
fun LazyListState.rowKeyAt(at: Offset, listBounds: Rect): Any? = if (listBounds.contains(at)) itemAt(at.y - listBounds.top)?.key else null

/** The row in view [y] pixels down from the top of the list, if there is one. */
fun LazyListState.itemAt(y: Float): LazyListItemInfo? = layoutInfo.visibleItemsInfo.firstOrNull { y >= it.offset && y < it.offset + it.size }

/** How far down the list the row [key] starts, or 0 when it is out of view. */
fun LazyListState.offsetOf(key: Any?): Int = layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }?.offset ?: 0

/** A row being swiped after a hold: which one, and how far it has gone. */
@Stable
class RowSwipe(private val scope: CoroutineScope, private val canGoLeft: Boolean) {
    var key by mutableStateOf<String?>(null)
        private set
    val offset = Animatable(0f)
    private var travel = 0f
    private var width = 0f

    fun start(key: String, width: Float) {
        this.key = key
        this.width = width
        travel = 0f
    }

    fun moveBy(dx: Float) {
        travel += dx
        val x = if (canGoLeft) travel else travel.coerceAtLeast(0f)
        scope.launch { offset.snapTo(x) }
    }

    /** Slides a row open and shut once, to show that rows move. */
    suspend fun peek(key: String, distance: Float) {
        this.key = key
        offset.animateTo(distance, tween(350))
        delay(250)
        offset.animateTo(0f, tween(300))
        this.key = null
    }

    /** Past a quarter of the width it counts: to the right it leaves the screen, to the left it comes back. */
    fun finish(onRight: (String) -> Unit, onLeft: (String) -> Unit) {
        val swiped = key ?: return
        scope.launch {
            val x = offset.value
            if (x > width / SWIPE_FRACTION) {
                offset.animateTo(width)
                onRight(swiped)
                // the row stays out of sight until the list has caught up
                delay(200)
                offset.snapTo(0f)
            } else {
                offset.animateTo(0f)
                if (x < -width / SWIPE_FRACTION) onLeft(swiped)
            }
            // a row swiped meanwhile is its own swipe, and keeps going
            if (key == swiped) key = null
        }
    }
}

/** A row that [swipe] can slide sideways, with what that means showing underneath. */
@Composable
internal fun SwipeRow(
    key: String,
    swipe: RowSwipe,
    removeLabel: String,
    renameLabel: String,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val swiping = swipe.key == key
    Box(modifier) {
        if (swiping) {
            SwipeBackground(swipe.offset.value < 0, removeLabel, renameLabel, Modifier.matchParentSize())
        }
        Box(
            Modifier.graphicsLayer { translationX = if (swiping) swipe.offset.value else 0f },
            content = content,
        )
    }
}

/**
 * The one gesture of a screen. A sideways sweep anywhere moves to the next tab, one step at a time and never
 * around in a loop: [onSweepLeft] and [onSweepRight] are null at the ends. A finger that rests on a row for a
 * moment takes hold of it: from there, up and down moves it ([onDrag], where the row can be moved at all) and
 * sideways swipes it ([swipe]). Everything after the hold belongs to this gesture alone, so the row is not also
 * touched and the list does not scroll.
 */
@Composable
fun Modifier.sweepAndHold(
    enabled: Boolean,
    onSweepLeft: (() -> Unit)?,
    onSweepRight: (() -> Unit)?,
    rowAt: (Offset) -> String?,
    swipe: RowSwipe,
    onSwipedRight: (String) -> Unit,
    onSwipedLeft: (String) -> Unit = {},
    onHold: (String, Offset) -> Unit = { _, _ -> },
    onDrag: ((Float) -> Unit)? = null,
    onRelease: (moved: Boolean) -> Unit = {},
): Modifier {
    val haptics = LocalHapticFeedback.current
    val left by rememberUpdatedState(onSweepLeft)
    val right by rememberUpdatedState(onSweepRight)
    val row by rememberUpdatedState(rowAt)
    val swipedRight by rememberUpdatedState(onSwipedRight)
    val swipedLeft by rememberUpdatedState(onSwipedLeft)
    val hold by rememberUpdatedState(onHold)
    val drag by rememberUpdatedState(onDrag)
    val release by rememberUpdatedState(onRelease)
    return pointerInput(enabled) {
        if (!enabled) return@pointerInput
        val sweepDistance = Space.touch.toPx() * 1.5f
        val slop = viewConfiguration.touchSlop
        val edge = Space.x6.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val heldKey = row(down.position)
            var at = down.position
            var dx = 0f
            var dy = 0f

            // wait on the row: null means the finger rested there long enough, true that it lifted, false that it moved off
            val lifted = if (heldKey == null) {
                false
            } else {
                withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                    var result: Boolean? = null
                    while (result == null) {
                        val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                        if (change == null || !change.pressed) {
                            result = true
                        } else {
                            at = change.position
                            dx += change.positionChangeIgnoreConsumed().x
                            dy += change.positionChangeIgnoreConsumed().y
                            if (hypot(dx, dy) > slop) result = false
                        }
                    }
                    result
                }
            }

            when (lifted) {
                true -> Unit
                null -> {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    hold(heldKey!!, at)
                    var dragging = false
                    var swiping = false
                    var heldDx = 0f
                    var heldDy = 0f
                    while (true) {
                        val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                        val step = change.positionChangeIgnoreConsumed()
                        val pressed = change.pressed
                        change.consume()
                        if (!pressed) break
                        when {
                            swiping -> swipe.moveBy(step.x)
                            dragging -> drag?.invoke(step.y)
                            else -> {
                                heldDx += step.x
                                heldDy += step.y
                                if (hypot(heldDx, heldDy) > slop) {
                                    if (abs(heldDx) > abs(heldDy)) {
                                        swiping = true
                                        release(false)
                                        swipe.start(heldKey, size.width.toFloat())
                                        swipe.moveBy(heldDx)
                                    } else if (drag != null) {
                                        dragging = true
                                        drag?.invoke(heldDy)
                                    }
                                }
                            }
                        }
                    }
                    if (swiping) swipe.finish(swipedRight, swipedLeft) else release(dragging)
                }
                false -> {
                    // not a hold: only a sweep is left to watch for, the list and the rows do the rest.
                    // a sweep that starts at the very edge belongs to the phone's own back gesture
                    val fromEdge = down.position.x < edge || down.position.x > size.width - edge
                    var switched = false
                    while (true) {
                        val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        dx += change.positionChangeIgnoreConsumed().x
                        dy += change.positionChangeIgnoreConsumed().y
                        if (!switched && !fromEdge && abs(dx) > sweepDistance && abs(dx) > 2 * abs(dy)) {
                            switched = true
                            if (dx < 0) left?.invoke() else right?.invoke()
                        }
                    }
                }
            }
        }
    }
}

/** Under a swiping row: swiping right removes it, swiping left renames it. */
@Composable
internal fun SwipeBackground(renaming: Boolean, removeLabel: String, renameLabel: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(
        contentAlignment = if (renaming) Alignment.CenterEnd else Alignment.CenterStart,
        modifier = modifier
            .background(if (renaming) colors.secondaryContainer else colors.error)
            .padding(horizontal = Space.gutter),
    ) {
        Text(
            text = if (renaming) renameLabel else removeLabel,
            style = MaterialTheme.typography.labelLarge,
            color = if (renaming) colors.onSecondaryContainer else colors.onError,
        )
    }
}
