package dev.fourthbreakfast.planner.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fourthbreakfast.planner.core.Destination
import dev.fourthbreakfast.planner.core.Priority
import dev.fourthbreakfast.planner.core.Task
import dev.fourthbreakfast.planner.core.Tasks
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private const val SETTLE_MILLIS = 1100L

/** What the task screens can do, each change run in the background. */
class TasksViewModel(private val tasks: Tasks) : ViewModel() {
    val all: Flow<List<Task>> = tasks.all

    fun add(title: String, parentId: String?) {
        viewModelScope.launch { tasks.add(title, parentId) }
    }

    fun rename(id: String, title: String) {
        viewModelScope.launch { tasks.rename(id, title) }
    }

    fun setDone(task: Task, done: Boolean) {
        viewModelScope.launch { tasks.setDone(task.id, done) }
    }

    /** Tasks whose priority just changed, with the one they had, which still decides where they stand until things settle. */
    private val holds = MutableStateFlow<Map<String, Priority>>(emptyMap())
    val heldPriorities: StateFlow<Map<String, Priority>> = holds
    private var settling: Job? = null

    // a task stays put for a moment after its priority changes, so it can be changed again without chasing it around
    private fun hold(task: Task) {
        holds.value = holds.value + (task.id to (holds.value[task.id] ?: task.priority))
        settling?.cancel()
        settling = viewModelScope.launch {
            delay(SETTLE_MILLIS)
            holds.value = emptyMap()
        }
    }

    fun cyclePriority(task: Task) {
        hold(task)
        viewModelScope.launch { tasks.cyclePriority(task.id) }
    }

    fun setPriority(task: Task, priority: Priority) {
        hold(task)
        viewModelScope.launch { tasks.setPriority(task.id, priority) }
    }

    fun move(id: String, to: Destination) {
        viewModelScope.launch { tasks.move(id, to) }
    }

    fun moveToBin(task: Task) {
        viewModelScope.launch { tasks.moveToBin(task.id) }
    }

    fun restore(task: Task) {
        viewModelScope.launch { tasks.restore(task.id) }
    }

    fun deleteForever(task: Task) {
        viewModelScope.launch { tasks.deleteForever(task.id) }
    }

    fun emptyBin() {
        viewModelScope.launch { tasks.emptyBin() }
    }
}
