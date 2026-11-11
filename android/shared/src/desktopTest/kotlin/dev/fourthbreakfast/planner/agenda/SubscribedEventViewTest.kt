package dev.fourthbreakfast.planner.agenda

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import dev.fourthbreakfast.planner.core.Event
import dev.fourthbreakfast.planner.ui.BackStack
import dev.fourthbreakfast.planner.ui.LocalBackStack
import dev.fourthbreakfast.planner.ui.theme.PlannerTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(InternalComposeUiApi::class)
class SubscribedEventViewTest {
    private fun SemanticsNode.descendants(): List<SemanticsNode> = listOf(this) + children.flatMap { it.descendants() }

    @Test
    fun subscribedDetailsAreReadableWithoutEditOrDeleteControls() {
        val event =
            Event(
                id = "subscribed",
                title = "school class",
                day = 20_000,
                createdAt = 1,
                location = "classroom",
                notes = "bring notes",
                calendarSourceId = "school",
                calendarName = "school feed",
            )
        ImageComposeScene(900, 1000, Density(1f)) {
            CompositionLocalProvider(LocalBackStack provides BackStack()) {
                PlannerTheme {
                    EventEditor(event, onSave = { _, _ -> error("read-only event saved") }, onDelete = { error("read-only event deleted") }, onClose = {})
                }
            }
        }.use { scene ->
            repeat(10) {
                scene.render(System.nanoTime())
                Thread.sleep(30)
            }
            val nodes = scene.semanticsOwners.flatMap { it.rootSemanticsNode.descendants() }
            assertTrue(nodes.none { it.config.getOrNull(SemanticsProperties.EditableText) != null })
            val text = nodes.flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty() }.joinToString(" ") { it.text }
            assertTrue(text.contains("school class"))
            assertTrue(text.contains("school feed"))
            assertTrue(text.contains("classroom"))
            assertTrue(text.contains("bring notes"))
            val labels = nodes.flatMap { it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() }
            assertFalse(labels.any { it == "save" || it == "move to bin" })
        }
    }
}
