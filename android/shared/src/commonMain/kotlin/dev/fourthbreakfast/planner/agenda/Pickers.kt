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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.fourthbreakfast.planner.core.REMINDER_CHOICES
import dev.fourthbreakfast.planner.resources.Res
import dev.fourthbreakfast.planner.resources.all_day
import dev.fourthbreakfast.planner.resources.cancel
import dev.fourthbreakfast.planner.resources.hour
import dev.fourthbreakfast.planner.resources.minute
import dev.fourthbreakfast.planner.resources.reminder_10
import dev.fourthbreakfast.planner.resources.reminder_60
import dev.fourthbreakfast.planner.resources.reminder_at
import dev.fourthbreakfast.planner.resources.reminder_none
import dev.fourthbreakfast.planner.resources.reminder_other
import dev.fourthbreakfast.planner.resources.set
import dev.fourthbreakfast.planner.resources.time_start
import dev.fourthbreakfast.planner.resources.time_start_end
import dev.fourthbreakfast.planner.ui.QuietButton
import dev.fourthbreakfast.planner.ui.theme.Space
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale
import org.jetbrains.compose.resources.stringResource

private const val LAST_MINUTE = 24 * 60 - 1

/** "09:30", "09:30 – 10:45", or "all day". Twelve hour clocks get "am" and "pm". */
@Composable
fun timeRangeText(start: Int?, end: Int?, is24Hour: Boolean): String = when {
    start == null -> stringResource(Res.string.all_day)
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
                .padding(horizontal = Space.x4)
                .fillMaxWidth()
                .background(colors.surfaceVariant, RoundedCornerShape(Space.radiusCheck))
                .border(Space.strokeThin, colors.outline, RoundedCornerShape(Space.radiusCheck))
                .padding(Space.x4),
        ) { content() }
    }
}

/** What a reminder is called: none, at the time, or so many minutes before. */
@Composable
fun reminderText(minutes: Int): String = when (minutes) {
    0 -> stringResource(Res.string.reminder_at)
    10 -> stringResource(Res.string.reminder_10)
    60 -> stringResource(Res.string.reminder_60)
    else -> stringResource(Res.string.reminder_other, minutes)
}

/** The few reminders there are, one to a line, the chosen one in ink. Touching one picks it. */
@Composable
fun ReminderChooser(current: Int?, onPick: (Int?) -> Unit, onCancel: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    ChooserPanel(onDismiss = onCancel) {
        REMINDER_CHOICES.forEach { choice ->
            Box(
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Space.touch)
                    .clickable { onPick(choice) },
            ) {
                Text(
                    text = if (choice == null) stringResource(Res.string.reminder_none) else reminderText(choice),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (choice == current) colors.onBackground else colors.onSurfaceVariant,
                )
            }
        }
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
        val current = if (editingEnd) end else start
        val next = (hour ?: (current / 60)) * 60 + (minute ?: (current % 60))
        if (editingEnd) {
            end = maxOf(next, minOf(start + 5, LAST_MINUTE))
        } else {
            start = next
            if (end <= start) end = minOf(start + 60, LAST_MINUTE)
        }
    }

    ChooserPanel(onDismiss = onCancel) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf(0 to Res.string.all_day, 1 to Res.string.time_start, 2 to Res.string.time_start_end).forEach { (value, text) ->
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
fun DayChooser(initialDay: Long?, today: Long, onSet: (Long) -> Unit, onCancel: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val locale = Locale.getDefault()
    var chosen by remember { mutableStateOf(initialDay ?: today) }
    var month by remember { mutableStateOf(YearMonth.from(LocalDate.ofEpochDay(initialDay ?: today))) }
    val first = WeekFields.of(locale).firstDayOfWeek
    val offset = (month.atDay(1).dayOfWeek.value - first.value + 7) % 7
    val cells: List<Long?> = List(offset) { null } + (1..month.lengthOfMonth()).map { month.atDay(it).toEpochDay() }

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
                            .then(if (day != null) Modifier.clickable { chosen = day } else Modifier),
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
                repeat(7 - week.size) { Box(Modifier.weight(1f)) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = Space.x2), horizontalArrangement = Arrangement.End) {
            QuietButton(stringResource(Res.string.cancel), onCancel)
            QuietButton(stringResource(Res.string.set)) { onSet(chosen) }
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
