package dev.fourthbreakfast.planner.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fourthbreakfast.planner.core.Backups
import dev.fourthbreakfast.planner.core.Destination
import dev.fourthbreakfast.planner.core.Priority
import dev.fourthbreakfast.planner.core.Task
import dev.fourthbreakfast.planner.core.Tasks
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class TasksViewModel(private val tasks: Tasks, private val backups: Backups) : ViewModel() {
    val all: Flow<List<Task>> = tasks.all

    fun add(title: String, parentId: String?) {
        viewModelScope.launch { tasks.add(title, parentId) }
    }

    suspend fun exportBackup(): String = backups.export()

    suspend fun importBackup(text: String): Int = backups.import(text)

    fun rename(id: String, title: String) {
        viewModelScope.launch { tasks.rename(id, title) }
    }

    fun setDone(task: Task, done: Boolean) {
        viewModelScope.launch { tasks.setDone(task.id, done) }
    }

    fun cyclePriority(task: Task) {
        viewModelScope.launch { tasks.cyclePriority(task.id) }
    }

    fun setPriority(task: Task, priority: Priority) {
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
