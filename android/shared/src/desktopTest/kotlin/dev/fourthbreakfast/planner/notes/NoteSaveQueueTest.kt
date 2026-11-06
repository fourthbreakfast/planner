package dev.fourthbreakfast.planner.notes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteSaveQueueTest {
    @Test
    fun undoWhileSavingWritesFinalTextBeforeClose() {
        val writes = mutableListOf<Triple<String, Long, (Long?) -> Unit>>()
        val queue = NoteSaveQueue("" to "A", 1) { _, body, base, done -> writes += Triple(body, base, done) }
        var closed = false

        queue.save("" to "B")
        queue.save("" to "A")
        queue.afterSaving { closed = true }
        assertFalse(closed)
        assertEquals(listOf("B"), writes.map { it.first })

        writes[0].third(2)
        assertEquals(listOf("B", "A"), writes.map { it.first })
        assertEquals(2L, writes[1].second)
        assertFalse(closed)
        writes[1].third(3)
        assertTrue(closed)
        assertEquals("" to "A", queue.saved)
    }

    @Test
    fun editsWhileWritingAreCoalesced() {
        val writes = mutableListOf<Triple<String, Long, (Long?) -> Unit>>()
        val queue = NoteSaveQueue("" to "A", 1) { _, body, base, done -> writes += Triple(body, base, done) }
        queue.save("" to "B")
        queue.save("" to "C")
        queue.save("" to "D")
        writes[0].third(2)
        assertEquals(listOf("B", "D"), writes.map { it.first })
        assertEquals(2L, writes[1].second)
    }

    @Test
    fun rejectedSaveDoesNotBlockClosing() {
        var complete: ((Long?) -> Unit)? = null
        val queue = NoteSaveQueue("" to "A", 1) { _, _, _, done -> complete = done }
        var closed = false
        queue.save("" to "B")
        queue.save("" to "C")
        queue.afterSaving { closed = true }
        complete!!(null)
        assertTrue(closed)
        assertEquals("" to "A", queue.saved)
    }
}
