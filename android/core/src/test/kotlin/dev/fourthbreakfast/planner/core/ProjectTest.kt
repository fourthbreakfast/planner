package dev.fourthbreakfast.planner.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectTest {
    private val parent = Task(id = "trip", title = "Plan trip", createdAt = 1)
    private val child = Task(id = "ticket", title = "Book ticket", parentId = parent.id, createdAt = 2)
    private val project = Project(title = "Holiday", taskIds = setOf(parent.id, child.id), createdAt = 1)

    @Test
    fun linkedParentAndChildCountOnlyOnce() {
        assertEquals(ProjectProgress(0, 2), projectProgress(project, listOf(parent, child)))
    }

    @Test
    fun viewsUseTheCurrentTaskNameAndCompletion() {
        val changed = child.copy(title = "Book train", done = true)
        val members = projectTasks(project, listOf(parent, changed))
        assertEquals("Book train", members.single { it.id == child.id }.title)
        assertEquals(ProjectProgress(1, 2), projectProgress(project, members))
        assertEquals(ProjectProgress(0, 2), projectProgress(project, listOf(parent, child)))
    }

    @Test
    fun missingAndBinnedTasksDoNotContributeToProgress() {
        val source = project.copy(taskIds = project.taskIds + "missing")
        assertEquals(ProjectProgress(0, 1), projectProgress(source, listOf(parent, child.copy(deletedAt = 3))))
        assertTrue(projectTasks(project, emptyList()).isEmpty())
    }

    @Test
    fun malformedHierarchyCannotLoopForever() {
        val cycle = listOf(parent.copy(parentId = child.id), child)
        assertEquals(2, projectTasks(project, cycle).size)
    }
}
