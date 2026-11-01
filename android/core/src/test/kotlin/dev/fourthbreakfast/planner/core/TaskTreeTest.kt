package dev.fourthbreakfast.planner.core

import org.junit.Assert.assertEquals
import org.junit.Test

class TaskTreeTest {
    private fun task(id: String, parentId: String? = null, done: Boolean = false) = Task(id = id, title = id, parentId = parentId, done = done, createdAt = 0)

    private val tasks = listOf(
        task("a"),
        task("a1", parentId = "a"),
        task("a1x", parentId = "a1"),
        task("a2", parentId = "a", done = true),
        task("b"),
    )

    @Test
    fun subtreeIsTheTaskAndEverythingInside() {
        assertEquals(listOf("a", "a1", "a1x", "a2"), subtree(tasks, "a").map { it.id })
        assertEquals(listOf("b"), subtree(tasks, "b").map { it.id })
    }

    @Test
    fun ancestorsGoFromNearestToTop() {
        assertEquals(listOf("a1", "a"), ancestors(tasks, "a1").map { it.id })
        assertEquals(emptyList<Task>(), ancestors(tasks, null))
    }

    private val mixed = listOf(
        task("banana").copy(position = 1.0, priority = Priority.LOW),
        task("Apple").copy(position = 2.0),
        task("cherry").copy(position = 3.0, priority = Priority.HIGH),
        task("avocado", done = true).copy(position = 0.5, priority = Priority.HIGH),
    )

    @Test
    fun treeOrderFollowsPositionWithDoneLast() {
        assertEquals(
            listOf("banana", "Apple", "cherry", "avocado"),
            sorted(mixed, SortOrder.TREE).map { it.id },
        )
    }

    @Test
    fun priorityGoesHighToNoneWithDoneLast() {
        assertEquals(
            listOf("cherry", "banana", "Apple", "avocado"),
            sorted(mixed, SortOrder.PRIORITY).map { it.id },
        )
    }

    @Test
    fun binShowsOneEntryForATaskBinnedWithItsSubtasks() {
        val binned = listOf(
            task("p").copy(deletedAt = 5),
            task("c", parentId = "p").copy(deletedAt = 5),
            task("open"),
        )
        val entry = binEntries(binned).single()
        assertEquals("p", entry.task.id)
        assertEquals(1, entry.insideCount)
    }

    @Test
    fun subtaskBinnedEarlierStaysItsOwnEntryAndOutOfItsParentsBatch() {
        val binned = listOf(
            task("p").copy(deletedAt = 9),
            task("old", parentId = "p").copy(deletedAt = 5),
            task("c", parentId = "p").copy(deletedAt = 9),
        )
        assertEquals(listOf("p", "old"), binEntries(binned).map { it.task.id })
        assertEquals(listOf("p", "c"), binBatch(binned, "p").map { it.id })
    }

    @Test
    fun subtaskBinnedAloneIsAnEntryWhileItsParentStaysOpen() {
        val binned = listOf(task("p"), task("c", parentId = "p").copy(deletedAt = 5))
        assertEquals(listOf("c"), binEntries(binned).map { it.task.id })
    }

    @Test
    fun priorityCyclesThroughEveryLevelAndBack() {
        assertEquals(Priority.LOW, Priority.NONE.next())
        assertEquals(Priority.MEDIUM, Priority.LOW.next())
        assertEquals(Priority.HIGH, Priority.MEDIUM.next())
        assertEquals(Priority.NONE, Priority.HIGH.next())
    }
}
