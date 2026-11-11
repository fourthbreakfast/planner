package dev.fourthbreakfast.planner.agenda

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.fourthbreakfast.planner.core.REMINDER_CHOICES
import dev.fourthbreakfast.planner.core.ReminderUnit
import dev.fourthbreakfast.planner.core.Repeat
import dev.fourthbreakfast.planner.core.customReminderMinutes
import dev.fourthbreakfast.planner.core.reminderAmount
import dev.fourthbreakfast.planner.resources.Res
import dev.fourthbreakfast.planner.resources.back
import dev.fourthbreakfast.planner.resources.cancel
import dev.fourthbreakfast.planner.resources.every_time
import dev.fourthbreakfast.planner.resources.hour
import dev.fourthbreakfast.planner.resources.minute
import dev.fourthbreakfast.planner.resources.no_time
import dev.fourthbreakfast.planner.resources.only_this_time
import dev.fourthbreakfast.planner.resources.reminder_at
import dev.fourthbreakfast.planner.resources.reminder_custom
import dev.fourthbreakfast.planner.resources.reminder_days
import dev.fourthbreakfast.planner.resources.reminder_days_unit
import dev.fourthbreakfast.planner.resources.reminder_hours
import dev.fourthbreakfast.planner.resources.reminder_hours_unit
import dev.fourthbreakfast.planner.resources.reminder_invalid_amount
import dev.fourthbreakfast.planner.resources.reminder_minutes
import dev.fourthbreakfast.planner.resources.reminder_minutes_unit
import dev.fourthbreakfast.planner.resources.reminder_none
import dev.fourthbreakfast.planner.resources.reminder_offset
import dev.fourthbreakfast.planner.resources.reminder_weeks
import dev.fourthbreakfast.planner.resources.repeat_daily
import dev.fourthbreakfast.planner.resources.repeat_monthly
import dev.fourthbreakfast.planner.resources.repeat_never
import dev.fourthbreakfast.planner.resources.repeat_weekly
import dev.fourthbreakfast.planner.resources.repeat_yearly
import dev.fourthbreakfast.planner.resources.set
import dev.fourthbreakfast.planner.resources.time_start
import dev.fourthbreakfast.planner.resources.time_start_end
import dev.fourthbreakfast.planner.ui.QuietButton
import dev.fourthbreakfast.planner.ui.theme.Space
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

private const val LAST_MINUTE = 24 * 60 - 1

/** "09:30", "09:30 – 10:45", or "no time". Twelve hour clocks get "am" and "pm". */
@Composable
fun timeRangeText(start: Int?, end: Int?, is24Hour: Boolean): String = when {
    start == null -> stringResource(Res.string.no_time)
    end == null -> timeText(start, is24Hour)
    else -> timeText(start, is24Hour) + " – " + timeText(end, is24Hour)
}

/** The panel both choosers sit on: the quiet panel colour, a hairline, square-ish corners. */
@Composable
private fun ChooserPanel(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .imePadding()
                .padding(Space.x4)
                .widthIn(max = Space.chooserWidth)
                .heightIn(max = Space.chooserHeight)
                .fillMaxWidth()
                .background(colors.surfaceVariant, RoundedCornerShape(Space.radiusCheck))
                .border(Space.strokeThin, colors.outline, RoundedCornerShape(Space.radiusCheck))
                .verticalScroll(rememberScrollState())
                .padding(Space.x4),
        ) { content() }
    }
}

/** An exact reminder offset in the largest whole unit. */
@Composable
fun reminderText(minutes: Int): String {
    if (minutes == 0) return stringResource(Res.string.reminder_at)
    val (amount, label) = when {
        minutes % (7 * 24 * 60) == 0 -> minutes / (7 * 24 * 60) to Res.plurals.reminder_weeks
        minutes % (24 * 60) == 0 -> minutes / (24 * 60) to Res.plurals.reminder_days
        minutes % 60 == 0 -> minutes / 60 to Res.plurals.reminder_hours
        else -> minutes to Res.plurals.reminder_minutes
    }
    return pluralStringResource(label, amount, amount)
}

/** Presets pick immediately; custom offsets are validated before they can be applied. */
@Composable
fun ReminderChooser(current: Int?, onPick: (Int?) -> Unit, onCancel: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    var custom by remember(current) { mutableStateOf(current != null && current !in REMINDER_CHOICES) }
    val initial = remember(current) { reminderAmount(current?.takeIf { it > 0 } ?: 10) }
    var amount by remember(current) { mutableStateOf(initial.first.toString()) }
    var unit by remember(current) { mutableStateOf(initial.second) }
    val parsed = customReminderMinutes(amount, unit)
    ChooserPanel(onDismiss = onCancel) {
        Column {
            if (custom) {
                Text(
                    text = stringResource(Res.string.reminder_custom),
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.onBackground,
                )
                TextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text(stringResource(Res.string.reminder_offset)) },
                    singleLine = true,
                    isError = parsed == null,
                    supportingText = {
                        if (parsed == null) {
                            Text(stringResource(Res.string.reminder_invalid_amount, Int.MAX_VALUE / unit.minutes))
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        errorContainerColor = Color.Transparent,
                        focusedTextColor = colors.onBackground,
                        unfocusedTextColor = colors.onBackground,
                        cursorColor = colors.onBackground,
                    ),
                    modifier = Modifier.fillMaxWidth().padding(top = Space.x2),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                    ReminderUnit.entries.forEach { choice ->
                        val selected = choice == unit
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = Space.touch)
                                .background(if (selected) colors.onBackground else colors.surfaceVariant, RoundedCornerShape(Space.radiusCheck))
                                .clickable { unit = choice },
                        ) {
                            Text(
                                text = stringResource(
                                    when (choice) {
                                        ReminderUnit.MINUTES -> Res.string.reminder_minutes_unit
                                        ReminderUnit.HOURS -> Res.string.reminder_hours_unit
                                        ReminderUnit.DAYS -> Res.string.reminder_days_unit
                                    },
                                ),
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selected) colors.background else colors.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (parsed != null) {
                    Text(
                        text = reminderText(parsed),
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.onBackground,
                        modifier = Modifier.padding(top = Space.x3),
                    )
                }
                Row(Modifier.fillMaxWidth().padding(top = Space.x2), horizontalArrangement = Arrangement.End) {
                    QuietButton(stringResource(Res.string.back)) { custom = false }
                    QuietButton(stringResource(Res.string.cancel), onCancel)
                    QuietButton(stringResource(Res.string.set), enabled = parsed != null) { parsed?.let(onPick) }
                }
            } else {
                REMINDER_CHOICES.forEach { choice ->
                    ReminderChoice(
                        text = if (choice == null) stringResource(Res.string.reminder_none) else reminderText(choice),
                        selected = choice == current,
                    ) { onPick(choice) }
                }
                ReminderChoice(stringResource(Res.string.reminder_custom), selected = false) { custom = true }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    QuietButton(stringResource(Res.string.cancel), onCancel)
                }
            }
        }
    }
}

@Composable
private fun ReminderChoice(text: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = Modifier.fillMaxWidth().heightIn(min = Space.touch).clickable(onClick = onClick),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) colors.onBackground else colors.onSurfaceVariant,
        )
    }
}

/** A row of numbers to pick from, [columns] to a row. The chosen one is filled like a checked box. */
@Composable
private fun NumberGrid(values: List<Int>, columns: Int, selected: Int?, label: (Int) -> String, onPick: (Int) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column {
        values.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { value ->
                    val on = value == selected
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = Space.touch)
                            .clickable { onPick(value) },
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(Space.touch - Space.x2)
                                .background(if (on) colors.onBackground else colors.surfaceVariant, RoundedCornerShape(Space.radiusCheck)),
                        ) {
                            Text(
                                text = label(value),
                                style = MaterialTheme.typography.titleMedium,
                                color = if (on) colors.background else colors.onSurfaceVariant,
                            )
                        }
                    }
                }
                repeat(columns - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * The start and end after an hour or a minute is touched in the time chooser, on the end when [editingEnd]: the other
 * part of that time stays. An end always comes at least five minutes after the start, and a start that passes the end
 * takes the end along to an hour after it, never past midnight.
 */
fun pickedTime(start: Int, end: Int, editingEnd: Boolean, hour: Int?, minute: Int?): Pair<Int, Int> {
    val current = if (editingEnd) end else start
    val next = (hour ?: (current / 60)) * 60 + (minute ?: (current % 60))
    return if (editingEnd) {
        start to maxOf(next, minOf(start + 5, LAST_MINUTE))
    } else {
        next to if (end <= next) minOf(next + 60, LAST_MINUTE) else end
    }
}

/**
 * The time of an event: all day, a start, or a start and an end. The hours and the minutes are two grids of
 * numbers, the same size and look as everything else, and what is chosen is filled in.
 */
@Composable
fun TimeChooser(initialStart: Int?, initialEnd: Int?, onSet: (start: Int?, end: Int?) -> Unit, onCancel: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    var mode by remember {
        mutableIntStateOf(
            if (initialStart == null) {
                0
            } else if (initialEnd == null) {
                1
            } else {
                2
            },
        )
    }
    var start by remember { mutableIntStateOf(initialStart ?: 9 * 60) }
    var end by remember { mutableIntStateOf(initialEnd ?: minOf((initialStart ?: 9 * 60) + 60, LAST_MINUTE)) }
    var editingEnd by remember { mutableStateOf(false) }

    fun pick(hour: Int?, minute: Int?) {
        val (newStart, newEnd) = pickedTime(start, end, editingEnd, hour, minute)
        start = newStart
        end = newEnd
    }

    ChooserPanel(onDismiss = onCancel) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf(0 to Res.string.no_time, 1 to Res.string.time_start, 2 to Res.string.time_start_end).forEach { (value, text) ->
                Text(
                    text = stringResource(text),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (mode == value) colors.onBackground else colors.onSurfaceVariant,
                    modifier = Modifier
                        .heightIn(min = Space.touch)
                        .clickable {
                            mode = value
                            if (value < 2) editingEnd = false
                        }
                        .padding(vertical = Space.x3),
                )
            }
        }
        if (mode >= 1) {
            Row(Modifier.fillMaxWidth().padding(top = Space.x2), horizontalArrangement = Arrangement.spacedBy(Space.x6)) {
                Text(
                    text = timeText(start, true),
                    style = MaterialTheme.typography.titleLarge,
                    color = if (!editingEnd) colors.onBackground else colors.onSurfaceVariant,
                    modifier = Modifier.clickable { editingEnd = false },
                )
                if (mode == 2) {
                    Text(
                        text = "– " + timeText(end, true),
                        style = MaterialTheme.typography.titleLarge,
                        color = if (editingEnd) colors.onBackground else colors.onSurfaceVariant,
                        modifier = Modifier.clickable { editingEnd = true },
                    )
                }
            }
            val current = if (editingEnd) end else start
            Text(
                text = stringResource(Res.string.hour),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = Space.x3),
            )
            NumberGrid((0..23).toList(), 6, current / 60, { "%02d".format(it) }) { pick(it, null) }
            Text(
                text = stringResource(Res.string.minute),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = Space.x2),
            )
            NumberGrid((0..55 step 5).toList(), 6, (current % 60).takeIf { it % 5 == 0 }, { "%02d".format(it) }) { pick(null, it) }
        }
        Row(Modifier.fillMaxWidth().padding(top = Space.x2), horizontalArrangement = Arrangement.End) {
            QuietButton(stringResource(Res.string.cancel), onCancel)
            QuietButton(stringResource(Res.string.set)) {
                onSet(
                    when (mode) {
                        0 -> null
                        else -> start
                    },
                    if (mode == 2) end else null,
                )
            }
        }
    }
}

/** The day of an event: a month of numbers, the chosen day filled in and today outlined. */
@Composable
fun DayChooser(
    initialDay: Long?,
    today: Long,
    onSet: (Long) -> Unit,
    onCancel: () -> Unit,
    onDayChange: ((Long) -> Unit)? = null,
    canSet: (Long) -> Boolean = { true },
    details: @Composable (Long) -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    val locale = Locale.getDefault()
    var chosen by remember { mutableStateOf(initialDay ?: today) }
    var month by remember { mutableStateOf(YearMonth.from(LocalDate.ofEpochDay(initialDay ?: today))) }
    val first = firstDayOfWeek()
    val cells = monthCells(month, first)

    ChooserPanel(onDismiss = onCancel) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = month.month.getDisplayName(TextStyle.SHORT, locale).lowercase(locale) + " " + month.year,
                style = MaterialTheme.typography.titleLarge,
                color = colors.onBackground,
                modifier = Modifier.weight(1f),
            )
            ArrowWord("‹") { month = month.minusMonths(1) }
            ArrowWord("›") { month = month.plusMonths(1) }
        }
        Row(Modifier.fillMaxWidth().padding(top = Space.x2)) {
            (0 until 7).map { first.plus(it.toLong()) }.forEach { weekday: DayOfWeek ->
                Text(
                    text = weekday.getDisplayName(TextStyle.NARROW, locale).lowercase(locale),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .height(Space.touch)
                            .then(
                                if (day != null) {
                                    Modifier.clickable {
                                        chosen = day
                                        onDayChange?.invoke(day)
                                    }
                                } else {
                                    Modifier
                                },
                            ),
                    ) {
                        if (day != null) {
                            val on = day == chosen
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(Space.touch - Space.x2)
                                    .then(
                                        when {
                                            on -> Modifier.background(colors.onBackground, RoundedCornerShape(Space.radiusCheck))
                                            day == today -> Modifier.border(Space.strokeThin, colors.onSurfaceVariant, RoundedCornerShape(Space.radiusCheck))
                                            else -> Modifier
                                        },
                                    ),
                            ) {
                                Text(
                                    text = LocalDate.ofEpochDay(day).dayOfMonth.toString(),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (on) colors.background else colors.onBackground,
                                )
                            }
                        }
                    }
                }
            }
        }
        details(chosen)
        Row(Modifier.fillMaxWidth().padding(top = Space.x2), horizontalArrangement = Arrangement.End) {
            QuietButton(stringResource(Res.string.cancel), onCancel)
            QuietButton(stringResource(Res.string.set), enabled = canSet(chosen)) { onSet(chosen) }
        }
    }
}

@Composable
private fun ArrowWord(text: String, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(Space.touch).clickable(onClick = onClick),
    ) {
        Text(text = text, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** How an event repeats, in words: "every week". */
@Composable
fun repeatText(repeat: Repeat): String = stringResource(
    when (repeat) {
        Repeat.NONE -> Res.string.repeat_never
        Repeat.DAILY -> Res.string.repeat_daily
        Repeat.WEEKLY -> Res.string.repeat_weekly
        Repeat.MONTHLY -> Res.string.repeat_monthly
        Repeat.YEARLY -> Res.string.repeat_yearly
    },
)

/** Never, or every day, week, month or year, one to a line like the reminders. */
@Composable
fun RepeatChooser(current: Repeat, onPick: (Repeat) -> Unit, onCancel: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    ChooserPanel(onDismiss = onCancel) {
        Repeat.entries.forEach { choice ->
            Box(
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Space.touch)
                    .clickable { onPick(choice) },
            ) {
                Text(
                    text = repeatText(choice),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (choice == current) colors.onBackground else colors.onSurfaceVariant,
                )
            }
        }
    }
}

/** For a repeating event: whether a change or a delete is for this one time or for every time. */
@Composable
fun WhichTimesChooser(onPick: (onlyThisTime: Boolean) -> Unit, onCancel: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    ChooserPanel(onDismiss = onCancel) {
        listOf(true to Res.string.only_this_time, false to Res.string.every_time).forEach { (only, label) ->
            Box(
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Space.touch)
                    .clickable { onPick(only) },
            ) {
                Text(text = stringResource(label), style = MaterialTheme.typography.bodyLarge, color = colors.onBackground)
            }
        }
    }
}
