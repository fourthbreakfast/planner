package dev.fourthbreakfast.planner.core

/**
 * A place between two neighbours in a list kept in order by a number: halfway between them, one before the first, or
 * one after the last. Only the moved thing changes, so a move on one device and a rename of a neighbour on another
 * both survive sync. Null when the two are too close to fit anything between, and the list has to be numbered again.
 */
fun placeBetween(before: Double?, after: Double?): Double? = when {
    before == null && after == null -> 1.0
    before == null -> after!! - 1
    after == null -> before + 1
    after - before < 1e-9 -> null
    else -> (before + after) / 2
}
