package dev.fourthbreakfast.planner.core

/** Units offered when entering a reminder offset. The stored value remains minutes. */
enum class ReminderUnit(val minutes: Int) {
    MINUTES(1),
    HOURS(60),
    DAYS(24 * 60),
}

/** A positive whole offset that fits the existing Int-minute field, or null for invalid input. */
fun customReminderMinutes(amount: String, unit: ReminderUnit): Int? {
    if (amount.isEmpty() || amount.any { it !in '0'..'9' }) return null
    val number = amount.toLongOrNull() ?: return null
    if (number !in 1..(Int.MAX_VALUE / unit.minutes).toLong()) return null
    return number.toInt() * unit.minutes
}

/** Use the largest exact unit, so reopening a custom reminder never rounds its value. */
fun reminderAmount(minutes: Int): Pair<Int, ReminderUnit> {
    val unit = ReminderUnit.entries.lastOrNull { minutes > 0 && minutes % it.minutes == 0 } ?: ReminderUnit.MINUTES
    return minutes / unit.minutes to unit
}
