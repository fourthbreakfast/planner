package dev.fourthbreakfast.planner.core

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The rules, run against a store that lives in memory. */
class TasksTest {
    private fun order(parentId: String? = null) = siblings(store.state.value, parentId).map { it.title }

    private suspend fun abc() {
        tasks.add("a")
        tasks.add("b")
        tasks.add("c")
    }

    @Test
    fun newTasksGoLast() = runBlocking {
        abc()
        assertEquals(listOf("a", "b", "c"), order())
    }

    @Test
    fun renameTrimsAndKeepsEverythingElse() = runBlocking {
        abc()
        tasks.cyclePriority(id("a"))
        val before = store.state.value.first { it.title == "a" }
        tasks.rename(before.id, "  new name  ")
        val after = store.state.value.first { it.id == before.id }
        assertEquals("new name", after.title)
        assertEquals(before.priority, after.priority)
        assertEquals(before.position, after.position, 0.0)
    }

    @Test
    fun renameToBlankChangesNothing() = runBlocking {
        abc()
        tasks.rename(id("a"), "   ")
        assertEquals(listOf("a", "b", "c"), order())
    }

    @Test
    fun nothingGoesDeeperThanFourLevels() = runBlocking {
        family()
        tasks.add("great", id("grandchild"))
        tasks.add("toodeep", id("great"))
        assertEquals(listOf("parent", "child", "grandchild", "great"), store.state.value.map { it.title })
    }

    @Test
    fun aTaskCannotMoveSomewhereItsSubtasksWouldBeTooDeep() = runBlocking {
        family()
        tasks.add("other")
        tasks.add("kid", id("other"))
        // other has one level inside it, so it can sit on level 2 at most: under child it would be level 2 with a level 3 kid
        tasks.move(id("other"), Destination(parentId = id("child")))
        assertEquals(id("child"), get("other").parentId)
        tasks.add("deeper")
        tasks.add("deeperkid", id("deeper"))
        tasks.add("deepestkid", id("deeperkid"))
        // deeper has two levels inside it: under grandchild it would reach level 5
        tasks.move(id("deeper"), Destination(parentId = id("grandchild")))
        assertEquals(null, get("deeper").parentId)
    }

    @Test
    fun moveBeforeAndAfterASibling() = runBlocking {
        abc()
        tasks.move(id("c"), Destination(beforeId = id("a")))
        assertEquals(listOf("c", "a", "b"), order())
        tasks.move(id("c"), Destination(afterId = id("a")))
        assertEquals(listOf("a", "c", "b"), order())
    }

    @Test
    fun movingAfterLastYellowKeepsItsPriorityAndOtherTasks() = runBlocking {
        tasks.add("yellow1")
        tasks.add("plain")
        tasks.add("yellow2")
        tasks.add("red")
        tasks.setPriority(id("yellow1"), Priority.MEDIUM)
        tasks.setPriority(id("yellow2"), Priority.MEDIUM)
        tasks.setPriority(id("red"), Priority.HIGH)
        tasks.move(id("yellow1"), Destination(afterId = id("yellow2")))
        assertEquals(listOf("red", "yellow2", "yellow1", "plain"), sorted(store.state.value, SortOrder.PRIORITY).map { it.title })
        assertEquals(Priority.MEDIUM, get("yellow1").priority)
        assertEquals(null, get("yellow1").parentId)
        assertFalse(get("yellow1").done)
    }

    @Test
    fun moveWithoutANeighbourGoesLast() = runBlocking {
        abc()
        tasks.move(id("a"), Destination())
        assertEquals(listOf("b", "c", "a"), order())
    }

    @Test
    fun movingOntoATaskNestsItWithEverythingInside() = runBlocking {
        family()
        tasks.add("other")
        tasks.move(id("child"), Destination(parentId = id("other")))
        assertEquals(id("other"), get("child").parentId)
        assertEquals(id("child"), get("grandchild").parentId)
    }

    @Test
    fun aTaskCannotMoveInsideItself() = runBlocking {
        family()
        tasks.move(id("parent"), Destination(parentId = id("grandchild")))
        assertEquals(null, get("parent").parentId)
    }

    @Test
    fun movingOpenWorkUnderADoneTaskReopensIt() = runBlocking {
        family()
        tasks.add("other")
        tasks.setDone(id("parent"), true)
        tasks.move(id("other"), Destination(parentId = id("child")))
        assertFalse(get("child").done)
        assertFalse(get("parent").done)
    }

    @Test
    fun movingDoneWorkUnderAnOpenTaskChangesNothingAbove() = runBlocking {
        tasks.add("done")
        tasks.add("open")
        tasks.setDone(id("done"), true)
        tasks.move(id("done"), Destination(parentId = id("open")))
        assertFalse(get("open").done)
        assertTrue(get("done").done)
    }

    private val store = MemoryTaskStore()
    private var now = 100L
    private val tasks = Tasks(store) { now++ }

    private fun get(title: String) = store.state.value.first { it.title == title }

    private fun id(title: String) = get(title).id

    /** parent > child > grandchild */
    private suspend fun family() {
        tasks.add("parent")
        tasks.add("child", id("parent"))
        tasks.add("grandchild", id("child"))
    }

    @Test
    fun addTrimsTitlesAndIgnoresBlanks() = runBlocking {
        tasks.add("  milk  ")
        tasks.add("   ")
        assertEquals(listOf("milk"), store.state.value.map { it.title })
    }

    @Test
    fun addingOpenWorkReopensTheTasksAbove() = runBlocking {
        family()
        tasks.setDone(id("parent"), true)
        tasks.add("new", id("grandchild"))
        assertFalse(get("grandchild").done)
        assertFalse(get("child").done)
        assertFalse(get("parent").done)
    }

    @Test
    fun checkingCoversEverythingInside() = runBlocking {
        family()
        tasks.setDone(id("parent"), true)
        assertTrue(listOf("parent", "child", "grandchild").all { get(it).done })
    }

    @Test
    fun checkingALastChildDoesNotCheckTheParent() = runBlocking {
        family()
        tasks.setDone(id("grandchild"), true)
        tasks.setDone(id("child"), true)
        assertFalse(get("parent").done)
    }

    @Test
    fun uncheckingReopensTheTasksAboveButNotInside() = runBlocking {
        family()
        tasks.setDone(id("parent"), true)
        tasks.setDone(id("child"), false)
        assertFalse(get("parent").done)
        assertFalse(get("child").done)
        assertTrue(get("grandchild").done)
    }

    @Test
    fun priorityBelongsToOneTask() = runBlocking {
        family()
        val before = get("parent").updatedAt
        tasks.cyclePriority(id("parent"))
        assertEquals(Priority.LOW, get("parent").priority)
        assertEquals(Priority.NONE, get("child").priority)
        assertTrue(get("parent").updatedAt > before)
    }

    @Test
    fun binningTakesEverythingInsideAndKeepsItIntact() = runBlocking {
        family()
        tasks.setDone(id("grandchild"), true)
        tasks.cyclePriority(id("parent"))
        tasks.moveToBin(id("parent"))
        assertTrue(listOf("parent", "child", "grandchild").all { get(it).inBin })
        assertEquals(1, binEntries(store.state.value).size)

        tasks.restore(id("parent"))
        assertTrue(listOf("parent", "child", "grandchild").none { get(it).inBin })
        assertEquals(Priority.LOW, get("parent").priority)
        assertTrue(get("grandchild").done)
        assertEquals(id("child"), get("grandchild").parentId)
    }

    @Test
    fun aTaskBinnedEarlierStaysBinnedWhenItsParentIsRestored() = runBlocking {
        family()
        tasks.moveToBin(id("grandchild"))
        tasks.moveToBin(id("parent"))
        tasks.restore(id("parent"))
        assertNull(get("parent").deletedAt)
        assertNull(get("child").deletedAt)
        assertNotNull(get("grandchild").deletedAt)
    }

    @Test
    fun checkingLeavesTasksInTheBinAlone() = runBlocking {
        family()
        tasks.moveToBin(id("grandchild"))
        tasks.setDone(id("parent"), true)
        assertFalse(get("grandchild").done)
    }

    @Test
    fun restoringOpenWorkUnderADoneParentReopensIt() = runBlocking {
        family()
        tasks.moveToBin(id("child"))
        tasks.setDone(id("parent"), true)
        tasks.restore(id("child"))
        assertFalse(get("parent").done)
    }

    @Test
    fun deleteForeverRemovesOnlyThatBatch() = runBlocking {
        tasks.add("keep")
        tasks.add("old")
        family()
        tasks.moveToBin(id("old"))
        tasks.moveToBin(id("parent"))
        tasks.deleteForever(id("parent"))
        assertEquals(listOf("keep", "old"), store.state.value.map { it.title })
    }

    @Test
    fun emptyBinLeavesOpenTasks() = runBlocking {
        tasks.add("keep")
        family()
        tasks.moveToBin(id("parent"))
        tasks.emptyBin()
        assertEquals(listOf("keep"), store.state.value.map { it.title })
    }

    @Test
    fun openTasksComeFirstThenOldestFirst() = runBlocking {
        tasks.add("first")
        tasks.add("second")
        tasks.add("third")
        tasks.setDone(id("first"), true)
        assertEquals(listOf("second", "third", "first"), tasks.all.first().map { it.title })
    }
}
