package dev.fourthbreakfast.planner.agenda

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import dev.fourthbreakfast.planner.R
import dev.fourthbreakfast.planner.core.Event
import dev.fourthbreakfast.planner.tasks.BackButton
import dev.fourthbreakfast.planner.tasks.QuietButton
import dev.fourthbreakfast.planner.ui.theme.Space

private const val DAY_MILLIS = 86_400_000L

/** The form for one event: name, day, time, place and notes. Saving needs a name. */
@OptIn(ExperimentalMaterial3Api::class)
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
    var minute by rememberSaveable { mutableStateOf(initial?.minute) }
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
                    text = stringResource(if (initial == null) R.string.new_event else R.string.event_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.onBackground,
                    modifier = Modifier.weight(1f).padding(vertical = Space.x2),
                )
                if (onDelete != null) QuietButton(stringResource(R.string.delete), onDelete)
                QuietButton(
                    label = stringResource(R.string.save),
                    enabled = title.isNotBlank(),
                    onClick = { onSave(EventDraft(title, day, minute, location, notes)) },
                )
                BackButton(onClose)
            }
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .imePadding()
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Label(R.string.event_name)
            QuietField(title, { title = it }, stringResource(R.string.event_name_hint), Modifier.focusRequester(nameFocus))
            Label(R.string.event_day)
            Choice(dateText(day, today())) { pickingDay = true }
            Label(R.string.event_time)
            Choice(minute?.let { timeText(it, is24Hour) } ?: stringResource(R.string.no_time)) { pickingTime = true }
            Label(R.string.event_location)
            QuietField(location, { location = it }, stringResource(R.string.event_location_hint))
            Label(R.string.event_notes)
            QuietField(notes, { notes = it }, stringResource(R.string.event_notes_hint), singleLine = false, minLines = 3)
        }
    }

    if (pickingDay) {
        val state = rememberDatePickerState(initialSelectedDateMillis = day * DAY_MILLIS)
        DatePickerDialog(
            onDismissRequest = { pickingDay = false },
            confirmButton = {
                QuietButton(stringResource(R.string.set)) {
                    state.selectedDateMillis?.let { day = Math.floorDiv(it, DAY_MILLIS) }
                    pickingDay = false
                }
            },
            dismissButton = { QuietButton(stringResource(R.string.cancel)) { pickingDay = false } },
        ) { DatePicker(state) }
    }

    if (pickingTime) {
        val start = minute ?: 9 * 60
        val state = rememberTimePickerState(initialHour = start / 60, initialMinute = start % 60, is24Hour = is24Hour)
        AlertDialog(
            onDismissRequest = { pickingTime = false },
            confirmButton = {
                QuietButton(stringResource(R.string.set)) {
                    minute = state.hour * 60 + state.minute
                    pickingTime = false
                }
            },
            dismissButton = { QuietButton(stringResource(R.string.cancel)) { pickingTime = false } },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    TimePicker(state)
                    if (minute != null) {
                        QuietButton(stringResource(R.string.no_time)) {
                            minute = null
                            pickingTime = false
                        }
                    }
                }
            },
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

/** A value you touch to change, like the day or the time. */
@Composable
private fun Choice(text: String, onClick: () -> Unit) {
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
}

/** A plain text field with nothing around it, like the add line. */
@Composable
private fun QuietField(
    value: String,
    onChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
) {
    val colors = MaterialTheme.colorScheme
    TextField(
        value = value,
        onValueChange = onChange,
        placeholder = { Text(hint, maxLines = 1) },
        singleLine = singleLine,
        minLines = minLines,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedTextColor = colors.onBackground,
            unfocusedTextColor = colors.onBackground,
            focusedPlaceholderColor = colors.onSurfaceVariant,
            unfocusedPlaceholderColor = colors.onSurfaceVariant,
            cursorColor = colors.onBackground,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
        modifier = modifier.fillMaxWidth(),
    )
}
