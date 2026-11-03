package dev.fourthbreakfast.planner.sync

import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
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
        // the temporary file a device writes before putting its file in place is never shared
        File(folder, ".stignore").writeText("*.tmp\n")

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
            "--gui-apikey=$key",
            "--log-file=${File(home, "syncthing.log").path}",
            "--log-max-size=1000000",
            "--log-max-old-files=1",
        )
        if (isWindows) args += "--no-console"
        process = ProcessBuilder(args)
            .apply { environment().putAll(ENVIRONMENT) }
            .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.to(File(home, "output.txt")))
            .start()
        val api = SyncthingApi(port, key).also { this@Syncthing.api = it }
        var waited = 0
        while (!api.alive()) {
            if (!running) throw IOException("syncthing stopped while starting")
            if (waited++ > 150) throw IOException("syncthing did not start")
            delay(200)
        }
        log("answering")
        myId = api.get("/rest/system/status").jsonObject.string("myID")
        log("id $myId")
        api.patch("/rest/config/devices/${encoded(myId)}", buildJsonObject { put("name", name) })
        log("named")
        ensureFolder(api)
        log("folder ready")
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
        if (!done.waitFor(60, TimeUnit.SECONDS) || done.exitValue() != 0) throw IOException("syncthing ${command.first()} failed")
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
        val connections = api.get("/rest/system/connections").jsonObject["connections"]?.jsonObject ?: JsonObject(emptyMap())
        return api.get("/rest/config/devices").jsonArray.map { it.jsonObject }
            .filter { it.string("deviceID") != myId }
            .map { device ->
                val id = device.string("deviceID")
                val connected = connections[id]?.jsonObject?.get("connected")?.jsonPrimitive?.boolean == true
                Peer(id, device.string("name"), connected)
            }
    }

    /** Devices running the app that this one can see on the local network. */
    suspend fun nearby(): List<String> = api().get("/rest/system/discovery").jsonObject.keys.filter { it != myId }

    /** Devices that asked to sync with this one and were not added yet. */
    suspend fun asking(): List<String> = api().get("/rest/cluster/pending/devices").jsonObject.keys.toList()

    /**
     * Starts syncing with another device. Each side introduces the devices it knows to the other, so pairing one new
     * device with any one of the others is enough for it to reach all of them. The [address] is found by itself unless given.
     */
    suspend fun pair(id: String, address: String = "dynamic") {
        val api = api()
        val known = peers().any { it.id == id }
        if (!known) {
            api.post(
                "/rest/config/devices",
                buildJsonObject {
                    put("deviceID", id)
                    put("addresses", buildJsonArray { add(JsonPrimitive(address)) })
                    put("introducer", true)
                    put("skipIntroductionRemovals", true)
                    put("autoAcceptFolders", true)
                },
            )
        }
        val shared = api.get("/rest/config/folders/$FOLDER").jsonObject["devices"]?.jsonArray.orEmpty()
        if (shared.none { it.jsonObject.string("deviceID") == id }) {
            api.patch(
                "/rest/config/folders/$FOLDER",
                buildJsonObject { put("devices", JsonArray(shared + buildJsonObject { put("deviceID", id) })) },
            )
        }
    }

    /** Stops syncing with a device. */
    suspend fun unpair(id: String) {
        api().delete("/rest/config/devices/${encoded(id)}")
    }

    /** Whether everything has been sent to and taken from every connected device. */
    suspend fun settled(): Boolean {
        val api = api()
        val status = api.get("/rest/db/status?folder=$FOLDER").jsonObject
        if (status.string("state") != "idle" || (status["needTotalItems"]?.jsonPrimitive?.long ?: 0) > 0) return false
        return peers().filter { it.connected }.all { peer ->
            val completion = api.get("/rest/db/completion?folder=$FOLDER&device=${encoded(peer.id)}").jsonObject
            (completion["completion"]?.jsonPrimitive?.double ?: 0.0) >= 100.0
        }
    }

    /** Asks it to look at the folder now rather than when it next would. */
    suspend fun rescan() {
        api().post("/rest/db/scan?folder=$FOLDER")
    }

    private companion object {
        val isWindows = System.getProperty("os.name").orEmpty().startsWith("Windows")

        val ENVIRONMENT = mapOf("STNODEFAULTFOLDER" to "1", "STNOUPGRADE" to "1")

        fun randomKey(): String {
            val letters = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
            val random = SecureRandom()
            return String(CharArray(32) { letters[random.nextInt(letters.length)] })
        }
    }
}

private fun JsonObject.string(name: String): String = this[name]?.jsonPrimitive?.contentOrNull.orEmpty()

/** The short form of a device id Syncthing shows people: its first seven characters. */
fun shortId(id: String): String = id.replace("-", "").take(7).uppercase()
