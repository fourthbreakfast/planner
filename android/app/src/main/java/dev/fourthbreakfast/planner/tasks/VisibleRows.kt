package dev.fourthbreakfast.planner.tasks

import dev.fourthbreakfast.planner.core.Destination
import dev.fourthbreakfast.planner.core.MAX_DEPTH
import dev.fourthbreakfast.planner.core.height
import dev.fourthbreakfast.planner.core.SortOrder
import dev.fourthbreakfast.planner.core.Task
import dev.fourthbreakfast.planner.core.ancestors
import dev.fourthbreakfast.planner.core.sorted
import dev.fourthbreakfast.planner.core.subtree

sealed interface ListRow {
    val key: String
}

data class TaskNode(
    val task: Task,
    val depth: Int,
    val childCount: Int,
    val doneChildCount: Int,
    val collapsed: Boolean,
) : ListRow {
    override val key get() = task.id
}

/** An invisible row of about a task and a half at the very end of the list. Dropping on it makes a task top-level and last. */
data object EndRow : ListRow {
    override val key get() = "end-row"
}

/** Which part of a row a dragged task is over. */
enum class DropZone { BEFORE, INTO, AFTER }

/**
 * Lays the tasks out as visible rows, each followed by what is inside it. Priority only ranks
 * a task among its siblings, so a subtask never leaves its parent. A task whose parent is
 * missing still shows, so it can never become unreachable.
 */
fun arrange(tasks: List<Task>, order: SortOrder, collapsedIds: Collection<String>): List<ListRow> {
    val ids = tasks.mapTo(HashSet()) { it.id }
    val children = sorted(tasks, order).groupBy { task -> task.parentId?.takeIf { it in ids } }
    val rows = ArrayList<ListRow>(tasks.size)

    fun visit(task: Task, depth: Int) {
        val kids = children[task.id].orEmpty()
        val collapsed = kids.isNotEmpty() && task.id in collapsedIds
        rows += TaskNode(task, depth, kids.size, kids.count { it.done }, collapsed)
        if (!collapsed) kids.forEach { visit(it, depth + 1) }
    }

    children[null].orEmpty().forEach { visit(it, 0) }
    return rows
}

/** Where a drop lands, and how deep in the list that is, so the drop line can be drawn at that depth. */
data class Drop(val destination: Destination, val depth: Int)

/**
 * Where a task dropped on this part of this row lands, or null if it would land inside itself.
 * Below the last of a group of subtasks there is a choice of depth: inside the parent, or out of it
 * right after it, or out of several parents at once. The task keeps its own depth unless the finger
 * has moved sideways, by [shift] levels, to ask for another one.
 */
fun resolveDrop(rows: List<ListRow>, tasks: List<Task>, taskId: String, targetKey: String, zone: DropZone, shift: Int = 0): Drop? {
    if (targetKey == EndRow.key) return Drop(Destination(), 0)
    val moving = subtree(tasks, taskId).mapTo(HashSet()) { it.id }
    // the task being moved is out of the way: it does not count as a neighbour of anything
    val visible = rows.filter { it !is TaskNode || it.task.id !in moving }
    val index = visible.indexOfFirst { it.key == targetKey }
    if (index < 0) return null
    val target = visible[index] as? TaskNode ?: return null
    val task = target.task
    val next = visible.getOrNull(index + 1) as? TaskNode
    val ownDepth = rows.filterIsInstance<TaskNode>().firstOrNull { it.task.id == taskId }?.depth ?: 0
    // the deepest level this task, with everything inside it, can land on
    val deepest = MAX_DEPTH - height(tasks, taskId)
    fun fits(depth: Int, drop: Drop) = if (depth <= deepest) drop else null
    return when (zone) {
        DropZone.BEFORE -> fits(target.depth, Drop(Destination(task.parentId, beforeId = task.id), target.depth))
        DropZone.INTO -> fits(target.depth + 1, Drop(Destination(parentId = task.id), target.depth + 1))
        DropZone.AFTER -> if (next != null && next.depth > target.depth) {
            // an open parent's next row is its first child, so that is where this lands
            fits(target.depth + 1, Drop(Destination(parentId = task.id, beforeId = next.task.id), target.depth + 1))
        } else {
            val lowest = next?.depth ?: 0
            if (lowest > deepest) return null
            val depth = (ownDepth + shift).coerceIn(lowest, minOf(target.depth, deepest))
            val anchor = if (depth == target.depth) task else ancestors(tasks, task.parentId)[target.depth - depth - 1]
            Drop(Destination(anchor.parentId, afterId = anchor.id), depth)
        }
    }
}

