package dev.fourthbreakfast.planner.notes

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import dev.fourthbreakfast.planner.core.LineKind
import dev.fourthbreakfast.planner.core.Note
import dev.fourthbreakfast.planner.core.NoteLine
import dev.fourthbreakfast.planner.core.formatBody
import dev.fourthbreakfast.planner.core.parseBody
import dev.fourthbreakfast.planner.ui.BackStack
import dev.fourthbreakfast.planner.ui.LocalBackStack
import dev.fourthbreakfast.planner.ui.theme.PlannerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteToolbarTest {
    private fun SemanticsNode.descendants(): List<SemanticsNode> = listOf(this) + children.flatMap { it.descendants() }

    @Test
    fun mouseFormattingKeepsASelectionAcrossSeveralLines() {
        val body = formatBody(listOf("first", "second", "third").map { NoteLine(text = it) })
        var saved = body
        ImageComposeScene(900, 700, Density(1f)) {
            CompositionLocalProvider(LocalBackStack provides BackStack()) {
                PlannerTheme {
                    NoteEditor(
                        Note(title = "test", body = body, createdAt = 0),
                        onPin = {},
                        toolbarAbove = false,
                        onSave = { _, changed, _, onSaved ->
                            saved = changed
                            onSaved(1)
                        },
                        onClose = {},
                    )
                }
            }
        }.use { scene ->
            var frame = 0L
            fun settle() = repeat(15) {
                scene.render(frame++ * 100_000_000L)
                Thread.sleep(40)
            }
            fun nodes() = scene.semanticsOwners.flatMap { it.rootSemanticsNode.descendants() }
            settle()
            val field = nodes().first { it.config.getOrNull(SemanticsProperties.EditableText)?.text?.contains("first") == true }
            val text = field.config[SemanticsProperties.EditableText].text
            assertTrue(field.config[SemanticsActions.SetSelection].action!!.invoke(1, text.length, true))
            settle()
            val button = nodes().first { it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("bulleted list") == true }
            val position = button.boundsInRoot.center
            scene.sendPointerEvent(PointerEventType.Press, position, type = PointerType.Mouse)
            scene.sendPointerEvent(PointerEventType.Release, position, type = PointerType.Mouse)
            settle()
            val after = nodes().first { it.config.getOrNull(SemanticsProperties.EditableText)?.text?.contains("first") == true }
            assertEquals("Formatting should keep the original selection", TextRange(1, text.length), after.config[SemanticsProperties.TextSelectionRange])
            assertTrue(
                "Clicking bullets must format every selected line: $saved; selection=" + nodes().first {
                    it.config.getOrNull(SemanticsProperties.EditableText)?.text?.contains("first") == true
                }.config.getOrNull(SemanticsProperties.TextSelectionRange),
                parseBody(saved).all { it.kind == LineKind.BULLET },
            )
        }
    }
}
