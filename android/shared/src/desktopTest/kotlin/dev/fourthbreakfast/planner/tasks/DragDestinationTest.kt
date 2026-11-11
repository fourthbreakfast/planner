package dev.fourthbreakfast.planner.tasks

import dev.fourthbreakfast.planner.core.Destination
import dev.fourthbreakfast.planner.core.SortOrder
import dev.fourthbreakfast.planner.core.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DragDestinationTest {
    private fun task(id: String, parent: String? = null, position: Double = 0.0) =
        Task(id = id, title = id, parentId = parent, createdAt = 0, position = position)

    private val tasks = listOf(
        task("project", position = 1.0),
        task("child", "project", 1.0),
        task("grandchild", "child", 1.0),
        task("next-child", "project", 2.0),
        task("next-project", position = 2.0),
        task("moving", position = 3.0),
    )
    private val rows = arrange(tasks, SortOrder.PRIORITY, emptySet())

    @Test
    fun theSameBoundaryCanChooseAnInnerSiblingOrAnOuterBranch() {
        assertEquals(
            Drop(Destination("child", afterId = "grandchild"), 2),
            resolveDrop(rows, tasks, "moving", "grandchild", DropZone.AFTER, 2),
        )
        assertEquals(
            Drop(Destination("project", afterId = "child"), 1),
            resolveDrop(rows, tasks, "moving", "grandchild", DropZone.AFTER, 1),
        )
        assertEquals(
            Drop(Destination(afterId = "project"), 0),
            resolveDrop(rows, tasks, "moving", "grandchild", DropZone.AFTER, 0),
        )
    }

    @Test
    fun movingBeforeAnAncestorDoesNotSplitItsSubtasks() {
        val drop = resolveDrop(rows, tasks, "moving", "grandchild", DropZone.BEFORE, 0)!!
        assertEquals(Destination(beforeId = "project"), drop.destination)
        assertEquals(DropMark("project", DropZone.BEFORE, 0), rankedMark(rows, tasks, "moving", drop.destination, drop.depth))
    }

    @Test
    fun movingBelowAnExpandedParentAtItsOwnLevelSkipsTheWholeBranch() {
        val drop = resolveDrop(rows, tasks, "moving", "project", DropZone.AFTER, 0)!!
        assertEquals(Destination(afterId = "project"), drop.destination)
        assertEquals(DropMark("next-project", DropZone.BEFORE, 0), rankedMark(rows, tasks, "moving", drop.destination, drop.depth))
        assertEquals(
            Drop(Destination(parentId = "project"), 1),
            resolveDrop(rows, tasks, "moving", "project", DropZone.AFTER, 1),
        )
    }

    @Test
    fun theEndAreaCanKeepTheChosenBranchLevel() {
        val nestedEnd = tasks.filterNot { it.id == "next-project" }
        val shown = arrange(nestedEnd, SortOrder.PRIORITY, emptySet())
        assertEquals(
            Drop(Destination("project", afterId = "next-child"), 1),
            resolveDrop(shown, nestedEnd, "moving", EndRow.key, DropZone.AFTER, 1),
        )
        assertEquals(Drop(Destination(afterId = "project"), 0), resolveDrop(shown, nestedEnd, "moving", EndRow.key, DropZone.AFTER, 0))
    }

    @Test
    fun aBranchNeverDropsIntoItsOwnDescendantEvenWhenDepthIsRequested() {
        assertNull(resolveDrop(rows, tasks, "project", "grandchild", DropZone.AFTER, 2))
        assertNull(resolveDrop(rows, tasks, "gone", "grandchild", DropZone.AFTER, 0))
    }

    @Test
    fun zoneAndDepthDeadBandsAvoidBoundaryFlicker() {
        assertEquals(DropZone.INTO, stableDropZone(0.26f, DropZone.INTO))
        assertEquals(DropZone.BEFORE, stableDropZone(0.31f, DropZone.BEFORE))
        assertEquals(DropZone.BEFORE, stableDropZone(0.20f, DropZone.INTO))
        assertEquals(DropZone.AFTER, stableDropZone(0.69f, DropZone.AFTER))
        assertEquals(DropZone.INTO, stableDropZone(0.60f, DropZone.AFTER))
        assertEquals(1, dragDepth(1, 11f, 20f, 1))
        assertEquals(2, dragDepth(1, 15f, 20f, 1))
        assertEquals(2, dragDepth(1, 9f, 20f, 2))
        assertEquals(0, dragDepth(1, -30f, 20f, 1))
    }

    @Test
    fun edgeScrollingStartsGentlyAndStopsOutsideTheList() {
        assertEquals(0f, dragScrollSpeed(100f, 600f, 48f, 12f), 0.001f)
        assertEquals(-6f, dragScrollSpeed(24f, 600f, 48f, 12f), 0.001f)
        assertEquals(12f, dragScrollSpeed(600f, 600f, 48f, 12f), 0.001f)
        assertEquals(0f, dragScrollSpeed(610f, 600f, 48f, 12f), 0.001f)
    }
}
