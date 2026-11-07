package dev.fourthbreakfast.planner.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import dev.fourthbreakfast.planner.Requests
import dev.fourthbreakfast.planner.UiSettings
import dev.fourthbreakfast.planner.agenda.AgendaScreen
import dev.fourthbreakfast.planner.agenda.AgendaViewModel
import dev.fourthbreakfast.planner.agenda.today
import dev.fourthbreakfast.planner.core.Event
import dev.fourthbreakfast.planner.core.Events
import dev.fourthbreakfast.planner.core.LineKind
import dev.fourthbreakfast.planner.core.MemoryEventStore
import dev.fourthbreakfast.planner.core.MemoryNoteStore
import dev.fourthbreakfast.planner.core.MemoryTaskStore
import dev.fourthbreakfast.planner.core.Note
import dev.fourthbreakfast.planner.core.NoteLine
import dev.fourthbreakfast.planner.core.Notes
import dev.fourthbreakfast.planner.core.Priority
import dev.fourthbreakfast.planner.core.Task
import dev.fourthbreakfast.planner.core.Tasks
import dev.fourthbreakfast.planner.core.formatBody
import dev.fourthbreakfast.planner.notes.NoteEditor
import dev.fourthbreakfast.planner.notes.NotesScreen
import dev.fourthbreakfast.planner.notes.NotesViewModel
import dev.fourthbreakfast.planner.tasks.TaskList
import dev.fourthbreakfast.planner.tasks.TasksViewModel
import dev.fourthbreakfast.planner.ui.theme.PlannerTheme
import java.io.File
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Assume.assumeTrue
import org.junit.Test

private class RenderPrefs : Prefs {
    private val values = HashMap<String, Any?>()

    override fun getBoolean(key: String, default: Boolean) = values[key] as? Boolean ?: default
    override fun putBoolean(key: String, value: Boolean) {
        values[key] = value
    }
    override fun getInt(key: String, default: Int) = values[key] as? Int ?: default
    override fun putInt(key: String, value: Int) {
        values[key] = value
    }
    override fun getLong(key: String, default: Long) = values[key] as? Long ?: default
    override fun putLong(key: String, value: Long) {
        values[key] = value
    }
    override fun getString(key: String) = values[key] as? String
    override fun putString(key: String, value: String?) {
        values[key] = value
    }
}

/** Opt-in reference screenshots use fixtures only, never a person's database or preferences. */
class DesignRenderTest {
    @Test
    fun renderTabsAtNormalAndLargeFontSizes() {
        val folder = System.getenv("PLANNER_RENDER")?.let(::File)
        assumeTrue(folder != null)
        folder!!.mkdirs()
        for (fontScale in listOf(1f, 2f)) {
            for (tab in Tab.entries) {
                ImageComposeScene(720, 160, Density(2f, fontScale)) {
                    PlannerTheme { TabBar(tab, {}) }
                }.use { scene ->
                    repeat(3) { scene.render(it * 100_000_000L) }
                    File(folder, "tabs-$fontScale-${tab.name.lowercase()}.png")
                        .writeBytes(scene.render(500_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes)
                }
            }
        }
    }

    @Test
    fun renderPhoneScreensInBothThemes() {
        val folder = System.getenv("PLANNER_RENDER")?.let(::File)
        assumeTrue(folder != null)
        folder!!.mkdirs()
        val prefs = RenderPrefs().apply { putBoolean("peeked", true) }
        val services = PlannerServices(Requests(), UiSettings(prefs), prefs)
        val tasks = listOf(
            Task(id = "trip", title = "plan the autumn trip", priority = Priority.HIGH, createdAt = 1),
            Task(id = "tickets", title = "book the train tickets", parentId = "trip", createdAt = 2),
            Task(id = "report", title = "write the quarterly report", priority = Priority.MEDIUM, createdAt = 3),
            Task(id = "dentist", title = "call the dentist", priority = Priority.HIGH, createdAt = 4),
            Task(id = "milk", title = "buy oat milk", priority = Priority.LOW, createdAt = 5),
        )
        val taskStore = MemoryTaskStore().apply { state.value = tasks }
        val taskModel = TasksViewModel(Tasks(taskStore))
        val eventStore = MemoryEventStore().apply {
            state.value = listOf(
                Event(title = "lunch with sam", day = today(), minute = 750, location = "the corner cafe", createdAt = 1),
                Event(title = "train to the coast", day = today() + 1, minute = 492, endMinute = 640, location = "central station", createdAt = 2),
                Event(title = "mum's birthday", day = today() + 3, createdAt = 3),
            )
        }
        val agenda = AgendaViewModel(Events(eventStore))
        val packing = Note(
            id = "packing",
            title = "packing list",
            pinned = true,
            createdAt = 1,
            body = formatBody(
                listOf("passport", "charger", "sunscreen", "a book").mapIndexed { i, text ->
                    NoteLine(LineKind.CHECK, checked = i == 1, text = text)
                },
            ),
        )
        val noteStore = MemoryNoteStore().apply {
            state.value = listOf(
                packing,
                Note(
                    id = "meeting",
                    title = "meeting notes",
                    createdAt = 2,
                    body = formatBody(listOf(NoteLine(LineKind.BULLET, text = "keep the app simple"))),
                ),
            )
        }
        val notes = NotesViewModel(Notes(noteStore), prefs)
        for ((theme, dark) in listOf("dark" to true, "light" to false)) {
            for (screen in listOf("tasks", "agenda", "notes", "editor")) {
                val owner = object : androidx.lifecycle.LifecycleOwner {
                    override val lifecycle = androidx.lifecycle.LifecycleRegistry.createUnsafe(this).apply {
                        currentState = androidx.lifecycle.Lifecycle.State.RESUMED
                    }
                }
                ImageComposeScene(880, 1800, Density(2f)) {
                    CompositionLocalProvider(
                        androidx.lifecycle.compose.LocalLifecycleOwner provides owner,
                        LocalServices provides services,
                        LocalBackStack provides BackStack(),
                    ) {
                        PlannerTheme(darkTheme = dark) {
                            when (screen) {
                                "tasks" -> TaskList(tasks, 0, {}, {}, {}, taskModel)
                                "agenda" -> AgendaScreen(agenda, {}, {}, {})
                                "notes" -> NotesScreen(notes, {}, {}, {}, {})
                                else -> NoteEditor(packing, {}, false, { _, _, base, done -> done(base) }, {})
                            }
                        }
                    }
                }.use { scene ->
                    repeat(10) {
                        scene.render(it * 100_000_000L)
                        Thread.sleep(100)
                    }
                    File(folder, "phone-$screen-$theme.png").writeBytes(scene.render(2_000_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes)
                }
            }
        }
    }
}
