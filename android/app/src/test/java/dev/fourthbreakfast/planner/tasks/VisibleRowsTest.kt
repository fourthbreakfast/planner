package dev.fourthbreakfast.planner.tasks

import dev.fourthbreakfast.planner.core.Destination
import dev.fourthbreakfast.planner.core.Priority
import dev.fourthbreakfast.planner.core.SortOrder
import dev.fourthbreakfast.planner.core.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VisibleRowsTest {
    private fun task(id: String, parentId: String? = null, done: Boolean = false, position: Double = 0.0) =
        Task(id = id, title = id, parentId = parentId, done = done, position = position, createdAt = 0)

    private val tasks = listOf(
        task("a", position = 1.0),
        task("a1", parentId = "a", position = 1.0),
        task("a1x", parentId = "a1", position = 1.0),
        task("a2", parentId = "a", done = true, position = 2.0),
        task("b", position = 2.0),
    )

    private fun rows(tasks: List<Task>, order: SortOrder = SortOrder.TREE, collapsed: List<String> = emptyList()) =
        arrange(tasks, order, collapsed)

    private fun List<ListRow>.keys() = map { it.key }

    @Test
    fun priorityOnlyRanksAmongSiblings() {
        val ranked = listOf(
            task("low", position = 1.0).copy(priority = Priority.LOW),
            task("low-plain", parentId = "low", position = 1.0),
            task("low-high", parentId = "low", position = 2.0).copy(priority = Priority.HIGH),
            task("medium", position = 2.0).copy(priority = Priority.MEDIUM),
            task("high", position = 3.0).copy(priority = Priority.HIGH),
        )
        assertEquals(
            listOf("high", "medium", "low", "low-high", "low-plain"),
            rows(ranked, SortOrder.PRIORITY).keys(),
        )
    }

    @Test
    fun treeModeKeepsTheOrderTasksWerePlacedIn() {
        val ranked = listOf(
            task("first", position = 1.0),
            task("second", position = 2.0).copy(priority = Priority.HIGH),
        )
        assertEquals(listOf("first", "second"), rows(ranked).keys())
    }

    @Test
    fun childrenSitUnderTheirParentWithDepth() {
        val rows = rows(tasks).filterIsInstance<TaskNode>()
        assertEquals(listOf("a", "a1", "a1x", "a2", "b"), rows.map { it.task.id })
        assertEquals(listOf(0, 1, 2, 1, 0), rows.map { it.depth })
    }

    @Test
    fun collapsedTaskHidesItsChildrenAndCountsThem() {
        val rows = rows(tasks, collapsed = listOf("a")).filterIsInstance<TaskNode>()
        assertEquals(listOf("a", "b"), rows.map { it.task.id })
        assertEquals(2, rows[0].childCount)
        assertEquals(1, rows[0].doneChildCount)
        assertEquals(true, rows[0].collapsed)
    }

    @Test
    fun taskWithoutChildrenIsNeverCollapsed() {
        assertEquals(false, (rows(listOf(task("b")), collapsed = listOf("b")).single() as TaskNode).collapsed)
    }

    @Test
    fun taskWithMissingParentStaysVisibleAtTheTop() {
        val row = rows(listOf(task("x", parentId = "gone"))).single() as TaskNode
        assertEquals("x", row.task.id)
        assertEquals(0, row.depth)
    }

    @Test
    fun droppingOnTheMiddleOfATaskNests() {
        assertEquals(Destination(parentId = "b"), resolveDrop(rows(tasks), tasks, "a2", "b", DropZone.INTO)?.destination)
    }

    @Test
    fun droppingOnTheEdgesOfATaskPlacesItBeside() {
        val rows = rows(tasks)
        assertEquals(Destination(beforeId = "b"), resolveDrop(rows, tasks, "a2", "b", DropZone.BEFORE)?.destination)
        assertEquals(Destination(afterId = "b"), resolveDrop(rows, tasks, "a2", "b", DropZone.AFTER)?.destination)
    }

    @Test
    fun droppingAfterAnOpenParentMakesItTheFirstChild() {
        assertEquals(
            Destination(parentId = "a", beforeId = "a1"),
            resolveDrop(rows(tasks), tasks, "b", "a", DropZone.AFTER)?.destination,
        )
    }

    @Test
    fun droppingOnTheEndRowMakesTheTaskTopLevelAndLast() {
        assertEquals(Destination(), resolveDrop(rows(tasks), tasks, "a1x", EndRow.key, DropZone.INTO)?.destination)
        assertEquals(Destination(), resolveDrop(rows(tasks), tasks, "a", EndRow.key, DropZone.BEFORE)?.destination)
    }

    @Test
    fun belowTheLastSubtaskATopLevelTaskLandsOutsideItsParent() {
        val drop = resolveDrop(rows(tasks), tasks, "b", "a2", DropZone.AFTER)
        assertEquals(Destination(afterId = "a"), drop?.destination)
        assertEquals(0, drop?.depth)
    }

    @Test
    fun movingTheFingerRightKeepsItInsideTheParent() {
        val drop = resolveDrop(rows(tasks), tasks, "b", "a2", DropZone.AFTER, shift = 1)
        assertEquals(Destination(parentId = "a", afterId = "a2"), drop?.destination)
        assertEquals(1, drop?.depth)
    }

    @Test
    fun aSubtaskKeepsItsDepthBelowItsLastSiblingUnlessMovedLeft() {
        val rows = rows(tasks)
        assertEquals(Destination(parentId = "a", afterId = "a1"), resolveDrop(rows, tasks, "a2", "a1x", DropZone.AFTER)?.destination)
        assertEquals(Destination(afterId = "a"), resolveDrop(rows, tasks, "a2", "a1x", DropZone.AFTER, shift = -1)?.destination)
    }

    @Test
    fun theDepthStaysWithinWhatTheNeighboursAllow() {
        val rows = rows(tasks)
        // a task cannot be pulled out of the list or pushed deeper than the row above it
        assertEquals(0, resolveDrop(rows, tasks, "a2", "a1x", DropZone.AFTER, shift = -5)?.depth)
        assertEquals(2, resolveDrop(rows, tasks, "a2", "a1x", DropZone.AFTER, shift = 5)?.depth)
    }

    @Test
    fun nothingCanBeDroppedInsideAFourthLevelTask() {
        val deep = listOf(
            task("l0", position = 1.0), task("l1", parentId = "l0"), task("l2", parentId = "l1"), task("l3", parentId = "l2"),
            task("x", position = 2.0),
        )
        assertNull(resolveDrop(rows(deep), deep, "x", "l3", DropZone.INTO))
        assertEquals(Destination(parentId = "l2", afterId = "l3"), resolveDrop(rows(deep), deep, "x", "l3", DropZone.AFTER, shift = 3)?.destination)
    }

    @Test
    fun aTaskCannotBeDroppedInsideItself() {
        assertNull(resolveDrop(rows(tasks), tasks, "a", "a1x", DropZone.INTO)?.destination)
        assertNull(resolveDrop(rows(tasks), tasks, "a", "a", DropZone.INTO)?.destination)
    }
}
