package dev.fourthbreakfast.planner.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

private class MemoryTaskStore : TaskStore {
    val state = MutableStateFlow<List<Task>>(emptyList())

    override fun observeAll(): Flow<List<Task>> = state

    override suspend fun all() = state.value

    override suspend fun insert(tasks: List<Task>) {
        state.value = state.value + tasks
    }

    override suspend fun update(tasks: List<Task>) {
        val changed = tasks.associateBy { it.id }
        state.value = state.value.map { changed[it.id] ?: it }
    }

    override suspend fun delete(tasks: List<Task>) {
        val gone = tasks.mapTo(HashSet()) { it.id }
        state.value = state.value.filterNot { it.id in gone }
    }

    override suspend fun <T> transaction(block: suspend () -> T): T = block()
}

class BackupsTest {
    private val tasks = MemoryTaskStore()
    private val events = MemoryEventStore()
    private val notes = MemoryNoteStore()
    private val backups = Backups(tasks, events, notes)

    private val groceries = Note(id = "n", title = "shop", body = formatBody(listOf(NoteLine(LineKind.CHECK, 1, true, "milk", listOf(BOLD, 0, 0, 0)))), position = 2.0, createdAt = 4, deletedAt = 8)
    private val parent = Task(id = "p", title = "parent", createdAt = 1)
    private val child = Task(id = "c", title = "child", parentId = "p", priority = Priority.HIGH, deletedAt = 9, createdAt = 2)
    private val dentist = Event(id = "e", title = "dentist", day = 20_000, minute = 570, endMinute = 640, reminder = 10, location = "clinic", notes = "card", createdAt = 3, deletedAt = 7)

    @Test
    fun importedThingsAreExactlyWhatWasExported() = runBlocking {
        tasks.state.value = listOf(parent, child)
        events.state.value = listOf(dentist)
        notes.state.value = listOf(groceries)
        val file = backups.export()
        val freshTasks = MemoryTaskStore()
        val freshEvents = MemoryEventStore()
        val freshNotes = MemoryNoteStore()
        assertEquals(4, Backups(freshTasks, freshEvents, freshNotes).import(file))
        assertEquals(setOf(parent, child), freshTasks.state.value.toSet())
        assertEquals(listOf(dentist), freshEvents.state.value)
        assertEquals(listOf(groceries), freshNotes.state.value)
    }

    @Test
    fun aBackupFromBeforeNotesStillImports() = runBlocking {
        val text = """{"app":"planner","version":2,"tasks":[{"id":"x","title":"old","createdAt":1}],"events":[]}"""
        assertEquals(1, backups.import(text))
        assertEquals(emptyList<Note>(), notes.state.value)
    }

    @Test
    fun importingTwiceAddsNothingAndChangesNothing() = runBlocking {
        tasks.state.value = listOf(parent)
        events.state.value = listOf(dentist)
        val file = backups.export()
        tasks.state.value = listOf(parent.copy(title = "renamed"))
        events.state.value = listOf(dentist.copy(title = "moved"))
        assertEquals(0, backups.import(file))
        assertEquals("renamed", tasks.state.value.single().title)
        assertEquals("moved", events.state.value.single().title)
    }

    @Test
    fun anImportedTaskWhoseParentIsMissingBecomesAMainTask() = runBlocking {
        val text = """{"app":"planner","version":1,"tasks":[{"id":"x","title":"orphan","createdAt":1,"parentId":"gone"}]}"""
        backups.import(text)
        assertEquals(null, tasks.state.value.single().parentId)
    }

    @Test
    fun aBackupFromBeforeTheAgendaStillImports() = runBlocking {
        val text = """{"app":"planner","version":1,"tasks":[{"id":"x","title":"old","createdAt":1}]}"""
        assertEquals(1, backups.import(text))
        assertEquals(emptyList<Event>(), events.state.value)
    }

    @Test
    fun eventsSurviveTheFileWithOddCharacters() {
        val odd = dentist.copy(title = "quotes \" and \\ and\nlines", notes = "ñ 日本 \t tab", minute = null)
        assertEquals(listOf(odd), decodeBackup(encodeBackup(emptyList(), listOf(odd))).events)
    }
}
