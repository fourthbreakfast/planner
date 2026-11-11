package dev.fourthbreakfast.planner.desktop

/** Center the navigation between content dividers while reserving the bottom settings row. */
internal fun navigationGroupTop(headerBottom: Float, taskPanelBottom: Float, groupHeight: Float, windowHeight: Float): Float {
    val safeBottom = (windowHeight - 72f - groupHeight).coerceAtLeast(0f)
    val safeTop = headerBottom.coerceIn(0f, safeBottom)
    val visiblePanelBottom = taskPanelBottom.coerceAtMost((windowHeight - 72f).coerceAtLeast(headerBottom))
    val desired = (headerBottom + visiblePanelBottom) / 2f - groupHeight / 2f
    return desired.coerceIn(safeTop, safeBottom)
}
