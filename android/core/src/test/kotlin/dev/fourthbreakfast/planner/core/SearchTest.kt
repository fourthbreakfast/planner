package dev.fourthbreakfast.planner.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTest {
    @Test
    fun aSearchNeedsEveryWordAndIgnoresCase() {
        val words = searchWords("  Milk  EGGS ")
        assertEquals(listOf("milk", "eggs"), words)
        assertTrue(matchesAll("buy eggs and Milk", words))
        assertTrue(!matchesAll("buy eggs", words))
        assertTrue(!matchesAll("anything", emptyList()))
    }

    @Test
    fun aNoteIsSearchedByTitleAndBody() {
        val note = Note(title = "shopping", body = formatBody(listOf(NoteLine(text = "oat milk", styles = List(8) { 0 }))), createdAt = 1)
        assertTrue(matchesAll(noteText(note), searchWords("shop milk")))
        assertTrue(!matchesAll(noteText(note), searchWords("bread")))
    }
}
