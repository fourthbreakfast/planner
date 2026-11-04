package dev.fourthbreakfast.planner.sync

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Test

/** Finding a Syncthing a killed run left behind, in a stand-in for Linux's /proc. */
class LeftBehindTest {
    private val proc: File = Files.createTempDirectory("proc").toFile()
    private val binary = File("/data/app/planner/lib/arm64/libsyncthing.so")
    private val home = File("/data/user/0/dev.fourthbreakfast.planner/files/syncthing/home")

    private fun process(pid: String, vararg args: String) {
        File(proc, pid).apply { mkdirs() }.resolve("cmdline").writeText(args.joinToString("\u0000", postfix = "\u0000"))
    }

    @Test
    fun onlyThisAppsSyncthingWithThisHomeIsFound() {
        process("101", binary.path, "serve", "--home=${home.path}", "--no-browser")
        process("102", binary.path, "serve", "--home=/somewhere/else")
        process("103", "/system/bin/syncthing", "serve", "--home=${home.path}")
        process("104", "dev.fourthbreakfast.planner")
        File(proc, "self").mkdirs()
        File(proc, "105").mkdirs()
        assertEquals(listOf(101), leftBehindSyncthings(binary, home, proc))
    }

    @Test
    fun nothingLeftMeansNothingFound() {
        assertEquals(emptyList<Int>(), leftBehindSyncthings(binary, home, proc))
    }
}
