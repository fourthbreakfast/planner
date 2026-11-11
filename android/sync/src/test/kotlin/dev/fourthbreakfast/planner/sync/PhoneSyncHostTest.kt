package dev.fourthbreakfast.planner.sync

import com.sun.net.httpserver.HttpServer
import dev.fourthbreakfast.planner.core.Task
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Explicitly selected real-phone QA fixture; uses a new directory and never reads the desktop app's preferences/data. */
class PhoneSyncHostTest {
    private data class Command(val action: String, val body: JsonObject, val reply: CompletableFuture<String>)

    @Test
    fun exchangeWithAnIsolatedQaPhone() = runBlocking {
        val path = System.getenv("PLANNER_PHONE_QA_ROOT")
        val token = System.getenv("PLANNER_PHONE_QA_RUN")
        val phoneAddress = System.getenv("PLANNER_PHONE_QA_ADDRESS")
        val binary = System.getenv("PLANNER_SYNCTHING")?.let(::File)
        assumeTrue("Real-phone QA is opt-in", path != null && token != null && phoneAddress != null && binary?.isFile == true)
        require(token!!.matches(Regex("[A-Za-z0-9_-]{8,64}")))
        require(phoneAddress!!.startsWith("tcp://"))
        val root = File(path!!).absoluteFile.apply { mkdirs() }
        require(root.listFiles().orEmpty().isEmpty()) { "QA requires a new empty fixture directory" }
        val prefix = "planner QA $token"
        val hostName = "planner QA PC $token"
        val listen = ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { it.localPort }
        val announce = ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { it.localPort }
        val pc = Device(binary!!, File(root, "device"), Ports(listen, announce, "127.0.0.1"))
        val requests = LinkedBlockingQueue<Command>()
        val executor = Executors.newCachedThreadPool { Thread(it, "phone QA control").apply { isDaemon = true } }
        val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0).apply { this.executor = executor }
        var registered = false
        var exchangeMode = "unknown"
        var periodicRequeued = false
        var acknowledged = false
        var completed = false
        fun write(name: String, body: String) = File(root, name).writeText(body)
        suspend fun publish() {
            pc.files.publish()
            pc.syncthing.rescan()
        }
        suspend fun add(id: String, title: String) {
            pc.tasks.insert(listOf(Task(id = id, title = title, createdAt = System.currentTimeMillis())))
            publish()
        }
        try {
            pc.start(hostName)
            pc.files.carryNames(pc.syncthing, hostName)
            add("qa-$token-pc", "$prefix pc")
            val host = buildJsonObject {
                put("hostId", pc.syncthing.myId)
                put("controlPort", server.address.port)
                put("run", token)
            }.toString()
            server.createContext("/qa/$token/") { exchange ->
                try {
                    val action = exchange.requestURI.path.removePrefix("/qa/$token/")
                    val response = if (action == "host" && exchange.requestMethod == "GET") {
                        host
                    } else {
                        check(exchange.requestMethod == "POST")
                        val body = Json.parseToJsonElement(exchange.requestBody.bufferedReader().use { it.readText() }).jsonObject
                        val reply = CompletableFuture<String>()
                        requests.put(Command(action, body, reply))
                        reply.get(25, TimeUnit.SECONDS)
                    }
                    val bytes = response.toByteArray()
                    exchange.responseHeaders.set("Content-Type", "application/json")
                    exchange.sendResponseHeaders(200, bytes.size.toLong())
                    exchange.responseBody.use { it.write(bytes) }
                } catch (failure: Exception) {
                    val bytes = failure.toString().toByteArray()
                    runCatching {
                        exchange.sendResponseHeaders(500, bytes.size.toLong())
                        exchange.responseBody.use { it.write(bytes) }
                    }
                } finally {
                    exchange.close()
                }
            }
            server.start()
            write("host.json", host)
            write("stage.txt", "ready")
            withTimeout(30 * 60_000L) {
                while (!completed) {
                    val command = requests.poll()
                    if (command != null) {
                        try {
                            when (command.action) {
                                "phone" -> {
                                    val id = command.body.getValue("phoneId").jsonPrimitive.content
                                    require(id.replace("-", "").length == 56) { "A full native phone device ID is required" }
                                    exchangeMode = command.body.getValue("exchangeMode").jsonPrimitive.content
                                    require(exchangeMode in setOf("headless", "foreground"))
                                    pc.syncthing.pair(id, introducer = false, addresses = listOf(phoneAddress))
                                    registered = true
                                    write("stage.txt", "paired $id")
                                }
                                "background-ready" -> {
                                    assertTrue("Complete foreground verification first", acknowledged)
                                    periodicRequeued = command.body["periodicRequeued"]?.jsonPrimitive?.booleanOrNull == true
                                    add("qa-$token-background", "$prefix background")
                                    write("stage.txt", "background-ready")
                                }
                                "direct-background" -> {
                                    assertTrue(acknowledged)
                                    add("qa-$token-direct", "$prefix direct")
                                    write("stage.txt", "direct-background-ready")
                                }
                                "complete" -> {
                                    val mode = command.body.getValue("mode").jsonPrimitive.content
                                    require(mode in setOf("scheduled-worker", "direct-run-once"))
                                    assertTrue(pc.tasks.state.value.any { it.id == "qa-$token-pc" && it.done })
                                    assertTrue(pc.tasks.state.value.any { it.title == "$prefix phone" && it.starred })
                                    write(
                                        "result.json",
                                        buildJsonObject {
                                            put("passed", true)
                                            put("mode", mode)
                                            put("exchangeMode", exchangeMode)
                                            put("periodicRequeued", periodicRequeued)
                                            put("run", token)
                                        }.toString(),
                                    )
                                    write("stage.txt", "passed $mode")
                                    completed = true
                                }
                                "failed" -> error("Phone driver failed: ${command.body}")
                                else -> error("Unknown QA control action: ${command.action}")
                            }
                            command.reply.complete("{\"ok\":true}")
                        } catch (failure: Throwable) {
                            command.reply.completeExceptionally(failure)
                            throw failure
                        }
                    }
                    pc.files.takeIn()
                    pc.files.carryNames(pc.syncthing, hostName)
                    if (registered &&
                        !acknowledged &&
                        pc.tasks.state.value.any { it.id == "qa-$token-pc" && it.done } &&
                        pc.tasks.state.value.any { it.title == "$prefix phone" && it.starred }
                    ) {
                        add("qa-$token-ack", "$prefix foreground acknowledged")
                        acknowledged = true
                        write("stage.txt", "foreground-verified")
                    }
                    delay(500)
                }
            }
        } catch (failure: Throwable) {
            write("failure.txt", failure.stackTraceToString())
            throw failure
        } finally {
            server.stop(0)
            executor.shutdownNow()
            pc.syncthing.stop()
        }
    }
}
