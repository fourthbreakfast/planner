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

    private fun rows(tasks: List<Task>, order: SortOrder = SortOrder.TREE, collapsed: List<String> = emptyList()) = arrange(tasks, order, collapsed)

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
    fun aLineBetweenTwoRowsPutsTheTaskAboveTheLowerOne() {
        val rows = rows(tasks)
        assertEquals(Destination(beforeId = "b"), resolveDrop(rows, tasks, "a2", "b", DropZone.BEFORE)?.destination)
        // the same line reached from the row above means the same thing
        assertEquals(Destination(beforeId = "b"), resolveDrop(rows, tasks, "a1x", "a2", DropZone.AFTER)?.destination)
        val drop = resolveDrop(rows, tasks, "b", "a1", DropZone.BEFORE)
        assertEquals(Destination(parentId = "a", beforeId = "a1"), drop?.destination)
        assertEquals(1, drop?.depth)
    }

    @Test
    fun theLineUnderAnOpenParentIsItsFirstChild() {
        assertEquals(
            Destination(parentId = "a", beforeId = "a1"),
            resolveDrop(rows(tasks), tasks, "b", "a", DropZone.AFTER)?.destination,
        )
    }

    @Test
    fun theMiddleOfATaskPutsItLastInside() {
        assertEquals(Destination(parentId = "a"), resolveDrop(rows(tasks), tasks, "b", "a", DropZone.INTO)?.destination)
    }

    @Test
    fun belowTheVeryLastRowItBecomesTheLastMainTask() {
        assertEquals(Destination(), resolveDrop(rows(tasks), tasks, "a1", "b", DropZone.AFTER)?.destination)
        assertEquals(Destination(), resolveDrop(rows(tasks), tasks, "a1x", EndRow.key, DropZone.INTO)?.destination)
        assertEquals(Destination(), resolveDrop(rows(tasks), tasks, "a", EndRow.key, DropZone.BEFORE)?.destination)
    }

    @Test
    fun theLineBelowARowSkipsTheTaskBeingMoved() {
        val rows = rows(tasks)
        assertEquals(Destination(beforeId = "b"), resolveDrop(rows, tasks, "a", "b", DropZone.BEFORE)?.destination)
        assertEquals(Destination(parentId = "a", beforeId = "a2"), resolveDrop(rows, tasks, "a1", "a2", DropZone.BEFORE)?.destination)
    }

    @Test
    fun nothingCanBeDroppedInsideAFourthLevelTask() {
        val deep = listOf(
            task("l0", position = 1.0),
            task("l1", parentId = "l0"),
            task("l2", parentId = "l1"),
            task("l3", parentId = "l2"),
            task("x", position = 2.0),
        )
        assertNull(resolveDrop(rows(deep), deep, "x", "l3", DropZone.INTO))
        assertEquals(Destination(parentId = "l2", beforeId = "l3"), resolveDrop(rows(deep), deep, "x", "l3", DropZone.BEFORE)?.destination)
    }

    private val ranked = listOf(
        task("red", position = 1.0).copy(priority = Priority.HIGH),
        task("yellow1", position = 2.0).copy(priority = Priority.MEDIUM),
        task("yellow2", position = 3.0).copy(priority = Priority.MEDIUM),
        task("plain", position = 4.0),
    )

    @Test
    fun aYellowDroppedAboveTheRedsBecomesTheFirstYellow() {
        assertEquals(Destination(beforeId = "yellow1"), rankedDestination(ranked, "yellow2", Destination(beforeId = "red")))
    }

    @Test
    fun aYellowDroppedBelowEverythingBecomesTheLastYellow() {
        assertEquals(Destination(afterId = "yellow2"), rankedDestination(ranked, "yellow1", Destination()))
    }

    @Test
    fun aColourAloneAmongItsNeighboursGoesWhereItWasPut() {
        assertEquals(Destination(beforeId = "yellow1"), rankedDestination(ranked, "red", Destination(beforeId = "yellow1")))
    }

    @Test
    fun droppedInsideAnotherTaskItRanksAmongItsNewNeighbours() {
        val inside = ranked + task("kid", parentId = "plain", position = 1.0).copy(priority = Priority.MEDIUM)
        assertEquals(Destination(parentId = "plain", afterId = "kid"), rankedDestination(inside, "yellow1", Destination(parentId = "plain")))
    }

    @Test
    fun theLineStaysAtTheTopOfTheColourWhenDraggedAboveIt() {
        val rows = rows(ranked, SortOrder.PRIORITY)
        assertEquals(DropMark("yellow1", DropZone.BEFORE, 0), rankedMark(rows, ranked, "yellow2", Destination(beforeId = "red"), 0))
    }

    @Test
    fun aColourWithNoOthersShowsItsLineWhereThatColourWouldSit() {
        val rows = rows(ranked, SortOrder.PRIORITY)
        // plain, dragged to the top, can only go under the yellows, which is where it is the last of everything
        assertEquals(DropMark("yellow2", DropZone.AFTER, 0), rankedMark(rows, ranked, "plain", Destination(beforeId = "red"), 0))
        val green = ranked + task("green", position = 5.0).copy(priority = Priority.LOW)
        assertEquals(DropMark("plain", DropZone.BEFORE, 0), rankedMark(rows(green, SortOrder.PRIORITY), green, "green", Destination(beforeId = "red"), 0))
    }

    @Test
    fun theLineBelowTheLastSiblingClearsWhatIsInsideIt() {
        val nested = listOf(
            task("p", position = 1.0).copy(priority = Priority.HIGH),
            task("kid", parentId = "p", position = 1.0),
            task("q", position = 2.0).copy(priority = Priority.MEDIUM),
            task("r", position = 3.0).copy(priority = Priority.MEDIUM),
        )
        val rows = rows(nested, SortOrder.PRIORITY)
        assertEquals(DropMark("r", DropZone.AFTER, 0), rankedMark(rows, nested, "q", Destination(), 0))
    }

    @Test
    fun aTaskCannotBeDroppedInsideItself() {
        assertNull(resolveDrop(rows(tasks), tasks, "a", "a1x", DropZone.INTO)?.destination)
        assertNull(resolveDrop(rows(tasks), tasks, "a", "a", DropZone.INTO)?.destination)
    }
}
