package dev.fourthbreakfast.planner.core

import java.util.UUID

enum class Priority {
    NONE,
    LOW,
    MEDIUM,
    HIGH,
    ;

    /** None, low, medium, high, and back to none. */
    fun next(): Priority = entries[(ordinal + 1) % entries.size]
}

data class Task(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val done: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long = createdAt,
    val parentId: String? = null,
    /** Only ranks the task among its siblings: a subtask never competes with its parent. */
    val priority: Priority = Priority.NONE,
    /** Set while the task is in the bin. Tasks binned together share the same value. */
    val deletedAt: Long? = null,
    /** Place among siblings, set by dragging. */
    val position: Double = 0.0,
) {
    val inBin: Boolean get() = deletedAt != null
}

enum class SortOrder { TREE, PRIORITY }

/** Where a moved task lands. With neither [beforeId] nor [afterId] it goes last among its new siblings. */
data class Destination(
    val parentId: String? = null,
    val beforeId: String? = null,
    val afterId: String? = null,
)

