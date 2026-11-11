package dev.fourthbreakfast.planner.today

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import dev.fourthbreakfast.planner.core.MemoryTaskStore
import dev.fourthbreakfast.planner.core.Tasks
import dev.fourthbreakfast.planner.tasks.TasksViewModel
import dev.fourthbreakfast.planner.ui.theme.PlannerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(InternalComposeUiApi::class)
class TodayInteractionTest {
    private fun SemanticsNode.nodes(): List<SemanticsNode> = listOf(this) + children.flatMap { it.nodes() }

    @Test
    fun enterAddsRepeatedTasksWithoutLosingInputFocus() {
        val store = MemoryTaskStore()
        val model = TasksViewModel(Tasks(store))
        ImageComposeScene(720, 1000, Density(1f)) {
            val tasks by store.state.collectAsState()
            PlannerTheme(darkTheme = true) { TodayWorkspace(tasks, emptyList(), model, {}, {}) }
        }.use { scene ->
            fun settle() = repeat(12) {
                scene.render(System.nanoTime())
                Thread.sleep(25)
            }
            fun field() = scene.semanticsOwners.flatMap { it.rootSemanticsNode.nodes() }
                .first { it.config.getOrNull(SemanticsActions.SetText) != null }
            settle()
            assertTrue(field().config[SemanticsActions.RequestFocus].action!!.invoke())
            for (name in listOf("One", "Two", "Three")) {
                assertTrue(field().config[SemanticsActions.SetText].action!!.invoke(AnnotatedString(name)))
                settle()
                for (type in listOf(KeyEventType.KeyDown, KeyEventType.KeyUp)) {
                    scene.sendKeyEvent(KeyEvent(Key.Enter, type, codePoint = 10))
                }
                settle()
                assertTrue("The add field must keep focus after $name", field().config.getOrNull(SemanticsProperties.Focused) == true)
            }
            assertEquals(3, store.state.value.size)
            assertEquals(setOf("One", "Two", "Three"), store.state.value.map { it.title }.toSet())
        }
    }
}
