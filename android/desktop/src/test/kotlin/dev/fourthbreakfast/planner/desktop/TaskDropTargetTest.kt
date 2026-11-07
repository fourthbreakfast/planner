package dev.fourthbreakfast.planner.desktop

import dev.fourthbreakfast.planner.tasks.EndRow
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskDropTargetTest {
    @Test
    fun blankSpaceAfterEndRowIsTargetedOnlyInsideTheViewportAtTheActualEnd() {
        assertTrue(isBelowEndRow(200f, EndRow.key, 200, 500, false))
        assertTrue(isBelowEndRow(499f, EndRow.key, 200, 500, false))
        assertFalse(isBelowEndRow(199f, EndRow.key, 200, 500, false))
        assertFalse(isBelowEndRow(500f, EndRow.key, 200, 500, false))
        assertFalse(isBelowEndRow(300f, EndRow.key, 200, 500, true))
        assertFalse(isBelowEndRow(300f, "task", 200, 500, false))
    }
}
