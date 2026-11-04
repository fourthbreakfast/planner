package dev.fourthbreakfast.planner.sync

import dev.fourthbreakfast.planner.core.Task
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Stands in for a computer next to a real phone: pairs with the phone named by PLANNER_PEER_ID at PLANNER_PEER_ADDRESS,
 * sends it one task and prints what the phone sends back. Run by hand with the phone's sync screen open; skipped
 * otherwise.
 */
class PhonePeerTest {
    @Test
    fun tradeWithThePhone() = runBlocking {
        val binary = System.getenv("PLANNER_SYNCTHING")?.let(::File)
        val id = System.getenv("PLANNER_PEER_ID")
        val address = System.getenv("PLANNER_PEER_ADDRESS")
        assumeTrue(binary?.exists() == true && id != null && address != null)
        val root = Files.createTempDirectory("planner-peer").toFile()
        val pc = Device(binary!!, root, Ports(22101, 21101, "127.0.0.1"))
        try {
            pc.start("test pc")
            pc.syncthing.pair(id, introducer = true, addresses = listOf(address))
            withTimeout(120_000) { while (pc.syncthing.peers().none { it.connected }) delay(500) }
            println("connected to ${pc.syncthing.peers().map { it.name }}")
            pc.tasks.insert(listOf(Task(id = "from-the-pc", title = "from the pc", createdAt = System.currentTimeMillis())))
            withTimeout(120_000) {
                while (pc.syncthing.folder.listFiles().orEmpty().none { it.name.endsWith(".json") && !it.name.startsWith(pc.syncthing.myId) }) {
                    pc.files.publish()
                    pc.files.takeIn()
                    delay(500)
                }
            }
            pc.files.takeIn()
            println("tasks here now: ${pc.tasks.state.value.map { it.title }}")
            // a while longer, so the phone can take the task in and show it
            repeat(40) {
                pc.files.publish()
                pc.files.takeIn()
                delay(500)
            }
        } finally {
            pc.syncthing.stop()
            root.deleteRecursively()
        }
    }
}
