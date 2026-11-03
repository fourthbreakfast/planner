package dev.fourthbreakfast.planner.notes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dev.fourthbreakfast.planner.R
import dev.fourthbreakfast.planner.core.BOLD
import dev.fourthbreakfast.planner.core.ITALIC
import dev.fourthbreakfast.planner.core.LineKind
import dev.fourthbreakfast.planner.core.NoteLine
import dev.fourthbreakfast.planner.core.UNDERLINE
import dev.fourthbreakfast.planner.core.numberLabels
import dev.fourthbreakfast.planner.ui.BackButton
import dev.fourthbreakfast.planner.ui.CheckBoxMark
import dev.fourthbreakfast.planner.ui.QuietButton
import dev.fourthbreakfast.planner.ui.SegmentTab
import dev.fourthbreakfast.planner.ui.theme.Space
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

private const val AUTOSAVE_MILLIS = 400L
private const val HISTORY_MILLIS = 700L

/** Writing one note: a row of marks at the top, then lines of text, each of which can be a list item. */
@Composable
fun NoteEditor(
    initialTitle: String,
    initialBody: String,
    pinned: Boolean,
    onPin: () -> Unit,
    toolbarAbove: Boolean,
    onSave: (String, String) -> Unit,
    onClose: (String, String) -> Unit,
) {
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
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .then(if (toolbarAbove) Modifier else Modifier.imePadding())
                .onPreviewKeyEvent { state.handleKey(it) }
                .verticalScroll(rememberScrollState()),
        ) {
            TitleField(state)
            NoteText(state)
            // a touch below the last line puts the cursor there
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(Space.listEnd())
                    .clickable(interactionSource = null, indication = null) { state.focusEnd() },
            )
        }
    }
}

/**
 * The text of the note, one field for all of it. Each line is pushed in by its depth and its mark through its paragraph
 * style, and the marks themselves are laid over the space that leaves, where the text says the line sits.
 */
@Composable
private fun NoteText(state: NoteEditState) {
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val focus = remember { FocusRequester() }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var touched by remember { mutableStateOf<Offset?>(null) }
    val marks = state.marks
    val looks = state.looks
    val quiet = colors.onSurfaceVariant
    val transformation = remember(marks, looks, quiet, density) {
        VisualTransformation { shown ->
            TransformedText(styledBody(shown.text, marks, looks, quiet, density), OffsetMapping.Identity)
        }
    }
    LaunchedEffect(state.focusRequest) {
        focus.requestFocus()
    }
    val numbers = remember(marks) { numberLabels(marks.map { NoteLine(it.kind, it.indent) }) }
    Box(Modifier.fillMaxWidth().padding(horizontal = Space.gutter, vertical = Space.x2)) {
        BasicTextField(
            value = state.value,
            onValueChange = { state.change(endOfLineForTouch(it, state.value.text, layout, touched)) },
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onBackground),
            cursorBrush = SolidColor(colors.onBackground),
            visualTransformation = transformation,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
            onTextLayout = { layout = it },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focus)
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull()?.let { touched = it.position }
                        }
                    }
                },
        )
        val placed = layout
        if (placed != null) {
            var start = 1
            state.text.split('\n').forEachIndexed { index, words ->
                val mark = marks.getOrNull(index)
                if (mark != null && mark.kind != LineKind.PLAIN && start <= placed.layoutInput.text.length) {
                    val row = placed.getLineForOffset(start)
                    val top = with(density) { placed.getLineTop(row).toDp() }
                    val height = with(density) { (placed.getLineBottom(row) - placed.getLineTop(row)).toDp() }
                    LineMarker(mark, numbers.getOrNull(index), top, height) { state.toggleChecked(index) }
                }
                start += words.length + 1
            }
        }
    }
}

@Composable
private fun LineMarker(mark: LineMark, number: Int?, top: Dp, height: Dp, onToggle: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val left = Space.indent * mark.indent
    if (mark.kind == LineKind.CHECK) {
        // the touch area is taller than the line, so a thumb does not have to be exact
        Box(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier
                .offset(x = left, y = top + height / 2 - Space.touch / 2)
                .size(Space.markWidth, Space.touch)
                .toggleable(value = mark.checked, role = Role.Checkbox, onValueChange = { onToggle() }),
        ) {
            CheckBoxMark(mark.checked, colors.onSurfaceVariant)
        }
    } else {
        Box(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier
                .offset(x = left, y = top)
                .height(height),
        ) {
            Text(
                text = if (mark.kind == LineKind.BULLET) "•" else "${number ?: 1}.",
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

/** The marks along the top: pull out and push in, the three kinds of list, then bold, italic and underline. */
@Composable
private fun Toolbar(state: NoteEditState) {
    val kind = state.kind
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
        ToolButton("", stringResource(R.string.checklist), kind == LineKind.CHECK, Modifier.weight(1f), box = true) { state.setKind(LineKind.CHECK) }
        Spacer(Modifier.width(Space.x2))
        ToolButton("b", stringResource(R.string.bold), state.isOn(BOLD), Modifier.weight(1f), BOLD) { state.toggle(BOLD) }
        ToolButton("i", stringResource(R.string.italic), state.isOn(ITALIC), Modifier.weight(1f), ITALIC) { state.toggle(ITALIC) }
        ToolButton("u", stringResource(R.string.underline), state.isOn(UNDERLINE), Modifier.weight(1f), UNDERLINE) { state.toggle(UNDERLINE) }
    }
}

@Composable
private fun ToolButton(glyph: String, label: String, on: Boolean, modifier: Modifier, look: Int = 0, box: Boolean = false, onClick: () -> Unit) {
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
        val ink = if (on) colors.onBackground else colors.onSurfaceVariant
        if (box) {
            CheckBoxMark(false, ink)
        } else {
            Text(
                text = buildAnnotatedString { withStyle(spanFor(look)) { append(glyph) } },
                style = MaterialTheme.typography.titleMedium,
                color = ink,
            )
        }
    }
}

/** The name of the note in big letters. Left empty, the first line of the text shows in its place, faintly. */
@Composable
private fun TitleField(state: NoteEditState) {
    val colors = MaterialTheme.colorScheme
    val style = MaterialTheme.typography.headlineSmall
    val titleLabel = stringResource(R.string.note_name)
    val borrowed = state.text.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
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
                    state.focusStart()
                } else {
                    state.title = it
                }
            },
            textStyle = style.copy(color = colors.onBackground),
            cursorBrush = SolidColor(colors.onBackground),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
            modifier = Modifier
                .fillMaxWidth()
                .semantics { if (state.title.isEmpty()) contentDescription = titleLabel },
        )
    }
}

/**
 * A line's break belongs to the end of its line, so touching just past the last word of a line lands after the break,
 * at the start of the next line. When the touch was on the line above that, the cursor goes back before the break.
 */
private fun endOfLineForTouch(v: TextFieldValue, before: String, layout: TextLayoutResult?, touched: Offset?): TextFieldValue {
    val at = v.selection.start
    if (layout == null || touched == null || v.text != before || !v.selection.collapsed) return v
    if (at < 2 || at > v.text.length || v.text[at - 1] != '\n' || at > layout.layoutInput.text.length) return v
    val touchedLine = layout.getLineForVerticalPosition(touched.y)
    return if (touchedLine < layout.getLineForOffset(at)) v.copy(selection = TextRange(at - 1)) else v
}
