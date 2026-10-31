package dev.fourthbreakfast.planner.agenda

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import dev.fourthbreakfast.planner.R
import dev.fourthbreakfast.planner.core.Event
import dev.fourthbreakfast.planner.tasks.BackButton
import dev.fourthbreakfast.planner.tasks.QuietButton
import dev.fourthbreakfast.planner.ui.theme.Space

/**
 * The form for one event: name, day, time, place and notes, each an empty line to write on.
 * Saving needs a name. A day left empty means today, a time left empty means the whole day.
 */
@Composable
fun EventEditor(
    initial: Event?,
    onSave: (EventDraft) -> Unit,
    onDelete: (() -> Unit)?,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    var title by rememberSaveable { mutableStateOf(initial?.title ?: "") }
    var day by rememberSaveable { mutableLongStateOf(initial?.day ?: today()) }
    var daySet by rememberSaveable { mutableStateOf(initial != null) }
    var start by rememberSaveable { mutableStateOf(initial?.minute) }
    var end by rememberSaveable { mutableStateOf(initial?.endMinute) }
    var timeSet by rememberSaveable { mutableStateOf(initial != null) }
    var location by rememberSaveable { mutableStateOf(initial?.location ?: "") }
    var notes by rememberSaveable { mutableStateOf(initial?.notes ?: "") }
    var pickingDay by rememberSaveable { mutableStateOf(false) }
    var pickingTime by rememberSaveable { mutableStateOf(false) }
    val is24Hour = rememberIs24Hour()
    val colors = MaterialTheme.colorScheme
    val nameFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (initial == null) nameFocus.requestFocus() }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = Space.gutter, end = Space.rowPad),
            ) {
                Text(
                    text = stringResource(R.string.event_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.onBackground,
                    modifier = Modifier.weight(1f).padding(vertical = Space.x2),
                )
                if (onDelete != null) QuietButton(stringResource(R.string.delete), onDelete)
                BackButton(onClose)
            }
        },
        bottomBar = {
            val ready = title.isNotBlank()
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = Space.gutter, vertical = Space.x3)
                    .heightIn(min = Space.touch)
                    .background(colors.surfaceVariant, RoundedCornerShape(Space.radiusCheck))
                    .border(Space.strokeThin, colors.outline, RoundedCornerShape(Space.radiusCheck))
                    .clickable(enabled = ready) { onSave(EventDraft(title, day, start, end, location, notes)) },
            ) {
                Text(
                    text = stringResource(R.string.save),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onBackground.copy(alpha = if (ready) 1f else 0.38f),
                )
            }
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Label(R.string.event_name)
            LineField(title, { title = it }, Modifier.focusRequester(nameFocus))
            Label(R.string.event_day)
            LineChoice(if (daySet) dateText(day, today()) else "") { pickingDay = true }
            Label(R.string.event_time)
            LineChoice(if (timeSet) timeRangeText(start, end, is24Hour) else "") { pickingTime = true }
            Label(R.string.event_location)
            LineField(location, { location = it })
            Label(R.string.event_notes)
            LineField(notes, { notes = it }, singleLine = false)
        }
    }

    if (pickingDay) {
        DayChooser(
            initialDay = if (daySet) day else null,
            today = today(),
            onSet = {
                day = it
                daySet = true
                pickingDay = false
            },
            onCancel = { pickingDay = false },
        )
    }
    if (pickingTime) {
        TimeChooser(
            initialStart = start,
            initialEnd = end,
            onSet = { newStart, newEnd ->
                start = newStart
                end = newEnd
                timeSet = true
                pickingTime = false
            },
            onCancel = { pickingTime = false },
        )
    }
}

@Composable
private fun Label(resource: Int) {
    Text(
        text = stringResource(resource),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = Space.gutter, top = Space.x4),
    )
}

/** The hairline an empty field sits on, like a line on paper. */
@Composable
private fun Rule() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outline,
        thickness = Space.strokeThin,
        modifier = Modifier.padding(horizontal = Space.gutter),
    )
}

/** A value you touch to change, like the day or the time. Empty until chosen. */
@Composable
private fun LineChoice(text: String, onClick: () -> Unit) {
    Column {
        Box(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Space.touch)
                .clickable(onClick = onClick)
                .padding(horizontal = Space.gutter),
        ) {
            Text(text = text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
        }
        Rule()
    }
}

/** A plain text field with nothing around it but its line: one touch tall, the text at the gutter. */
@Composable
private fun LineField(
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    Column {
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = singleLine,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onBackground),
            cursorBrush = SolidColor(colors.onBackground),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
            decorationBox = { inner ->
                Box(
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = Space.touch)
                        .padding(horizontal = Space.gutter, vertical = Space.x2),
                ) { inner() }
            },
            modifier = modifier.fillMaxWidth(),
        )
        Rule()
    }
}
