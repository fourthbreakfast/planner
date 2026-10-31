package dev.fourthbreakfast.planner.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Every spacing and size a screen uses, in dp, on a 4dp grid. Screens take their numbers from here
 * and never write their own, so the design system (docs/design-system/tokens.json) and the app agree.
 */
object Space {
    // the scale
    val x1 = 4.dp
    val x2 = 8.dp
    val x3 = 12.dp
    val x4 = 16.dp
    val x5 = 20.dp
    val x6 = 24.dp

    // roles
    /** Left edge of text on every screen. */
    val gutter = x4

    /** Side padding of rows and bars; the check or control at the right edge fills the rest of its touch slot. */
    val rowPad = x1

    /** How far each level of subtask moves right, dot and text together. */
    val indent = x5

    /** Minimum size of anything tappable. */
    val touch = 48.dp

    /** The invisible row that ends the list: a task and a half. */
    val endRow = 72.dp

    // marks
    val icon = 20.dp
    val check = 18.dp
    val dot = 8.dp
    val dotLane = 40.dp
    val modeDot = 10.dp
    val radiusCheck = 3.dp
    val strokeThin = 1.5.dp
    val strokeThick = 2.dp

    // the seven-segment digits
    val digitWidth = 10.dp
    val digitHeight = 18.dp
    val digitBar = 2.4.dp
    val digitGap = x1
}
