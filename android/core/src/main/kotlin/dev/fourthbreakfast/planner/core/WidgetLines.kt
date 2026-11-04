package dev.fourthbreakfast.planner.core

/**
 * The lines of the home screen widget: today's events, soonest first, then the open main tasks, most urgent first.
 * When there are more than [max], the last line says how many are left out.
 */
fun widgetLines(events: List<Event>, tasks: List<Task>, today: Long, max: Int, allDay: String, more: (Int) -> String, time: (Int) -> String): List<String> {
    // every time of an event that is on today; one that began on an earlier day is all day by now
    val eventLines = occurrences(events.filter { !it.inBin }, today, today)
        .map { (it.event.minute?.takeIf { _ -> it.start == today }?.let(time) ?: allDay).padEnd(9) + it.event.title }
    val taskLines = sorted(tasks.filter { !it.inBin && !it.done && it.parentId == null }, SortOrder.PRIORITY)
        .map { "·".padEnd(9) + it.title }
    val lines = eventLines + taskLines
    return if (lines.size <= max) lines else lines.take(max - 1) + more(lines.size - max + 1)
}
