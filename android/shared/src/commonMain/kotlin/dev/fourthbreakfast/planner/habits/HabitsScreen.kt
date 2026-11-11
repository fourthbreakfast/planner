package dev.fourthbreakfast.planner.habits

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.fourthbreakfast.planner.agenda.rememberToday
import dev.fourthbreakfast.planner.agenda.weekdayNames
import dev.fourthbreakfast.planner.core.Habit
import dev.fourthbreakfast.planner.core.HabitCheck
import dev.fourthbreakfast.planner.core.habitProgress
import dev.fourthbreakfast.planner.resources.Res
import dev.fourthbreakfast.planner.resources.back
import dev.fourthbreakfast.planner.resources.event_name
import dev.fourthbreakfast.planner.resources.habit_active
import dev.fourthbreakfast.planner.resources.habit_add
import dev.fourthbreakfast.planner.resources.habit_archive
import dev.fourthbreakfast.planner.resources.habit_archived
import dev.fourthbreakfast.planner.resources.habit_completed
import dev.fourthbreakfast.planner.resources.habit_days
import dev.fourthbreakfast.planner.resources.habit_history_collapsed
import dev.fourthbreakfast.planner.resources.habit_history_expanded
import dev.fourthbreakfast.planner.resources.habit_history_hide
import dev.fourthbreakfast.planner.resources.habit_history_show
import dev.fourthbreakfast.planner.resources.habit_incomplete
import dev.fourthbreakfast.planner.resources.habit_not_scheduled
import dev.fourthbreakfast.planner.resources.habit_progress
import dev.fourthbreakfast.planner.resources.habit_streak_hint
import dev.fourthbreakfast.planner.resources.habits_empty
import dev.fourthbreakfast.planner.resources.habits_title
import dev.fourthbreakfast.planner.resources.rename
import dev.fourthbreakfast.planner.resources.restore
import dev.fourthbreakfast.planner.resources.save
import dev.fourthbreakfast.planner.tasks.TaskCheck
import dev.fourthbreakfast.planner.ui.BackButton
import dev.fourthbreakfast.planner.ui.BackHandler
import dev.fourthbreakfast.planner.ui.CheckBoxMark
import dev.fourthbreakfast.planner.ui.QuietButton
import dev.fourthbreakfast.planner.ui.ScreenHeading
import dev.fourthbreakfast.planner.ui.SettingsButton
import dev.fourthbreakfast.planner.ui.Tab
import dev.fourthbreakfast.planner.ui.TabBar
import dev.fourthbreakfast.planner.ui.theme.Space
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HabitsScreen(
    viewModel: HabitsViewModel,
    onClose: () -> Unit,
    onSelectTab: ((Tab) -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
    embedded: Boolean = false,
) {
    BackHandler(onBack = onClose)
    val habits by viewModel.all.collectAsStateWithLifecycle(initialValue = emptyList())
    val checks by viewModel.history.collectAsStateWithLifecycle(initialValue = emptyList())
    val today = rememberToday()
    var archived by rememberSaveable { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    val editing = habits.firstOrNull { it.id == editingId }
    var creating by rememberSaveable { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            Column {
                if (onSelectTab != null) TabBar(Tab.HABITS, onSelectTab, onLongPress = onOpenSettings)
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val compact = maxWidth < 360.dp * LocalDensity.current.fontScale
                    Column {
                        Row(Modifier.fillMaxWidth().padding(horizontal = Space.rowPad), verticalAlignment = Alignment.CenterVertically) {
                            if (embedded) {
                                Box(
                                    Modifier.weight(1f),
                                )
                            } else {
                                ScreenHeading(stringResource(Res.string.habits_title), Modifier.weight(1f).padding(start = Space.x3))
                            }
                            if (!compact) {
                                QuietButton(stringResource(if (archived) Res.string.habit_active else Res.string.habit_archived)) {
                                    archived =
                                        !archived
                                }
                            }
                            if (onOpenSettings != null) {
                                SettingsButton(onOpenSettings)
                            } else if (!embedded) {
                                BackButton(onClose)
                            }
                        }
                        if (compact) {
                            FlowRow(Modifier.fillMaxWidth().padding(horizontal = Space.gutter), horizontalArrangement = Arrangement.SpaceBetween) {
                                QuietButton(stringResource(if (archived) Res.string.habit_active else Res.string.habit_archived)) { archived = !archived }
                                QuietButton(stringResource(Res.string.habit_add)) { creating = true }
                            }
                            Text(
                                stringResource(Res.string.habit_streak_hint),
                                Modifier.padding(horizontal = Space.gutter),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        } else {
                            Row(Modifier.fillMaxWidth().padding(horizontal = Space.gutter), verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(Res.string.habit_streak_hint), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                QuietButton(stringResource(Res.string.habit_add)) { creating = true }
                            }
                        }
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            val shown = habits.filter { it.inBin == archived }
            if (shown.isEmpty()) {
                item {
                    Text(stringResource(Res.string.habits_empty), Modifier.padding(Space.gutter), style = MaterialTheme.typography.bodyMedium)
                }
            }
            items(shown, key = Habit::id) { habit ->
                val history = checks.filter { it.habitId == habit.id }
                val progress = remember(habit, history, today) { habitProgress(habit, history, today) }
                val completed = history.filter { it.day == today }.maxByOrNull { it.updatedAt }?.done == true
                val isExpanded = expanded == habit.id
                val historyAction = stringResource(if (isExpanded) Res.string.habit_history_hide else Res.string.habit_history_show)
                val historyState = stringResource(if (isExpanded) Res.string.habit_history_expanded else Res.string.habit_history_collapsed)
                Column(Modifier.fillMaxWidth().padding(horizontal = Space.gutter, vertical = Space.x2)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(
                            Modifier.weight(1f).heightIn(min = Space.touch)
                                .clickable(enabled = !archived, role = Role.Button, onClickLabel = historyAction) {
                                    expanded =
                                        if (isExpanded) null else habit.id
                                }
                                .semantics { if (!archived) stateDescription = historyState },
                        ) {
                            Text(habit.title, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                stringResource(Res.string.habit_progress, progress.current, progress.longest),
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.onSurfaceVariant,
                            )
                        }
                        if (archived) {
                            QuietButton(stringResource(Res.string.restore)) { viewModel.archive(habit.id, false) }
                        } else if (habit.due(today)) {
                            TaskCheck(completed, colors.onBackground, habit.title) { viewModel.setDone(habit.id, today, it, today) }
                        } else {
                            Text(stringResource(Res.string.habit_not_scheduled), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    if (expanded == habit.id && !archived) {
                        HabitHistory(habit, history, today) { day, done -> viewModel.setDone(habit.id, day, done, today) }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            QuietButton(stringResource(Res.string.rename)) { editingId = habit.id }
                            QuietButton(stringResource(Res.string.habit_archive)) { viewModel.archive(habit.id, true) }
                        }
                    }
                }
            }
        }
    }
    if (creating || editing != null) {
        val habit = editing
        HabitDialog(habit, onDismiss = {
            creating = false
            editingId = null
        }) { title, weekdays ->
            if (habit == null) viewModel.add(title, today, weekdays) else viewModel.update(habit.id, title, weekdays)
            creating = false
            editingId = null
        }
    }
}

@Composable
private fun HabitHistory(habit: Habit, history: List<HabitCheck>, today: Long, onSet: (Long, Boolean) -> Unit) {
    val done = history.groupBy { it.day }.mapValues { (_, values) -> values.maxBy { it.updatedAt }.done }
    val labels = weekdayNames(DayOfWeek.MONDAY)
    val scroll = rememberScrollState()
    LaunchedEffect(today, scroll.maxValue) { scroll.scrollTo(scroll.maxValue) }
    Row(Modifier.fillMaxWidth().horizontalScroll(scroll)) {
        for (day in maxOf(habit.startDay, today - 13)..today) {
            val date = LocalDate.ofEpochDay(day)
            val checked = done[day] == true
            val scheduled = habit.due(day)
            val description = date.toString() + ": " + stringResource(
                if (!scheduled) {
                    Res.string.habit_not_scheduled
                } else if (checked) {
                    Res.string.habit_completed
                } else {
                    Res.string.habit_incomplete
                },
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.widthIn(min = Space.touch).heightIn(min = Space.touch * 1.5f)
                    .toggleable(checked, enabled = scheduled, role = Role.Checkbox) { onSet(day, it) }
                    .semantics { contentDescription = description }
                    .padding(vertical = Space.x2),
            ) {
                Text(labels[date.dayOfWeek.value - 1], style = MaterialTheme.typography.labelMedium)
                Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.bodySmall)
                if (scheduled) CheckBoxMark(checked, MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HabitDialog(habit: Habit?, onDismiss: () -> Unit, onSave: (String, Set<Int>) -> Unit) {
    var title by rememberSaveable(habit?.id) { mutableStateOf(habit?.title.orEmpty()) }
    var weekdays by rememberSaveable(habit?.id) { mutableStateOf(habit?.weekdays ?: (1..7).toSet()) }
    val labels = weekdayNames(DayOfWeek.MONDAY)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.habits_title)) },
        text = {
            Column {
                TextField(title, { title = it }, singleLine = true, label = { Text(stringResource(Res.string.event_name)) })
                Text(stringResource(Res.string.habit_days), Modifier.padding(top = Space.x4), style = MaterialTheme.typography.bodyMedium)
                FlowRow(Modifier.fillMaxWidth(), maxItemsInEachRow = 4, horizontalArrangement = Arrangement.SpaceBetween) {
                    for (day in 1..7) {
                        val dayLabel = DayOfWeek.of(day).getDisplayName(TextStyle.FULL, Locale.getDefault())
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.widthIn(min = Space.touch).heightIn(min = Space.touch)
                                .toggleable(day in weekdays, role = Role.Checkbox) { weekdays = if (it) weekdays + day else weekdays - day }
                                .semantics { contentDescription = dayLabel },
                        ) {
                            Text(labels[day - 1], style = MaterialTheme.typography.labelMedium)
                            CheckBoxMark(day in weekdays, MaterialTheme.colorScheme.onBackground)
                        }
                    }
                }
            }
        },
        confirmButton = { QuietButton(stringResource(Res.string.save), title.isNotBlank() && weekdays.isNotEmpty()) { onSave(title, weekdays) } },
        dismissButton = { QuietButton(stringResource(Res.string.back), onDismiss) },
    )
}
