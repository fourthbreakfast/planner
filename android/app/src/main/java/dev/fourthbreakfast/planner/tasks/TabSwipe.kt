package dev.fourthbreakfast.planner.tasks

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import dev.fourthbreakfast.planner.ui.theme.Space
import kotlin.math.abs

/**
 * Moves to the other tab when a finger sweeps sideways across empty space: leftwards when [towardsLeft],
 * rightwards otherwise. It only ever goes one way from a tab, so the two tabs never loop. A touch that starts on
 * a row, where a swipe means rename or remove, is left alone, as is anything mostly up or down, which scrolls.
 */
fun Modifier.switchTabBySwiping(towardsLeft: Boolean, onRow: (Offset) -> Boolean, onSwitch: () -> Unit): Modifier =
    pointerInput(towardsLeft) {
        val distance = Space.touch.toPx() * 1.5f
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val onEmpty = !onRow(down.position)
            var dx = 0f
            var dy = 0f
            var switched = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull() ?: break
                if (!change.pressed) break
                if (onEmpty && !switched) {
                    dx += change.positionChange().x
                    dy += change.positionChange().y
                    if (abs(dx) > distance && abs(dx) > 2 * abs(dy) && (dx < 0) == towardsLeft) {
                        switched = true
                        onSwitch()
                    }
                }
            }
        }
    }
