package dev.fourthbreakfast.planner.core

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortlistTest {
    @Test
    fun shortlistIncludesChosenBranchesWithoutReparentingThem() {
        val parent = Task(id = "p", title = "project", createdAt = 1)
        val chosen = Task(id = "c", title = "chosen", createdAt = 2, parentId = "p", starred = true)
        val child = Task(id = "g", title = "step", createdAt = 3, parentId = "c")
        val important = Task(id = "h", title = "important later", createdAt = 4, priority = Priority.HIGH)
        assertEquals(listOf(chosen, child), shortlisted(listOf(parent, chosen, child, important)))
        assertEquals("p", chosen.parentId)
        assertEquals(emptyList<Task>(), shortlisted(listOf(chosen.copy(done = true))))
        assertEquals(emptyList<Task>(), shortlisted(listOf(chosen.copy(deletedAt = 9))))
    }

    @Test
    fun markerSurvivesBackupAndKeepsItsOwnChangeTimestamp() = runBlocking {
        val store = MemoryTaskStore()
        var now = 100L
        val rules = Tasks(store) { now }
        rules.add("do this", starred = true)
        val chosen = store.all().single()
        assertTrue(chosen.starred)
        assertTrue(decodeBackup(encodeBackup(listOf(chosen))).tasks.single().starred)
        now = 200L
        rules.setStarred(chosen.id, false)
        assertFalse(store.all().single().starred)
        assertEquals(200L, store.all().single().updatedAt)
    }
}
