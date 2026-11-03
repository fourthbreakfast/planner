package dev.fourthbreakfast.planner.agenda

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.fourthbreakfast.planner.Launch
import dev.fourthbreakfast.planner.core.Event
import dev.fourthbreakfast.planner.core.sortedEvents
import dev.fourthbreakfast.planner.resources.Res
import dev.fourthbreakfast.planner.resources.add_event
import dev.fourthbreakfast.planner.resources.all_day
import dev.fourthbreakfast.planner.resources.earlier
import dev.fourthbreakfast.planner.resources.nothing_planned
import dev.fourthbreakfast.planner.resources.open_event
import dev.fourthbreakfast.planner.resources.remove_task
import dev.fourthbreakfast.planner.resources.show_list
import dev.fourthbreakfast.planner.resources.show_month
import dev.fourthbreakfast.planner.resources.task_removed
import dev.fourthbreakfast.planner.resources.undo
import dev.fourthbreakfast.planner.resources.upcoming_events
import dev.fourthbreakfast.planner.ui.AddButton
import dev.fourthbreakfast.planner.ui.BackHandler
import dev.fourthbreakfast.planner.ui.BinButton
import dev.fourthbreakfast.planner.ui.DiscreetMessage
import dev.fourthbreakfast.planner.ui.LocalServices
import dev.fourthbreakfast.planner.ui.RowSwipe
import dev.fourthbreakfast.planner.ui.SwipeRow
import dev.fourthbreakfast.planner.ui.Tab
import dev.fourthbreakfast.planner.ui.TabBar
import dev.fourthbreakfast.planner.ui.TitleWithCount
import dev.fourthbreakfast.planner.ui.rememberIs24Hour
import dev.fourthbreakfast.planner.ui.rowKeyAt
import dev.fourthbreakfast.planner.ui.showUndo
import dev.fourthbreakfast.planner.ui.sweepAndHold
import dev.fourthbreakfast.planner.ui.theme.Space
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** The agenda: what is coming up, soonest first, with everything about each event on view. */
@Composable
fun AgendaScreen(viewModel: AgendaViewModel, onShowTasks: () -> Unit, onShowNotes: () -> Unit, onOpenSettings: () -> Unit) {
    val everything by viewModel.all.collectAsStateWithLifecycle(initialValue = emptyList())
    val events = remember(everything) { everything.filterNot { it.inBin } }
    val binned = remember(everything) { everything.filter { it.inBin }.sortedByDescending { it.deletedAt } }
    // 0 means closed, 1 a new event, 2 an existing one
    var editorMode by rememberSaveable { mutableStateOf(0) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    // the day a new event starts on, when it is added from a day of the month
    var newDay by rememberSaveable { mutableStateOf<Long?>(null) }
    var binOpen by rememberSaveable { mutableStateOf(false) }
    val editing = events.firstOrNull { it.id == editingId }
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val binnedMessage = stringResource(Res.string.task_removed)
    val undoLabel = stringResource(Res.string.undo)

    fun moveToBinWithUndo(id: String) {
        viewModel.moveToBin(id)
        scope.launch { if (snackbarHost.showUndo(binnedMessage, undoLabel)) viewModel.restore(id) }
    }

    val requests = LocalServices.current.requests
    val request = requests.pending
    LaunchedEffect(request, events) {
        when (request) {
            Launch.NewEvent -> {
                requests.clear()
                binOpen = false
                editingId = null
                newDay = null
                editorMode = 1
            }
            is Launch.OpenEvent -> if (events.any { it.id == request.id }) {
                requests.clear()
                binOpen = false
                editingId = request.id
                editorMode = 2
            }
            else -> Unit
        }
    }

    // the list, the form and the bin fade into one another
    val mode = if (editorMode != 0 && (editorMode == 1 || editing != null)) {
        1
    } else if (binOpen) {
        2
    } else {
        0
    }
    Crossfade(targetState = mode, modifier = Modifier.background(MaterialTheme.colorScheme.background), animationSpec = tween(150), label = "agenda") { shown ->
        when {
            shown == 1 -> EventEditor(
                initial = if (editorMode == 1) null else editing,
                newDay = newDay,
                onSave = { draft ->
                    if (editorMode == 1) viewModel.add(draft) else editingId?.let { viewModel.update(it, draft) }
                    editorMode = 0
                },
                onDelete = editing?.takeIf { editorMode == 2 }?.let { event ->
                    {
                        editorMode = 0
                        moveToBinWithUndo(event.id)
                    }
                },
                onClose = { editorMode = 0 },
            )
            shown == 2 -> AgendaBin(binned, viewModel, onClose = { binOpen = false })
            else -> AgendaList(
                events = events,
                binCount = binned.size,
                snackbarHost = snackbarHost,
                onShowTasks = onShowTasks,
                onShowNotes = onShowNotes,
                onOpenSettings = onOpenSettings,
                onOpenBin = { binOpen = true },
                onNew = { day ->
                    newDay = day
                    editingId = null
                    editorMode = 1
                },
                onOpen = { id ->
                    editingId = id
                    editorMode = 2
                },
                onRemove = ::moveToBinWithUndo,
            )
        }
    }
}

@Composable
private fun AgendaList(
    events: List<Event>,
    binCount: Int,
    snackbarHost: SnackbarHostState,
    onShowTasks: () -> Unit,
    onShowNotes: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenBin: () -> Unit,
    onNew: (day: Long?) -> Unit,
    onOpen: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    BackHandler(onBack = onShowTasks)
    val today = rememberToday()
    val rows = remember(events, today) { agendaRows(events, today) }
    val upcoming = upcomingCount(events, today)
    val is24Hour = rememberIs24Hour()
    val list = rememberLazyListState()
    val endSpace = Space.listEnd()
    val scope = rememberCoroutineScope()
    val swipe = remember(scope) { RowSwipe(scope, canGoLeft = false) }
    var listBounds by remember { mutableStateOf(Rect.Zero) }
    // the agenda is a list or a month, and stays as it was last left
    val prefs = LocalServices.current.prefs
    var asMonth by remember { mutableStateOf(prefs.getBoolean(PREF_MONTH, false)) }
    var chosen by rememberSaveable { mutableLongStateOf(today) }
    var month by rememberSaveable { mutableLongStateOf(today) }
    val busyDays = remember(events) { events.mapTo(HashSet()) { it.day } }
    val dayEvents = remember(events, chosen) { sortedEvents(events.filter { it.day == chosen }) }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .sweepAndHold(
                enabled = true,
                onSweepLeft = onShowNotes,
                onSweepRight = onShowTasks,
                // only events can be held, the day headings cannot
                rowAt = { at -> list.rowKeyAt(at, listBounds)?.let { key -> events.firstOrNull { it.id == key }?.id } },
                swipe = swipe,
                onSwipedRight = onRemove,
            ),
        snackbarHost = {
            SnackbarHost(snackbarHost) { data ->
                DiscreetMessage(data.visuals.message, data.visuals.actionLabel, data::performAction)
            }
        },
        floatingActionButton = { AddButton(stringResource(Res.string.add_event)) { onNew(if (asMonth) chosen else null) } },
        topBar = {
            Column {
                TabBar(
                    selected = Tab.AGENDA,
                    onSelect = {
                        when (it) {
                            Tab.TASKS -> onShowTasks()
                            Tab.NOTES -> onShowNotes()
                            Tab.AGENDA -> Unit
                        }
                    },
                    onLongPress = onOpenSettings,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = Space.gutter, end = Space.rowPad),
                ) {
                    TitleWithCount(
                        title = null,
                        count = upcoming,
                        countLabel = pluralStringResource(Res.plurals.upcoming_events, upcoming, upcoming),
                        modifier = Modifier.weight(1f),
                    )
                    ViewToggle(asMonth) {
                        asMonth = !asMonth
                        prefs.putBoolean(PREF_MONTH, asMonth)
                        if (asMonth) {
                            chosen = today
                            month = today
                        }
                    }
                    BinButton(binCount, onOpenBin)
                }
            }
        },
    ) { padding ->
        if (asMonth) {
            LazyColumn(
                state = list,
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .onGloballyPositioned { listBounds = it.boundsInRoot() },
            ) {
                item(key = "month") {
                    MonthGrid(
                        month = monthOf(month),
                        today = today,
                        chosen = chosen,
                        busyDays = busyDays,
                        onChoose = { chosen = it },
                        onMonth = { month = it.atDay(1).toEpochDay() },
                    )
                }
                item(key = "day") { DayHeading(dayText(chosen, today), chosen < today) }
                if (dayEvents.isEmpty()) {
                    item(key = "none") {
                        Text(
                            text = stringResource(Res.string.nothing_planned),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = Space.gutter, top = Space.x2),
                        )
                    }
                }
                items(dayEvents, key = { it.id }) { event ->
                    EventRow(
                        event = event,
                        earlier = event.day < today,
                        is24Hour = is24Hour,
                        onOpen = { onOpen(event.id) },
                        swipe = swipe,
                        onRemove = { onRemove(event.id) },
                        modifier = Modifier.animateItem(),
                    )
                }
                item(key = "end") { Box(Modifier.height(endSpace)) }
            }
        } else if (rows.isEmpty()) {
            Box(
                Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(Res.string.nothing_planned),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                state = list,
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .onGloballyPositioned { listBounds = it.boundsInRoot() },
            ) {
                items(rows, key = { it.key }) { row ->
                    when (row) {
                        is DayRow -> DayHeading(dayText(row.day, today), row.earlier)
                        is EarlierRow -> DayHeading(stringResource(Res.string.earlier), earlier = true, big = true)
                        is EventItem -> EventRow(
                            event = row.event,
                            earlier = row.earlier,
                            is24Hour = is24Hour,
                            onOpen = { onOpen(row.event.id) },
                            swipe = swipe,
                            onRemove = { onRemove(row.event.id) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
                // a third of the screen left empty under the last event, so the list can always be pulled up
                item(key = "end") { Box(Modifier.height(endSpace)) }
            }
        }
    }
}

@Composable
private fun DayHeading(text: String, earlier: Boolean, big: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = Space.gutter, top = if (big) Space.x6 else Space.x4, bottom = Space.x1),
    )
}

/** One event with everything on view: the time, the name, the place and the notes. A touch opens it, a swipe right bins it. */
@Composable
private fun EventRow(
    event: Event,
    earlier: Boolean,
    is24Hour: Boolean,
    onOpen: () -> Unit,
    swipe: RowSwipe,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val removeLabel = stringResource(Res.string.remove_task)
    val quiet = if (earlier) colors.onSurfaceVariant else colors.onBackground
    SwipeRow(
        key = event.id,
        swipe = swipe,
        removeLabel = removeLabel,
        renameLabel = "",
        modifier = modifier.semantics {
            customActions = listOf(
                CustomAccessibilityAction(removeLabel) {
                    onRemove()
                    true
                },
            )
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.background)
                .clickable(onClickLabel = stringResource(Res.string.open_event), onClick = onOpen)
                .heightIn(min = Space.touch)
                .padding(start = Space.gutter, end = Space.gutter, top = Space.x3, bottom = Space.x3),
        ) {
            val textScale = LocalDensity.current.fontScale.coerceIn(1f, 2f)
            Column(Modifier.width((if (is24Hour) Space.timeColumn else Space.timeColumn12) * textScale).padding(end = Space.x2)) {
                Text(
                    text = event.minute?.let { timeText(it, is24Hour) } ?: stringResource(Res.string.all_day),
                    style = if (event.minute != null) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelMedium,
                    color = if (event.minute != null) quiet else colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
                // when it ends, under the start
                event.endMinute?.let { end ->
                    Text(
                        text = "– " + timeText(end, is24Hour),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text(text = event.title, style = MaterialTheme.typography.bodyLarge, color = quiet)
                if (event.location.isNotEmpty()) {
                    Text(text = event.location, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                }
                if (event.notes.isNotEmpty()) {
                    Text(text = event.notes, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                }
            }
        }
    }
}

/** Whether the agenda shows the month, kept in the preferences on every device. */
const val PREF_MONTH = "agenda_month"

/** Switches the agenda between the list and the month: a little grid while it is a list, lines while it is a month. */
@Composable
fun ViewToggle(asMonth: Boolean, onClick: () -> Unit) {
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    val label = stringResource(if (asMonth) Res.string.show_list else Res.string.show_month)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(Space.touch)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
    ) {
        Canvas(Modifier.size(Space.icon)) {
            val w = size.width
            val h = size.height
            val line = Stroke(Space.strokeThin.toPx(), cap = StrokeCap.Round)
            if (asMonth) {
                // three lines, a list
                for (y in listOf(0.28f, 0.5f, 0.72f)) drawLine(ink, Offset(w * 0.18f, h * y), Offset(w * 0.82f, h * y), line.width, StrokeCap.Round)
            } else {
                // a page with a bar on top and a grid of days
                drawRect(ink, Offset(w * 0.14f, h * 0.18f), Size(w * 0.72f, h * 0.68f), style = line)
                drawLine(ink, Offset(w * 0.14f, h * 0.36f), Offset(w * 0.86f, h * 0.36f), line.width)
                for (x in listOf(0.38f, 0.62f)) {
                    for (y in listOf(0.52f, 0.70f)) drawCircle(ink, radius = line.width, center = Offset(w * x, h * y))
                }
            }
        }
    }
}
