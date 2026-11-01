package dev.fourthbreakfast.planner.core

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetLinesTest {
    @Test
    fun theWidgetListsTodaysEventsThenOpenTasksAndCountsWhatIsLeftOut() {
        val events = listOf(
            Event(title = "later", day = 5, minute = 600, createdAt = 1),
            Event(title = "holiday", day = 5, createdAt = 2),
            Event(title = "tomorrow", day = 6, minute = 60, createdAt = 3),
            Event(title = "binned", day = 5, minute = 30, createdAt = 4, deletedAt = 9),
        )
        val tasks = listOf(
            Task(title = "plain", position = 1.0, createdAt = 1),
            Task(title = "urgent", position = 2.0, priority = Priority.HIGH, createdAt = 2),
            Task(title = "done", position = 3.0, done = true, createdAt = 3),
            Task(title = "kid", parentId = "x", position = 1.0, createdAt = 4),
        )
        val all = widgetLines(events, tasks, 5, 10, "all day", { "+$it more" }) { "%02d:%02d".format(it / 60, it % 60) }
        assertEquals(listOf("all day  holiday", "10:00    later", "·        urgent", "·        plain"), all)
        val short = widgetLines(events, tasks, 5, 3, "all day", { "+$it more" }) { "%02d:%02d".format(it / 60, it % 60) }
        assertEquals(listOf("all day  holiday", "10:00    later", "+2 more"), short)
    }
}
