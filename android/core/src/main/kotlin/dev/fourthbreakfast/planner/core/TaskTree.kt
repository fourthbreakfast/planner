package dev.fourthbreakfast.planner.core

/** The saved shortlist includes each chosen open task's subtree, without changing parentage. */
fun shortlisted(tasks: List<Task>): List<Task> {
    val active = tasks.filterNot { it.inBin }
    val children = active.groupBy { it.parentId }
    val included = HashSet<String>()
    val waiting = ArrayDeque(active.filter { it.starred && !it.done })
    while (waiting.isNotEmpty()) {
        val task = waiting.removeFirst()
        if (included.add(task.id)) waiting.addAll(children[task.id].orEmpty())
    }
    return active.filter { it.id in included }
}

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

/** The task and everything inside it. A task is visited once, so a loop of parents, which sync can leave, ends. */
fun subtree(tasks: List<Task>, rootId: String): List<Task> {
    val children = tasks.groupBy { it.parentId }
    val result = ArrayList<Task>()
    val seen = HashSet<String>()

    fun visit(task: Task) {
        if (!seen.add(task.id)) return
        result += task
        children[task.id].orEmpty().forEach(::visit)
    }

    tasks.firstOrNull { it.id == rootId }?.let(::visit)
    return result
}

/** The tasks above this one, nearest first, stopping where a loop of parents would start again. */
fun ancestors(tasks: List<Task>, parentId: String?): List<Task> {
    val byId = tasks.associateBy { it.id }
    val seen = HashSet<String>()
    return generateSequence(parentId?.let(byId::get)) { task -> task.parentId?.let(byId::get) }
        .takeWhile { seen.add(it.id) }
        .toList()
}

/**
 * The tasks to bring back to the top level so that no task is inside itself. Two devices can each move one task into
 * the other before they sync, and both moves are kept; then each task is the other's parent and neither can be reached.
 * In each such loop the task changed last made it, so that one is let go. Every device picks the same one.
 */
fun loopBreaks(tasks: List<Task>): List<Task> {
    val byId = tasks.associateBy { it.id }
    val breaks = LinkedHashMap<String, Task>()
    for (start in tasks) {
        val path = LinkedHashSet<String>()
        var at: Task? = start
        while (at != null && path.add(at.id)) at = at.parentId?.let(byId::get)
        if (at == null) continue
        // at is where the walk met itself: the loop is the part of the path from there on
        val loop = path.dropWhile { it != at.id }.mapNotNull(byId::get)
        val newest = loop.maxWith(compareBy<Task>({ it.updatedAt }, { it.id }))
        breaks[newest.id] = newest
    }
    return breaks.values.toList()
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

    fun levels(taskId: String, seen: Set<String>): Int =
        children[taskId].orEmpty().filter { it.id !in seen }.maxOfOrNull { 1 + levels(it.id, seen + it.id) } ?: 0

    return levels(id, setOf(id))
}
