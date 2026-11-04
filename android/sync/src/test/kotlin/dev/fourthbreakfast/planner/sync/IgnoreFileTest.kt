package dev.fourthbreakfast.planner.sync

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Test

class IgnoreFileTest {
    @Test
    fun aHiddenIgnoreFileIsStillWritten() {
        val folder = Files.createTempDirectory("planner-ignore").toFile()
        val ignore = File(folder, ".stignore")
        ignore.writeText("old\n")
        // what Syncthing does to it on Windows, which made the second start of sync fail
        runCatching { Files.setAttribute(ignore.toPath(), "dos:hidden", true) }
        writeIgnore(folder, "*.tmp\n")
        assertEquals("*.tmp\n", ignore.readText())
        folder.deleteRecursively()
    }
}
