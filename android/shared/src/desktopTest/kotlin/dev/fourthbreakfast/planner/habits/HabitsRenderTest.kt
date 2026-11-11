package dev.fourthbreakfast.planner.habits

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import dev.fourthbreakfast.planner.core.Habit
import dev.fourthbreakfast.planner.core.HabitCheck
import dev.fourthbreakfast.planner.core.Habits
import dev.fourthbreakfast.planner.core.MemoryStore
import dev.fourthbreakfast.planner.ui.BackStack
import dev.fourthbreakfast.planner.ui.LocalBackStack
import dev.fourthbreakfast.planner.ui.theme.PlannerTheme
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class HabitsRenderTest {
    private fun SemanticsNode.descendants(): List<SemanticsNode> = listOf(this) + children.flatMap { it.descendants() }

    @Test
    fun drawHabitsAndHistory() = runBlocking(Dispatchers.Main) {
        val out = System.getenv("PLANNER_RENDER")?.let(::File)
        out?.mkdirs()
        val habits = MemoryStore(Habit::id)
        val checks = MemoryStore(HabitCheck::id)
        val rules = Habits(habits, checks)
        val today = LocalDate.now().toEpochDay()
        rules.add("read for 20 minutes", today - 20, (1..7).toSet())
        rules.add("go outside", today - 10, (1..7).toSet())
        rules.add("stretch", today - 7, setOf(1, 3, 5))
        val first = habits.all().first()
        for (day in today - 5..today) rules.setDone(first.id, day, true, today)
        val viewModel = HabitsViewModel(rules)
        for ((width, fontScale) in listOf(400 to 1f, 320 to 1.7f)) {
            for (dark in listOf(false, true)) {
                ImageComposeScene(width, 850, Density(1f, fontScale)) {
                    CompositionLocalProvider(LocalBackStack provides BackStack()) {
                        PlannerTheme(darkTheme = dark) { HabitsScreen(viewModel, onClose = {}, onSelectTab = {}, onOpenSettings = {}) }
                    }
                }.use { scene ->
                    var frame = 0L
                    suspend fun settle() = repeat(15) {
                        scene.render(frame++ * 100_000_000L)
                        delay(30)
                    }
                    settle()
                    fun nodes() = scene.semanticsOwners.flatMap { it.rootSemanticsNode.descendants() }
                    val tabs = nodes().filter { it.config.getOrNull(SemanticsProperties.Role) == Role.Tab }
                    assertTrue("All five destinations must stay visible", tabs.size == 5)
                    for (tab in tabs) {
                        val bounds = tab.boundsInRoot
                        assertTrue("Tabs need full touch targets", bounds.width >= 48f && bounds.height >= 48f)
                        assertTrue(
                            "Tabs must be visible without scrolling",
                            bounds.left >= 0f && bounds.right <= width && bounds.top >= 0f && bounds.bottom <= 850f,
                        )
                    }
                    assertTrue(tabs.count { it.config.getOrNull(SemanticsProperties.Selected) == true } == 1)
                    val checkbox = nodes().first {
                        it.config.getOrNull(SemanticsProperties.Role) == Role.Checkbox &&
                            it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(first.title) == true
                    }
                    assertTrue("Daily completion needs a full touch target", checkbox.boundsInRoot.width >= 48f && checkbox.boundsInRoot.height >= 48f)
                    val title = nodes().first {
                        it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == first.title } == true &&
                            it.config.getOrNull(SemanticsActions.OnClick) != null
                    }
                    assertTrue(title.config.getOrNull(SemanticsProperties.Role) == Role.Button)
                    assertTrue(title.config.getOrNull(SemanticsProperties.StateDescription) == "history collapsed")
                    assertTrue(title.config[SemanticsActions.OnClick].action!!.invoke())
                    settle()
                    assertTrue(nodes().any { it.config.getOrNull(SemanticsProperties.StateDescription) == "history expanded" })
                    val suffix = (if (fontScale > 1f) "large-text-" else "") + (if (dark) "dark" else "light")
                    if (out != null) File(out, "habits-$suffix.png").writeBytes(scene.render(frame * 100_000_000L).encodeToData()!!.bytes)
                    val add = nodes().first {
                        it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == "add habit" } == true &&
                            it.config.getOrNull(SemanticsActions.OnClick) != null
                    }
                    assertTrue(add.config[SemanticsActions.OnClick].action!!.invoke())
                    settle()
                    for (day in DayOfWeek.entries) {
                        val label = day.getDisplayName(TextStyle.FULL, Locale.getDefault())
                        val control = nodes().first { it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(label) == true }
                        assertTrue(control.config.getOrNull(SemanticsProperties.Role) == Role.Checkbox)
                        val bounds = control.boundsInRoot
                        assertTrue("$day needs a full touch target", bounds.width >= 48f && bounds.height >= 48f)
                        assertTrue(
                            "$day must be visible without scrolling",
                            bounds.left >= 0f && bounds.right <= width && bounds.top >= 0f && bounds.bottom <= 850f,
                        )
                    }
                    if (out != null) File(out, "habits-dialog-$suffix.png").writeBytes(scene.render(frame * 100_000_000L).encodeToData()!!.bytes)
                }
            }
        }
    }
}
