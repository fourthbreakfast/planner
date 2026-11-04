package dev.fourthbreakfast.planner.sync

import dev.fourthbreakfast.planner.core.MemoryEventStore
import dev.fourthbreakfast.planner.core.MemoryLedger
import dev.fourthbreakfast.planner.core.MemoryNoteStore
import dev.fourthbreakfast.planner.core.MemoryTaskStore
import dev.fourthbreakfast.planner.core.Sync
import dev.fourthbreakfast.planner.core.Task
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The files in the shared folder, without Syncthing: what Syncthing would carry is put in the folder by hand. */
class DeviceFilesTest {
    private val folder: File = Files.createTempDirectory("planner-files").toFile()

    private class Device {
        val tasks = MemoryTaskStore()
        val sync = Sync(tasks, MemoryEventStore(), MemoryNoteStore(), MemoryLedger())
    }

    private val phone = Device()
    private val files = DeviceFiles(folder, "PHONE", phone.sync)

    /** Another device's file, as Syncthing would leave it in the folder. */
    private fun fileFrom(id: String, vararg tasks: Task) = runBlocking {
        val other = Device()
        other.tasks.insert(tasks.toList())
        File(folder, "$id.json").writeText(other.sync.snapshot())
    }

    @Test
    fun publishingWritesThisDevicesFileAndNothingElse() = runBlocking {
        phone.tasks.insert(listOf(Task(id = "t", title = "buy milk", createdAt = 1)))
        files.publish()
        assertEquals(listOf("PHONE.json"), folder.list()!!.toList())
        assertTrue(File(folder, "PHONE.json").readText().contains("buy milk"))
    }

    @Test
    fun anotherDevicesFileIsTakenInOnceUntilItChanges() = runBlocking {
        fileFrom("PC", Task(id = "a", title = "from the pc", createdAt = 1))
        assertEquals(1, files.takeIn())
        assertEquals(0, files.takeIn())
        assertEquals(listOf("from the pc"), phone.tasks.all().map { it.title })
    }

    @Test
    fun itsOwnFileIsNeverTakenIn() = runBlocking {
        fileFrom("PHONE", Task(id = "a", title = "an old copy of itself", createdAt = 1))
        assertEquals(0, files.takeIn())
        assertTrue(phone.tasks.all().isEmpty())
    }

    @Test
    fun aHalfWrittenFileIsLeftForLater() = runBlocking {
        File(folder, "PC.json").writeText("{\"app\": \"planner\", \"tasks\": [")
        assertEquals(0, files.takeIn())
        fileFrom("PC", Task(id = "a", title = "whole now", createdAt = 1))
        assertEquals(1, files.takeIn())
        assertFalse(phone.tasks.all().isEmpty())
    }
}
