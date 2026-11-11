package dev.fourthbreakfast.planner.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.fourthbreakfast.planner.PlannerData
import dev.fourthbreakfast.planner.PlannerDatabase
import dev.fourthbreakfast.planner.Requests
import dev.fourthbreakfast.planner.UiSettings
import dev.fourthbreakfast.planner.core.SortOrder
import dev.fourthbreakfast.planner.core.Task
import dev.fourthbreakfast.planner.core.sorted
import dev.fourthbreakfast.planner.tasks.TasksViewModel
import dev.fourthbreakfast.planner.ui.BackStack
import dev.fourthbreakfast.planner.ui.DesktopPrefs
import dev.fourthbreakfast.planner.ui.LocalBackStack
import dev.fourthbreakfast.planner.ui.LocalServices
import dev.fourthbreakfast.planner.ui.PlannerServices
import dev.fourthbreakfast.planner.ui.theme.PlannerTheme
import java.io.File
import java.util.UUID
import java.util.prefs.Preferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskDragInteractionTest {
    private fun SemanticsNode.descendants(): List<SemanticsNode> = listOf(this) + children.flatMap { it.descendants() }

    private fun ImageComposeScene.row(title: String) = semanticsOwners.flatMap { it.rootSemanticsNode.descendants() }.first {
        it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == title } == true &&
            it.config.getOrNull(SemanticsActions.OnClick) != null
    }.boundsInRoot

    private suspend fun ImageComposeScene.settle() = repeat(8) {
        render(System.nanoTime())
        delay(25)
    }

    private suspend fun ImageComposeScene.drag(state: DashboardState, from: Offset, to: Offset, screenshot: String? = null): Pair<Boolean, List<String>> {
        var recognized = false
        var labels = emptyList<String>()
        sendPointerEvent(PointerEventType.Press, from, type = PointerType.Mouse, buttons = PointerButtons(isPrimaryPressed = true))
        for (step in 1..20) {
            sendPointerEvent(
                PointerEventType.Move,
                from + (to - from) * (step / 20f),
                type = PointerType.Mouse,
                buttons = PointerButtons(isPrimaryPressed = true),
            )
            render(System.nanoTime())
            delay(20)
            if (state.draggingTask) {
                recognized = true
                labels = semanticsOwners.flatMap { it.rootSemanticsNode.descendants() }.flatMap { node ->
                    node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
                }
            }
        }
        val folder = System.getenv("PLANNER_RENDER")?.let(::File)
        if (folder != null && screenshot != null) {
            folder.mkdirs()
            File(folder, "$screenshot.png").writeBytes(render(System.nanoTime()).encodeToData()!!.bytes)
        }
        sendPointerEvent(PointerEventType.Release, to, type = PointerType.Mouse, buttons = PointerButtons())
        settle()
        return recognized to labels
    }

    private fun withTasks(seed: suspend (PlannerData) -> Unit, test: suspend (PlannerData, DashboardState, ImageComposeScene) -> Unit) =
        runBlocking(Dispatchers.Main) {
            val database = Room.inMemoryDatabaseBuilder<PlannerDatabase>()
                .setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO).build()
            val node = "dev/fourthbreakfast/planner-drag-test/${UUID.randomUUID()}"
            try {
                val data = PlannerData.create(database).also { seed(it) }
                val prefs = DesktopPrefs(node)
                val services = PlannerServices(Requests(), UiSettings(prefs), prefs)
                val state = DashboardState()
                val viewModel = TasksViewModel(data.tasks)
                ImageComposeScene(700, 600, Density(1f)) {
                    val tasks by viewModel.all.collectAsState(initial = emptyList())
                    CompositionLocalProvider(LocalServices provides services, LocalBackStack provides BackStack()) {
                        PlannerTheme { TaskColumn(tasks, 0, state, viewModel) }
                    }
                }.use { scene ->
                    scene.settle()
                    test(data, state, scene)
                }
            } finally {
                database.close()
                Preferences.userRoot().node(node).removeNode()
            }
        }

    @Test
    fun draggingOntoTheMiddleNestsAndReleasesTheKeyboardGuard() = withTasks({ data ->
        data.tasks.add("alpha")
        data.tasks.add("beta")
        data.tasks.add("gamma")
    }) { data, state, scene ->
        val observation = scene.drag(state, scene.row("gamma").center, scene.row("alpha").center, "task-drag-nesting")
        assertTrue("Mouse movement never entered drag mode: ${observation.second}", observation.first)
        val tasks = runBlocking { data.tasks.all.first() }
        assertEquals("Visible destination: ${observation.second}", tasks.first { it.title == "alpha" }.id, tasks.first { it.title == "gamma" }.parentId)
        assertFalse(state.draggingTask)
    }

    @Test
    fun pullingLeftAtAnEdgeMovesAChildOutsideItsParent() = withTasks({ data ->
        data.tasks.add("project")
        data.tasks.add("next")
        val project = data.tasks.all.first().first { it.title == "project" }
        data.tasks.add("first step", project.id)
        data.tasks.add("moving step", project.id)
    }) { data, state, scene ->
        val project = runBlocking { data.tasks.all.first().first { it.title == "project" } }
        state.openTasks = setOf(project.id)
        scene.settle()
        val source = scene.row("moving step")
        val edge = scene.row("first step")
        val observation = scene.drag(state, source.center, Offset(source.center.x - 40f, edge.bottom - 1f), "task-drag-outdent")
        assertTrue("Mouse movement never entered drag mode: ${observation.second}", observation.first)
        val tasks = runBlocking { data.tasks.all.first() }
        assertEquals("Visible destination: ${observation.second}", null, tasks.first { it.title == "moving step" }.parentId)
        assertEquals(listOf("project", "moving step", "next"), sorted(tasks.filter { it.parentId == null }, SortOrder.PRIORITY).map(Task::title))
    }

    @Test
    fun aFilteredShortlistCannotReparentTasksByDragging() = withTasks({ data ->
        data.tasks.add("alpha", starred = true)
        data.tasks.add("gamma", starred = true)
    }) { data, state, scene ->
        state.shortlistOnly = true
        scene.settle()
        val observation = scene.drag(state, scene.row("gamma").center, scene.row("alpha").center)
        assertFalse("A partial tree must not enter drag mode", observation.first)
        assertEquals(listOf(null, null), runBlocking { data.tasks.all.first() }.map { it.parentId })
        assertFalse(state.draggingTask)
    }
}
