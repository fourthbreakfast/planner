package dev.fourthbreakfast.planner.sync

import dev.fourthbreakfast.planner.core.Event
import dev.fourthbreakfast.planner.core.EventStore
import dev.fourthbreakfast.planner.core.Gone
import dev.fourthbreakfast.planner.core.Note
import dev.fourthbreakfast.planner.core.NoteStore
import dev.fourthbreakfast.planner.core.Sync
import dev.fourthbreakfast.planner.core.SyncLedger
import dev.fourthbreakfast.planner.core.Task
import dev.fourthbreakfast.planner.core.TaskStore
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Keeps things in memory, which is all a sync test needs from a store. */
internal class Memory<T>(private val id: (T) -> String) {
    val state = MutableStateFlow<List<T>>(emptyList())

    fun insert(items: List<T>) {
        state.value = state.value + items
    }

    fun update(items: List<T>) {
        val changed = items.associateBy(id)
        state.value = state.value.map { changed[id(it)] ?: it }
    }

    fun delete(items: List<T>) {
        val gone = items.mapTo(HashSet(), id)
        state.value = state.value.filterNot { id(it) in gone }
    }
}

internal class Device(binary: File, root: File, ports: Ports) {
    val tasks = Memory<Task> { it.id }
    val events = Memory<Event> { it.id }
    val notes = Memory<Note> { it.id }
    val gone = ArrayList<Gone>()
    val agreed = HashMap<String, Long>()

    private val taskStore = object : TaskStore {
        override fun observeAll(): Flow<List<Task>> = tasks.state

        override suspend fun all() = tasks.state.value

        override suspend fun insert(tasks: List<Task>) = this@Device.tasks.insert(tasks)

        override suspend fun update(tasks: List<Task>) = this@Device.tasks.update(tasks)

        override suspend fun delete(tasks: List<Task>) {
            this@Device.tasks.delete(tasks)
            gone += tasks.map { Gone(it.id, System.currentTimeMillis()) }
        }

        override suspend fun <T> transaction(block: suspend () -> T): T = block()
    }

    private val eventStore = object : EventStore {
        override fun observeAll(): Flow<List<Event>> = events.state

        override suspend fun all() = events.state.value

        override suspend fun insert(events: List<Event>) = this@Device.events.insert(events)

        override suspend fun update(events: List<Event>) = this@Device.events.update(events)

        override suspend fun delete(events: List<Event>) = this@Device.events.delete(events)

        override suspend fun <T> transaction(block: suspend () -> T): T = block()
    }

    private val noteStore = object : NoteStore {
        override fun observeAll(): Flow<List<Note>> = notes.state

        override suspend fun all() = notes.state.value

        override suspend fun insert(notes: List<Note>) = this@Device.notes.insert(notes)

        override suspend fun update(notes: List<Note>) = this@Device.notes.update(notes)

        override suspend fun delete(notes: List<Note>) = this@Device.notes.delete(notes)

        override suspend fun <T> transaction(block: suspend () -> T): T = block()
    }

    private val ledger = object : SyncLedger {
        override suspend fun gone() = gone.toList()

        override suspend fun addGone(items: List<Gone>) {
            gone.removeAll { old -> items.any { it.id == old.id } }
            gone += items
        }

        override suspend fun agreed() = agreed.toMap()

        override suspend fun setAgreed(items: Map<String, Long>) {
            agreed += items
        }
    }

    val sync = Sync(taskStore, eventStore, noteStore, ledger)
    val syncthing = Syncthing(binary, File(root, "home"), File(root, "share"), ports)
    lateinit var files: DeviceFiles

    suspend fun start(name: String) {
        syncthing.start(anywhere = false, name = name)
        files = DeviceFiles(syncthing.folder, syncthing.myId, sync)
    }

    suspend fun deleteTask(id: String) = taskStore.delete(tasks.state.value.filter { it.id == id })
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
            pc.syncthing.pair(phone.syncthing.myId, introducer = true, address = phone.syncthing.address)
            phone.syncthing.pair(pc.syncthing.myId, introducer = false, address = pc.syncthing.address)
            until("connected") { pc.syncthing.peers().all { it.connected } && phone.syncthing.peers().all { it.connected } }
            assertEquals(listOf("phone"), pc.syncthing.peers().map { it.name })

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
            pc.syncthing.pair(phone.syncthing.myId, introducer = true, address = phone.syncthing.address)
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
