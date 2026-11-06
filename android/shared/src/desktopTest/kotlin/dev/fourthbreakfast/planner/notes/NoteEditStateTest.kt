package dev.fourthbreakfast.planner.notes

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import dev.fourthbreakfast.planner.core.BOLD
import dev.fourthbreakfast.planner.core.LineKind
import dev.fourthbreakfast.planner.core.MAX_INDENT
import dev.fourthbreakfast.planner.core.NoteLine
import dev.fourthbreakfast.planner.core.formatBody
import dev.fourthbreakfast.planner.core.plainBody
import dev.fourthbreakfast.planner.ui.theme.PlannerTheme
import dev.fourthbreakfast.planner.ui.theme.Space
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteEditStateTest {
    private fun state(vararg lines: NoteLine) = NoteEditState("", formatBody(lines.toList()))

    private fun line(text: String, kind: LineKind = LineKind.PLAIN, indent: Int = 0) = NoteLine(kind, indent, false, text)

    private fun NoteEditState.texts() = lines().map { it.text }

    /** What a keyboard does: the whole new text comes back, guard included, with the cursor where it ends up. */
    private fun NoteEditState.type(text: String, cursor: Int = text.length) {
        change(TextFieldValue(GUARD + text, TextRange(cursor + 1)))
    }

    private fun NoteEditState.select(from: Int, to: Int) {
        change(TextFieldValue(GUARD + text, TextRange(from + 1, to + 1)))
    }

    /** A backspace at the very start of the note deletes the guard and nothing else. */
    private fun NoteEditState.backspaceAtVeryStart() {
        change(TextFieldValue(text, TextRange(0)))
    }

    @Test
    fun enterMakesATightNewLineInTheSameText() {
        val s = state(line("helloworld"))
        s.type("hello\nworld", 6)
        assertEquals(listOf("hello", "world"), s.texts())
        assertEquals(TextRange(7), s.value.selection)
    }

    @Test
    fun twoEntersLeaveAnEmptyLineBetweenParagraphs() {
        val s = state(line("one"))
        s.type("one\n")
        s.type("one\n\n")
        s.type("one\n\ntwo")
        assertEquals(listOf("one", "", "two"), s.texts())
    }

    @Test
    fun aSelectionAcrossLinesDeletesThemAll() {
        val s = state(line("first"), line("second", LineKind.BULLET), line("third", LineKind.CHECK), line("fourth"))
        s.select(2, 14)
        s.type("fiird\nfourth".replace("fiird", "fi" + "rd"), 2)
        assertEquals(listOf("fird", "fourth"), s.texts())
        assertEquals(listOf(LineKind.PLAIN, LineKind.PLAIN), s.lines().map { it.kind })
    }

    @Test
    fun deletingWholeLinesKeepsTheMarksOfTheLinesAfterThem() {
        val s = state(line("a"), line("b"), line("c", LineKind.CHECK))
        s.type("a\nc", 2)
        assertEquals(listOf("a", "c"), s.texts())
        assertEquals(LineKind.CHECK, s.lines()[1].kind)
    }

    @Test
    fun aNewLineKeepsTheListKindAndDepthButIsNotChecked() {
        val s = state(NoteLine(LineKind.CHECK, 2, true, "milk"))
        s.type("milk\n")
        assertEquals(LineKind.CHECK, s.lines()[1].kind)
        assertEquals(2, s.lines()[1].indent)
        assertFalse(s.lines()[1].checked)
        assertTrue(s.lines()[0].checked)
    }

    @Test
    fun enterOnAnEmptyListItemEndsTheList() {
        val s = state(line("a", LineKind.BULLET), line("", LineKind.BULLET))
        s.type("a\n\n")
        assertEquals(listOf("a", ""), s.texts())
        assertEquals(LineKind.PLAIN, s.lines()[1].kind)
    }

    @Test
    fun enterOnAnEmptyPushedInListItemPullsItOutFirst() {
        val s = state(line("", LineKind.BULLET, 1))
        s.type("\n")
        assertEquals(1, s.lines().size)
        assertEquals(LineKind.BULLET, s.lines()[0].kind)
        assertEquals(0, s.lines()[0].indent)
    }

    @Test
    fun backspaceTakesOffTheMarkThenTheIndentThenJoinsTheLineAbove() {
        val s = state(line("first"), line("second", LineKind.BULLET, 1))
        s.type("first\nsecond", 6)
        s.type("firstsecond", 5)
        assertEquals(LineKind.PLAIN, s.lines()[1].kind)
        assertEquals(TextRange(7), s.value.selection)
        s.type("firstsecond", 5)
        assertEquals(0, s.lines()[1].indent)
        s.type("firstsecond", 5)
        assertEquals(listOf("firstsecond"), s.texts())
    }

    @Test
    fun backspaceAtTheVeryStartTakesOffTheMarkAndOtherwiseDoesNothing() {
        val s = state(line("only", LineKind.BULLET))
        s.backspaceAtVeryStart()
        assertEquals(LineKind.PLAIN, s.lines()[0].kind)
        s.backspaceAtVeryStart()
        assertEquals(listOf("only"), s.texts())
    }

    @Test
    fun pastingSeveralLinesMakesSeveralLines() {
        val s = state(line(""))
        s.type("a\r\nb\nc")
        assertEquals(listOf("a", "b", "c"), s.texts())
    }

    @Test
    fun theLookMarkersCannotBeTypedIn() {
        val s = state(line(""))
        s.type("ab")
        assertEquals(listOf("ab"), s.texts())
    }

    @Test
    fun theGuardNeverLetsTheCursorGoInFrontOfIt() {
        val s = state(line("abc"))
        s.change(TextFieldValue(GUARD + "abc", TextRange(0)))
        assertEquals(TextRange(1), s.value.selection)
    }

    @Test
    fun boldWithASelectionChangesTheSelectedLetters() {
        val s = state(line("abcd"))
        s.select(1, 3)
        s.toggle(BOLD)
        assertEquals(listOf(0, BOLD, BOLD, 0), s.lines()[0].styles)
        assertTrue(s.isOn(BOLD))
    }

    @Test
    fun boldAcrossLinesReadsAsOnAndGoesOffAgain() {
        val s = state(line("ab"), line("cd"))
        s.select(0, 5)
        s.toggle(BOLD)
        assertEquals(listOf(BOLD, BOLD), s.lines()[0].styles)
        assertEquals(listOf(BOLD, BOLD), s.lines()[1].styles)
        assertTrue(s.isOn(BOLD))
        s.toggle(BOLD)
        assertEquals(listOf(0, 0), s.lines()[1].styles)
    }

    @Test
    fun boldWithoutASelectionAppliesToWhatIsTypedNext() {
        val s = state(line("a"))
        s.toggle(BOLD)
        s.type("ab")
        assertEquals(listOf(0, BOLD), s.lines()[0].styles)
    }

    @Test
    fun theListButtonWorksOnEveryLineOfTheSelection() {
        val s = state(line("a"), line("b"), line("c"))
        s.select(0, 3)
        s.setKind(LineKind.NUMBER)
        assertEquals(listOf(LineKind.NUMBER, LineKind.NUMBER, LineKind.PLAIN), s.lines().map { it.kind })
        s.setKind(LineKind.NUMBER)
        assertEquals(LineKind.PLAIN, s.lines()[0].kind)
    }

    @Test
    fun pushingStopsAtBothEnds() {
        val s = state(line("a"))
        s.push(-1)
        assertEquals(0, s.lines()[0].indent)
        repeat(MAX_INDENT + 3) { s.push(1) }
        assertEquals(MAX_INDENT, s.lines()[0].indent)
    }

    @Test
    fun aCheckBoxCanBeTicked() {
        val s = state(line("a"), line("b", LineKind.CHECK))
        s.toggleChecked(1)
        assertTrue(s.lines()[1].checked)
        s.undo()
        assertFalse(s.lines()[1].checked)
    }

    @Test
    fun undoGoesBackAndRedoComesForward() {
        val s = state(line("one"))
        assertFalse(s.canUndo)
        s.type("one two")
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
        s.type("ab", 1)
        s.undo()
        assertEquals("title", s.title)
        assertEquals(listOf("a", "b"), s.texts())
    }

    @Test
    fun typingAfterAnUndoDropsWhatCouldBeRedone() {
        val s = state(line("a"))
        s.type("ab")
        s.undo()
        s.type("ac")
        assertFalse(s.canRedo)
        s.commit()
        assertFalse(s.canRedo)
    }

    @Test
    fun whatIsWrittenComesBackOutAsTheBody() {
        val body = formatBody(listOf(NoteLine(LineKind.BULLET, 1, false, "kept", listOf(BOLD, 0, 0, 0)), NoteLine(text = "next")))
        assertEquals(body, NoteEditState("", body).body())
    }

    @Test
    fun continuedEmptyListLineHasAStyledParagraphAndCaretAfterItsMarker() {
        for (kind in listOf(LineKind.BULLET, LineKind.NUMBER, LineKind.CHECK)) {
            val s = state(line("item", kind, 1))
            s.type("item\n")
            val shown = styledBody(s.value.text, s.marks, s.looks, Color.Gray, Density(1f))
            val caret = s.value.selection.start
            val paragraph = shown.paragraphStyles.single { it.start == caret }
            assertEquals(caret + 1, paragraph.end)
            assertTrue(paragraph.item.textIndent!!.firstLine.value > 0f)
            val offsets = bodyOffsetMapping(s.value.text)
            assertEquals(caret + 1, offsets.originalToTransformed(caret))
            assertEquals(caret, offsets.transformedToOriginal(caret + 1))
            assertEquals(kind, s.lines().last().kind)
        }
    }

    @Test
    fun indentingSelectionPreservesKindsAndExcludesNextLineAtSelectionEnd() {
        val s = state(line("one", LineKind.NUMBER), line("two", LineKind.BULLET), line("three", LineKind.CHECK))
        s.select(0, 8) // Ends immediately after the second line's newline.
        val selection = s.value.selection
        s.push(1)
        assertEquals(listOf(1, 1, 0), s.lines().map { it.indent })
        assertEquals(listOf(LineKind.NUMBER, LineKind.BULLET, LineKind.CHECK), s.lines().map { it.kind })
        assertEquals(selection, s.value.selection)
        s.push(-1)
        assertEquals(listOf(0, 0, 0), s.lines().map { it.indent })
    }

    @Test
    fun continuedEmptyLineCaretIsIndentedInComposeLayout() {
        for (fontScale in listOf(1f, 2f)) {
            for (depth in listOf(0, 1, MAX_INDENT)) {
                for (kind in LineKind.entries) {
                    val s = state(line("item", kind, depth))
                    s.type("item\n")
                    val density = Density(1f, fontScale)
                    val shown = styledBody(s.value.text, s.marks, s.looks, Color.Gray, density)
                    var caretX: Float? = null
                    ImageComposeScene(400, 200, density) {
                        PlannerTheme {
                            BasicText(
                                shown,
                                modifier = Modifier.fillMaxWidth(),
                                style = MaterialTheme.typography.bodyLarge,
                                onTextLayout = {
                                    caretX = it.getCursorRect(bodyOffsetMapping(s.value.text).originalToTransformed(s.value.selection.start)).left
                                },
                            )
                        }
                    }.use { scene -> repeat(3) { scene.render(it * 100_000_000L) } }
                    val expected = Space.indent.value * depth + if (kind == LineKind.PLAIN) 0f else Space.markWidth.value
                    assertEquals("caret for $kind at depth $depth and font scale $fontScale", expected, caretX!!, 0.5f)
                }
            }
        }
    }
}
