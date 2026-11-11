package dev.fourthbreakfast.planner.core

import java.util.UUID

/** A project references existing tasks; titles and completion stay owned by those tasks. */
data class Project(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val taskIds: Set<String> = emptySet(),
    val createdAt: Long,
    val updatedAt: Long = createdAt,
) {
    init {
        require(title.isNotBlank()) { "A project needs a title" }
    }
}

data class ProjectProgress(val completed: Int, val total: Int)

/** Includes descendants once even when both a parent and its child are explicitly linked. */
fun projectTasks(project: Project, tasks: List<Task>): List<Task> {
    val active = tasks.filterNot { it.inBin }
    val byId = active.associateBy { it.id }
    val children = active.groupBy { it.parentId }
    val included = linkedSetOf<String>()
    val pending = ArrayDeque(project.taskIds.sorted())
    while (pending.isNotEmpty()) {
        val id = pending.removeFirst()
        if (id !in byId || !included.add(id)) continue
        children[id].orEmpty().forEach { pending.addLast(it.id) }
    }
    return active.filter { it.id in included }
}

fun projectProgress(project: Project, tasks: List<Task>): ProjectProgress {
    val members = projectTasks(project, tasks)
    return ProjectProgress(members.count { it.done }, members.size)
}
