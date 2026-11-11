package dev.fourthbreakfast.planner.core

import java.util.concurrent.atomic.AtomicLong

/**
 * The time stamped on every change. It is the device's own clock, except that it never goes back to or before a time
 * it has already seen, here or from another device. So a device whose clock runs late still makes changes that count
 * as newer than what it last took in, and a change always lands after the one it replaced.
 */
class LogicalClock(private val wall: () -> Long = System::currentTimeMillis) {
    private val highest = AtomicLong(0)

    fun now(): Long {
        while (true) {
            val seen = highest.get()
            val next = maxOf(wall(), seen + 1)
            if (highest.compareAndSet(seen, next)) return next
        }
    }

    /** Takes in a time seen elsewhere: a stamp from another device, or the newest one already saved. */
    fun saw(time: Long) {
        highest.accumulateAndGet(time) { a, b -> maxOf(a, b) }
    }
}

/** The newest change already saved, for the clock to start from after the app starts. */
suspend fun newestChange(
    tasks: TaskStore,
    events: EventStore,
    notes: NoteStore,
    ledger: SyncLedger,
    habits: Store<Habit>? = null,
    habitChecks: Store<HabitCheck>? = null,
): Long = maxOf(
    tasks.all().maxOfOrNull { it.updatedAt } ?: 0,
    events.all().maxOfOrNull { it.updatedAt } ?: 0,
    notes.all().maxOfOrNull { it.updatedAt } ?: 0,
    ledger.gone().maxOfOrNull { it.at } ?: 0,
    habits?.all()?.maxOfOrNull { it.updatedAt } ?: 0,
    habitChecks?.all()?.maxOfOrNull { it.updatedAt } ?: 0,
)
