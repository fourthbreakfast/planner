package dev.fourthbreakfast.planner.notes

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dev.fourthbreakfast.planner.R
import dev.fourthbreakfast.planner.core.BOLD
import dev.fourthbreakfast.planner.core.ITALIC
import dev.fourthbreakfast.planner.core.LineKind
import dev.fourthbreakfast.planner.core.UNDERLINE
import dev.fourthbreakfast.planner.core.numberLabels
import dev.fourthbreakfast.planner.tasks.BackButton
import dev.fourthbreakfast.planner.tasks.QuietButton
import dev.fourthbreakfast.planner.tasks.SegmentTab
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
private fun ToolButton(glyph: String, label: String, on: Boolean, modifier: Modifier, look: Int = 0, onClick: () -> Unit) {
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
    val titleLabel = stringResource(R.string.note_name)
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
            modifier = Modifier
                .fillMaxWidth()
                .semantics { if (state.title.isEmpty()) contentDescription = titleLabel },
        )
    }
}
