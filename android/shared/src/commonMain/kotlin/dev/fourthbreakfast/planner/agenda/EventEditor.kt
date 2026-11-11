package dev.fourthbreakfast.planner.agenda

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.KeyboardCapitalization
import dev.fourthbreakfast.planner.core.Event
import dev.fourthbreakfast.planner.core.Repeat
import dev.fourthbreakfast.planner.resources.Res
import dev.fourthbreakfast.planner.resources.calendar_read_only
import dev.fourthbreakfast.planner.resources.clear_target
import dev.fourthbreakfast.planner.resources.complete_task
import dev.fourthbreakfast.planner.resources.delete
import dev.fourthbreakfast.planner.resources.event_date_range_invalid
import dev.fourthbreakfast.planner.resources.event_day
import dev.fourthbreakfast.planner.resources.event_ends
import dev.fourthbreakfast.planner.resources.event_location
import dev.fourthbreakfast.planner.resources.event_name
import dev.fourthbreakfast.planner.resources.event_notes
import dev.fourthbreakfast.planner.resources.event_reminder
import dev.fourthbreakfast.planner.resources.event_repeat_ends
import dev.fourthbreakfast.planner.resources.event_repeats
import dev.fourthbreakfast.planner.resources.event_time
import dev.fourthbreakfast.planner.resources.event_title
import dev.fourthbreakfast.planner.resources.event_until
import dev.fourthbreakfast.planner.resources.reminder_none
import dev.fourthbreakfast.planner.resources.save
import dev.fourthbreakfast.planner.resources.scheduled_task_hint
import dev.fourthbreakfast.planner.ui.BackButton
import dev.fourthbreakfast.planner.ui.BackHandler
import dev.fourthbreakfast.planner.ui.QuietButton
import dev.fourthbreakfast.planner.ui.ScreenHeading
import dev.fourthbreakfast.planner.ui.rememberAskToNotify
import dev.fourthbreakfast.planner.ui.rememberIs24Hour
import dev.fourthbreakfast.planner.ui.theme.Space
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The form for one event: name, day, time, place and notes, each an empty line to write on.
 * Saving needs a name. A day left empty means today, a time left empty means the whole day. [occurrence] is the day of
 * the time of a repeating event that was opened: saving or deleting it then asks whether that is for this one time only
 * or for every time, and [onSave] and [onDelete] are told which.
 */
@Composable
fun EventEditor(
    initial: Event?,
    occurrence: Long? = null,
    newDay: Long? = null,
    onSave: (draft: EventDraft, onlyThisTime: Boolean) -> Unit,
    onDelete: ((onlyThisTime: Boolean) -> Unit)?,
    onClose: () -> Unit,
    onCompleteTask: (() -> Unit)? = null,
) {
    BackHandler(onBack = onClose)
    if (initial?.readOnly == true) {
        SubscribedEventView(initial, onClose)
        return
    }
    val asksWhich = initial != null && initial.repeat != Repeat.NONE && occurrence != null
    // a time of a repeating event shows its own day; the series starts on the event's first day
    val shownDay = if (asksWhich) occurrence!! else initial?.day
    var title by rememberSaveable { mutableStateOf(initial?.title ?: "") }
    var day by rememberSaveable { mutableLongStateOf(shownDay ?: newDay ?: today()) }
    var endDay by rememberSaveable { mutableStateOf(initial?.endDay?.let { it - initial.day + (shownDay ?: initial.day) }) }
    var repeat by rememberSaveable { mutableStateOf(initial?.repeat ?: Repeat.NONE) }
    var until by rememberSaveable { mutableStateOf(initial?.repeatUntil) }
    var asking by rememberSaveable { mutableStateOf<String?>(null) }
    var daySet by rememberSaveable { mutableStateOf(initial != null || newDay != null) }
    var start by rememberSaveable { mutableStateOf(initial?.minute) }
    var end by rememberSaveable { mutableStateOf(initial?.endMinute) }
    var timeSet by rememberSaveable { mutableStateOf(initial != null) }
    var location by rememberSaveable { mutableStateOf(initial?.location ?: "") }
    var notes by rememberSaveable { mutableStateOf(initial?.notes ?: "") }
    var pickingDay by rememberSaveable { mutableStateOf(false) }
    var reminder by rememberSaveable { mutableStateOf(initial?.reminder) }
    var pickingTime by rememberSaveable { mutableStateOf(false) }
    var pickingReminder by rememberSaveable { mutableStateOf(false) }
    val askToNotify = rememberAskToNotify()
    val is24Hour = rememberIs24Hour()
    val today = rememberToday()
    val colors = MaterialTheme.colorScheme
    val nameFocus = remember { FocusRequester() }

    // what is on the form, as an event. For every time of a repeating event, a moved day moves the whole series by as much
    fun draft(onlyThisTime: Boolean = false): EventDraft {
        val first = if (asksWhich && !onlyThisTime) initial!!.day + (day - occurrence!!) else day
        return EventDraft(
            title,
            first,
            start,
            end,
            location,
            notes,
            reminder.takeIf { timeSet && start != null },
            endDay?.let { it - day + first },
            if (onlyThisTime) Repeat.NONE else repeat,
            until.takeIf { repeat != Repeat.NONE && !onlyThisTime },
        )
    }
    LaunchedEffect(Unit) { if (initial == null) nameFocus.requestFocus() }

    fun save() {
        if (title.isBlank() || pickingDay || pickingTime || pickingReminder || asking != null) return
        if (asksWhich) asking = "save" else onSave(draft(), false)
    }

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
                ScreenHeading(stringResource(Res.string.event_title), Modifier.weight(1f))
                if (onCompleteTask != null) QuietButton(stringResource(Res.string.complete_task), onCompleteTask)
                if (onDelete != null) QuietButton(stringResource(Res.string.delete)) { if (asksWhich) asking = "delete" else onDelete(false) }
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
                    .clickable(enabled = ready) { save() },
            ) {
                Text(
                    text = stringResource(Res.string.save),
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
            Label(Res.string.event_name)
            QuietField(title, { title = it }, Modifier.focusRequester(nameFocus), readOnly = initial?.taskId != null, onSubmit = ::save)
            if (initial?.taskId != null) {
                Text(
                    stringResource(Res.string.scheduled_task_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Space.gutter),
                )
            }
            Label(Res.string.event_day)
            Choice(
                if (daySet) {
                    listOfNotNull(
                        dateText(day, today) + (endDay?.let { " – " + dateText(it, today) } ?: ""),
                        repeat.takeIf { it != Repeat.NONE }?.let { repeatText(it) },
                        until?.takeIf { repeat != Repeat.NONE }?.let { stringResource(Res.string.event_until) + " " + dateText(it, today) },
                    ).joinToString(" · ")
                } else {
                    ""
                },
            ) { pickingDay = true }
            Label(Res.string.event_time)
            Choice(if (timeSet) timeRangeText(start, end, is24Hour) else "") { pickingTime = true }
            // a reminder needs a time to count back from
            if (timeSet && start != null) {
                Column {
                    Label(Res.string.event_reminder)
                    Choice(reminder?.let { reminderText(it) } ?: "") { pickingReminder = true }
                }
            }
            Label(Res.string.event_location)
            QuietField(location, { location = it }, onSubmit = ::save)
            Label(Res.string.event_notes)
            QuietField(notes, { notes = it }, singleLine = false, onSubmit = ::save)
        }
    }

    if (pickingDay) {
        DayOptionsChooser(
            initialDay = day,
            initialEnd = endDay,
            initialRepeat = repeat,
            initialUntil = until,
            allowRepeat = initial?.taskId == null,
            today = today,
            onSet = { chosenDay, chosenEnd, chosenRepeat, chosenUntil ->
                day = chosenDay
                endDay = chosenEnd
                repeat = chosenRepeat
                until = chosenUntil
                daySet = true
                pickingDay = false
            },
            onCancel = { pickingDay = false },
        )
    }
    asking?.let { what ->
        WhichTimesChooser(
            onPick = { onlyThisTime ->
                asking = null
                if (what == "save") onSave(draft(onlyThisTime), onlyThisTime) else onDelete?.invoke(onlyThisTime)
            },
            onCancel = { asking = null },
        )
    }
    if (pickingReminder) {
        ReminderChooser(
            current = reminder,
            onPick = { picked ->
                reminder = picked
                pickingReminder = false
                // the first reminder asks to be allowed to notify
                if (picked != null) {
                    askToNotify()
                }
            },
            onCancel = { pickingReminder = false },
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

/** Subscriptions are inspected here; edits belong in the publishing calendar. */
@Composable
private fun SubscribedEventView(event: Event, onClose: () -> Unit) {
    val today = rememberToday()
    val is24Hour = rememberIs24Hour()
    Scaffold(
        topBar = {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = Space.gutter, end = Space.rowPad), verticalAlignment = Alignment.CenterVertically) {
                ScreenHeading(stringResource(Res.string.event_title), Modifier.weight(1f))
                BackButton(onClose)
            }
        },
    ) { padding ->
        SelectionContainer {
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(Space.gutter)) {
                Text(event.title, style = MaterialTheme.typography.headlineSmall)
                event.calendarName?.let { Text(it, Modifier.padding(top = Space.x2), style = MaterialTheme.typography.bodyMedium) }
                Text(stringResource(Res.string.calendar_read_only), Modifier.padding(vertical = Space.x3), style = MaterialTheme.typography.bodySmall)
                Text(
                    dateText(event.day, today) + (event.endDay?.let { " – " + dateText(it, today) } ?: ""),
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (event.minute != null) Text(timeRangeText(event.minute, event.endMinute, is24Hour), style = MaterialTheme.typography.bodyLarge)
                if (event.location.isNotBlank()) Text(event.location, Modifier.padding(top = Space.x4), style = MaterialTheme.typography.bodyLarge)
                if (event.notes.isNotBlank()) Text(event.notes, Modifier.padding(top = Space.x4), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun Label(resource: StringResource) {
    Text(
        text = stringResource(resource),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = Space.gutter, top = Space.x4),
    )
}

/** A value you touch to change, like the day or the time. Empty until chosen. */
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
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    readOnly: Boolean = false,
    onSubmit: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    TextField(
        value = value,
        onValueChange = onChange,
        singleLine = singleLine,
        readOnly = readOnly,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedTextColor = colors.onBackground,
            unfocusedTextColor = colors.onBackground,
            cursorColor = colors.onBackground,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
        modifier = modifier.fillMaxWidth().onPreviewKeyEvent { event ->
            if (event.type == KeyEventType.KeyDown &&
                (event.key == Key.Enter || event.key == Key.NumPadEnter) &&
                !event.isShiftPressed &&
                !event.isAltPressed &&
                !event.isMetaPressed &&
                (singleLine || event.isCtrlPressed)
            ) {
                onSubmit()
                true
            } else {
                false
            }
        },
    )
}

private enum class DayPart { MAIN, END, REPEAT, UNTIL }

/** Keep date settings together and commit them only when the day panel is confirmed. */
@Composable
private fun DayOptionsChooser(
    initialDay: Long,
    initialEnd: Long?,
    initialRepeat: Repeat,
    initialUntil: Long?,
    today: Long,
    onSet: (Long, Long?, Repeat, Long?) -> Unit,
    onCancel: () -> Unit,
    allowRepeat: Boolean = true,
) {
    var day by rememberSaveable { mutableLongStateOf(initialDay) }
    var end by rememberSaveable { mutableStateOf(initialEnd) }
    var repeat by rememberSaveable { mutableStateOf(initialRepeat) }
    var until by rememberSaveable { mutableStateOf(initialUntil) }
    var part by remember { mutableStateOf(DayPart.MAIN) }
    when (part) {
        DayPart.END -> DayChooser(end ?: day, today, onSet = {
            end = it
            part = DayPart.MAIN
        }, onCancel = { part = DayPart.MAIN })
        DayPart.UNTIL -> DayChooser(until ?: day, today, onSet = {
            until = it
            part = DayPart.MAIN
        }, onCancel = { part = DayPart.MAIN })
        DayPart.REPEAT -> RepeatChooser(repeat, onPick = {
            repeat = it
            part = DayPart.MAIN
        }, onCancel = { part = DayPart.MAIN })
        DayPart.MAIN -> DayChooser(
            initialDay = day,
            today = today,
            onDayChange = { day = it },
            canSet = { selected -> (end == null || end!! >= selected) && (repeat == Repeat.NONE || until == null || until!! >= selected) },
            onSet = { selected -> onSet(selected, end?.takeIf { it > selected }, repeat, until.takeIf { repeat != Repeat.NONE }) },
            onCancel = onCancel,
            details = { selected ->
                Label(Res.string.event_ends)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { Choice(end?.let { dateText(it, today) } ?: stringResource(Res.string.reminder_none)) { part = DayPart.END } }
                    if (end != null) QuietButton(stringResource(Res.string.clear_target)) { end = null }
                }
                if (allowRepeat) {
                    Label(Res.string.event_repeats)
                    Choice(repeatText(repeat)) { part = DayPart.REPEAT }
                }
                if (repeat != Repeat.NONE) {
                    Label(Res.string.event_repeat_ends)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            Choice(until?.let { dateText(it, today) } ?: stringResource(Res.string.reminder_none)) { part = DayPart.UNTIL }
                        }
                        if (until != null) QuietButton(stringResource(Res.string.clear_target)) { until = null }
                    }
                }
                if ((end != null && end!! < selected) || (repeat != Repeat.NONE && until != null && until!! < selected)) {
                    Text(
                        stringResource(Res.string.event_date_range_invalid),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            },
        )
    }
}
