package dev.fourthbreakfast.planner.sync

import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketException
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** The one folder the app shares. */
const val FOLDER = "planner"

/** Another device this one syncs with. */
data class Peer(val id: String, val name: String, val connected: Boolean)

/**
 * The app's own Syncthing: started from [binary] with its keys and database in [home], sharing [folder] and nothing
 * else. It only ever listens on the loopback address for its own REST interface, with a fresh key each start.
 */
class Syncthing(
    private val binary: File,
    private val home: File,
    val folder: File,
    private val ports: Ports = Ports(),
    private val log: (String) -> Unit = {},
) {
    private var process: Process? = null
    private var api: SyncthingApi? = null

    /** Where another copy on this same computer reaches this one, for tests that cannot rely on discovery. */
    val address: String get() = "tcp://127.0.0.1:${ports.listen}"

    val listenPort: Int get() = ports.listen

    /** This device's Syncthing id, known once it has started. */
    var myId: String = ""
        private set

    val running: Boolean get() = process?.isAlive == true

    /** Starts it, or does nothing when it is already running. [name] is what other devices see this one as. */
    suspend fun start(anywhere: Boolean, name: String) = withContext(Dispatchers.IO) {
        if (running) return@withContext
        home.mkdirs()
        folder.mkdirs()
        val config = File(home, "config.xml")
        if (!config.exists()) run("generate", "--home=${home.path}", "--no-port-probing")
        config.writeText(plannerConfig(config.readText(), ports, anywhere))
        writeIgnore(folder, IGNORE)

        val port = ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { it.localPort }
        val key = randomKey()
        val args = mutableListOf(
            binary.path,
            "serve",
            "--home=${home.path}",
            "--no-browser",
            "--no-restart",
            "--no-upgrade",
            "--gui-address=http://127.0.0.1:$port",
            "--log-file=${File(home, "syncthing.log").path}",
            "--log-max-size=1000000",
            "--log-max-old-files=1",
        )
        if (isWindows) args += "--no-console"
        process = ProcessBuilder(args)
            // the key goes in the environment, where other programs cannot read it as they can a command line
            .apply { environment().putAll(ENVIRONMENT + ("STGUIAPIKEY" to key)) }
            .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.to(File(home, "output.txt")))
            .start()
        val api = SyncthingApi(port, key).also { this@Syncthing.api = it }
        var waited = 0
        while (!api.alive()) {
            if (!running) throw IOException("syncthing stopped while starting: ${File(home, "output.txt").lastLines(6)}")
            if (waited++ > 50) throw IOException("syncthing did not answer")
            delay(200)
        }
        myId = retryStartupConnection { api.get("/rest/system/status").obj().string("myID") }
        retryStartupConnection { api.patch("/rest/config/devices/${encoded(myId)}", buildJsonObject { put("name", name) }) }
        // its web pages need a password nobody knows, so nothing else on this computer can use them. changing it
        // restarts the web side, at the same address, so the app waits until it answers again
        val gui = buildJsonObject {
            put("address", "127.0.0.1:$port")
            put("user", "planner")
            put("password", randomKey())
        }
        retryStartupConnection { api.patch("/rest/config/gui", gui) }
        waited = 0
        while (!api.alive()) {
            if (waited++ > 50) throw IOException("syncthing did not answer after its settings changed")
            delay(200)
        }
        retryStartupConnection { ensureFolder(api) }
        log("running as ${shortId(myId)}")
    }

    /** Stops it, letting it close its database first. */
    suspend fun stop() = withContext(Dispatchers.IO) {
        val running = process ?: return@withContext
        try {
            api?.post("/rest/system/shutdown")
        } catch (_: IOException) {
        }
        if (!running.waitFor(10, TimeUnit.SECONDS)) running.destroyForcibly()
        process = null
        api = null
    }

    private fun run(vararg command: String) {
        val done = ProcessBuilder(listOf(binary.path) + command)
            .apply { environment().putAll(ENVIRONMENT) }
            .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.to(File(home, "output.txt")))
            .start()
        if (!done.waitFor(60, TimeUnit.SECONDS)) {
            done.destroyForcibly()
            throw IOException("syncthing ${command.first()} did not finish")
        }
        if (done.exitValue() != 0) throw IOException("syncthing ${command.first()} failed")
    }

    private suspend fun ensureFolder(api: SyncthingApi) {
        try {
            api.get("/rest/config/folders/$FOLDER")
        } catch (_: SyncthingApi.NotFound) {
            api.post(
                "/rest/config/folders",
                buildJsonObject {
                    put("id", FOLDER)
                    put("label", FOLDER)
                    put("path", folder.path)
                    put("type", "sendreceive")
                    put("fsWatcherEnabled", true)
                    put("fsWatcherDelayS", 1)
                    put("rescanIntervalS", 60)
                    // phones and windows keep no unix permissions worth syncing
                    put("ignorePerms", true)
                    put("devices", buildJsonArray { add(buildJsonObject { put("deviceID", myId) }) })
                },
            )
        }
    }

    private fun api(): SyncthingApi = api ?: throw IOException("syncthing is not running")

    /** Every other device this one syncs with, and whether it is connected now. */
    suspend fun peers(): List<Peer> {
        val api = api()
        val connections = api.get("/rest/system/connections").obj()["connections"]?.obj() ?: JsonObject(emptyMap())
        return api.get("/rest/config/devices").list().map { it.obj() }
            .filter { it.string("deviceID") != myId }
            .map { device ->
                val id = device.string("deviceID")
                val connected = (connections[id] as? JsonObject)?.primitive("connected")?.booleanOrNull == true
                Peer(id, device.string("name"), connected)
            }
    }

    /** This device's addresses on its local networks, for another device to dial. */
    fun addresses(): List<String> = lanAddresses(ports.listen)

    /** Devices running the app that this one can see on the local network. */
    suspend fun nearby(): List<String> = api().get("/rest/system/discovery").obj().keys.filter { it != myId }

    /** Devices that asked to sync with this one and were not let in yet, with the name each gave itself. */
    suspend fun asking(): List<Peer> = api().get("/rest/cluster/pending/devices").obj().map { (id, details) ->
        Peer(id, details.obj().string("name"), connected = false)
    }

    /** Turns down a device that asked, so it is not shown again until it asks again. */
    suspend fun refuse(id: String) {
        api().delete("/rest/cluster/pending/devices?device=${encoded(id)}")
    }

    /**
     * Starts syncing with another device. The device that pairs by code takes the other as its [introducer]: it then
     * learns every device the other one already syncs with, so pairing a new device with any one of them is enough.
     * Only one side of a pair may introduce, so the side that lets a device in does not. A device taken away on the
     * introducing side is taken away from the devices it introduced to as well, so a lost phone can be shut out. The device is dialed at its [addresses], and found by itself
     * as well.
     */
    suspend fun pair(id: String, introducer: Boolean, addresses: List<String> = emptyList()) {
        val api = api()
        val known = peers().any { it.id == id }
        if (!known) {
            api.post(
                "/rest/config/devices",
                buildJsonObject {
                    put("deviceID", id)
                    put("addresses", buildJsonArray { (addresses + "dynamic").forEach { add(JsonPrimitive(it)) } })
                    put("introducer", introducer)
                    put("skipIntroductionRemovals", false)
                    put("autoAcceptFolders", true)
                },
            )
        }
        val shared = api.get("/rest/config/folders/$FOLDER").obj()["devices"]?.list().orEmpty()
        if (shared.none { it.obj().string("deviceID") == id }) {
            api.patch(
                "/rest/config/folders/$FOLDER",
                buildJsonObject { put("devices", JsonArray(shared + buildJsonObject { put("deviceID", id) })) },
            )
        }
    }

    /** Changes the name this device shows for the device [id], this device's own or another's. */
    suspend fun rename(id: String, name: String) {
        api().patch("/rest/config/devices/${encoded(id)}", buildJsonObject { put("name", name) })
    }

    /** Stops syncing with a device. */
    suspend fun unpair(id: String) {
        api().delete("/rest/config/devices/${encoded(id)}")
    }

    /** Whether everything has been sent to and taken from every connected device. */
    suspend fun settled(): Boolean {
        val api = api()
        val status = api.get("/rest/db/status?folder=$FOLDER").obj()
        if (status.string("state") != "idle" || (status.primitive("needTotalItems")?.longOrNull ?: 0) > 0) return false
        return peers().filter { it.connected }.all { peer ->
            val completion = api.get("/rest/db/completion?folder=$FOLDER&device=${encoded(peer.id)}").obj()
            (completion.primitive("completion")?.doubleOrNull ?: 0.0) >= 100.0
        }
    }

    /** Asks it to look at the folder now rather than when it next would. */
    suspend fun rescan() {
        api().post("/rest/db/scan?folder=$FOLDER")
    }

    private companion object {
        // the temporary file a device writes before putting its file in place is never shared
        const val IGNORE = "*.tmp\n"
        val isWindows = System.getProperty("os.name").orEmpty().startsWith("Windows")

        val ENVIRONMENT = mapOf("STNODEFAULTFOLDER" to "1", "STNOUPGRADE" to "1")

        fun randomKey(): String {
            val letters = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
            val random = SecureRandom()
            return String(CharArray(32) { letters[random.nextInt(letters.length)] })
        }
    }
}

/** A config change can briefly restart Syncthing's local API between consecutive startup requests. */
internal suspend fun <T> retryStartupConnection(block: suspend () -> T): T {
    repeat(50) {
        try {
            return block()
        } catch (_: SocketException) {
            delay(200)
        }
    }
    return block()
}

// what syncthing answers is read without trusting its shape: an answer of another shape than expected is a failure to
// talk to it, like a dropped connection, and not a fault in the app
private fun JsonElement.obj(): JsonObject = this as? JsonObject ?: throw IOException("syncthing answered something unexpected")

private fun JsonElement.list(): JsonArray = this as? JsonArray ?: throw IOException("syncthing answered something unexpected")

private fun JsonObject.primitive(name: String): JsonPrimitive? = this[name] as? JsonPrimitive

private fun JsonObject.string(name: String): String = primitive(name)?.contentOrNull.orEmpty()

/** The short form of a device id Syncthing shows people: its first seven characters. */
fun shortId(id: String): String = id.replace("-", "").take(7).uppercase()

/** Puts [text] in the folder's .stignore, written in place so a file Syncthing has hidden on Windows still takes it. */
internal fun writeIgnore(folder: File, text: String) {
    val ignore = File(folder, ".stignore")
    if (!ignore.exists() || ignore.readText() != text) java.nio.file.Files.writeString(ignore.toPath(), text)
}

/**
 * The processes that run [binary] with [home] as their home, as Linux lists them under [proc]: a Syncthing an earlier
 * run of the app left going when it was killed, which still holds the database and the port. Only processes this user
 * may see are listed, which are the only ones it may stop. Used on the phone; Windows has no /proc.
 */
fun leftBehindSyncthings(binary: File, home: File, proc: File = File("/proc")): List<Int> = proc.listFiles().orEmpty().mapNotNull { entry ->
    val pid = entry.name.toIntOrNull() ?: return@mapNotNull null
    val args = runCatching { File(entry, "cmdline").readText() }.getOrNull()?.split('\u0000') ?: return@mapNotNull null
    pid.takeIf { args.firstOrNull() == binary.path && "--home=${home.path}" in args }
}

/** The last [count] lines of a text file, on one line, for a message about why a program stopped; empty when there is no file. */
internal fun File.lastLines(count: Int): String = runCatching { readLines().takeLast(count).joinToString(" | ") }.getOrDefault("")
