package dev.fourthbreakfast.planner.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupTest {
    private val full = Task(
        id = "a",
        title = "quotes \" slash \\ line\nbreak tab\t ñ 日本 \u0001",
        done = true,
        createdAt = 1_793_466_716_000,
        updatedAt = 1_793_466_716_999,
        parentId = "p",
        priority = Priority.HIGH,
        deletedAt = 1_793_466_800_000,
        position = 2.5,
    )

    @Test
    fun everyFieldSurvivesARoundTrip() {
        val plain = Task(id = "b", title = "plain", createdAt = 5)
        assertEquals(listOf(full, plain), decodeBackup(encodeBackup(listOf(full, plain))).tasks)
    }

    @Test
    fun anEmptyListSurvivesARoundTrip() {
        assertEquals(emptyList<Task>(), decodeBackup(encodeBackup(emptyList())).tasks)
    }

    @Test
    fun anUnknownPriorityBecomesNone() {
        val text = """{"app":"planner","version":1,"tasks":[{"id":"a","title":"t","createdAt":1,"priority":"URGENT"}]}"""
        assertEquals(Priority.NONE, decodeBackup(text).tasks.single().priority)
    }

    @Test
    fun whatIsNotABackupIsRefused() {
        val bad = listOf(
            "",
            "hello",
            "[]",
            """{"app":"other","version":1,"tasks":[]}""",
            """{"app":"planner","version":1}""",
            """{"app":"planner","version":99,"tasks":[]}""",
            """{"app":"planner","version":1,"tasks":[{"title":"no id","createdAt":1}]}""",
            """{"app":"planner","version":1,"tasks":[""",
            """{"app":"planner","version":1,"tasks":[]} trailing""",
        )
        bad.forEach { text -> assertThrows(text, BackupException::class.java) { decodeBackup(text) } }
    }
}
