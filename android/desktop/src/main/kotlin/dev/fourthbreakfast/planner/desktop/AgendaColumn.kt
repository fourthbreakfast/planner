package dev.fourthbreakfast.planner.desktop

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.style.TextOverflow
import dev.fourthbreakfast.planner.agenda.DayRow
import dev.fourthbreakfast.planner.agenda.EarlierRow
import dev.fourthbreakfast.planner.agenda.EventItem
import dev.fourthbreakfast.planner.agenda.MonthGrid
import dev.fourthbreakfast.planner.agenda.PREF_MONTH
import dev.fourthbreakfast.planner.agenda.agendaRows
import dev.fourthbreakfast.planner.agenda.busyDays
import dev.fourthbreakfast.planner.agenda.dateText
import dev.fourthbreakfast.planner.agenda.dayText
import dev.fourthbreakfast.planner.agenda.monthOf
import dev.fourthbreakfast.planner.agenda.rememberToday
import dev.fourthbreakfast.planner.agenda.repeatText
import dev.fourthbreakfast.planner.agenda.timeText
import dev.fourthbreakfast.planner.agenda.upcomingCount
import dev.fourthbreakfast.planner.core.Event
import dev.fourthbreakfast.planner.core.Repeat
import dev.fourthbreakfast.planner.core.occurrences
import dev.fourthbreakfast.planner.core.span
import dev.fourthbreakfast.planner.resources.Res
import dev.fourthbreakfast.planner.resources.add_event
import dev.fourthbreakfast.planner.resources.all_day
import dev.fourthbreakfast.planner.resources.earlier
import dev.fourthbreakfast.planner.resources.nothing_planned
import dev.fourthbreakfast.planner.resources.open_event
import dev.fourthbreakfast.planner.resources.remove_task
import dev.fourthbreakfast.planner.resources.show_list
import dev.fourthbreakfast.planner.resources.show_month
import dev.fourthbreakfast.planner.resources.until_day
import dev.fourthbreakfast.planner.ui.BinButton
import dev.fourthbreakfast.planner.ui.LocalServices
import dev.fourthbreakfast.planner.ui.rememberIs24Hour
import dev.fourthbreakfast.planner.ui.theme.Space
import org.jetbrains.compose.resources.stringResource

/** The agenda, as a list of days or as a month with the chosen day's events under it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AgendaColumn(events: List<Event>, binCount: Int, state: DashboardState, onRemove: (String) -> Unit, onMove: (event: Event, from: Long, to: Long) -> Unit) {
    val today = rememberToday()
    val prefs = LocalServices.current.prefs
    var asMonth by remember { mutableStateOf(prefs.getBoolean(PREF_MONTH, false)) }
    var month by remember { mutableStateOf(today) }
    val is24Hour = rememberIs24Hour()
    val rows = remember(events, today) { agendaRows(events, today) }
    val busyDays = remember(events, month) { busyDays(events, monthOf(month).atDay(1).toEpochDay() - 7, monthOf(month).atEndOfMonth().toEpochDay() + 7) }
    val dayEvents = remember(events, state.chosenDay) { occurrences(events, state.chosenDay, state.chosenDay) }
    // a selected event is a key of its id and the day of that time of it, as a repeating one shows more than once
    state.visibleEvents = if (asMonth) dayEvents.map { it.key } else rows.filterIsInstance<EventItem>().map { it.key }
    // an event dragged out of the day under the month lands on whichever day it is let go over
    val drag = remember { EventDrag() }

    Column(Modifier.fillMaxSize()) {
        ColumnHeader("agenda", upcomingCount(events, today), state.focused == Section.AGENDA) {
            HeaderButton(stringResource(if (asMonth) Res.string.show_list else Res.string.show_month), {
                asMonth = !asMonth
                prefs.putBoolean(PREF_MONTH, asMonth)
                if (asMonth) {
                    state.chosenDay = today
                    month = today
                }
            }) { if (asMonth) listLines(it) else calendar(it) }
            HeaderButton(stringResource(Res.string.add_event), { state.newEvent(if (asMonth) state.chosenDay else null) }) { plus(it) }
            BinButton(binCount, { state.panel = Panel.AgendaBin })
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            if (asMonth) {
                item(key = "month") {
                    MonthGrid(
                        month = monthOf(month),
                        today = today,
                        chosen = state.chosenDay,
                        busyDays = busyDays,
                        onChoose = {
                            state.chosenDay = it
                            state.focused = Section.AGENDA
                        },
                        onMonth = { month = it.atDay(1).toEpochDay() },
                        onDayPlaced = { day, bounds -> drag.days[day] = bounds },
                        dropDay = drag.over,
                    )
                }
                item(key = "day") { Heading(dayText(state.chosenDay, today)) }
                if (dayEvents.isEmpty()) item(key = "none") { Quiet(stringResource(Res.string.nothing_planned)) }
                items(dayEvents, key = {
                    it.key
                }) { time -> EventLine(time.event, time.start, time.end < today, today, is24Hour, state, onRemove, drag, onMove) }
            } else {
                if (rows.isEmpty()) item(key = "none") { Quiet(stringResource(Res.string.nothing_planned)) }
                items(rows, key = { it.key }) { row ->
                    when (row) {
                        is DayRow -> Heading(dayText(row.day, today))
                        is EarlierRow -> Heading(stringResource(Res.string.earlier))
                        is EventItem -> EventLine(row.event, row.day, row.earlier, today, is24Hour, state, onRemove)
                    }
                }
            }
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = Space.gutter, top = Space.x4, bottom = Space.x1),
    )
}

/** One event: the time, the name, the place. A click selects it, a double click or enter opens it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EventLine(
    event: Event,
    day: Long,
    earlier: Boolean,
    today: Long,
    is24Hour: Boolean,
    state: DashboardState,
    onRemove: (String) -> Unit,
    drag: EventDrag? = null,
    onMove: (event: Event, from: Long, to: Long) -> Unit = { _, _, _ -> },
) {
    val key = "${event.id}@$day"
    val openLabel = stringResource(Res.string.open_event)
    val binLabel = stringResource(Res.string.remove_task)
    var rowCorner by remember { mutableStateOf(Offset.Zero) }
    val colors = MaterialTheme.colorScheme
    val (hover, hovered) = rememberHover()
    val selected = state.focused == Section.AGENDA && state.selectedEvent == key
    ContextMenuArea(items = {
        listOf(
            ContextMenuItem(openLabel) { state.openEvent(key) },
            ContextMenuItem(binLabel) { onRemove(event.id) },
        )
    }) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Desk.row)
                .background(
                    when {
                        selected -> colors.secondaryContainer
                        hovered -> colors.secondaryContainer.copy(alpha = 0.5f)
                        else -> colors.background
                    },
                )
                .hoverable(hover)
                .combinedClickable(
                    onClick = {
                        state.focused = Section.AGENDA
                        state.selectedEvent = key
                    },
                    onDoubleClick = { state.openEvent(key) },
                )
                .onGloballyPositioned { rowCorner = it.positionInRoot() }
                .then(
                    if (drag == null) {
                        Modifier
                    } else {
                        Modifier.pointerInput(key) {
                            var at = Offset.Zero
                            detectDragGestures(
                                onDragStart = { at = rowCorner + it },
                                onDrag = { change, amount ->
                                    change.consume()
                                    at += amount
                                    drag.over = drag.dayAt(at)
                                },
                                onDragEnd = {
                                    drag.over?.takeIf { it != day }?.let { onMove(event, day, it) }
                                    drag.over = null
                                },
                                onDragCancel = { drag.over = null },
                            )
                        }
                    },
                )
                .padding(start = Space.gutter, end = Space.x1, top = Space.x2, bottom = Space.x2),
        ) {
            val ink = if (earlier) colors.onSurfaceVariant else colors.onBackground
            Column(Modifier.width(if (is24Hour) Space.timeColumn else Space.timeColumn12)) {
                Text(
                    text = event.minute?.let { timeText(it, is24Hour) } ?: stringResource(Res.string.all_day),
                    style = if (event.minute != null) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelMedium,
                    color = if (event.minute != null) ink else colors.onSurfaceVariant,
                    maxLines = 1,
                )
                event.endMinute?.let { Text("– " + timeText(it, is24Hour), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant) }
            }
            Column(Modifier.weight(1f)) {
                Text(event.title, style = MaterialTheme.typography.bodyLarge, color = ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (event.location.isNotEmpty()) Text(event.location, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                if (event.span > 0) {
                    Text(
                        stringResource(Res.string.until_day, dateText(event.day + event.span, today)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
                if (event.repeat != Repeat.NONE) Text(repeatText(event.repeat), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
            if (hovered) BinButton(0, { onRemove(event.id) })
        }
    }
}

/** Where the days of the month sit on screen, and which one a dragged event is over. */
class EventDrag {
    val days = HashMap<Long, Rect>()
    var over by mutableStateOf<Long?>(null)

    fun dayAt(point: Offset): Long? = days.entries.firstOrNull { it.value.contains(point) }?.key
}
