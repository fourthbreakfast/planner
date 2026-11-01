package dev.fourthbreakfast.planner.notes

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
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
import dev.fourthbreakfast.planner.core.restyle
import dev.fourthbreakfast.planner.core.toggleStyle

// every field starts with this invisible character, so that a backspace at the start of a line still has
// something to delete and the editor hears about it
private const val GUARD = '\u200B'
private const val MAX_HISTORY = 100

@Stable
internal class EditLine(val id: Long, line: NoteLine, cursor: Int = line.text.length) {
    var kind by mutableStateOf(line.kind)
    var indent by mutableIntStateOf(line.indent)
    var checked by mutableStateOf(line.checked)
    var looks by mutableStateOf(line.styles)
    var value by mutableStateOf(TextFieldValue(GUARD + line.text, TextRange(cursor + 1)))

    val text: String get() = value.text.substring(1)

    fun toLine() = NoteLine(kind, indent, checked, text, looks)
}

/** Everything a note being written is: its lines, which one the cursor is in, and the look new letters get. */
@Stable
internal class NoteEditState(initialTitle: String, body: String) {
    var title by mutableStateOf(initialTitle)
    private var nextId = 0L
    val lines = mutableStateListOf<EditLine>().apply { parseBody(body).forEach { add(EditLine(nextId++, it)) } }
    var focusedId by mutableStateOf<Long?>(null)
    var focusRequest by mutableStateOf<Long?>(lines.last().id)
    var typing by mutableIntStateOf(0)

    val focused: EditLine? get() = lines.firstOrNull { it.id == focusedId }

    fun body(): String = formatBody(lines.map { it.toLine() })

    // the steps back and forward: a snapshot each time writing pauses, and before every mark or look is changed
    private class Snap(val title: String, val body: String) {
        override fun equals(other: Any?) = other is Snap && other.title == title && other.body == body
        override fun hashCode() = 31 * title.hashCode() + body.hashCode()
    }

    private val history = arrayListOf(Snap(initialTitle, formatBody(lines.map { it.toLine() })))
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
        lines.clear()
        parseBody(snap.body).forEach { lines.add(EditLine(nextId++, it)) }
        typing = 0
        focusRequest = lines.last().id
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

    fun focusLast() {
        val last = lines.last()
        last.value = last.value.copy(selection = TextRange(last.value.text.length))
        focusRequest = last.id
    }

    fun change(line: EditLine, v: TextFieldValue) {
        val at = lines.indexOf(line)
        if (at < 0) return
        val guarded = v.text.startsWith(GUARD)
        val shift = if (guarded) 1 else 0
        val raw = (if (guarded) v.text.substring(1) else v.text)
            .filterNot(::isLookMark)
            .replace("\r\n", "\n")
            .replace('\r', '\n')
        val old = line.text
        if (!guarded && raw == old) {
            backspaceAtStart(at)
            return
        }
        val looks = restyle(old, line.looks, raw, typing)
        if ('\n' in raw) {
            splitLine(at, line, raw, looks, (v.selection.end - shift).coerceIn(0, raw.length))
            return
        }
        line.looks = looks
        if (guarded && raw == v.text.substring(1)) {
            // the keyboard's own composing state stays as it is
            line.value = v.copy(selection = TextRange(maxOf(1, v.selection.start), maxOf(1, v.selection.end)))
        } else {
            line.value = TextFieldValue(
                GUARD + raw,
                TextRange((v.selection.start - shift + 1).coerceIn(1, raw.length + 1), (v.selection.end - shift + 1).coerceIn(1, raw.length + 1)),
            )
        }
        // moving the cursor without typing: new letters take the look of the ones beside it
        if (raw == old && raw.isNotEmpty() && v.selection.collapsed) {
            val before = v.selection.start - 1
            typing = line.looks[if (before > 0) before - 1 else 0]
        }
    }

    private fun splitLine(at: Int, line: EditLine, raw: String, looks: List<Int>, cursor: Int) {
        val parts = raw.split('\n')
        // enter on an empty list item ends the list instead of making another
        if (parts.size == 2 && raw == "\n" && line.text.isEmpty() && line.kind != LineKind.PLAIN) {
            if (line.indent > 0) line.indent-- else line.kind = LineKind.PLAIN
            line.checked = false
            line.value = TextFieldValue(GUARD.toString(), TextRange(1))
            return
        }
        var offset = 0
        val slices = parts.map { part ->
            val slice = looks.subList(offset, offset + part.length)
            offset += part.length + 1
            part to slice
        }
        val lastStart = raw.length - parts.last().length
        line.looks = slices[0].second
        line.value = TextFieldValue(GUARD + parts[0], TextRange(parts[0].length + 1))
        val added = slices.drop(1).mapIndexed { index, (text, slice) ->
            val isLast = index == slices.size - 2
            EditLine(nextId++, NoteLine(line.kind, line.indent, false, text, slice), if (isLast) (cursor - lastStart).coerceIn(0, text.length) else text.length)
        }
        lines.addAll(at + 1, added)
        focusRequest = added.last().id
    }

    /** A backspace at the very start: take off the mark, then the indent, then join the line to the one above. */
    private fun backspaceAtStart(at: Int) {
        val line = lines[at]
        when {
            line.kind != LineKind.PLAIN -> {
                line.kind = LineKind.PLAIN
                line.checked = false
            }
            line.indent > 0 -> line.indent--
            at > 0 -> {
                val above = lines[at - 1]
                val join = above.text.length
                above.value = TextFieldValue(GUARD + above.text + line.text, TextRange(join + 1))
                above.looks = above.looks + line.looks
                lines.removeAt(at)
                focusRequest = above.id
            }
        }
    }

    fun setKind(kind: LineKind) {
        val line = focused ?: return
        commit()
        line.kind = if (line.kind == kind) LineKind.PLAIN else kind
        line.checked = false
    }

    fun push(by: Int) {
        val line = focused ?: return
        commit()
        line.indent = (line.indent + by).coerceIn(0, MAX_INDENT)
    }

    fun toggle(bit: Int) {
        val line = focused ?: return
        commit()
        val selection = line.value.selection
        val from = minOf(selection.start, selection.end) - 1
        val to = maxOf(selection.start, selection.end) - 1
        if (from != to) line.looks = toggleStyle(line.looks, from, to, bit) else typing = typing xor bit
    }

    fun isOn(bit: Int): Boolean {
        val line = focused ?: return typing and bit != 0
        val selection = line.value.selection
        val from = minOf(selection.start, selection.end) - 1
        val to = maxOf(selection.start, selection.end) - 1
        return if (from != to) (from until to).all { line.looks.getOrElse(it) { 0 } and bit != 0 } else typing and bit != 0
    }
}
