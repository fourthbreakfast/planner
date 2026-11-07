package dev.fourthbreakfast.planner.agenda

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import dev.fourthbreakfast.planner.Requests
import dev.fourthbreakfast.planner.UiSettings
import dev.fourthbreakfast.planner.core.Events
import dev.fourthbreakfast.planner.core.MemoryEventStore
import dev.fourthbreakfast.planner.ui.LocalServices
import dev.fourthbreakfast.planner.ui.PlannerServices
import dev.fourthbreakfast.planner.ui.Prefs
import dev.fourthbreakfast.planner.ui.theme.PlannerTheme
import java.io.File
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Assume.assumeTrue
import org.junit.Test

private class MemoryPrefs : Prefs {
    private val values = HashMap<String, Any?>()

    override fun getBoolean(key: String, default: Boolean) = values[key] as? Boolean ?: default

    override fun putBoolean(key: String, value: Boolean) = values.set(key, value)

    override fun getInt(key: String, default: Int) = values[key] as? Int ?: default

    override fun putInt(key: String, value: Int) = values.set(key, value)

    override fun getLong(key: String, default: Long) = values[key] as? Long ?: default

    override fun putLong(key: String, value: Long) = values.set(key, value)

    override fun getString(key: String) = values[key] as? String

    override fun putString(key: String, value: String?) = values.set(key, value)
}

/** Draws the agenda as a month, without a window, into PLANNER_RENDER to look at. Skipped unless that folder is given. */
class AgendaRenderTest {
    @Test
    fun drawTheMonth() {
        val out = System.getenv("PLANNER_RENDER")?.let(::File)
        assumeTrue(out != null)
        out!!.mkdirs()
        val prefs = MemoryPrefs().apply { putBoolean("agenda_month", true) }
        val events = Events(MemoryEventStore())
        val now = today()
        runBlocking {
            events.add("lunch with sam", now, 12 * 60 + 30, "the corner cafe", "")
            events.add("train to the coast", now + 1, 8 * 60 + 12, "central station", "", endMinute = 10 * 60 + 40)
            events.add("mum's birthday", now + 3, null, "", "")
        }
        val viewModel = AgendaViewModel(events)
        for ((name, dark) in listOf("month-dark" to true, "month-light" to false)) {
            ImageComposeScene(440 * 2, 900 * 2, Density(2f)) {
                CompositionLocalProvider(LocalServices provides PlannerServices(Requests(), UiSettings(prefs), prefs)) {
                    PlannerTheme(darkTheme = dark) { AgendaScreen(viewModel, {}, {}, {}) }
                }
            }.use { scene ->
                repeat(10) {
                    scene.render(it * 100_000_000L)
                    Thread.sleep(100)
                }
                File(out, "$name.png").writeBytes(scene.render(2_000_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes)
            }
        }
    }

    @Test
    fun drawEventForm() {
        val out = System.getenv("PLANNER_RENDER")?.let(::File)
        assumeTrue(out != null)
        out!!.mkdirs()
        val prefs = MemoryPrefs()
        val event = dev.fourthbreakfast.planner.core.Event(
            title = "train to the coast",
            day = today(),
            minute = 8 * 60 + 12,
            endMinute = 10 * 60 + 40,
            reminder = 45,
            createdAt = 0,
            repeat = dev.fourthbreakfast.planner.core.Repeat.WEEKLY,
        )
        for (dark in listOf(true, false)) {
            ImageComposeScene(880, 1800, Density(2f)) {
                CompositionLocalProvider(
                    LocalServices provides PlannerServices(Requests(), UiSettings(prefs), prefs),
                    dev.fourthbreakfast.planner.ui.LocalBackStack provides dev.fourthbreakfast.planner.ui.BackStack(),
                ) {
                    PlannerTheme(darkTheme = dark) { EventEditor(event, onSave = { _, _ -> }, onDelete = null, onClose = {}) }
                }
            }.use { scene ->
                repeat(10) {
                    scene.render(it * 100_000_000L)
                    Thread.sleep(100)
                }
                File(out, "event-form-" + (if (dark) "dark" else "light") + ".png")
                    .writeBytes(scene.render(2_000_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes)
            }
        }
    }
}
