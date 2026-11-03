package dev.fourthbreakfast.planner.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import dev.fourthbreakfast.planner.agenda.today
import dev.fourthbreakfast.planner.core.LineKind
import dev.fourthbreakfast.planner.core.NoteLine
import dev.fourthbreakfast.planner.core.Priority
import dev.fourthbreakfast.planner.core.formatBody
import dev.fourthbreakfast.planner.ui.LocalBackStack
import dev.fourthbreakfast.planner.ui.LocalServices
import dev.fourthbreakfast.planner.ui.theme.PlannerTheme
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Draws the dashboard without showing a window, into PLANNER_RENDER, to look at. Skipped unless that folder is given. */
class RenderTest {
    @Test
    fun drawTheDashboard() {
        val out = System.getenv("PLANNER_RENDER")?.let(::File)
        assumeTrue(out != null)
        out!!.mkdirs()
        val app = DesktopApp(Files.createTempDirectory("planner-render").toFile(), "dev/fourthbreakfast/planner-render-test")
        val now = today()
        app.tasksViewModel.add("plan the autumn trip", null)
        app.tasksViewModel.add("call the dentist", null)
        app.tasksViewModel.add("buy oat milk", null)
        runBlocking {
            app.events.add("lunch with sam", now, 12 * 60 + 30, "the corner cafe", "")
            app.events.add("train to the coast", now + 1, 8 * 60 + 12, "central station", "", endMinute = 10 * 60 + 40)
            app.events.add("mum's birthday", now + 3, null, "", "")
        }
        var noteId: String? = null
        app.notesViewModel.add { id ->
            noteId = id
            app.notesViewModel.save(
                id,
                "packing list",
                formatBody(listOf(NoteLine(LineKind.CHECK, text = "passport"), NoteLine(LineKind.CHECK, checked = true, text = "charger"))),
            )
        }
        Thread.sleep(1_500)
        val trip = runBlocking { app.tasks.value.first { it.title == "plan the autumn trip" } }
        app.tasksViewModel.add("book the train tickets", trip.id)
        app.tasksViewModel.setPriority(trip, Priority.HIGH)
        app.dashboard.openTasks = setOf(trip.id)
        Thread.sleep(1_000)
        fun draw(name: String, dark: Boolean) {
            ImageComposeScene(1280, 820, Density(1f)) {
                CompositionLocalProvider(LocalServices provides app.services, LocalBackStack provides app.backStack) {
                    PlannerTheme(darkTheme = dark) {
                        Dashboard(app.dashboard, app.tasksViewModel, app.agendaViewModel, app.notesViewModel) { onClose ->
                            DesktopSettings(app.tasksViewModel, app.sync, app.version, onClose)
                        }
                    }
                }
            }.use { scene ->
                repeat(30) {
                    scene.render(it * 100_000_000L)
                    Thread.sleep(100)
                }
                File(out, "$name.png").writeBytes(scene.render(2_000_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes)
            }
        }
        draw("dashboard-dark", dark = true)
        draw("dashboard-light", dark = false)
        app.dashboard.openNote(noteId!!)
        draw("dashboard-note", dark = true)
    }
}

/**
 * Writes the installer's icon, desktop/icons/planner.ico, from the same drawing as the window's. Run by hand when the
 * icon changes, with PLANNER_ICON naming the file; skipped otherwise.
 */
class IconTest {
    @Test
    fun writeTheIcon() {
        val target = System.getenv("PLANNER_ICON")?.let(::File)
        assumeTrue(target != null)
        val sizes = listOf(16, 24, 32, 48, 64, 128, 256)
        val images = sizes.map { size ->
            val bytes = java.io.ByteArrayOutputStream()
            javax.imageio.ImageIO.write(appIcon(size), "png", bytes)
            bytes.toByteArray()
        }
        val out = java.io.ByteArrayOutputStream()
        fun short(value: Int) = out.write(byteArrayOf(value.toByte(), (value shr 8).toByte()))
        fun int(value: Int) = repeat(4) { out.write(value shr (8 * it)) }
        short(0)
        short(1)
        short(sizes.size)
        var offset = 6 + 16 * sizes.size
        sizes.forEachIndexed { i, size ->
            out.write(if (size >= 256) 0 else size)
            out.write(if (size >= 256) 0 else size)
            out.write(0)
            out.write(0)
            short(1)
            short(32)
            int(images[i].size)
            int(offset)
            offset += images[i].size
        }
        images.forEach { out.write(it) }
        target!!.writeBytes(out.toByteArray())
    }
}

/**
 * Fills a trial data folder from a backup file, for trying the app by hand with PLANNER_DATA pointing at the folder.
 * Run with PLANNER_SEED (the folder) and PLANNER_BACKUP (the file); skipped otherwise.
 */
class SeedTest {
    @Test
    fun fillTheTrialFolder() {
        val folder = System.getenv("PLANNER_SEED")?.let(::File)
        val backup = System.getenv("PLANNER_BACKUP")?.let(::File)
        assumeTrue(folder != null && backup != null)
        val app = DesktopApp(folder!!, "dev/fourthbreakfast/planner-seed-test")
        runBlocking { app.tasksViewModel.importBackup(backup!!.readText()) }
    }
}
