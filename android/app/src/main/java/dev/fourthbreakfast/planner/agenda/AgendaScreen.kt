package dev.fourthbreakfast.planner.agenda

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.fourthbreakfast.planner.R
import dev.fourthbreakfast.planner.core.Event
import dev.fourthbreakfast.planner.tasks.AddButton
import dev.fourthbreakfast.planner.tasks.BinButton
import dev.fourthbreakfast.planner.tasks.DiscreetMessage
import dev.fourthbreakfast.planner.tasks.RowSwipe
import dev.fourthbreakfast.planner.tasks.SwipeRow
import dev.fourthbreakfast.planner.tasks.Tab
import dev.fourthbreakfast.planner.tasks.TabBar
import dev.fourthbreakfast.planner.tasks.TitleWithCount
import dev.fourthbreakfast.planner.tasks.rowKeyAt
import dev.fourthbreakfast.planner.tasks.sweepAndHold
import dev.fourthbreakfast.planner.ui.theme.Space
import kotlinx.coroutines.launch

/** The agenda: what is coming up, soonest first, with everything about each event on view. */
@Composable
fun AgendaScreen(viewModel: AgendaViewModel, onShowTasks: () -> Unit, onShowNotes: () -> Unit) {
    val everything by viewModel.all.collectAsStateWithLifecycle(initialValue = emptyList())
    val events = remember(everything) { everything.filterNot { it.inBin } }
    val binned = remember(everything) { everything.filter { it.inBin }.sortedByDescending { it.deletedAt } }
    // 0 means closed, 1 a new event, 2 an existing one
    var editorMode by rememberSaveable { mutableStateOf(0) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var binOpen by rememberSaveable { mutableStateOf(false) }
    val editing = events.firstOrNull { it.id == editingId }
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val binnedMessage = stringResource(R.string.task_removed)
    val undoLabel = stringResource(R.string.undo)

    fun moveToBinWithUndo(id: String) {
        viewModel.moveToBin(id)
        scope.launch {
            snackbarHost.currentSnackbarData?.dismiss()
            val result = snackbarHost.showSnackbar(message = binnedMessage, actionLabel = undoLabel, duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) viewModel.restore(id)
        }
    }

    when {
        editorMode != 0 && (editorMode == 1 || editing != null) -> EventEditor(
            initial = if (editorMode == 1) null else editing,
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
        binOpen -> AgendaBin(binned, viewModel, onClose = { binOpen = false })
        else -> AgendaList(
            events = events,
            binCount = binned.size,
            snackbarHost = snackbarHost,
            onShowTasks = onShowTasks,
            onShowNotes = onShowNotes,
            onOpenBin = { binOpen = true },
            onNew = {
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

@Composable
private fun AgendaList(
    events: List<Event>,
    binCount: Int,
    snackbarHost: SnackbarHostState,
    onShowTasks: () -> Unit,
    onShowNotes: () -> Unit,
    onOpenBin: () -> Unit,
    onNew: () -> Unit,
    onOpen: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    BackHandler(onBack = onShowTasks)
    val today = today()
    val rows = remember(events, today) { agendaRows(events, today) }
    val upcoming = upcomingCount(events, today)
    val is24Hour = rememberIs24Hour()
    val list = rememberLazyListState()
    val endSpace = Space.listEnd()
    val scope = rememberCoroutineScope()
    val swipe = remember(scope) { RowSwipe(scope, canGoLeft = false) }
    var listBounds by remember { mutableStateOf(Rect.Zero) }

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
        floatingActionButton = { AddButton(stringResource(R.string.add_event), onNew) },
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
                        countLabel = pluralStringResource(R.plurals.upcoming_events, upcoming, upcoming),
                        modifier = Modifier.weight(1f),
                    )
                    BinButton(binCount, onOpenBin)
                }
            }
        },
    ) { padding ->
        if (rows.isEmpty()) {
            Box(
                Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.nothing_planned),
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
                        is EarlierRow -> DayHeading(stringResource(R.string.earlier), earlier = true, big = true)
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
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (earlier) 0.7f else 1f),
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
    val removeLabel = stringResource(R.string.remove_task)
    val quiet = if (earlier) colors.onSurfaceVariant else colors.onBackground
    SwipeRow(
        key = event.id,
        swipe = swipe,
        removeLabel = removeLabel,
        renameLabel = "",
        modifier = modifier.semantics {
            customActions = listOf(CustomAccessibilityAction(removeLabel) { onRemove(); true })
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.background)
                .clickable(onClickLabel = stringResource(R.string.open_event), onClick = onOpen)
                .heightIn(min = Space.touch)
                .padding(start = Space.gutter, end = Space.gutter, top = Space.x3, bottom = Space.x3),
        ) {
            Column(Modifier.width(Space.timeColumn)) {
                Text(
                    text = event.minute?.let { timeText(it, is24Hour) } ?: stringResource(R.string.all_day),
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
