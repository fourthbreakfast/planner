package dev.fourthbreakfast.planner.agenda

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.fourthbreakfast.planner.core.Event
import dev.fourthbreakfast.planner.core.Task

@Composable
fun TaskScheduleEditor(task: Task, agenda: AgendaViewModel, onClose: () -> Unit) {
    val events by agenda.all.collectAsStateWithLifecycle(initialValue = null)
    val loaded = events ?: return
    val existing = loaded.firstOrNull { it.taskId == task.id && !it.inBin }
    EventEditor(
        initial = existing ?: Event(title = task.title, day = today(), createdAt = task.createdAt, taskId = task.id),
        onSave = { draft, _ ->
            agenda.scheduleTask(task.id, draft)
            onClose()
        },
        onDelete = existing?.let { event ->
            { _: Boolean ->
                agenda.moveToBin(event.id)
                onClose()
            }
        },
        onCompleteTask = existing?.let { event ->
            {
                agenda.completeTask(event.id)
                onClose()
            }
        },
        onClose = onClose,
    )
}
