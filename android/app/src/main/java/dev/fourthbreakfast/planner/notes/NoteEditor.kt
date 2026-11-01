package dev.fourthbreakfast.planner.notes

import androidx.activity.compose.BackHandler
import dev.fourthbreakfast.planner.tasks.QuietButton
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dev.fourthbreakfast.planner.R
import dev.fourthbreakfast.planner.core.BOLD
import dev.fourthbreakfast.planner.core.ITALIC
import dev.fourthbreakfast.planner.core.LineKind
import dev.fourthbreakfast.planner.core.MAX_INDENT
import dev.fourthbreakfast.planner.core.NoteLine
import dev.fourthbreakfast.planner.core.UNDERLINE
import dev.fourthbreakfast.planner.core.formatBody
import dev.fourthbreakfast.planner.core.numberLabels
import dev.fourthbreakfast.planner.core.parseBody
import dev.fourthbreakfast.planner.core.restyle
import dev.fourthbreakfast.planner.core.toggleStyle
import dev.fourthbreakfast.planner.tasks.BackButton
import dev.fourthbreakfast.planner.tasks.SegmentTab
import dev.fourthbreakfast.planner.ui.theme.Space
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

// every field starts with this invisible character, so that a backspace at the start of a line still has
// something to delete and the editor hears about it
private const val GUARD = '​'
private const val AUTOSAVE_MILLIS = 400L
private const val HISTORY_MILLIS = 700L
private const val MAX_HISTORY = 100

@Stable
private class EditLine(val id: Long, line: NoteLine, cursor: Int = line.text.length) {
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
private class NoteEditState(initialTitle: String, body: String) {
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
            .filterNot { it in ''..'' }
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

/** Writing one note: a row of marks at the top, then lines of text, each of which can be a list item. */
@Composable
fun NoteEditor(initialTitle: String, initialBody: String, pinned: Boolean, onPin: () -> Unit, toolbarAbove: Boolean, onSave: (String, String) -> Unit, onClose: (String, String) -> Unit) {
    val state = remember { NoteEditState(initialTitle, initialBody) }
    BackHandler { onClose(state.title, state.body()) }
    LaunchedEffect(state) {
        var first = true
        snapshotFlow { state.title to state.body() }.collectLatest { (title, body) ->
            if (first) {
                first = false
                return@collectLatest
            }
            delay(AUTOSAVE_MILLIS)
            onSave(title, body)
        }
    }
    LaunchedEffect(state) {
        snapshotFlow { state.title to state.body() }.collectLatest {
            delay(HISTORY_MILLIS)
            state.commit()
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { onSave(state.title, state.body()) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(start = Space.gutter, end = Space.rowPad),
                ) {
                    SegmentTab(stringResource(R.string.note_title), Modifier.weight(1f))
                    QuietButton(stringResource(if (pinned) R.string.unpin else R.string.pin), onPin)
                    QuietButton(stringResource(R.string.undo), state.canUndo) { state.undo() }
                    QuietButton(stringResource(R.string.redo), state.canRedo) { state.redo() }
                    BackButton { onClose(state.title, state.body()) }
                }
                if (!toolbarAbove) Toolbar(state)
            }
        },
        bottomBar = {
            if (toolbarAbove) {
                Box(Modifier.navigationBarsPadding().imePadding()) { Toolbar(state) }
            }
        },
    ) { padding ->
        val numbers = numberLabels(state.lines.map { it.toLine() })
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .then(if (toolbarAbove) Modifier else Modifier.imePadding())
                .onPreviewKeyEvent { state.handleKey(it) }
                .verticalScroll(rememberScrollState()),
        ) {
            TitleField(state)
            state.lines.forEachIndexed { index, line ->
                androidx.compose.runtime.key(line.id) { LineRow(line, numbers.getOrNull(index), state) }
            }
            // a touch below the last line puts the cursor there
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(Space.listEnd())
                    .clickable(interactionSource = null, indication = null) { state.focusLast() },
            )
        }
    }
}

@Composable
private fun LineRow(line: EditLine, number: Int?, state: NoteEditState) {
    val colors = MaterialTheme.colorScheme
    val focus = remember { FocusRequester() }
    val ink = if (line.checked) colors.onSurfaceVariant else colors.onBackground
    val looks = line.looks
    val checkedInk = line.checked
    val transformation = remember(looks, checkedInk, ink) {
        VisualTransformation { text ->
            val body = text.text.drop(1)
            val shown = buildAnnotatedString {
                append(text.text.take(1))
                if (looks.size == body.length) appendStyled(body, looks) else append(body)
                if (checkedInk) addStyle(SpanStyle(color = ink), 0, length)
            }
            TransformedText(shown, OffsetMapping.Identity)
        }
    }
    LaunchedEffect(state.focusRequest) {
        if (state.focusRequest == line.id) {
            focus.requestFocus()
            state.focusRequest = null
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Space.gutter + Space.indent * line.indent, end = Space.gutter),
    ) {
        when (line.kind) {
            LineKind.PLAIN -> Unit
            // the box is a full touch square, though the mark in it is small
            LineKind.CHECK -> Box(
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier
                    .sizeIn(minWidth = Space.touch, minHeight = Space.touch)
                    .toggleable(value = line.checked, role = Role.Checkbox) { line.checked = it },
            ) {
                Text(
                    text = if (line.checked) "[x]" else "[ ]",
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurfaceVariant,
                )
            }
            else -> Text(
                text = if (line.kind == LineKind.BULLET) "•" else "${number ?: 1}.",
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurfaceVariant,
                modifier = Modifier
                    .defaultMinSize(minWidth = Space.x5)
                    .padding(top = Space.x2, bottom = Space.x2, end = Space.x2),
            )
        }
        BasicTextField(
            value = line.value,
            onValueChange = { state.change(line, it) },
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = ink),
            cursorBrush = SolidColor(colors.onBackground),
            visualTransformation = transformation,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
            modifier = Modifier
                .weight(1f)
                .padding(top = if (line.kind == LineKind.CHECK) Space.x3 else Space.x2, bottom = Space.x2)
                .focusRequester(focus)
                .onFocusChanged { if (it.isFocused) state.focusedId = line.id },
        )
    }
}

/** The marks along the top: pull out and push in, the three kinds of list, then bold, italic and underline. */
@Composable
private fun Toolbar(state: NoteEditState) {
    val kind = state.focused?.kind
    Row(
        horizontalArrangement = Arrangement.spacedBy(Space.x1),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.rowPad),
    ) {
        ToolButton("«", stringResource(R.string.less_indent), false, Modifier.weight(1f)) { state.push(-1) }
        ToolButton("»", stringResource(R.string.more_indent), false, Modifier.weight(1f)) { state.push(1) }
        Spacer(Modifier.width(Space.x2))
        ToolButton("1.", stringResource(R.string.numbered_list), kind == LineKind.NUMBER, Modifier.weight(1f)) { state.setKind(LineKind.NUMBER) }
        ToolButton("•", stringResource(R.string.bulleted_list), kind == LineKind.BULLET, Modifier.weight(1f)) { state.setKind(LineKind.BULLET) }
        ToolButton("[ ]", stringResource(R.string.checklist), kind == LineKind.CHECK, Modifier.weight(1f)) { state.setKind(LineKind.CHECK) }
        Spacer(Modifier.width(Space.x2))
        ToolButton("b", stringResource(R.string.bold), state.isOn(BOLD), Modifier.weight(1f), BOLD) { state.toggle(BOLD) }
        ToolButton("i", stringResource(R.string.italic), state.isOn(ITALIC), Modifier.weight(1f), ITALIC) { state.toggle(ITALIC) }
        ToolButton("u", stringResource(R.string.underline), state.isOn(UNDERLINE), Modifier.weight(1f), UNDERLINE) { state.toggle(UNDERLINE) }
    }
}

@Composable
private fun ToolButton(
    glyph: String,
    label: String,
    on: Boolean,
    modifier: Modifier,
    look: Int = 0,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .heightIn(min = Space.touch)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = label
                selected = on
            },
    ) {
        Text(
            text = buildAnnotatedString { withStyle(spanFor(look)) { append(glyph) } },
            style = MaterialTheme.typography.titleMedium,
            color = if (on) colors.onBackground else colors.onSurfaceVariant,
        )
    }
}

/** The name of the note in big letters. Left empty, the first line of the text shows in its place, faintly. */
@Composable
private fun TitleField(state: NoteEditState) {
    val colors = MaterialTheme.colorScheme
    val style = MaterialTheme.typography.headlineSmall
    val borrowed = state.lines.firstOrNull { it.text.isNotBlank() }?.text?.trim() ?: ""
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = Space.gutter, end = Space.gutter, top = Space.x2, bottom = Space.x1),
    ) {
        if (state.title.isEmpty() && borrowed.isNotEmpty()) {
            Text(text = borrowed, style = style, color = colors.onSurfaceVariant, maxLines = 1)
        }
        BasicTextField(
            value = state.title,
            onValueChange = {
                if ('\n' in it) {
                    state.title = it.replace("\n", "")
                    state.focusRequest = state.lines.first().id
                } else {
                    state.title = it
                }
            },
            textStyle = style.copy(color = colors.onBackground),
            cursorBrush = SolidColor(colors.onBackground),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
