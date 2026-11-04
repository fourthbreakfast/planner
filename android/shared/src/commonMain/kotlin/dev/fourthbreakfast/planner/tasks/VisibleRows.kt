package dev.fourthbreakfast.planner.tasks

import dev.fourthbreakfast.planner.core.Destination
import dev.fourthbreakfast.planner.core.MAX_DEPTH
import dev.fourthbreakfast.planner.core.SortOrder
import dev.fourthbreakfast.planner.core.Task
import dev.fourthbreakfast.planner.core.height
import dev.fourthbreakfast.planner.core.siblings
import dev.fourthbreakfast.planner.core.sorted
import dev.fourthbreakfast.planner.core.subtree

sealed interface ListRow {
    val key: String
}

data class TaskNode(val task: Task, val depth: Int, val childCount: Int, val doneChildCount: Int, val collapsed: Boolean) : ListRow {
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
 * Where a task dropped on this part of this row lands, or null if it cannot go there. Every place means one thing:
 * the middle of a row puts the task inside it, at the end, and a line between two rows puts it just above the
 * lower one, at that row's depth. Below the very last row it becomes the last main task.
 */
fun resolveDrop(rows: List<ListRow>, tasks: List<Task>, taskId: String, targetKey: String, zone: DropZone): Drop? {
    if (targetKey == EndRow.key) return Drop(Destination(), 0)
    val moving = subtree(tasks, taskId).mapTo(HashSet()) { it.id }
    // the task being moved is out of the way: it does not count as a neighbour of anything
    val visible = rows.filter { it !is TaskNode || it.task.id !in moving }
    val index = visible.indexOfFirst { it.key == targetKey }
    if (index < 0) return null
    val target = visible[index] as? TaskNode ?: return null
    // the deepest level this task, with everything inside it, can land on
    val deepest = MAX_DEPTH - height(tasks, taskId)
    fun fits(depth: Int, drop: Drop) = if (depth <= deepest) drop else null
    fun above(row: TaskNode) = fits(row.depth, Drop(Destination(row.task.parentId, beforeId = row.task.id), row.depth))
    return when (zone) {
        DropZone.BEFORE -> above(target)
        DropZone.INTO -> fits(target.depth + 1, Drop(Destination(parentId = target.task.id), target.depth + 1))
        DropZone.AFTER -> {
            val next = visible.getOrNull(index + 1) as? TaskNode
            if (next != null) above(next) else Drop(Destination(), 0)
        }
    }
}

/**
 * In priority mode a task cannot leave its colour, so a drop only decides its place among the tasks of the same
 * colour: dropped above all the reds, a yellow becomes the first yellow. It lands on the nearest one at or below
 * where it was dropped, or after the last of them.
 */
fun rankedDestination(tasks: List<Task>, taskId: String, to: Destination): Destination {
    val task = tasks.firstOrNull { it.id == taskId } ?: return to
    val shown = sorted(siblings(tasks, to.parentId).filter { it.id != taskId }, SortOrder.PRIORITY)
    val slot = when {
        to.beforeId != null -> shown.indexOfFirst { it.id == to.beforeId }.takeIf { it >= 0 } ?: shown.size
        to.afterId != null -> shown.indexOfFirst { it.id == to.afterId } + 1
        else -> shown.size
    }
    fun alike(other: Task) = other.priority == task.priority && other.done == task.done
    shown.drop(slot).firstOrNull(::alike)?.let { return Destination(to.parentId, beforeId = it.id) }
    shown.lastOrNull(::alike)?.let { return Destination(to.parentId, afterId = it.id) }
    return to
}

/** Where the drop line is drawn: along which edge of which row, starting at which depth. */
data class DropMark(val key: String, val zone: DropZone, val depth: Int)

/**
 * The line for a drop in priority mode, drawn where the task will really end up and not under the finger: a green
 * dragged to the very top shows its line at the top of the greens. It sits above the sibling that will come right
 * after the task, or below the last sibling and everything inside it.
 */
fun rankedMark(rows: List<ListRow>, tasks: List<Task>, taskId: String, to: Destination, depth: Int): DropMark? {
    val task = tasks.firstOrNull { it.id == taskId } ?: return null
    val shown = sorted(siblings(tasks, to.parentId).filter { it.id != taskId }, SortOrder.PRIORITY)
    val beforeId = rankedDestination(tasks, taskId, to).beforeId
    val below = shown.firstOrNull { it.id == beforeId && it.priority == task.priority && it.done == task.done }
        ?: shown.firstOrNull { it.done > task.done || (it.done == task.done && it.priority < task.priority) }
    if (below != null) return DropMark(below.id, DropZone.BEFORE, depth)
    val moving = subtree(tasks, taskId).mapTo(HashSet()) { it.id }
    val visible = rows.filterIsInstance<TaskNode>().filter { it.task.id !in moving }
    val last = shown.lastOrNull() ?: return to.parentId?.let { DropMark(it, DropZone.AFTER, depth) }
    val index = visible.indexOfFirst { it.task.id == last.id }
    if (index < 0) return null
    val next = visible.drop(index + 1).firstOrNull { it.depth <= visible[index].depth }
    return if (next != null) DropMark(next.key, DropZone.BEFORE, depth) else DropMark(visible.last().key, DropZone.AFTER, depth)
}

/**
 * Which part of a row a point is over, from how far down the row it is, 0 at its top and 1 at its bottom: the top and
 * bottom edges put a task beside the row, the middle inside it.
 */
fun dropZoneAt(fraction: Float): DropZone = when {
    fraction < 0.28f -> DropZone.BEFORE
    fraction > 0.72f -> DropZone.AFTER
    else -> DropZone.INTO
}
