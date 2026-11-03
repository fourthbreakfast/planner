package dev.fourthbreakfast.planner.agenda

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import dev.fourthbreakfast.planner.resources.Res
import dev.fourthbreakfast.planner.resources.next_month
import dev.fourthbreakfast.planner.resources.previous_month
import dev.fourthbreakfast.planner.ui.theme.Space
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale
import org.jetbrains.compose.resources.stringResource

/** The day the person's weeks start on, from their language and region. */
fun firstDayOfWeek(locale: Locale = Locale.getDefault()): DayOfWeek = WeekFields.of(locale).firstDayOfWeek

/**
 * The days of [month] laid out a week to a row, the first row starting on [firstDay]: days as days since 1970, and
 * null for the room before the first of the month and after its last.
 */
fun monthCells(month: YearMonth, firstDay: DayOfWeek): List<Long?> {
    val first = month.atDay(1)
    val before = (first.dayOfWeek.value - firstDay.value + 7) % 7
    val days = (0 until month.lengthOfMonth()).map { first.toEpochDay() + it }
    val cells = List(before) { null } + days
    return cells + List((7 - cells.size % 7) % 7) { null }
}

/** The short names of the weekdays in order from [firstDay], lowercase: "mo tu we". */
fun weekdayNames(firstDay: DayOfWeek, locale: Locale = Locale.getDefault()): List<String> = (0 until 7).map {
    firstDay.plus(it.toLong()).getDisplayName(TextStyle.SHORT, locale).lowercase(locale).take(2)
}

/** "november 2026". */
fun monthTitle(month: YearMonth, locale: Locale = Locale.getDefault()): String =
    DateTimeFormatter.ofPattern("LLLL yyyy", locale).format(month).lowercase(locale)

/** The month a day falls in. */
fun monthOf(day: Long): YearMonth = YearMonth.from(LocalDate.ofEpochDay(day))

/**
 * A month as a grid: its name between the arrows that go back and forward, the weekday names, then the days. Today is
 * set in ink and bold, the chosen day sits on the aim colour, and a day with events has a dot under its number.
 */
@Composable
fun MonthGrid(
    month: YearMonth,
    today: Long,
    chosen: Long,
    busyDays: Set<Long>,
    onChoose: (Long) -> Unit,
    onMonth: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
    // on a computer an event can be dragged onto a day: where each day sits, and the day it would land on
    onDayPlaced: ((day: Long, bounds: Rect) -> Unit)? = null,
    dropDay: Long? = null,
) {
    val colors = MaterialTheme.colorScheme
    val firstDay = firstDayOfWeek()
    Column(modifier.fillMaxWidth().padding(horizontal = Space.rowPad)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Arrow(pointsLeft = true, label = stringResource(Res.string.previous_month)) { onMonth(month.minusMonths(1)) }
            Text(
                text = monthTitle(month),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.weight(1f).padding(horizontal = Space.x2),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Arrow(pointsLeft = false, label = stringResource(Res.string.next_month)) { onMonth(month.plusMonths(1)) }
        }
        Row(Modifier.fillMaxWidth()) {
            weekdayNames(firstDay).forEach { name ->
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.weight(1f).padding(vertical = Space.x1),
                )
            }
        }
        monthCells(month, firstDay).chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    Box(Modifier.weight(1f).height(Space.touch), contentAlignment = Alignment.Center) {
                        if (day != null) {
                            DayCell(
                                day,
                                day == today,
                                day == chosen || day == dropDay,
                                day in busyDays,
                                Modifier.onGloballyPositioned { onDayPlaced?.invoke(day, it.boundsInRoot()) },
                            ) { onChoose(day) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(day: Long, isToday: Boolean, isChosen: Boolean, busy: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val date = LocalDate.ofEpochDay(day)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .size(Space.touch)
            .background(if (isChosen) colors.secondaryContainer else colors.background)
            .clickable(onClick = onClick)
            .semantics {
                selected = isChosen
                contentDescription = dateText(day, day)
            },
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            color = if (isToday || isChosen) colors.onBackground else colors.onSurfaceVariant,
        )
        // a dot under a day that has something on it
        Canvas(Modifier.size(Space.x1)) {
            if (busy) drawCircle(colors.onSurfaceVariant, radius = size.minDimension / 2)
        }
    }
}

/** A thin chevron in a touch square, drawn like the other icons. */
@Composable
private fun Arrow(pointsLeft: Boolean, label: String, onClick: () -> Unit) {
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .heightIn(min = Space.touch)
            .widthIn(min = Space.touch)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
    ) {
        Canvas(Modifier.size(Space.icon)) {
            val w = size.width
            val h = size.height
            val tip = if (pointsLeft) w * 0.36f else w * 0.64f
            val back = if (pointsLeft) w * 0.60f else w * 0.40f
            val line = Space.strokeThin.toPx()
            drawLine(ink, Offset(back, h * 0.24f), Offset(tip, h * 0.5f), line, StrokeCap.Round)
            drawLine(ink, Offset(tip, h * 0.5f), Offset(back, h * 0.76f), line, StrokeCap.Round)
        }
    }
}
