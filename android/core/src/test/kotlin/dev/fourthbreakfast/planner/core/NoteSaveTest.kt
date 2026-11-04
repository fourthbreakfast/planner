package dev.fourthbreakfast.planner.core

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteSaveTest {
    private val store = MemoryNoteStore()
    private var now = 100L
    private val notes = Notes(store) { now++ }

    private fun body(text: String) = formatBody(listOf(NoteLine(text = text)))

    @Test
    fun anEditorThatMissedAChangeKeepsTheOtherWordsInTheBin() = runBlocking {
        val id = notes.add(body = body("first"))
        val opened = store.state.value.single().updatedAt
        // sync brings a newer version while the note is open
        store.update(listOf(store.state.value.single().copy(body = body("from the pc"), updatedAt = opened + 50)))
        now = opened + 100
        val saved = notes.update(id, "", body("typed here"), base = opened)
        val live = store.state.value.filterNot { it.inBin }.single()
        assertEquals(saved, live.updatedAt)
        assertTrue(live.body.contains("typed here"))
        assertTrue(store.state.value.single { it.inBin }.body.contains("from the pc"))
    }

    @Test
    fun anEditorThatSawTheLastVersionMakesNoCopy() = runBlocking {
        val id = notes.add()
        val opened = store.state.value.single().updatedAt
        notes.update(id, "title", body("words"), base = opened)
        assertEquals(1, store.state.value.size)
    }
}
