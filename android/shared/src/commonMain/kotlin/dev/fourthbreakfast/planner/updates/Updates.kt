package dev.fourthbreakfast.planner.updates

import dev.fourthbreakfast.planner.ui.Prefs
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val LATEST = "https://api.github.com/repos/fourthbreakfast/planner/releases/latest"
private const val PREF_DAILY = "update_daily"
private const val PREF_LAST = "update_last_check"
private const val DAY_MILLIS = 24 * 60 * 60 * 1000L
private const val MAX_RELEASE_BYTES = 1024 * 1024
private const val MAX_INSTALLER_BYTES = 512L * 1024 * 1024
private val SHA256 = Regex("[0-9a-fA-F]{64}")

/** One file of a release: where it is, and the checksum github gives for it. */
data class Asset(val url: String, val sha256: String?)

/** A published version, with its phone and windows files. */
data class Release(val version: String, val apk: Asset?, val msi: Asset?, val setup: Asset? = null)

/** Whether [candidate] ("0.9.0" or "v0.9.0") is a later version than [current]. */
fun isNewer(candidate: String, current: String): Boolean {
    fun parts(version: String) = version.trim().removePrefix("v").split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
    val a = parts(candidate)
    val b = parts(current)
    for (i in 0 until maxOf(a.size, b.size)) {
        val x = a.getOrElse(i) { 0 }
        val y = b.getOrElse(i) { 0 }
        if (x != y) return x > y
    }
    return false
}

/** What a release page on GitHub says, read into a [Release]. */
fun parseRelease(json: String): Release? {
    val root = runCatching { Json.parseToJsonElement(json) }.getOrNull() as? JsonObject ?: return null
    val tag = root.text("tag_name").takeIf { it.isNotBlank() } ?: return null
    val assets = (root["assets"] as? JsonArray)?.mapNotNull { it as? JsonObject } ?: return null

    fun asset(ending: String): Asset? {
        val found = assets.firstOrNull { it.text("name").endsWith(ending) } ?: return null
        // only ever fetched over https: anything else is no file to download
        val url = found.text("browser_download_url").takeIf { httpsUri(it) != null } ?: return null
        val digest = found.text("digest").takeIf { it.startsWith("sha256:") }
            ?.removePrefix("sha256:")?.takeIf(SHA256::matches)
        return Asset(url, digest)
    }
    return Release(tag.removePrefix("v"), asset(".apk"), asset(".msi"), asset("-setup.exe"))
}

private fun JsonObject.text(name: String): String = (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content.orEmpty()

/** Where updating stands, for the settings and the status bar to show. */
sealed interface UpdateState {
    data object Unknown : UpdateState

    data object Checking : UpdateState

    data object UpToDate : UpdateState

    data class Downloading(val release: Release) : UpdateState

    /** The new version is downloaded, checked and waiting to be installed. */
    data class Ready(val release: Release, val file: File) : UpdateState

    data object Failed : UpdateState
}

/**
 * Keeps the app up to date from its GitHub releases: once a day on its own, unless that is switched off, and whenever
 * asked. A newer version is downloaded in the background into [folder], the app's own, and checked against the
 * checksum GitHub publishes; only then is it ready to install. [pick] chooses this device's file from a release. It is
 * the only thing the app sends out besides sync, and it sends nothing about the person, only requests for the files.
 */
class Updates internal constructor(
    private val prefs: Prefs,
    private val current: String,
    private val folder: File,
    private val pick: (Release) -> Asset?,
    private val clock: () -> Long,
    private val transport: UpdateTransport,
    private val maxInstallerBytes: Long = MAX_INSTALLER_BYTES,
) {
    constructor(
        prefs: Prefs,
        current: String,
        folder: File,
        pick: (Release) -> Asset?,
        clock: () -> Long = System::currentTimeMillis,
    ) : this(prefs, current, folder, pick, clock, HttpsUpdateTransport())

    private val now = MutableStateFlow<UpdateState>(UpdateState.Unknown)
    private val checking = Mutex()
    val state: StateFlow<UpdateState> = now

    var daily: Boolean
        get() = prefs.getBoolean(PREF_DAILY, true)
        set(value) = prefs.putBoolean(PREF_DAILY, value)

    /** Checks if the daily check is on and the last one was a day ago or more. */
    suspend fun checkIfDue() {
        check(onlyIfDue = true)
    }

    suspend fun check() {
        check(onlyIfDue = false)
    }

    private suspend fun check(onlyIfDue: Boolean) {
        if (!checking.tryLock()) return
        try {
            if (now.value is UpdateState.Ready) return
            if (onlyIfDue && (!daily || clock() - prefs.getLong(PREF_LAST, 0) < DAY_MILLIS)) return
            now.value = UpdateState.Checking
            val release = withContext(Dispatchers.IO) {
                transport.open(LATEST, json = true).use { input ->
                    val bytes = input.readNBytesLimited(MAX_RELEASE_BYTES)
                    parseRelease(bytes.toString(Charsets.UTF_8))
                }
            }
            prefs.putLong(PREF_LAST, clock())
            val asset = release?.let(pick)
            now.value = when {
                release == null -> UpdateState.Failed
                !isNewer(release.version, current) -> UpdateState.UpToDate
                asset == null -> UpdateState.Failed
                else -> download(release, asset)
            }
        } catch (cancelled: CancellationException) {
            now.value = UpdateState.Unknown
            throw cancelled
        } catch (_: IOException) {
            prefs.putLong(PREF_LAST, clock())
            now.value = UpdateState.Failed
        } finally {
            checking.unlock()
        }
    }

    private suspend fun download(release: Release, asset: Asset): UpdateState {
        now.value = UpdateState.Downloading(release)
        var partial: File? = null
        var verified: File? = null
        try {
            val ready = withContext(Dispatchers.IO) {
                val checksum = asset.sha256?.takeIf(SHA256::matches) ?: throw IOException("missing checksum")
                val uri = httpsUri(asset.url) ?: throw IOException("invalid update URL")
                val extension = uri.path.substringAfterLast('.').takeIf { it in setOf("apk", "msi", "exe") }
                    ?: throw IOException("invalid installer type")
                if (!folder.isDirectory && !folder.mkdirs()) throw IOException("cannot create update folder")
                folder.listFiles().orEmpty().filter {
                    it.isFile && Regex("planner-update-[0-9]+\\.(part|apk|msi|exe)").matches(it.name)
                }.forEach { it.delete() }
                val temp = File.createTempFile("planner-update-", ".part", folder).also { partial = it }
                val digest = MessageDigest.getInstance("SHA-256")
                var size = 0L
                transport.open(asset.url, json = false).use { input ->
                    temp.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            if (size + count > maxInstallerBytes) throw IOException("installer too large")
                            output.write(buffer, 0, count)
                            digest.update(buffer, 0, count)
                            size += count
                        }
                    }
                }
                if (size == 0L || digest.digest().hex() != checksum.lowercase()) throw IOException("checksum differs")
                val file = File(folder, "${temp.nameWithoutExtension}.$extension")
                if (!temp.renameTo(file)) throw IOException("cannot save verified update")
                verified = file
                UpdateState.Ready(release, file)
            }
            verified = null
            return ready
        } finally {
            partial?.delete()
            verified?.delete()
        }
    }
}

private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }

private suspend fun InputStream.readNBytesLimited(limit: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        currentCoroutineContext().ensureActive()
        val count = read(buffer)
        if (count < 0) return output.toByteArray()
        if (output.size() + count > limit) throw IOException("release metadata too large")
        output.write(buffer, 0, count)
    }
}
