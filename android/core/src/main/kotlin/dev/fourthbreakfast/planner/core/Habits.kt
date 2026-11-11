package dev.fourthbreakfast.planner.core

import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow

data class Habit(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val startDay: Long,
    /** ISO weekdays: Monday is 1, Sunday is 7. */
    val weekdays: Set<Int> = (1..7).toSet(),
    val createdAt: Long,
    val updatedAt: Long = createdAt,
    val deletedAt: Long? = null,
) {
    val inBin: Boolean get() = deletedAt != null
    fun due(day: Long): Boolean = day >= startDay && LocalDate.ofEpochDay(day).dayOfWeek.value in weekdays
}

/** One independently synchronized record per habit/day, including an explicit unchecked state. */
data class HabitCheck(val id: String, val habitId: String, val day: Long, val done: Boolean, val createdAt: Long, val updatedAt: Long = createdAt)

data class HabitProgress(val current: Int, val longest: Int, val completed: Int)

/** Only scheduled days count; today's unfinished day can still be completed and does not end yesterday's streak. */
fun habitProgress(habit: Habit, checks: List<HabitCheck>, today: Long): HabitProgress {
    val done = checks.filter { it.habitId == habit.id }.groupBy { it.day }.mapValues { (_, versions) ->
        versions.maxWith(compareBy<HabitCheck>({ it.updatedAt }, { it.id })).done
    }
    var current = 0
    var longest = 0
    var completed = 0
    // Progress is computed over recorded completions, rather than walking an unbounded range of calendar days.
    val completedDays = done.filter { (day, complete) -> complete && day <= today && habit.due(day) }.keys.sorted()
    fun previousDue(day: Long): Long? {
        if (habit.weekdays.isEmpty()) return null
        var previous = day - 1
        repeat(7) {
            if (habit.due(previous)) return previous
            previous--
        }
        return null
    }
    var last: Long? = null
    var run = 0
    for (day in completedDays) {
        run = if (last != null && previousDue(day) == last) run + 1 else 1
        longest = maxOf(longest, run)
        last = day
        completed++
    }
    val lastExpected = if (habit.due(today) && done[today] == true) today else previousDue(today)
    if (lastExpected != null && last == lastExpected) current = run
    return HabitProgress(current, longest, completed)
}

class Habits(private val habits: Store<Habit>, private val checks: Store<HabitCheck>, private val clock: () -> Long = System::currentTimeMillis) {
    val all: Flow<List<Habit>> = habits.observeAll()
    val history: Flow<List<HabitCheck>> = checks.observeAll()

    suspend fun add(title: String, startDay: Long, weekdays: Set<Int>) {
        val clean = title.trim()
        val days = weekdays.filter { it in 1..7 }.toSet()
        if (clean.isBlank() || days.isEmpty()) return
        habits.insert(listOf(Habit(title = clean, startDay = startDay, weekdays = days, createdAt = clock())))
    }

    suspend fun update(id: String, title: String, weekdays: Set<Int>) = habits.transaction {
        val habit = habits.all().firstOrNull { it.id == id && !it.inBin } ?: return@transaction
        val clean = title.trim()
        val days = weekdays.filter { it in 1..7 }.toSet()
        if (clean.isBlank() || days.isEmpty()) return@transaction
        habits.update(listOf(habit.copy(title = clean, weekdays = days, updatedAt = clock())))
    }

    suspend fun setDone(id: String, day: Long, done: Boolean, today: Long = LocalDate.now().toEpochDay()) = checks.transaction {
        val habit = habits.all().firstOrNull { it.id == id && !it.inBin } ?: return@transaction
        if (day > today || !habit.due(day)) return@transaction
        val checkId = UUID.nameUUIDFromBytes("planner-habit:$id:$day".toByteArray(Charsets.UTF_8)).toString()
        val old = checks.all().firstOrNull { it.id == checkId }
        if (old?.done == done) return@transaction
        val now = clock()
        val value = HabitCheck(checkId, id, day, done, old?.createdAt ?: now, now)
        if (old == null) checks.insert(listOf(value)) else checks.update(listOf(value))
    }

    suspend fun archive(id: String, archived: Boolean) = habits.transaction {
        val habit = habits.all().firstOrNull { it.id == id } ?: return@transaction
        val now = clock()
        habits.update(listOf(habit.copy(deletedAt = now.takeIf { archived }, updatedAt = now)))
    }
}
