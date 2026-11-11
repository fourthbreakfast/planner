package dev.fourthbreakfast.planner.agenda

import androidx.compose.runtime.CompositionLocalProvider
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
import dev.fourthbreakfast.planner.ui.BackStack
import dev.fourthbreakfast.planner.ui.LocalBackStack
import dev.fourthbreakfast.planner.ui.theme.PlannerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(InternalComposeUiApi::class)
class EventKeyboardTest {
    private fun SemanticsNode.descendants(): List<SemanticsNode> = listOf(this) + children.flatMap { it.descendants() }

    private fun withEditor(test: (ImageComposeScene, List<EventDraft>) -> Unit) {
        val saved = mutableListOf<EventDraft>()
        ImageComposeScene(900, 1000, Density(1f)) {
            CompositionLocalProvider(LocalBackStack provides BackStack()) {
                PlannerTheme { EventEditor(null, onSave = { draft, _ -> saved += draft }, onDelete = null, onClose = {}) }
            }
        }.use { scene ->
            scene.settle()
            test(scene, saved)
        }
    }

    private fun ImageComposeScene.settle() = repeat(10) {
        render(System.nanoTime())
        Thread.sleep(30)
    }

    private fun ImageComposeScene.fields() = semanticsOwners.flatMap { it.rootSemanticsNode.descendants() }
        .filter { it.config.getOrNull(SemanticsProperties.EditableText) != null }

    private fun ImageComposeScene.enter(control: Boolean = false) {
        for (type in listOf(KeyEventType.KeyDown, KeyEventType.KeyUp)) {
            sendKeyEvent(KeyEvent(Key.Enter, type, codePoint = 10, isCtrlPressed = control))
        }
        settle()
    }

    @Test
    fun enterInTheNameSavesExactlyOnce() = withEditor { scene, saved ->
        scene.fields().first().config[SemanticsActions.RequestFocus].action!!.invoke()
        assertTrue(scene.fields().first().config[SemanticsActions.SetText].action!!.invoke(AnnotatedString("meet sam")))
        scene.settle()
        scene.enter()
        assertEquals(listOf("meet sam"), saved.map { it.title })
    }

    @Test
    fun enterCannotSaveAnEmptyName() = withEditor { scene, saved ->
        scene.enter()
        assertTrue(saved.isEmpty())
    }

    @Test
    fun notesKeepNewlinesAndControlEnterSaves() = withEditor { scene, saved ->
        scene.fields().first().config[SemanticsActions.SetText].action!!.invoke(AnnotatedString("meet sam"))
        scene.settle()
        scene.fields().last().config[SemanticsActions.RequestFocus].action!!.invoke()
        scene.fields().last().config[SemanticsActions.SetText].action!!.invoke(AnnotatedString("bring tickets"))
        scene.settle()
        scene.enter()
        assertTrue(saved.isEmpty())
        scene.enter(control = true)
        assertEquals(1, saved.size)
        assertTrue(saved.single().notes.contains('\n'))
    }
}
