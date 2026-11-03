package dev.fourthbreakfast.planner.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import dev.fourthbreakfast.planner.PlannerScreen
import dev.fourthbreakfast.planner.ui.LocalBackStack
import dev.fourthbreakfast.planner.ui.LocalServices
import dev.fourthbreakfast.planner.ui.theme.PlannerTheme
import java.io.File
import java.nio.file.Files
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Draws the window without showing it, into PLANNER_RENDER, to look at. Skipped unless that folder is given. */
class RenderTest {
    @Test
    fun drawTheWindow() {
        val out = System.getenv("PLANNER_RENDER")?.let(::File)
        assumeTrue(out != null)
        out!!.mkdirs()
        val app = DesktopApp(Files.createTempDirectory("planner-render").toFile(), "dev/fourthbreakfast/planner-render-test")
        app.tasksViewModel.add("plan the trip", null)
        app.tasksViewModel.add("buy milk", null)
        Thread.sleep(1_000)
        ImageComposeScene(460 * 2, 880 * 2, Density(2f)) {
            CompositionLocalProvider(LocalServices provides app.services, LocalBackStack provides app.backStack) {
                PlannerTheme(darkTheme = true) {
                    PlannerScreen(app.tasksViewModel, app.agendaViewModel, app.notesViewModel) { onClose ->
                        DesktopSettings(app.tasksViewModel, app.sync, app.version, onClose)
                    }
                }
            }
        }.use { scene ->
            repeat(10) {
                scene.render(it * 100_000_000L)
                Thread.sleep(100)
            }
            val image = scene.render(2_000_000_000L)
            File(out, "tasks.png").writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
        }
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
