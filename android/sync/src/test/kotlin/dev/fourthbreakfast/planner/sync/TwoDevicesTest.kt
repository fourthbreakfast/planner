package dev.fourthbreakfast.planner.sync

import dev.fourthbreakfast.planner.core.Gone
import dev.fourthbreakfast.planner.core.MemoryEventStore
import dev.fourthbreakfast.planner.core.MemoryLedger
import dev.fourthbreakfast.planner.core.MemoryNoteStore
import dev.fourthbreakfast.planner.core.MemoryTaskStore
import dev.fourthbreakfast.planner.core.Note
import dev.fourthbreakfast.planner.core.Sync
import dev.fourthbreakfast.planner.core.Task
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

internal class Device(binary: File, root: File, ports: Ports) {
    private val ledger = MemoryLedger()
    val tasks = MemoryTaskStore { deleted -> ledger.gone += deleted.map { Gone(it.id, System.currentTimeMillis()) } }
    val events = MemoryEventStore()
    val notes = MemoryNoteStore()

    val sync = Sync(tasks, events, notes, ledger)
    val syncthing = Syncthing(binary, File(root, "home"), File(root, "share"), ports)
    lateinit var files: DeviceFiles

    suspend fun start(name: String) {
        syncthing.start(anywhere = false, name = name)
        files = DeviceFiles(syncthing.folder, syncthing.myId, sync)
    }

    suspend fun deleteTask(id: String) = tasks.delete(tasks.state.value.filter { it.id == id })
}

/**
 * Two real Syncthings on this computer, each with its own home, folder and ports, paired by address. It needs a
 * Syncthing built for this computer, named by PLANNER_SYNCTHING, and is skipped without one.
 */
class TwoDevicesTest {
    private val binary = System.getenv("PLANNER_SYNCTHING")?.let(::File)

    private suspend fun until(what: String, check: suspend () -> Boolean) = withTimeout(60_000) {
        while (!check()) delay(250)
    }.also { println("settled: $what") }

    @Test
    fun aChangeOnOneReachesTheOther() = runBlocking {
        assumeTrue(binary?.exists() == true)
        val root = Files.createTempDirectory("planner-sync").toFile()
        val pc = Device(binary!!, File(root, "pc"), Ports(22191, 21191, "127.0.0.1"))
        val phone = Device(binary, File(root, "phone"), Ports(22192, 21192, "127.0.0.1"))
        try {
            pc.start("pc")
            phone.start("phone")
            pc.syncthing.pair(phone.syncthing.myId, introducer = true, addresses = listOf(phone.syncthing.address))
            phone.syncthing.pair(pc.syncthing.myId, introducer = false, addresses = listOf(pc.syncthing.address))
            until("connected") { pc.syncthing.peers().all { it.connected } && phone.syncthing.peers().all { it.connected } }
            // a device tells its id to anyone who starts a connection, which is how one is found without broadcasts
            assertEquals(phone.syncthing.myId, deviceIdAt("127.0.0.1", 22192))
            assertEquals(listOf("phone"), pc.syncthing.peers().map { it.name })

            // a device is named on itself, and the others call it that
            phone.files.carryNames(phone.syncthing, "kalypso")
            until("the name arrives") {
                pc.files.carryNames(pc.syncthing, "pc")
                pc.syncthing.peers().map { it.name } == listOf("kalypso")
            }

            pc.tasks.insert(listOf(Task(id = "t", title = "buy milk", createdAt = 1)))
            pc.files.publish()
            until("task arrived") {
                phone.files.takeIn()
                phone.tasks.state.value.isNotEmpty()
            }
            assertEquals("buy milk", phone.tasks.state.value.single().title)

            phone.deleteTask("t")
            phone.notes.insert(listOf(Note(id = "n", body = "from the phone", createdAt = 2)))
            phone.files.publish()
            until("delete and note arrived") {
                pc.files.takeIn()
                pc.tasks.state.value.isEmpty() && pc.notes.state.value.isNotEmpty()
            }
            assertEquals("from the phone", pc.notes.state.value.single().body)
        } finally {
            pc.syncthing.stop()
            phone.syncthing.stop()
            root.deleteRecursively()
        }
    }

    @Test
    fun aDeviceThatAsksWaitsForAnAnswer() = runBlocking {
        assumeTrue(binary?.exists() == true)
        val root = Files.createTempDirectory("planner-ask").toFile()
        val pc = Device(binary!!, File(root, "pc"), Ports(22193, 21193, "127.0.0.1"))
        val phone = Device(binary, File(root, "phone"), Ports(22194, 21194, "127.0.0.1"))
        try {
            pc.start("pc")
            phone.start("phone")
            pc.syncthing.pair(phone.syncthing.myId, introducer = true, addresses = listOf(phone.syncthing.address))
            until("the phone sees the pc asking") { phone.syncthing.asking().any { it.id == pc.syncthing.myId && it.name == "pc" } }
            // nothing is let in by itself
            assertEquals(emptyList<Peer>(), phone.syncthing.peers())
            phone.syncthing.refuse(pc.syncthing.myId)
            assertEquals(emptyList<Peer>(), phone.syncthing.asking())
        } finally {
            pc.syncthing.stop()
            phone.syncthing.stop()
            root.deleteRecursively()
        }
    }
}
