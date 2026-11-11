package dev.fourthbreakfast.planner.habits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fourthbreakfast.planner.core.Habits
import kotlinx.coroutines.launch

class HabitsViewModel(private val habits: Habits) : ViewModel() {
    val all = habits.all
    val history = habits.history

    fun add(title: String, startDay: Long, weekdays: Set<Int>) {
        viewModelScope.launch { habits.add(title, startDay, weekdays) }
    }

    fun update(id: String, title: String, weekdays: Set<Int>) {
        viewModelScope.launch { habits.update(id, title, weekdays) }
    }

    fun setDone(id: String, day: Long, done: Boolean, today: Long) {
        viewModelScope.launch { habits.setDone(id, day, done, today) }
    }

    fun archive(id: String, archived: Boolean) {
        viewModelScope.launch { habits.archive(id, archived) }
    }
}
