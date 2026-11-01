package dev.fourthbreakfast.planner.notes

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import dev.fourthbreakfast.planner.core.BOLD
import dev.fourthbreakfast.planner.core.LineKind
import dev.fourthbreakfast.planner.core.MAX_INDENT
import dev.fourthbreakfast.planner.core.NoteLine
import dev.fourthbreakfast.planner.core.formatBody
import dev.fourthbreakfast.planner.core.plainBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val GUARD = "\u200B"

class NoteEditStateTest {
    private fun state(vararg lines: NoteLine) = NoteEditState("", formatBody(lines.toList()))

    private fun line(text: String, kind: LineKind = LineKind.PLAIN, indent: Int = 0) = NoteLine(kind, indent, false, text)

    private fun NoteEditState.texts() = lines.map { it.text }

    /** Types into a line the way a keyboard does: the whole new text comes back, guard included. */
    private fun NoteEditState.type(index: Int, text: String, cursor: Int = text.length) {
        change(lines[index], TextFieldValue(GUARD + text, TextRange(cursor + 1)))
    }

    /** A backspace at the very start deletes the guard and nothing else. */
    private fun NoteEditState.backspaceAtStart(index: Int) {
        val line = lines[index]
        change(line, TextFieldValue(line.text, TextRange(0)))
    }

    @Test
    fun enterInTheMiddleSplitsTheLineAndPutsTheCursorOnTheNewOne() {
        val s = state(line("helloworld"))
        s.type(0, "hello\nworld", 6)
        assertEquals(listOf("hello", "world"), s.texts())
        assertEquals(s.lines[1].id, s.focusRequest)
        assertEquals(TextRange(1), s.lines[1].value.selection)
    }

    @Test
    fun aNewLineKeepsTheListKindAndDepthButIsNotChecked() {
        val s = state(NoteLine(LineKind.CHECK, 2, true, "milk"))
        s.type(0, "milk\n")
        assertEquals(LineKind.CHECK, s.lines[1].kind)
        assertEquals(2, s.lines[1].indent)
        assertFalse(s.lines[1].checked)
    }

    @Test
    fun enterOnAnEmptyListItemEndsTheList() {
        val s = state(line("", LineKind.BULLET))
        s.type(0, "\n")
        assertEquals(1, s.lines.size)
        assertEquals(LineKind.PLAIN, s.lines[0].kind)
    }

    @Test
    fun enterOnAnEmptyPushedInListItemPullsItOutFirst() {
        val s = state(line("", LineKind.BULLET, 1))
        s.type(0, "\n")
        assertEquals(1, s.lines.size)
        assertEquals(LineKind.BULLET, s.lines[0].kind)
        assertEquals(0, s.lines[0].indent)
    }

    @Test
    fun backspaceTakesOffTheMarkThenTheIndentThenJoinsTheLineAbove() {
        val s = state(line("first"), line("second", LineKind.BULLET, 1))
        s.backspaceAtStart(1)
        assertEquals(LineKind.PLAIN, s.lines[1].kind)
        s.backspaceAtStart(1)
        assertEquals(0, s.lines[1].indent)
        s.backspaceAtStart(1)
        assertEquals(listOf("firstsecond"), s.texts())
        assertEquals(TextRange(6), s.lines[0].value.selection)
    }

    @Test
    fun backspaceAtTheStartOfTheFirstPlainLineDoesNothing() {
        val s = state(line("only"))
        s.backspaceAtStart(0)
        assertEquals(listOf("only"), s.texts())
    }

    @Test
    fun pastingSeveralLinesMakesSeveralLines() {
        val s = state(line(""))
        s.type(0, "a\r\nb\nc")
        assertEquals(listOf("a", "b", "c"), s.texts())
    }

    @Test
    fun theLookMarkersCannotBeTypedIn() {
        val s = state(line(""))
        s.type(0, "a\uE000b")
        assertEquals(listOf("ab"), s.texts())
    }

    @Test
    fun theGuardNeverLetsTheCursorGoInFrontOfIt() {
        val s = state(line("abc"))
        s.change(s.lines[0], TextFieldValue(GUARD + "abc", TextRange(0)))
        assertEquals(TextRange(1), s.lines[0].value.selection)
    }

    @Test
    fun boldWithASelectionChangesTheSelectedLetters() {
        val s = state(line("abcd"))
        s.focusedId = s.lines[0].id
        s.change(s.lines[0], TextFieldValue(GUARD + "abcd", TextRange(2, 4)))
        s.toggle(BOLD)
        assertEquals(listOf(0, BOLD, BOLD, 0), s.lines[0].looks)
        assertTrue(s.isOn(BOLD))
    }

    @Test
    fun boldWithoutASelectionAppliesToWhatIsTypedNext() {
        val s = state(line("a"))
        s.focusedId = s.lines[0].id
        s.toggle(BOLD)
        s.type(0, "ab")
        assertEquals(listOf(0, BOLD), s.lines[0].looks)
    }

    @Test
    fun theListButtonSwitchesAKindOnAndOff() {
        val s = state(line("a"))
        s.focusedId = s.lines[0].id
        s.setKind(LineKind.NUMBER)
        assertEquals(LineKind.NUMBER, s.lines[0].kind)
        s.setKind(LineKind.NUMBER)
        assertEquals(LineKind.PLAIN, s.lines[0].kind)
    }

    @Test
    fun pushingStopsAtBothEnds() {
        val s = state(line("a"))
        s.focusedId = s.lines[0].id
        s.push(-1)
        assertEquals(0, s.lines[0].indent)
        repeat(MAX_INDENT + 3) { s.push(1) }
        assertEquals(MAX_INDENT, s.lines[0].indent)
    }

    @Test
    fun undoGoesBackAndRedoComesForward() {
        val s = state(line("one"))
        assertFalse(s.canUndo)
        s.type(0, "one two")
        assertTrue(s.canUndo)
        s.undo()
        assertEquals(listOf("one"), s.texts())
        assertTrue(s.canRedo)
        s.redo()
        assertEquals(listOf("one two"), s.texts())
        assertFalse(s.canRedo)
    }

    @Test
    fun undoBringsBackTheTitleAndLinesThatWereJoined() {
        val s = NoteEditState("title", plainBody("a\nb"))
        s.title = "changed"
        s.backspaceAtStart(1)
        s.undo()
        assertEquals("title", s.title)
        assertEquals(listOf("a", "b"), s.texts())
    }

    @Test
    fun typingAfterAnUndoDropsWhatCouldBeRedone() {
        val s = state(line("a"))
        s.type(0, "ab")
        s.undo()
        s.type(0, "ac")
        assertFalse(s.canRedo)
        s.commit()
        assertFalse(s.canRedo)
    }

    @Test
    fun whatIsWrittenComesBackOutAsTheBody() {
        val body = formatBody(listOf(NoteLine(LineKind.BULLET, 1, false, "kept", listOf(BOLD, 0, 0, 0))))
        assertEquals(body, NoteEditState("", body).body())
    }
}
