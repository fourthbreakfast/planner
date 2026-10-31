package dev.fourthbreakfast.planner.core

/** Open tasks come before done ones in every order. Ties keep the order they were placed in. */
fun sorted(tasks: List<Task>, order: SortOrder): List<Task> {
    val placed = compareBy<Task>({ it.position }, { it.createdAt })
    val byOrder = when (order) {
        SortOrder.TREE -> placed
        SortOrder.PRIORITY -> compareByDescending<Task> { it.priority }.then(placed)
    }
    return tasks.sortedWith(compareBy<Task> { it.done }.then(byOrder))
}

/** The tasks sharing a place in tree order, ignoring whether they are done. */
fun siblings(tasks: List<Task>, parentId: String?): List<Task> = tasks
    .filter { it.parentId == parentId }
    .sortedWith(compareBy({ it.position }, { it.createdAt }))

data class BinEntry(val task: Task, val insideCount: Int)

/** What was binned together with this task: the task and whatever inside it left at the same moment. */
fun binBatch(tasks: List<Task>, rootId: String): List<Task> {
    val root = tasks.firstOrNull { it.id == rootId } ?: return emptyList()
    return subtree(tasks, rootId).filter { it.deletedAt == root.deletedAt }
}

/** One entry per thing that was binned, newest first, with its subtasks counted inside it. */
fun binEntries(tasks: List<Task>): List<BinEntry> {
    val byId = tasks.associateBy { it.id }
    return tasks
        .filter { it.inBin }
        .filter { task -> task.parentId?.let(byId::get)?.deletedAt != task.deletedAt }
        .sortedByDescending { it.deletedAt }
        .map { BinEntry(it, binBatch(tasks, it.id).size - 1) }
}

/** The task and everything inside it. */
fun subtree(tasks: List<Task>, rootId: String): List<Task> {
    val children = tasks.groupBy { it.parentId }
    val result = ArrayList<Task>()

    fun visit(task: Task) {
        result += task
        children[task.id].orEmpty().forEach(::visit)
    }

    tasks.firstOrNull { it.id == rootId }?.let(::visit)
    return result
}

/** The tasks above this one, nearest first. */
fun ancestors(tasks: List<Task>, parentId: String?): List<Task> {
    val byId = tasks.associateBy { it.id }
    return generateSequence(parentId?.let(byId::get)) { task -> task.parentId?.let(byId::get) }.toList()
}

/** Main task, subtask, sub-subtask, sub-sub-subtask: four levels, counted from zero. Nothing goes deeper. */
const val MAX_DEPTH = 3

/** How many levels above this task: 0 for a main task. */
fun depthOf(tasks: List<Task>, id: String): Int {
    val task = tasks.firstOrNull { it.id == id } ?: return 0
    return ancestors(tasks, task.parentId).size
}

/** How many levels there are inside this task: 0 when it has no subtasks. */
fun height(tasks: List<Task>, id: String): Int {
    val children = tasks.groupBy { it.parentId }

    fun levels(taskId: String): Int = children[taskId].orEmpty().maxOfOrNull { 1 + levels(it.id) } ?: 0

    return levels(id)
}
