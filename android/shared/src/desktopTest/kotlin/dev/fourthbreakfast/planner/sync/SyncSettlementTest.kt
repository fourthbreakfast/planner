package dev.fourthbreakfast.planner.sync

import dev.fourthbreakfast.planner.core.MemoryEventStore
import dev.fourthbreakfast.planner.core.MemoryLedger
import dev.fourthbreakfast.planner.core.MemoryNoteStore
import dev.fourthbreakfast.planner.core.MemoryTaskStore
import dev.fourthbreakfast.planner.core.Sync
import dev.fourthbreakfast.planner.core.Task
import dev.fourthbreakfast.planner.ui.Prefs
import java.io.File
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Real wire transfer with the regular app polling coroutine deliberately paused, as it can be at worker shutdown. */
class SyncSettlementTest {
    @get:Rule val temporary = TemporaryFolder()

    private class Paused : CoroutineDispatcher() {
        private val pending = ConcurrentLinkedQueue<Runnable>()
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            pending.add(block)
        }
        fun flushCancelled() {
            while (true) (pending.poll() ?: return).run()
        }
    }

    private class MemoryPrefs : Prefs {
        private val values = mutableMapOf<String, Any>("anywhere" to false)
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
            if (value == null) values.remove(key) else values[key] = value
        }
    }

    private class BackgroundRunner(binary: File, root: File, sync: Sync, scope: CoroutineScope, ports: Ports) :
        SyncRunner(
            binary = binary, root = root, sync = sync, changes = emptyFlow(), prefs = MemoryPrefs(),
            keys = SyncPrefKeys("anywhere", "name", "paired"), defaultName = "worker", scope = scope, ports = ports, log = {},
        ) {
        suspend fun refreshStatus() = refresh()
        suspend fun finishBackgroundRun() = settled()
    }

    private fun ports(): Ports {
        val loopback = InetAddress.getByName("127.0.0.1")
        val listen = ServerSocket(0, 1, loopback).use { it.localPort }
        val announce = DatagramSocket(0, loopback).use { it.localPort }
        return Ports(listen, announce, "127.0.0.1")
    }

    @Test
    fun settlementCommitsAndRepublishesAnArrivalBeforeThePollingLoopCanRun() = runBlocking {
        val binary = System.getenv("PLANNER_SYNCTHING")?.let(::File)
        assumeTrue("Native settlement regression needs the host Syncthing binary", binary?.isFile == true)
        val root = temporary.newFolder("settlement")
        val phoneRoot = File(root, "phone")
        val phoneTasks = MemoryTaskStore()
        val phoneSync = Sync(phoneTasks, MemoryEventStore(), MemoryNoteStore(), MemoryLedger())
        val paused = Paused()
        val scope = CoroutineScope(SupervisorJob() + paused)
        val phonePorts = ports()
        val runner = BackgroundRunner(binary!!, phoneRoot, phoneSync, scope, phonePorts)
        val peer = Syncthing(binary, File(root, "pc/home"), File(root, "pc/share"), ports())
        try {
            peer.start(anywhere = false, name = "fixture pc")
            assertTrue(runner.pairWith(pairText(peer.myId, listOf(peer.address))))
            peer.pair(runner.state.value.myId, introducer = false, addresses = listOf("tcp://127.0.0.1:${phonePorts.listen}"))
            val peerTasks = MemoryTaskStore().apply { insert(listOf(Task(id = "fresh", title = "received in the final poll gap", createdAt = 1))) }
            val peerSync = Sync(peerTasks, MemoryEventStore(), MemoryNoteStore(), MemoryLedger())
            DeviceFiles(peer.folder, peer.myId, peerSync).publish()
            peer.rescan()
            val receivedFile = File(phoneRoot, "share/${peer.myId}.json")
            withTimeout(60_000) {
                while (!receivedFile.isFile || !peer.settled() || peer.peers().none { it.connected }) delay(200)
            }
            runner.refreshStatus()
            assertTrue("The normal polling coroutine must not have consumed the arrival", phoneTasks.all().isEmpty())
            withTimeout(30_000) { while (!runner.finishBackgroundRun()) delay(200) }
            assertEquals("received in the final poll gap", phoneTasks.all().single().title)
            val published = File(phoneRoot, "share/${runner.state.value.myId}.json")
            assertTrue("The committed snapshot must be published before shutdown", published.readText().contains("received in the final poll gap"))
            peer.stop()
            delay(500)
            assertTrue("The paused polling loop deliberately leaves a stale connected UI state", runner.state.value.peers.any { it.connected })
            assertFalse("A cached connection and a vacuously settled wire must not finish a background run", runner.finishBackgroundRun())
        } finally {
            scope.cancel()
            paused.flushCancelled()
            runner.stop()
            assertFalse("Stopped UI state must not retain connected peers", runner.state.value.peers.any { it.connected })
            peer.stop()
        }
    }
}
