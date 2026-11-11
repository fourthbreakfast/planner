package dev.fourthbreakfast.planner.core

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduledTaskTest {
    @Test
    fun agendaUsesTheOriginalTaskNameAndCompletion() = runBlocking {
        val tasks = MemoryTaskStore()
        val events = MemoryEventStore()
        val taskRules = Tasks(tasks) { 100L }
        val agenda = Events(events, tasks) { 200L }
        taskRules.add("book trip")
        val task = tasks.all().single()
        agenda.scheduleTask(task.id, 20_000, 600, reminder = 10)
        val scheduled = agenda.all.first().single()
        assertEquals(task.id, scheduled.taskId)
        assertEquals(10, scheduled.reminder)
        taskRules.rename(task.id, "book the train")
        assertEquals("book the train", agenda.all.first().single().title)
        agenda.completeTask(scheduled.id)
        assertTrue(tasks.all().single().done)
        assertTrue(agenda.all.first().isEmpty())
        taskRules.setDone(task.id, false)
        assertEquals(scheduled.id, agenda.all.first().single().id)
        assertEquals(task.id, decodeBackup(encodeBackup(tasks.all(), events.all())).events.single().taskId)
    }

    @Test
    fun reschedulingDoesNotCreateCopiesOrChangeTheTask() = runBlocking {
        val tasks = MemoryTaskStore()
        val events = MemoryEventStore()
        val rules = Tasks(tasks) { 100L }
        val agenda = Events(events, tasks) { 200L }
        rules.add("send draft", starred = true)
        val task = tasks.all().single()
        agenda.scheduleTask(task.id, 20_000, null)
        agenda.scheduleTask(task.id, 20_001, 900)
        assertEquals(1, events.all().size)
        assertEquals(20_001L, events.all().single().day)
        assertEquals(listOf(task), tasks.all())
        rules.moveToBin(task.id)
        assertTrue(agenda.all.first().isEmpty())
    }
}
