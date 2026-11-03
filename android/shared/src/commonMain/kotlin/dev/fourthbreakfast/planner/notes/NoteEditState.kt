package dev.fourthbreakfast.planner.notes

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import dev.fourthbreakfast.planner.core.BOLD
import dev.fourthbreakfast.planner.core.ITALIC
import dev.fourthbreakfast.planner.core.LineKind
import dev.fourthbreakfast.planner.core.MAX_INDENT
import dev.fourthbreakfast.planner.core.NoteLine
import dev.fourthbreakfast.planner.core.UNDERLINE
import dev.fourthbreakfast.planner.core.formatBody
import dev.fourthbreakfast.planner.core.isLookMark
import dev.fourthbreakfast.planner.core.parseBody

// the text starts with this invisible character, so that a backspace at the very start still has
// something to delete and the editor hears about it
internal const val GUARD = '​'
private const val MAX_HISTORY = 100

/** What a line is, apart from its words. */
internal data class LineMark(val kind: LineKind = LineKind.PLAIN, val indent: Int = 0, val checked: Boolean = false)

/**
 * A note being written, as one text with a line break between lines, so a selection can run across lines. Beside the
 * text it keeps a [LineMark] for every line and a look for every character. Line breaks have a look too, which is ignored.
 */
@Stable
internal class NoteEditState(initialTitle: String, body: String) {
    var title by mutableStateOf(initialTitle)
    var value by mutableStateOf(TextFieldValue(GUARD.toString()))
        private set
    var marks by mutableStateOf(listOf(LineMark()))
        private set
    var looks by mutableStateOf(emptyList<Int>())
        private set

    /** The look new letters get. */
    var typing by mutableIntStateOf(0)

    /** Asks the screen to put the cursor in the text. */
    var focusRequest by mutableIntStateOf(1)

    val text: String get() = value.text.substring(1)

    init {
        load(body)
    }

    private fun load(body: String) {
        val lines = parseBody(body)
        val joined = lines.joinToString("\n") { it.text }
        marks = lines.map { LineMark(it.kind, it.indent, it.checked) }
        looks = lines.flatMapIndexed { i, line -> if (i == 0) line.styles else listOf(0) + line.styles }
        value = TextFieldValue(GUARD + joined, TextRange(joined.length + 1))
    }

    /** Shows another version of the note, one sync brought, in place of what is here. */
    fun replaceWith(newTitle: String, body: String) {
        title = newTitle
        load(body)
        commit()
    }

    /** The lines as they are saved. */
    fun lines(): List<NoteLine> {
        var start = 0
        return text.split('\n').mapIndexed { i, words ->
            val mark = marks[i]
            val line = NoteLine(mark.kind, mark.indent, mark.checked, words, looks.subList(start, start + words.length))
            start += words.length + 1
            line
        }
    }

    fun body(): String = formatBody(lines())

    // the steps back and forward: a snapshot each time writing pauses, and before every mark or look is changed
    private data class Snap(val title: String, val body: String)

    private val history = arrayListOf(Snap(initialTitle, body()))
    private var at by mutableIntStateOf(0)
    private var count by mutableIntStateOf(1)

    private fun current() = Snap(title, body())

    val canUndo: Boolean get() = at > 0 || current() != history[at]

    val canRedo: Boolean get() = at < count - 1 && current() == history[at]

    fun commit() {
        val now = current()
        if (now == history[at]) return
        while (history.size > at + 1) history.removeAt(history.size - 1)
        history.add(now)
        if (history.size > MAX_HISTORY) history.removeAt(0)
        at = history.size - 1
        count = history.size
    }

    fun undo() {
        commit()
        if (at == 0) return
        at -= 1
        restore(history[at])
    }

    fun redo() {
        if (!canRedo) return
        at += 1
        restore(history[at])
    }

    private fun restore(snap: Snap) {
        title = snap.title
        load(snap.body)
        typing = 0
        focusRequest++
    }

    /** The shortcuts of a keyboard: control with b, i, u, z (and shift z, or y, to go forward). */
    fun handleKey(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown || !event.isCtrlPressed) return false
        when (event.key) {
            Key.B -> toggle(BOLD)
            Key.I -> toggle(ITALIC)
            Key.U -> toggle(UNDERLINE)
            Key.Z -> if (event.isShiftPressed) redo() else undo()
            Key.Y -> redo()
            else -> return false
        }
        return true
    }

    fun focusEnd() {
        value = value.copy(selection = TextRange(value.text.length))
        focusRequest++
    }

    fun focusStart() {
        value = value.copy(selection = TextRange(1))
        focusRequest++
    }

    /** Takes in whatever the keyboard did to the text. */
    fun change(v: TextFieldValue) {
        val guarded = v.text.startsWith(GUARD)
        val shift = if (guarded) 1 else 0
        val typed = if (guarded) v.text.substring(1) else v.text
        val new = typed.filterNot { isLookMark(it) || it == GUARD }.replace("\r\n", "\n").replace('\r', '\n')
        val old = text
        if (!guarded && new == old) {
            takeOffMark(0)
            return
        }
        if (new == old) {
            value = v.copy(selection = v.selection.atLeastOne())
            moved()
            return
        }
        val cursor = (v.selection.end - shift).coerceIn(0, new.length)
        val tail = if (v.selection.collapsed && old.endsWith(new.substring(cursor))) new.length - cursor else commonTail(old, new)
        val head = commonHead(old, new, minOf(old.length, new.length) - tail)
        val removed = old.substring(head, old.length - tail)
        val added = new.substring(head, new.length - tail)
        val line = old.substring(0, head).count { it == '\n' }

        // enter on an empty list item ends the list instead of making another
        if (removed.isEmpty() && added == "\n" && isEmptyLine(old, head) && marks[line].kind != LineKind.PLAIN) {
            val mark = marks[line]
            setMark(line, if (mark.indent > 0) mark.copy(indent = mark.indent - 1, checked = false) else LineMark())
            refresh(old, head)
            return
        }
        // a backspace at the start of a marked line takes off the mark, and only then joins the line to the one above
        if (removed == "\n" && added.isEmpty() && cursor == head) {
            val below = marks[line + 1]
            if (below.kind != LineKind.PLAIN || below.indent > 0) {
                takeOffMark(line + 1)
                return
            }
        }

        val lost = removed.count { it == '\n' }
        val gained = added.count { it == '\n' }
        // deleting from the start of a line leaves the words of the last line touched, so its mark stays with them
        val fromLineStart = added.isEmpty() && (head == 0 || old[head - 1] == '\n')
        val first = if (fromLineStart) marks[line + lost] else marks[line]
        marks = marks.subList(0, line) + first + List(gained) { first.copy(checked = false) } + marks.subList(line + lost + 1, marks.size)
        looks = looks.subList(0, head) + List(added.length) { typing } + looks.subList(old.length - tail, old.length)
        val plain = guarded && new == typed
        value = if (plain) {
            v.copy(selection = v.selection.atLeastOne())
        } else {
            TextFieldValue(GUARD + new, TextRange(cursor + 1))
        }
    }

    private fun TextRange.atLeastOne() = TextRange(maxOf(1, start), maxOf(1, end))

    private fun commonHead(a: String, b: String, most: Int): Int {
        var i = 0
        while (i < most && a[i] == b[i]) i++
        return i
    }

    private fun commonTail(a: String, b: String): Int {
        var i = 0
        while (i < a.length && i < b.length && a[a.length - 1 - i] == b[b.length - 1 - i]) i++
        return i
    }

    private fun isEmptyLine(text: String, at: Int) = (at == 0 || text[at - 1] == '\n') && (at == text.length || text[at] == '\n')

    /** The cursor moved without typing: new letters take the look of the one before it. */
    private fun moved() {
        val selection = value.selection
        if (!selection.collapsed || looks.isEmpty()) return
        val before = selection.start - 2
        val old = text
        typing = when {
            before >= 0 && old[before] != '\n' -> looks[before]
            before + 1 in old.indices && old[before + 1] != '\n' -> looks[before + 1]
            else -> 0
        }
    }

    /** Puts the text back as it was with the cursor at [cursor], when the keyboard's change meant something else. */
    private fun refresh(text: String, cursor: Int) {
        value = TextFieldValue(GUARD + text, TextRange(cursor + 1))
    }

    private fun takeOffMark(line: Int) {
        val mark = marks[line]
        when {
            mark.kind != LineKind.PLAIN -> setMark(line, mark.copy(kind = LineKind.PLAIN, checked = false))
            mark.indent > 0 -> setMark(line, mark.copy(indent = mark.indent - 1))
        }
        refresh(text, lineStart(line))
    }

    private fun setMark(line: Int, mark: LineMark) {
        marks = marks.mapIndexed { i, old -> if (i == line) mark else old }
    }

    private fun lineStart(line: Int): Int {
        var start = 0
        repeat(line) { start = text.indexOf('\n', start) + 1 }
        return start
    }

    /** Which lines the selection touches. */
    private fun selectedLines(): IntRange {
        val selection = value.selection
        val from = (minOf(selection.start, selection.end) - 1).coerceIn(0, text.length)
        val to = (maxOf(selection.start, selection.end) - 1).coerceIn(0, text.length)
        return text.substring(0, from).count { it == '\n' }..text.substring(0, to).count { it == '\n' }
    }

    /** The kind of the line the selection starts in. */
    val kind: LineKind get() = marks[selectedLines().first].kind

    fun setKind(kind: LineKind) {
        commit()
        val lines = selectedLines()
        val allOfIt = lines.all { marks[it].kind == kind }
        marks = marks.mapIndexed { i, mark ->
            if (i in lines) mark.copy(kind = if (allOfIt) LineKind.PLAIN else kind, checked = false) else mark
        }
    }

    fun push(by: Int) {
        commit()
        val lines = selectedLines()
        marks = marks.mapIndexed { i, mark -> if (i in lines) mark.copy(indent = (mark.indent + by).coerceIn(0, MAX_INDENT)) else mark }
    }

    fun toggleChecked(line: Int) {
        commit()
        setMark(line, marks[line].copy(checked = !marks[line].checked))
    }

    /** The letters a selection covers, line breaks left out: they have no look to speak of. */
    private fun selectedLetters(): List<Int> {
        val selection = value.selection
        val from = minOf(selection.start, selection.end) - 1
        val to = maxOf(selection.start, selection.end) - 1
        return (maxOf(from, 0) until to).filter { it < text.length && text[it] != '\n' }
    }

    fun toggle(bit: Int) {
        commit()
        val letters = selectedLetters()
        if (letters.isEmpty()) {
            typing = typing xor bit
            return
        }
        val turnOff = letters.all { looks[it] and bit != 0 }
        val chosen = letters.toSet()
        looks = looks.mapIndexed { i, look ->
            when {
                i !in chosen -> look
                turnOff -> look and bit.inv()
                else -> look or bit
            }
        }
    }

    fun isOn(bit: Int): Boolean {
        val letters = selectedLetters()
        return if (letters.isEmpty()) typing and bit != 0 else letters.all { looks[it] and bit != 0 }
    }
}
