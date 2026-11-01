package dev.fourthbreakfast.planner.core

/**
 * The lines of the home screen widget: today's events, soonest first, then the open main tasks, most urgent first.
 * When there are more than [max], the last line says how many are left out.
 */
fun widgetLines(events: List<Event>, tasks: List<Task>, today: Long, max: Int, allDay: String, more: (Int) -> String, time: (Int) -> String): List<String> {
    val eventLines = sortedEvents(events.filter { !it.inBin && it.day == today })
        .map { (it.minute?.let(time) ?: allDay).padEnd(9) + it.title }
    val taskLines = sorted(tasks.filter { !it.inBin && !it.done && it.parentId == null }, SortOrder.PRIORITY)
        .map { "·".padEnd(9) + it.title }
    val lines = eventLines + taskLines
    return if (lines.size <= max) lines else lines.take(max - 1) + more(lines.size - max + 1)
}
